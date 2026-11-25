package app.xnkfit.smoke.kill

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.xnkfit.R
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.smoke.SmokeBase
import app.xnkfit.smoke.clickDesc
import app.xnkfit.smoke.clickText
import app.xnkfit.smoke.ctx
import app.xnkfit.smoke.has
import app.xnkfit.smoke.hasDesc
import app.xnkfit.smoke.seedProfile
import app.xnkfit.smoke.str
import app.xnkfit.smoke.typeInto
import app.xnkfit.smoke.waitForText
import app.xnkfit.smoke.wipeApp
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * K-1 on an emulator. `am force-stop` kills the instrumentation too, so the kill is made by the workflow between two
 * runs of these tests: `phase1Writes` (run first), then `am force-stop app.xnkfit`, then `phase2SetSurvives` in a new process.
 */
@RunWith(AndroidJUnit4::class)
class LoggerKillTest : SmokeBase() {

    private fun doneSets(): Int {
        val db = XnkDatabase.open(ctx, "smoke").openHelper.readableDatabase
        return db.query("SELECT COUNT(*) FROM workout_set WHERE done = 1").use { it.moveToFirst(); it.getInt(0) }
    }

    @Test fun phase1Writes() {
        wipeApp(); seedProfile()
        launch()
        rule.waitForText(str(R.string.nav_today))
        rule.clickText(str(R.string.nav_workout))
        rule.clickText(str(R.string.lat_empty_workout))
        // First run: the mode choice appears; later runs go straight to the workout.
        rule.waitUntil(30_000) { rule.has(str(R.string.lg_mode_bebas)) || rule.has(str(R.string.lg_add_exercise)) }
        if (rule.has(str(R.string.lg_mode_bebas))) {
            rule.clickText(str(R.string.lg_mode_bebas))
            rule.clickText(str(R.string.lg_start))
        }
        rule.waitForText(str(R.string.lg_add_exercise))
        rule.clickText(str(R.string.lg_add_exercise))
        rule.typeInto(str(R.string.lg_search), "Back Squat")
        rule.clickText("Back Squat")
        rule.waitUntil(30_000) { rule.hasDesc(str(R.string.lg_tick)) }
        // Expand the first row and fill what a tick needs (weight and reps), then tick.
        rule.onAllNodesWithText(str(R.string.lg_prev_none), substring = true).onFirst().performClick()
        rule.waitForIdle()
        repeat(2) { if (rule.hasDesc(str(R.string.lg_weight))) rule.clickDesc("${str(R.string.lg_plus)} ${str(R.string.lg_weight)}") }
        repeat(2) { if (rule.hasDesc(str(R.string.lg_reps))) rule.clickDesc("${str(R.string.lg_plus)} ${str(R.string.lg_reps)}") }
        rule.clickDesc(str(R.string.lg_tick))
        rule.waitUntil(30_000) { doneSets() >= 1 }
        assertTrue(doneSets() >= 1)
    }

    @Test fun phase2SetSurvives() {
        // New process after force-stop: nothing is wiped or seeded here.
        assertTrue("phase 1 did not leave a done set", doneSets() >= 1)
        val before = doneSets()
        launch()
        rule.waitForText(str(R.string.lg_resume))
        rule.clickText(str(R.string.lg_resume))
        rule.waitForText(str(R.string.lg_finish))
        assertEquals(before, doneSets())
    }
}
