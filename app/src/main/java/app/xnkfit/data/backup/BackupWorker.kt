package app.xnkfit.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.progress.PhotoStore
import app.xnkfit.domain.rules.autoBackupDue
import java.io.File
import java.io.IOException

/** Process-wide pieces the screen and the worker share, so both see one DataStore file (DataStore needs that). */
object BackupGraph {
    private const val SETTINGS_FILE = "backup" // text-ok: DataStore file name
    @Volatile private var settings: BackupSettingsStore? = null

    fun settings(context: Context): BackupSettingsStore = settings ?: synchronized(this) {
        settings ?: BackupSettingsStore(
            PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile(SETTINGS_FILE) },
        ).also { settings = it }
    }

    fun service(context: Context): BackupService {
        val app = context.applicationContext
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
        val auth = PlayDriveAuth(app)
        val ids = DeviceId(app.noBackupFilesDir)
        return BackupService(
            db = XnkDatabase.open(app, version),
            deviceId = ids::get,
            appVersion = version,
            photoDir = File(app.filesDir, PhotoStore.DIR_NAME),
            workDir = File(app.cacheDir, "backup"), // text-ok: cache folder name
            now = System::currentTimeMillis,
            drive = if (DriveConfig.configured) ({ DriveClient(UrlConnectionHttp(), auth::tokenOrThrow) }) else null,
        )
    }

    fun onUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Automatic backup (B-2): checked when the app is opened or resumed, then one one-time WorkManager job.
 * No periodic work, no foreground service, no boot receiver.
 */
object BackupScheduler {
    private const val WORK_NAME = "backup-auto" // text-ok: unique work name

    suspend fun onAppOpen(context: Context) {
        if (!DriveConfig.configured) return
        val settings = BackupGraph.settings(context).current()
        val wifi = BackupGraph.onUnmeteredNetwork(context)
        val hours = BackupGraph.service(context).hoursSinceLast()
        if (!autoBackupDue(settings.backupOn, wifi, hours)) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = BackupGraph.settings(applicationContext)
        val settings = store.current()
        return try {
            when (BackupGraph.service(applicationContext).autoBackup(settings)) {
                AutoOutcome.NEEDS_SIGN_IN -> store.setNeedsSignIn(true)
                AutoOutcome.OTHER_DEVICE -> store.setOtherDeviceWaiting(true)
                AutoOutcome.DONE -> store.setNeedsSignIn(false)
                AutoOutcome.NOT_DUE -> Unit
            }
            Result.success()
        } catch (e: IOException) {
            // No retry loop in the background: the next app open tries again (B-2).
            Result.failure()
        } catch (e: BackupException) {
            Result.failure()
        }
    }
}
