package app.xnkfit.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.xnkfit.data.db.ExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.xnkfit.R
import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.domain.logger.LoggerMode
import app.xnkfit.ui.common.XnkTheme
import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.ui.logger.AlarmAccess
import app.xnkfit.ui.logger.LoggerPrefs
import app.xnkfit.ui.logger.LoggerScreen
import app.xnkfit.ui.logger.LoggerViewModel
import app.xnkfit.ui.logger.RestAlarms
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.UUID

/** NF-5: the logger (mode choice, then the table of an empty workout) at phone and tablet widths, font 2.0, dark. */
@RunWith(RobolectricTestRunner::class)
abstract class LoggerScreenBase(private val fontScale: Float, private val dark: Boolean) {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val db: XnkDatabase = DbTestSupport.inMemory()

    @After fun close() = db.close()

    private class Prefs : LoggerPrefs {
        var mode: LoggerMode? = null
        override suspend fun mode() = mode
        override suspend fun setMode(mode: LoggerMode) { this.mode = mode }
        override suspend fun alarmAsked() = true
        override suspend fun setAlarmAsked() = Unit
    }

    private object Alarms : RestAlarms {
        override fun access() = AlarmAccess.READY
        override fun schedule(atMs: Long) = Unit
        override fun cancel() = Unit
    }

    /** B-07: the set table has column titles SET | SEBELUMNYA | KG | REP | ✓ lined up with the cells, KG and REP labelled at every size. */
    @Test fun setTableHasAlignedColumnTitles() {
        RuntimeEnvironment.setFontScale(fontScale)
        runBlocking {
            db.programDao().upsertExercises(listOf(ExerciseEntity("SQUAT_GOB", 0, 0, "Goblet Squat", "Goblet Squat", "SQUAT",
                """{"main":["QUADS"],"helper":[]}""", "1", settingsJson = """["gym"]""", equipmentJson = """["dumbbell"]""")))
        }
        val repo = LoggerRepository(db, { 1_763_510_400_000L }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }
        val vm = LoggerViewModel(repo, Prefs(), Alarms, { 1_763_510_400_000L }, { UUID.randomUUID().toString() }, tickClock = false)
        rule.setContent { XnkTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { LoggerScreen(vm) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(ctx.getString(R.string.lg_mode_bebas), substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(ctx.getString(R.string.lg_mode_bebas), substring = true).onFirst().performClick()
        rule.onAllNodesWithText(ctx.getString(R.string.lg_start), substring = true).onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(ctx.getString(R.string.lg_mode_bebas), substring = true).fetchSemanticsNodes().isEmpty() }
        vm.addExercise("SQUAT_GOB")
        val kg = ctx.getString(R.string.lg_col_kg)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(kg).fetchSemanticsNodes().isNotEmpty() }
        val reps = ctx.getString(R.string.lg_col_reps)
        val cellKg = ctx.getString(R.string.lg_cell_kg, "").trim()
        val cellReps = ctx.getString(R.string.lg_cell_reps, "").trim()
        listOf(ctx.getString(R.string.lg_col_set), kg, reps).forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        val big = fontScale >= 1.8f
        if (!big) rule.onNodeWithText(ctx.getString(if (fontScale >= 1.15f) R.string.lg_col_prev_short else R.string.lg_col_prev)).assertIsDisplayed()
        // Order left to right and the same left edge as the cells below.
        val x = { n: String -> rule.onNodeWithText(n).getUnclippedBoundsInRoot().left }
        assertTrue(x(ctx.getString(R.string.lg_col_set)) < x(kg) && x(kg) < x(reps))
        val cx = { d: String -> rule.onAllNodes(hasContentDescription(d, substring = true) and hasText(ctx.getString(R.string.lg_prev_none)), useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot().left }
        assertEquals(x(kg).value, cx(cellKg).value, 1f)
        assertEquals(x(reps).value, cx(cellReps).value, 1f)

    }

    @Test fun modeChoiceAndEmptyWorkoutHaveBigTouchTargets() {
        RuntimeEnvironment.setFontScale(fontScale)
        val repo = LoggerRepository(db, { 1_763_510_400_000L }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }
        val vm = LoggerViewModel(repo, Prefs(), Alarms, { 1_763_510_400_000L }, { UUID.randomUUID().toString() }, tickClock = false)
        rule.setContent { XnkTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { LoggerScreen(vm) } }
        val dipandu = ctx.getString(R.string.lg_mode_dipandu)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(dipandu, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.assertTouchTargets(ctx, "Logger: pilih cara")
        rule.onAllNodesWithText(ctx.getString(R.string.lg_mode_bebas), substring = true).onFirst().performClick()
        rule.onAllNodesWithText(ctx.getString(R.string.lg_start), substring = true).onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(ctx.getString(R.string.lg_mode_bebas), substring = true).fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        rule.assertTouchTargets(ctx, "Logger: latihan kosong")
    }
}

@Config(qualifiers = "w360dp-h740dp-notnight") class LoggerPhoneLight : LoggerScreenBase(1.0f, false)
@Config(qualifiers = "w411dp-h891dp-night") class LoggerPhoneDarkFont20 : LoggerScreenBase(2.0f, true)
@Config(qualifiers = "w840dp-h1200dp-night") class LoggerTabletDarkFont13 : LoggerScreenBase(1.3f, true)
