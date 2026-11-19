package app.xnkfit.ui.saya

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Small per-phone choices on the Saya screen. */
interface SayaPrefs {
    suspend fun weightHidden(): Boolean

    suspend fun setWeightHidden(hidden: Boolean)
}

private val Context.sayaStore by preferencesDataStore(name = "saya") // text-ok: preference key

class DataStoreSayaPrefs(private val context: Context) : SayaPrefs {
    private val hiddenKey = booleanPreferencesKey("weight_hidden") // text-ok: preference key

    override suspend fun weightHidden(): Boolean = context.sayaStore.data.first()[hiddenKey] ?: false

    override suspend fun setWeightHidden(hidden: Boolean) {
        context.sayaStore.edit { it[hiddenKey] = hidden }
    }
}
