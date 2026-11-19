package app.xnkfit.data.food

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Which shopping lines are ticked, per plan week (M-8). Kept in DataStore, not Room: it is a view state of the list,
 * not a record, and it needs no schema change. Food ids only; nothing leaves the phone (PV-1).
 */
class ShoppingTickStore(private val store: DataStore<Preferences>) {
    private fun key(weekStart: String) = stringSetPreferencesKey("shop_ticks_$weekStart")

    fun observe(weekStart: String): Flow<Set<String>> = store.data.map { it[key(weekStart)] ?: emptySet() }

    suspend fun observeOnce(weekStart: String): Set<String> = observe(weekStart).first()

    suspend fun set(weekStart: String, foodId: String, ticked: Boolean) {
        store.edit { p ->
            val cur = p[key(weekStart)] ?: emptySet()
            p[key(weekStart)] = if (ticked) cur + foodId else cur - foodId
        }
    }
}
