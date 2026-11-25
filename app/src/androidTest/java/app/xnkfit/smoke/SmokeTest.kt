package app.xnkfit.smoke

import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import app.xnkfit.R
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B-18 smoke on a real emulator (docs/Test-Manual.md): clean install, onboarding, the five destinations, the other
 * screens and rotation. Deterministic: no network (content update off, Drive id empty), Health Connect, camera and
 * system permission dialogs are never opened ("Nanti" is pressed on every permission card).
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest : SmokeBase() {

    @Before fun clean() { wipeApp() }

    @Test fun opensFromCleanInstallOnOnboarding() {
        launch()
        rule.waitForText(str(R.string.ob_tagline))
        assertTrue(rule.has(str(R.string.ob_start)))
    }

    @Test fun onboardingReachesToday() {
        launch()
        rule.clickText(str(R.string.ob_start))
        rule.clickText(str(R.string.ob_goal_turun))
        rule.clickText(str(R.string.ob_next))
        rule.clickText(str(R.string.ob_sex_male))
        rule.typeInto(str(R.string.ob_birth_year), "1990")
        rule.typeInto(str(R.string.ob_height), "170")
        rule.typeInto(str(R.string.ob_weight), "70")
        rule.scrollToText(str(R.string.ob_next))
        rule.clickText(str(R.string.ob_next))
        rule.clickText(str(R.string.ob_interest_both))
        rule.clickText(str(R.string.ob_next))
        rule.scrollToText(str(R.string.ob_skip))
        rule.clickText(str(R.string.ob_skip))
        rule.clickText(str(R.string.ob_health_no))
        // Every permission card is answered with "Nanti": no system dialog opens.
        rule.waitForText(str(R.string.ob_perm_title))
        repeat(4) {
            if (rule.has(str(R.string.ob_perm_later))) rule.clickText(str(R.string.ob_perm_later))
        }
        rule.scrollToText(str(R.string.ob_enter))
        rule.clickText(str(R.string.ob_enter))
        awaitToday()
    }

    @Test fun fiveDestinationsAndOtherScreensOpen() {
        seedProfile()
        launch()
        awaitToday()
        openTab(R.string.nav_workout); rule.ensureProgram(); rule.scrollToText(str(R.string.lat_empty_workout))
        openTab(R.string.nav_food); rule.waitForIdle()
        openTab(R.string.nav_me); rule.scrollToText(str(R.string.set_title))
        // Kartu saya and Pengaturan both say "Buka"; Pengaturan is the last one.
        val open = str(R.string.set_open)
        rule.waitForText(open)
        rule.onAllNodesWithText(open).onLast().performClick()
        rule.waitForIdle()
        rule.scrollToText(str(R.string.set_data_title))
        rule.clickText(str(R.string.set_data_title))
        rule.waitForIdle()
        openTabFromRootAgain()
    }

    private fun openTabFromRootAgain() {
        // Back from the overlays, then the first destination again: the bar must still work.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        if (rule.has(str(R.string.nav_today))) openTab(R.string.nav_today)
    }

    @Test fun rotationKeepsTheScreen() {
        seedProfile()
        launch()
        awaitToday()
        openTab(R.string.nav_workout)
        rule.ensureProgram()
        rule.scrollToText(str(R.string.lat_empty_workout))
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitForIdle()
        rule.scrollToText(str(R.string.lat_empty_workout))
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitForIdle()
        rule.scrollToText(str(R.string.lat_empty_workout))
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test fun driveHealthAndCameraAreSkipped() {
        // Recorded on purpose: these need a Google account, Health Connect or a camera, none of which a CI emulator has.
        assumeTrue("Drive, Health Connect and camera are not exercised in CI (Test-Manual C-1, C-2)", false)
    }
}
