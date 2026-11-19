package app.xnkfit.ui.pengaturan

import app.xnkfit.data.content.ContentUpdater
import app.xnkfit.data.content.ContentUpdater.Outcome
import app.xnkfit.data.prefs.AppSettings
import app.xnkfit.data.prefs.ReminderGroup
import app.xnkfit.data.prefs.SettingsStore
import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.data.prefs.WelcomeTab
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

class FakeSettingsStore : SettingsStore {
    val state = MutableStateFlow(AppSettings())
    override val settings = state
    override suspend fun setTheme(mode: ThemeMode) = state.update { it.copy(theme = mode) }
    override suspend fun setReminder(group: ReminderGroup, on: Boolean) =
        state.update { it.copy(reminders = if (on) it.reminders + group else it.reminders - group) }
    override suspend fun markCardSeen(tab: WelcomeTab) = state.update { it.copy(seenCards = it.seenCards + tab) }
    override suspend fun resetCards() = state.update { it.copy(seenCards = emptySet()) }
}

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private fun await(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("timeout: $what")
    }

    private fun vm(store: FakeSettingsStore = FakeSettingsStore(), version: Int? = 3, check: suspend () -> ContentUpdater.Result) =
        SettingsViewModel(store, { version }, check)

    @Test fun themeAndReminderSwitchesAreStored() {
        val store = FakeSettingsStore()
        val vm = vm(store) { ContentUpdater.Result(Outcome.UP_TO_DATE) }
        vm.setTheme(ThemeMode.DARK)
        vm.setReminder(ReminderGroup.TIMBANG, true)
        vm.setReminder(ReminderGroup.LATIHAN, false)
        await("stored") { vm.ui.value.settings.reminders == setOf(ReminderGroup.MAKAN, ReminderGroup.TIMBANG) }
        assertEquals(ThemeMode.DARK, vm.ui.value.settings.theme)
        assertEquals(setOf(ReminderGroup.MAKAN, ReminderGroup.TIMBANG), vm.ui.value.settings.reminders)
    }

    @Test fun contentVersionIsShown() {
        val vm = vm { ContentUpdater.Result(Outcome.UP_TO_DATE) }
        await("version") { vm.ui.value.contentVersion == 3 }
    }

    @Test fun outcomesMapToCalmStates() {
        assertEquals(UpdateState.UPDATED, updateStateOf(Outcome.APPLIED))
        assertEquals(UpdateState.UP_TO_DATE, updateStateOf(Outcome.THROTTLED_24H))
        assertEquals(UpdateState.UNAVAILABLE, updateStateOf(Outcome.DISABLED))
        assertEquals(UpdateState.APP_TOO_OLD, updateStateOf(Outcome.APP_TOO_OLD))
        assertEquals(UpdateState.FAILED, updateStateOf(Outcome.REJECTED))
        assertEquals(UpdateState.FAILED, updateStateOf(Outcome.FETCH_FAILED))
    }

    @Test fun manualCheckReportsTheOutcome() {
        val vm = vm { ContentUpdater.Result(Outcome.APPLIED, contentVersion = 4) }
        vm.checkContent()
        await("done") { vm.ui.value.update == UpdateState.UPDATED }
    }

    @Test fun anExceptionBecomesAFailedCheckNotACrash() {
        val vm = vm { error("boom") }
        vm.checkContent()
        await("done") { vm.ui.value.update == UpdateState.FAILED }
    }
}
