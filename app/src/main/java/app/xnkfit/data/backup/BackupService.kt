package app.xnkfit.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.xnkfit.data.db.BackupMetaEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.rules.R_PRIV_BACKUP_DEFAULT_AUTO_AGE_HOURS
import app.xnkfit.domain.rules.R_PRIV_BACKUP_DEFAULT_ON
import app.xnkfit.domain.rules.R_PRIV_PHOTO_BACKUP_DEFAULT
import app.xnkfit.domain.rules.backupsToDelete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The user's backup switches (DataStore `backup`). Both start off (R_PRIV_BACKUP_DEFAULT, R_PRIV_PHOTO). */
data class BackupSettings(
    val backupOn: Boolean = R_PRIV_BACKUP_DEFAULT_ON,
    val photosOn: Boolean = R_PRIV_PHOTO_BACKUP_DEFAULT,
    /** The last automatic run had no valid sign-in: the Data screen shows "masuk lagi". */
    val needsSignIn: Boolean = false,
    /** Automatic backup stopped because Drive holds a backup from another phone (B-6): the Data screen asks. */
    val otherDeviceWaiting: Boolean = false,
)

class BackupSettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<BackupSettings> = store.data.map { p ->
        BackupSettings(
            backupOn = p[ON] ?: R_PRIV_BACKUP_DEFAULT_ON,
            photosOn = p[PHOTOS] ?: R_PRIV_PHOTO_BACKUP_DEFAULT,
            needsSignIn = p[SIGN_IN] ?: false,
            otherDeviceWaiting = p[OTHER] ?: false,
        )
    }

    suspend fun current(): BackupSettings = settings.first()
    suspend fun setBackupOn(on: Boolean) { store.edit { it[ON] = on } }
    suspend fun setPhotosOn(on: Boolean) { store.edit { it[PHOTOS] = on } }
    suspend fun setNeedsSignIn(v: Boolean) { store.edit { it[SIGN_IN] = v } }
    suspend fun setOtherDeviceWaiting(v: Boolean) { store.edit { it[OTHER] = v } }

    private companion object {
        // text-ok: preference keys, never shown
        val ON = booleanPreferencesKey("backup_on")
        val PHOTOS = booleanPreferencesKey("photos_on")
        val SIGN_IN = booleanPreferencesKey("needs_sign_in")
        val OTHER = booleanPreferencesKey("other_device_waiting")
    }
}

/** A checked backup waiting for the user's typed confirmation (B-5), with the B-6 flag. */
class PreparedRestore(val backup: CheckedBackup, val fromOtherDevice: Boolean) {
    val manifest: Manifest get() = backup.manifest
}

enum class AutoOutcome { NOT_DUE, DONE, OTHER_DEVICE, NEEDS_SIGN_IN }

/**
 * Backup, restore, export and import (B-1…B-6). Every path uses [BackupWriter], [BackupReader] and [BackupRestorer].
 * [drive] is null when Drive is not set up in this build (no OAuth client configured): Drive features then show
 * "belum tersedia"; export and import to a file still work.
 */
class BackupService(
    private val db: XnkDatabase,
    private val deviceId: () -> String,
    private val appVersion: String,
    private val photoDir: File,
    private val workDir: File,
    private val now: () -> Long,
    private val drive: (() -> DriveClient)?,
) {
    val driveAvailable: Boolean get() = drive != null

    private fun writer() = BackupWriter(db, deviceId(), appVersion, now) { File(photoDir, "$it.jpg") }

    suspend fun meta(): BackupMetaEntity? = db.metaDao().backupMeta()

    /** True when backup is on and the last one is missing or older than the automatic age (R_PRIV_BACKUP_DEFAULT). */
    suspend fun isStale(settings: BackupSettings): Boolean {
        if (!settings.backupOn) return false
        val last = meta()?.lastBackupAt ?: return true
        return now() - last >= R_PRIV_BACKUP_DEFAULT_AUTO_AGE_HOURS * HOUR_MS
    }

    suspend fun hoursSinceLast(): Double? = meta()?.lastBackupAt?.let { (now() - it) / HOUR_MS.toDouble() }

    /**
     * Uploads a new backup and keeps the last three (R_PRIV_KEEP): older ones are deleted only after the upload
     * succeeded. Throws [DriveAuthException] or an IOException; nothing local changes on failure.
     */
    suspend fun backupToDrive(includePhotos: Boolean): String = withContext(Dispatchers.IO) {
        val client = requireNotNull(drive) { "drive not configured" }()
        workDir.mkdirs()
        val zip = File(workDir, "upload.zip")
        try {
            zip.outputStream().buffered().use { writer().write(it, includePhotos, withCsv = false) }
            val id = client.upload(fileName(now()), zip, deviceId())
            val all = client.list()
            backupsToDelete(all).forEach { old -> runCatching { client.delete(old.id) } }
            val kept = all.map { it.id }.filterNot { it in backupsToDelete(all).map { o -> o.id } }
            val t = now()
            val prev = meta()
            db.metaDao().upsertBackupMeta(
                BackupMetaEntity(
                    createdAt = prev?.createdAt ?: t, updatedAt = t, lastBackupAt = t, lastDeviceId = deviceId(),
                    lastDriveFileIdsJson = JSONArray(kept.ifEmpty { listOf(id) }).toString(),
                ),
            )
            id
        } finally {
            zip.delete()
        }
    }

    /**
     * The automatic run (B-2). Stops without uploading when this phone has never backed up and the newest Drive
     * backup comes from another phone, so a new phone never pushes the old phone's backups out unasked (B-6).
     */
    suspend fun autoBackup(settings: BackupSettings): AutoOutcome {
        if (!settings.backupOn || drive == null) return AutoOutcome.NOT_DUE
        return try {
            if (meta()?.lastBackupAt == null) {
                val newest = withContext(Dispatchers.IO) { drive.invoke().list() }.lastOrNull()
                if (newest != null && newest.deviceId != deviceId()) return AutoOutcome.OTHER_DEVICE
            }
            backupToDrive(settings.photosOn)
            AutoOutcome.DONE
        } catch (e: DriveAuthException) {
            AutoOutcome.NEEDS_SIGN_IN
        }
    }

    suspend fun driveBackups(): List<DriveBackup> = withContext(Dispatchers.IO) {
        requireNotNull(drive) { "drive not configured" }().list().asReversed()
    }

    /** Downloads and checks a Drive backup; nothing is written to the database. */
    suspend fun prepareFromDrive(b: DriveBackup): PreparedRestore = withContext(Dispatchers.IO) {
        val client = requireNotNull(drive) { "drive not configured" }()
        workDir.mkdirs()
        val f = File(workDir, "download.zip")
        try {
            client.download(b.id, f)
            f.inputStream().buffered().use { prepare(it) }
        } finally {
            f.delete()
        }
    }

    /** Import (B-4): the same check as restore. */
    suspend fun prepareFromFile(input: InputStream): PreparedRestore = withContext(Dispatchers.IO) { prepare(input) }

    private suspend fun prepare(input: InputStream): PreparedRestore {
        val checked = BackupReader.read(input, File(workDir, "staging"))
        val mine = meta()?.lastDeviceId ?: deviceId()
        return PreparedRestore(checked, fromOtherDevice = checked.manifest.deviceId != mine)
    }

    /** Replaces everything (B-5) in one transaction; throws [BackupException] with WRITE_FAILED and changes nothing. */
    suspend fun restore(p: PreparedRestore) = withContext(Dispatchers.IO) {
        BackupRestorer(db, photoDir).restore(p.backup)
        val t = now()
        val prev = meta()
        db.metaDao().upsertBackupMeta(
            BackupMetaEntity(
                createdAt = prev?.createdAt ?: t, updatedAt = t, lastBackupAt = prev?.lastBackupAt,
                lastDeviceId = deviceId(), lastDriveFileIdsJson = prev?.lastDriveFileIdsJson,
            ),
        )
    }

    /** Export (B-4): same zip plus readable CSVs. */
    suspend fun exportTo(out: OutputStream, includePhotos: Boolean): Manifest = withContext(Dispatchers.IO) {
        writer().write(out, includePhotos, withCsv = true)
    }

    companion object {
        const val HOUR_MS = 3_600_000L
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        /** `backup-<timestamp>.zip` (Design §9), UTC so names sort by time. */
        fun fileName(at: Long): String = DriveClient.NAME_PREFIX + NAME_TIME.format(Instant.ofEpochMilli(at)) + ".zip"

        /** Time encoded in a `backup-<timestamp>.zip` name, or null for any other name. */
        fun timeOfName(name: String): Long? = runCatching {
            Instant.from(NAME_TIME.parse(name.removePrefix(DriveClient.NAME_PREFIX).removeSuffix(".zip"))).toEpochMilli()
        }.getOrNull()

        /** Export file name `xnkfit-backup-<date>.zip` (Design §9). */
        fun exportName(at: Long): String =
            "xnkfit-backup-" + DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(at)) + ".zip"
    }
}
