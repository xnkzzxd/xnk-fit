package app.xnkfit.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

/**
 * One DataStore per file in the process. MainActivity used to create its stores in the ViewModel factories, so
 * opening the app again after leaving it with the process still alive made a second store on the same file, which
 * DataStore refuses ("multiple DataStores active for the same file"). Found by the emulator smoke test (B-18).
 */
object SharedDataStores {
    private val stores = HashMap<String, DataStore<Preferences>>()

    fun get(context: Context, fileName: String): DataStore<Preferences> = synchronized(stores) {
        val file = context.applicationContext.preferencesDataStoreFile(fileName)
        // Keyed by path: a test that starts from a fresh files directory gets a fresh store.
        stores.getOrPut(file.absolutePath) { PreferenceDataStoreFactory.create { file } }
    }
}
