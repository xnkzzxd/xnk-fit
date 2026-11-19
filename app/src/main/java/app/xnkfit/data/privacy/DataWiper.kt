package app.xnkfit.data.privacy

import android.content.Context
import androidx.work.WorkManager
import app.xnkfit.data.backup.DeviceId
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.ui.logger.AndroidRestAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Removes everything the app keeps on the phone (PRD B-7, PV-4). Drive backups are the user's own and stay. */
interface DataWiper {
    /** Throws when something could not be removed; the caller then keeps the screen and says so. */
    suspend fun wipe()
}

class AndroidDataWiper(private val context: Context, private val database: XnkDatabase) : DataWiper {
    override suspend fun wipe() = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        // Nothing may fire after the data is gone: the rest alarm and any queued backup work.
        runCatching { AndroidRestAlarms(app).cancel() }
        runCatching { WorkManager.getInstance(app).cancelAllWork() }
        database.clearAllTables()
        // Photos, share and capture cache, settings files, the install id. The process restarts afterwards,
        // so no open DataStore writes its old content back.
        val failed = listOf(
            File(app.filesDir, PHOTOS_DIR),
            File(app.filesDir, DATASTORE_DIR),
            File(app.noBackupFilesDir, DeviceId.FILE_NAME),
        ).filter { it.exists() && !it.deleteRecursively() } +
            (app.cacheDir.listFiles() ?: emptyArray()).filter { !it.deleteRecursively() }
        check(failed.isEmpty()) { "could not remove ${failed.size} item(s)" }
    }

    private companion object {
        const val PHOTOS_DIR = "photos" // text-ok: directory name, see PhotoStore
        const val DATASTORE_DIR = "datastore" // text-ok: directory name, Jetpack DataStore default
    }
}
