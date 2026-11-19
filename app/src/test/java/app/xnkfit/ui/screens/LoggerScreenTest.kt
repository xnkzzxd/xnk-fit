package app.xnkfit.ui.screens

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
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
