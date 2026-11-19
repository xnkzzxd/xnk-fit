package app.xnkfit.ui.pengaturan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.content.ContentUpdater
import app.xnkfit.data.prefs.AppSettings
import app.xnkfit.data.prefs.ReminderGroup
import app.xnkfit.data.prefs.SettingsStore
import app.xnkfit.data.prefs.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the manual content update stands (Content-Update.md: manual check from Pengaturan). */
enum class UpdateState { IDLE, CHECKING, UPDATED, UP_TO_DATE, UNAVAILABLE, APP_TOO_OLD, FAILED }

data class SettingsUi(
    val settings: AppSettings = AppSettings(),
    val contentVersion: Int? = null,
    val update: UpdateState = UpdateState.IDLE,
)

/** Maps the updater's outcome to what the screen says. Nothing is shown as an error when the check simply cannot run. */
fun updateStateOf(outcome: ContentUpdater.Outcome): UpdateState = when (outcome) {
    ContentUpdater.Outcome.APPLIED -> UpdateState.UPDATED
    ContentUpdater.Outcome.UP_TO_DATE, ContentUpdater.Outcome.THROTTLED_24H, ContentUpdater.Outcome.THROTTLED_6H -> UpdateState.UP_TO_DATE
    ContentUpdater.Outcome.DISABLED -> UpdateState.UNAVAILABLE
    ContentUpdater.Outcome.APP_TOO_OLD -> UpdateState.APP_TOO_OLD
    ContentUpdater.Outcome.FETCH_FAILED, ContentUpdater.Outcome.REJECTED -> UpdateState.FAILED
}

/**
 * Pengaturan (PRD P-7): theme, reminder switches, manual content update. Reminders are only stored here;
 * the notifications themselves are B-15.
 */
class SettingsViewModel(
    private val store: SettingsStore,
    private val installedContentVersion: suspend () -> Int?,
    /** Runs the content check without the waiting time. Must not throw. */
    private val checkUpdate: suspend () -> ContentUpdater.Result,
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui

    init {
        viewModelScope.launch { store.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
        viewModelScope.launch { refreshVersion() }
    }

    private suspend fun refreshVersion() {
        val v = runCatching { installedContentVersion() }.getOrNull()
        _ui.update { it.copy(contentVersion = v) }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { store.setTheme(mode) } }

    fun setReminder(group: ReminderGroup, on: Boolean) { viewModelScope.launch { store.setReminder(group, on) } }

    fun checkContent() {
        if (_ui.value.update == UpdateState.CHECKING) return
        _ui.update { it.copy(update = UpdateState.CHECKING) }
        viewModelScope.launch {
            val state = try {
                updateStateOf(checkUpdate().outcome)
            } catch (e: Exception) {
                UpdateState.FAILED
            }
            refreshVersion()
            _ui.update { it.copy(update = state) }
        }
    }
}
