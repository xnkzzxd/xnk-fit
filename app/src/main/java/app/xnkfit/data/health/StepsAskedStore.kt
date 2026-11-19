package app.xnkfit.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/** Remembers that the user has answered the system dialog once, so a refusal hides the card instead of asking again (O-3, L-15). */
interface StepsAskedStore {
    suspend fun asked(): Boolean
    suspend fun markAsked()
}

class DataStoreStepsAsked(private val store: DataStore<Preferences>) : StepsAskedStore {
    private val key = booleanPreferencesKey("steps_asked") // text-ok: preference key
    override suspend fun asked(): Boolean = store.data.first()[key] ?: false
    override suspend fun markAsked() { store.edit { it[key] = true } }
}
