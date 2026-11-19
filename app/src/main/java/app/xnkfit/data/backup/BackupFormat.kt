package app.xnkfit.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/*
 * Backup and export format (PRD B-4, Design §9). One zip:
 *   manifest.json           schema version, app version, device id, created at, per-file SHA-256 and row counts
 *   data/<table>.json       one JSON array of row objects per table (column name to value)
 *   photos/<id>.jpg         only when photo backup is on (B-3, PV-3)
 *   csv/<table>.csv         export only: readable copy, never read back
 * Backup, export, restore and import all go through this package; there is no second format.
 * JSON uses org.json from the Android framework, so the format needs no extra library (Decisions.md, B-16).
 */

/** Version of the zip layout itself (not the database). A reader refuses a layout it does not know. */
const val BACKUP_FORMAT_VERSION = 1

const val MANIFEST_NAME = "manifest.json"
const val DATA_DIR = "data/"
const val PHOTO_DIR = "photos/"
const val CSV_DIR = "csv/"

data class ManifestFile(val path: String, val sha256: String, val rows: Int?)

data class Manifest(
    val format: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val deviceId: String,
    val createdAt: Long,
    val photos: Boolean,
    val files: List<ManifestFile>,
) {
    /** Row count per table, for the summary on the Data screen (B-5). */
    val tableRows: Map<String, Int>
        get() = files.filter { it.path.startsWith(DATA_DIR) && it.rows != null }
            .associate { it.path.removePrefix(DATA_DIR).removeSuffix(".json") to it.rows!! }

    val photoCount: Int get() = files.count { it.path.startsWith(PHOTO_DIR) }

    fun toJson(): String = JSONObject().apply {
        put("format", format)
        put("schemaVersion", schemaVersion)
        put("appVersion", appVersion)
        put("deviceId", deviceId)
        put("createdAt", createdAt)
        put("photos", photos)
        put("files", JSONArray().apply {
            files.forEach { f ->
                put(JSONObject().apply {
                    put("path", f.path)
                    put("sha256", f.sha256)
                    if (f.rows != null) put("rows", f.rows)
                })
            }
        })
    }.toString(2)

    companion object {
        /** Parses a manifest; throws [BackupException] with [BackupError.BAD_MANIFEST] on anything missing or malformed. */
        fun parse(text: String): Manifest = try {
            val o = JSONObject(text)
            val arr = o.getJSONArray("files")
            Manifest(
                format = o.getInt("format"),
                schemaVersion = o.getInt("schemaVersion"),
                appVersion = o.getString("appVersion"),
                deviceId = o.getString("deviceId"),
                createdAt = o.getLong("createdAt"),
                photos = o.optBoolean("photos", false),
                files = (0 until arr.length()).map { i ->
                    val f = arr.getJSONObject(i)
                    ManifestFile(
                        path = f.getString("path"),
                        sha256 = f.getString("sha256").lowercase(),
                        rows = if (f.has("rows")) f.getInt("rows") else null,
                    )
                },
            )
        } catch (e: org.json.JSONException) {
            throw BackupException(BackupError.BAD_MANIFEST, e)
        }
    }
}

/** Why a backup file cannot be used. Each maps to one Indonesian message on the Data screen. */
enum class BackupError {
    /** Not a zip, or a zip with an unsafe or duplicate entry name. */
    NOT_A_BACKUP,
    /** manifest.json missing, unreadable or incomplete. */
    BAD_MANIFEST,
    /** A file is missing, extra, or its SHA-256 or row count differs from the manifest. */
    CHECKSUM,
    /** The backup comes from a newer app (schema or layout): "perbarui aplikasi dulu" (B-5). */
    NEWER_APP,
    /** Bigger than the reader accepts. */
    TOO_LARGE,
    /** The rows did not fit this database; nothing was changed (the transaction rolled back). */
    WRITE_FAILED,
}

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
