package app.xnkfit.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Theme choice (PRD P-7 Tampilan). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** One switch per kind of reminder (mockup "Pengingat"). Only the switch lives here; scheduling is B-15. */
enum class ReminderGroup { LATIHAN, MAKAN, TIMBANG }

/** Tabs that can show a one-time welcome card (PRD P-8). */
enum class WelcomeTab { TODAY, WORKOUT, FOOD, ME }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Reminder groups that are switched on. Defaults follow the approved mockup: Latihan and Makan on, Timbang off. */
    val reminders: Set<ReminderGroup> = DEFAULT_REMINDERS,
    /** Tabs whose welcome card was already dismissed. */
    val seenCards: Set<WelcomeTab> = emptySet(),
) {
    companion object {
        val DEFAULT_REMINDERS: Set<ReminderGroup> = setOf(ReminderGroup.LATIHAN, ReminderGroup.MAKAN)
    }
}

interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setTheme(mode: ThemeMode)

    suspend fun setReminder(group: ReminderGroup, on: Boolean)

    suspend fun markCardSeen(tab: WelcomeTab)

    /** "Kartu sambutan: tampilkan lagi" in Bantuan. */
    suspend fun resetCards()
}

/** Phone-only preferences in one DataStore file (Design §1: DataStore for settings). Nothing here is ever sent anywhere. */
class DataStoreSettingsStore(private val store: DataStore<Preferences>) : SettingsStore {
    private val themeKey = stringPreferencesKey("theme") // text-ok: preference key
    private val remindersOnKey = stringSetPreferencesKey("reminders_on") // text-ok: preference key
    private val seenKey = stringSetPreferencesKey("cards_seen") // text-ok: preference key
    private val remindersSetKey = booleanPreferencesKey("reminders_set") // text-ok: preference key

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.SYSTEM,
            // Until the user changes a switch the defaults apply; afterwards the stored set is the truth.
            reminders = if (p[remindersSetKey] == true) groups(p[remindersOnKey]) else AppSettings.DEFAULT_REMINDERS,
            seenCards = (p[seenKey] ?: emptySet()).mapNotNull { n -> WelcomeTab.entries.firstOrNull { it.name == n } }.toSet(),
        )
    }

    override suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    override suspend fun setReminder(group: ReminderGroup, on: Boolean) {
        store.edit { p ->
            val current = if (p[remindersSetKey] == true) groups(p[remindersOnKey]) else AppSettings.DEFAULT_REMINDERS
            val next = if (on) current + group else current - group
            p[remindersSetKey] = true
            p[remindersOnKey] = next.map { it.name }.toSet()
        }
    }

    override suspend fun markCardSeen(tab: WelcomeTab) {
        store.edit { p -> p[seenKey] = (p[seenKey] ?: emptySet()) + tab.name }
    }

    override suspend fun resetCards() {
        store.edit { it.remove(seenKey) }
    }

    private fun groups(names: Set<String>?): Set<ReminderGroup> =
        (names ?: emptySet()).mapNotNull { n -> ReminderGroup.entries.firstOrNull { it.name == n } }.toSet()
}
