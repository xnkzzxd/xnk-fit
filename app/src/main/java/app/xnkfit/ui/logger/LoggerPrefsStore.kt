package app.xnkfit.ui.logger

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.xnkfit.domain.logger.LoggerMode
import kotlinx.coroutines.flow.first

/** Two small flags (Design §1: DataStore for flags): the last chosen mode and whether the alarm was asked once. */
class DataStoreLoggerPrefs(private val store: DataStore<Preferences>) : LoggerPrefs {
    override suspend fun mode(): LoggerMode? = LoggerMode.of(store.data.first()[MODE])
    override suspend fun setMode(mode: LoggerMode) { store.edit { it[MODE] = mode.code } }
    override suspend fun alarmAsked(): Boolean = store.data.first()[ASKED] ?: false
    override suspend fun setAlarmAsked() { store.edit { it[ASKED] = true } }

    private companion object {
        val MODE = stringPreferencesKey("mode") // text-ok: key
        val ASKED = booleanPreferencesKey("alarm_asked") // text-ok: key
    }
}
