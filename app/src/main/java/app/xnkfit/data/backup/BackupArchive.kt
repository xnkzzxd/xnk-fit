package app.xnkfit.data.backup

import app.xnkfit.data.db.XnkDatabase
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Photo ids become file names; anything else than a plain id is refused (no path tricks from a zip). */
private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
private val PHOTO_ENTRY = Regex("photos/([A-Za-z0-9_-]{1,64})\\.jpg")

/** Reader limits: a backup of one person's data is a few MB plus photos; these only stop a broken or hostile file. */
internal const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
internal const val MAX_DATA_FILE_BYTES = 64L * 1024 * 1024
internal const val MAX_ENTRIES = 5_000

/**
 * Writes the backup zip (B-4). The same writer serves the Drive backup and the export to a file;
 * [withCsv] adds the readable CSVs of the export.
 */
class BackupWriter(
    private val db: XnkDatabase,
    private val deviceId: String,
    private val appVersion: String,
    private val now: () -> Long,
    /** The stored picture of a photo id (PhotoStore.file), or null when none. */
    private val photoFile: (String) -> File?,
) {
    fun write(out: OutputStream, includePhotos: Boolean, withCsv: Boolean): Manifest {
        // One read transaction: every table comes from the same moment.
        val dumps = LinkedHashMap<BackupTable, JSONArray>()
        db.runInTransaction {
            val sql = db.openHelper.writableDatabase
            BACKUP_TABLES.forEach { dumps[it] = dumpTable(sql, it) }
            if (includePhotos) dumps[PHOTO_TABLE] = dumpTable(sql, PHOTO_TABLE)
        }
        val files = mutableListOf<ManifestFile>()
        ZipOutputStream(out).use { zip ->
            val blobs = LinkedHashMap<String, ByteArray>()
            dumps.forEach { (table, rows) ->
                val bytes = rows.toString().toByteArray(Charsets.UTF_8)
                blobs[table.path] = bytes
                files += ManifestFile(table.path, sha256Hex(bytes), rows.length())
            }
            val photoRows = dumps[PHOTO_TABLE]
            if (photoRows != null) {
                for (i in 0 until photoRows.length()) {
                    val id = photoRows.getJSONObject(i).getString("id")
                    if (!SAFE_ID.matches(id)) continue
                    val f = photoFile(id)?.takeIf { it.isFile } ?: continue
                    val bytes = f.readBytes()
                    val path = "$PHOTO_DIR$id.jpg"
                    blobs[path] = bytes
                    files += ManifestFile(path, sha256Hex(bytes), null)
                }
            }
            val manifest = Manifest(
                format = BACKUP_FORMAT_VERSION,
                schemaVersion = XnkDatabase.VERSION,
                appVersion = appVersion,
                deviceId = deviceId,
                createdAt = now(),
                photos = includePhotos,
                files = files,
            )
            zip.putBytes(MANIFEST_NAME, manifest.toJson().toByteArray(Charsets.UTF_8))
            blobs.forEach { (path, bytes) -> zip.putBytes(path, bytes) }
            if (withCsv) {
                dumps.forEach { (table, rows) -> zip.putBytes("$CSV_DIR${table.name}.csv", toCsv(rows).toByteArray(Charsets.UTF_8)) }
            }
            return manifest
        }
    }

    private fun ZipOutputStream.putBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }
}

/** A backup that passed every check; nothing has been written yet. Photos wait in [stagingDir]. */
class CheckedBackup internal constructor(
    val manifest: Manifest,
    internal val tables: Map<String, JSONArray>,
    internal val photos: Map<String, File>,
    internal val stagingDir: File,
) {
    /** Removes the staged photos; call when the user cancels. */
    fun discard() {
        stagingDir.deleteRecursively()
    }
}

/**
 * Reads and checks a backup or export zip (B-5). Every check runs before anything is written:
 * safe entry names, manifest, layout and schema version, every file present with its SHA-256 and row count,
 * no file the manifest does not list (csv/ is ignored). Throws [BackupException].
 */
object BackupReader {
    fun read(input: InputStream, stagingDir: File, appSchemaVersion: Int = XnkDatabase.VERSION): CheckedBackup {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        try {
            return readChecked(input, stagingDir, appSchemaVersion)
        } catch (e: Throwable) {
            stagingDir.deleteRecursively()
            throw when (e) {
                is BackupException -> e
                is ZipException, is java.io.EOFException -> BackupException(BackupError.NOT_A_BACKUP, e)
                is JSONException -> BackupException(BackupError.CHECKSUM, e)
                else -> e
            }
        }
    }

    private fun readChecked(input: InputStream, staging: File, appSchema: Int): CheckedBackup {
        var manifestText: String? = null
        val dataBytes = HashMap<String, ByteArray>()
        val photoFiles = HashMap<String, File>()
        val photoSha = HashMap<String, String>()
        val seen = HashSet<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name
                if (seen.size >= MAX_ENTRIES) throw BackupException(BackupError.TOO_LARGE)
                if (!seen.add(name) || name.startsWith("/") || name.contains("..") || name.contains('\\')) {
                    throw BackupException(BackupError.NOT_A_BACKUP)
                }
                when {
                    name == MANIFEST_NAME -> manifestText = String(zip.readCapped(MAX_DATA_FILE_BYTES), Charsets.UTF_8)
                    name.startsWith(CSV_DIR) -> zip.skipCapped()
                    name.startsWith(DATA_DIR) -> dataBytes[name] = zip.readCapped(MAX_DATA_FILE_BYTES).also { total += it.size }
                    name.startsWith(PHOTO_DIR) -> {
                        val id = PHOTO_ENTRY.matchEntire(name)?.groupValues?.get(1) ?: throw BackupException(BackupError.CHECKSUM)
                        val f = File(staging, "$id.jpg")
                        val (size, sha) = zip.copyTo(f, MAX_TOTAL_BYTES - total)
                        total += size
                        photoFiles[name] = f
                        photoSha[name] = sha
                    }
                    else -> throw BackupException(BackupError.CHECKSUM)
                }
                if (total > MAX_TOTAL_BYTES) throw BackupException(BackupError.TOO_LARGE)
            }
        }
        val manifest = Manifest.parse(manifestText ?: throw BackupException(BackupError.BAD_MANIFEST))
        if (manifest.format > BACKUP_FORMAT_VERSION || manifest.schemaVersion > appSchema) {
            throw BackupException(BackupError.NEWER_APP)
        }
        if (manifest.format < 1 || manifest.schemaVersion < 1) throw BackupException(BackupError.BAD_MANIFEST)

        val allowed = (BACKUP_TABLES + PHOTO_TABLE).associateBy { it.path }
        val listed = manifest.files.associateBy { it.path }
        if (listed.size != manifest.files.size) throw BackupException(BackupError.BAD_MANIFEST)
        // Every stored file must be listed, and every listed file stored.
        if (listed.keys != dataBytes.keys + photoFiles.keys) throw BackupException(BackupError.CHECKSUM)
        // A backup without the user tables is not one of ours.
        if (BACKUP_TABLES.any { it.path !in listed }) throw BackupException(BackupError.CHECKSUM)
        if (!manifest.photos && (photoFiles.isNotEmpty() || PHOTO_TABLE.path in listed)) throw BackupException(BackupError.CHECKSUM)

        val tables = LinkedHashMap<String, JSONArray>()
        for ((path, bytes) in dataBytes) {
            val entry = listed.getValue(path)
            val table = allowed[path] ?: throw BackupException(BackupError.CHECKSUM)
            if (sha256Hex(bytes) != entry.sha256) throw BackupException(BackupError.CHECKSUM)
            val rows = JSONArray(String(bytes, Charsets.UTF_8))
            if (entry.rows != rows.length()) throw BackupException(BackupError.CHECKSUM)
            tables[table.name] = rows
        }
        for ((path, sha) in photoSha) {
            if (listed.getValue(path).sha256 != sha) throw BackupException(BackupError.CHECKSUM)
        }
        val photosById = photoFiles.mapKeys { (path, _) -> PHOTO_ENTRY.matchEntire(path)!!.groupValues[1] }
        return CheckedBackup(manifest, tables, photosById, staging)
    }

    private fun ZipInputStream.readCapped(cap: Long): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var n = 0L
        while (true) {
            val r = read(chunk)
            if (r < 0) break
            n += r
            if (n > cap) throw BackupException(BackupError.TOO_LARGE)
            buf.write(chunk, 0, r)
        }
        return buf.toByteArray()
    }

    private fun ZipInputStream.skipCapped() {
        val chunk = ByteArray(16 * 1024)
        var n = 0L
        while (true) {
            val r = read(chunk)
            if (r < 0) break
            n += r
            if (n > MAX_DATA_FILE_BYTES) throw BackupException(BackupError.TOO_LARGE)
        }
    }

    private fun ZipInputStream.copyTo(f: File, cap: Long): Pair<Long, String> {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(16 * 1024)
        var n = 0L
        f.outputStream().use { out ->
            while (true) {
                val r = read(chunk)
                if (r < 0) break
                n += r
                if (n > cap) throw BackupException(BackupError.TOO_LARGE)
                md.update(chunk, 0, r)
                out.write(chunk, 0, r)
            }
        }
        return n to md.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Restore and import (B-5): replaces the user's data with the checked backup in one transaction. Never merges.
 * When anything fails the transaction rolls back and the old data stays as it was ([BackupError.WRITE_FAILED]).
 * Photos: when the backup carries photos they replace the photos on the phone; when it does not, the photos on the
 * phone stay (they could not be in that backup, PV-3), see Decisions.md B-16.
 */
class BackupRestorer(private val db: XnkDatabase, private val photoDir: File) {
    fun restore(backup: CheckedBackup) {
        try {
            db.runInTransaction {
                val sql = db.openHelper.writableDatabase
                BACKUP_TABLES.asReversed().forEach { deleteRows(sql, it) }
                if (backup.manifest.photos) deleteRows(sql, PHOTO_TABLE)
                BACKUP_TABLES.forEach { t ->
                    insertRows(sql, t.name, backup.tables[t.name] ?: JSONArray(), columnsOf(sql, t.name))
                }
                if (backup.manifest.photos) {
                    val rows = backup.tables[PHOTO_TABLE.name] ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val o = rows.getJSONObject(i)
                        val id = o.getString("id")
                        require(SAFE_ID.matches(id)) { "bad photo id" }
                        // The stored path is this phone's private folder, never the path from the other phone.
                        o.put("path", File(photoDir, "$id.jpg").path)
                    }
                    insertRows(sql, PHOTO_TABLE.name, rows, columnsOf(sql, PHOTO_TABLE.name))
                }
            }
        } catch (e: Exception) {
            backup.discard()
            throw BackupException(BackupError.WRITE_FAILED, e)
        }
        if (backup.manifest.photos) {
            photoDir.mkdirs()
            val keep = backup.photos.keys.map { "$it.jpg" }.toSet()
            photoDir.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
            backup.photos.forEach { (id, staged) ->
                val target = File(photoDir, "$id.jpg")
                if (!staged.renameTo(target)) {
                    staged.copyTo(target, overwrite = true)
                }
            }
        }
        backup.discard()
    }
}

/** RFC 4180 CSV of one table for the export; columns from the first row, in its order. */
internal fun toCsv(rows: JSONArray): String {
    if (rows.length() == 0) return ""
    val cols = rows.getJSONObject(0).keys().asSequence().toList()
    val sb = StringBuilder()
    sb.append(cols.joinToString(",") { csvCell(it) }).append("\r\n")
    for (i in 0 until rows.length()) {
        val o = rows.getJSONObject(i)
        sb.append(cols.joinToString(",") { c -> if (o.isNull(c)) "" else csvCell(o.get(c).toString()) }).append("\r\n")
    }
    return sb.toString()
}

private fun csvCell(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
