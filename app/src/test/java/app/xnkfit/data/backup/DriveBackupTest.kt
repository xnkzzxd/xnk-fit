package app.xnkfit.data.backup

import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.XnkDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder

/**
 * Drive backup against a fake Drive server on 127.0.0.1 (real HttpURLConnection, real multipart).
 * A real Google account and phone are not used here; see TODO B-16 for what still needs one.
 */
@RunWith(RobolectricTestRunner::class)
class DriveBackupTest {
    private lateinit var server: FakeDrive
    private lateinit var phoneA: XnkDatabase
    private lateinit var phoneB: XnkDatabase
    private lateinit var tmp: File
    private var clock = T0
    private var token = "good"

    @Before fun setUp() {
        server = FakeDrive()
        phoneA = DbTestSupport.inMemory()
        phoneB = DbTestSupport.inMemory()
        tmp = File(DbTestSupport.context().cacheDir, "drive-test").apply { deleteRecursively(); mkdirs() }
        BackupArchiveTest.fill(phoneA, "a", rows = 2)
        BackupArchiveTest.fill(phoneB, "b", rows = 1)
    }

    @After fun tearDown() {
        server.stop(); phoneA.close(); phoneB.close(); tmp.deleteRecursively()
    }

    private fun service(db: XnkDatabase, device: String, drive: Boolean = true) = BackupService(
        db = db, deviceId = { device }, appVersion = "1.0", photoDir = File(tmp, "$device-photos"),
        workDir = File(tmp, "$device-work"), now = { clock },
        drive = if (drive) ({ DriveClient(UrlConnectionHttp(), { token }, server.base) }) else null,
    )

    @Test fun upload_goesToAppFolderWithTimestampName() = runBlocking {
        service(phoneA, "A").backupToDrive(includePhotos = false)
        val f = server.files.values.single()
        assertEquals("backup-20231114T221320Z.zip", f.name)
        assertEquals(listOf("appDataFolder"), f.parents)
        assertEquals("A", f.deviceId)
        assertTrue(server.listQueries.isEmpty() || server.listQueries.all { "spaces=appDataFolder" in it })
        assertEquals(clock, service(phoneA, "A").meta()!!.lastBackupAt)
    }

    @Test fun keepsLastThree_deletesOlderAfterUpload() = runBlocking {
        val s = service(phoneA, "A")
        repeat(5) { clock += 3_600_000L; s.backupToDrive(false) }
        assertEquals(3, server.files.size)
        val names = server.files.values.map { it.name }.sorted()
        assertEquals(BackupService.fileName(T0 + 3 * 3_600_000L), names.first())
        assertEquals(3, JSONArray(s.meta()!!.lastDriveFileIdsJson).length())
    }

    @Test fun failedUpload_deletesNothing() = runBlocking {
        val s = service(phoneA, "A")
        repeat(3) { clock += 1000; s.backupToDrive(false) }
        server.failUploads = true
        runCatching { s.backupToDrive(false) }
        assertEquals(3, server.files.size)
    }

    @Test fun restoreFromDrive_onOtherPhone_flagsOtherDeviceAndReplacesData() = runBlocking {
        service(phoneA, "A").backupToDrive(false)
        val b = service(phoneB, "B")
        val list = b.driveBackups()
        val prepared = b.prepareFromDrive(list.first())
        assertTrue(prepared.fromOtherDevice)
        assertEquals("A", prepared.manifest.deviceId)
        b.restore(prepared)
        for (t in BACKUP_TABLES) {
            assertEquals(t.name, dumpTable(phoneA.openHelper.readableDatabase, t).toString(), dumpTable(phoneB.openHelper.readableDatabase, t).toString())
        }
        assertEquals("B", b.meta()!!.lastDeviceId)
    }

    @Test fun restoreFromSamePhone_isNotFlagged() = runBlocking {
        val a = service(phoneA, "A")
        a.backupToDrive(false)
        assertFalse(a.prepareFromDrive(a.driveBackups().first()).fromOtherDevice)
    }

    @Test fun auto_newPhoneDoesNotPushOldPhoneBackupsOut() = runBlocking {
        service(phoneA, "A").backupToDrive(false)
        val out = service(phoneB, "B").autoBackup(BackupSettings(backupOn = true))
        assertEquals(AutoOutcome.OTHER_DEVICE, out)
        assertEquals(1, server.files.size)
    }

    @Test fun auto_offOrNotConfigured_doesNothing() = runBlocking {
        assertEquals(AutoOutcome.NOT_DUE, service(phoneA, "A").autoBackup(BackupSettings(backupOn = false)))
        assertEquals(AutoOutcome.NOT_DUE, service(phoneA, "A", drive = false).autoBackup(BackupSettings(backupOn = true)))
        assertTrue(server.files.isEmpty())
    }

    @Test fun auto_expiredToken_asksToSignInAgain() = runBlocking {
        token = "expired"
        assertEquals(AutoOutcome.NEEDS_SIGN_IN, service(phoneA, "A").autoBackup(BackupSettings(backupOn = true)))
        assertTrue(server.files.isEmpty())
    }

    @Test fun stale_whenOnAndOlderThanAutoAge() = runBlocking {
        val s = service(phoneA, "A")
        val on = BackupSettings(backupOn = true)
        assertTrue(s.isStale(on))
        s.backupToDrive(false)
        assertFalse(s.isStale(on))
        clock += 24 * 3_600_000L
        assertTrue(s.isStale(on))
        assertFalse(s.isStale(BackupSettings(backupOn = false)))
    }

    @Test fun photosOnlyWhenSwitchedOn() = runBlocking {
        val dir = File(tmp, "A-photos").apply { mkdirs() }
        File(dir, "photo_a_1.jpg").writeBytes(byteArrayOf(7))
        val s = service(phoneA, "A")
        s.backupToDrive(includePhotos = false)
        assertFalse(server.lastUploadHas("photos/"))
        clock += 1000
        s.backupToDrive(includePhotos = true)
        assertTrue(server.lastUploadHas("photos/photo_a_1.jpg"))
    }

    @Test fun export_isSameFormatPlusCsv_andImportsBack() = runBlocking {
        val f = File(tmp, "export.zip")
        f.outputStream().use { service(phoneA, "A", drive = false).exportTo(it, includePhotos = false) }
        val b = service(phoneB, "B", drive = false)
        val p = f.inputStream().use { b.prepareFromFile(it) }
        b.restore(p)
        assertEquals(2, phoneB.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workout").use { it.moveToFirst(); it.getInt(0) })
        assertEquals("xnkfit-backup-2023-11-14.zip", BackupService.exportName(T0))
    }

    companion object {
        const val T0 = 1_700_000_000_000L
    }

    /** Minimal Drive v3 stand-in: list, multipart upload, media download, delete, Bearer check. */
    class FakeDrive {
        data class F(val id: String, val name: String, val parents: List<String>, val deviceId: String?, val bytes: ByteArray)

        val files = LinkedHashMap<String, F>()
        val listQueries = mutableListOf<String>()
        var failUploads = false
        private var next = 1
        private var lastUpload: ByteArray = ByteArray(0)
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val base: String get() = "http://127.0.0.1:${socket.localPort}"

        init {
            Thread {
                while (!socket.isClosed) {
                    val c = runCatching { socket.accept() }.getOrNull() ?: break
                    c.use { runCatching { synchronized(this) { handle(Exchange.read(it)) } } }
                }
            }.apply { isDaemon = true }.start()
        }

        fun stop() = socket.close()

        fun lastUploadHas(prefix: String): Boolean {
            val z = java.util.zip.ZipInputStream(lastUpload.inputStream())
            while (true) { val e = z.nextEntry ?: return false; if (e.name.startsWith(prefix)) return true }
        }

        private fun handle(ex: Exchange) {
            if (ex.headers["authorization"] != "Bearer good") return send(ex, 401, "{}")
            val path = ex.uri.path
            val query = ex.uri.rawQuery.orEmpty()
            when {
                ex.method == "GET" && path == "/drive/v3/files" -> {
                    listQueries += URLDecoder.decode(query, "UTF-8")
                    if ("spaces=appDataFolder" !in query) return send(ex, 400, "{}")
                    val arr = JSONArray()
                    files.values.forEach { f ->
                        arr.put(JSONObject().put("id", f.id).put("name", f.name).put("createdTime", "t")
                            .put("size", f.bytes.size.toString())
                            .put("appProperties", JSONObject().put("deviceId", f.deviceId)))
                    }
                    send(ex, 200, JSONObject().put("files", arr).toString())
                }
                ex.method == "POST" && path == "/upload/drive/v3/files" -> {
                    if (failUploads) return send(ex, 500, "{}")
                    val boundary = ex.headers.getValue("content-type").substringAfter("boundary=")
                    val body = String(ex.body, Charsets.ISO_8859_1)
                    val parts = body.split("--$boundary").drop(1).dropLast(1)
                    val meta = JSONObject(parts[0].substringAfter("\r\n\r\n").trimEnd())
                    val bytes = parts[1].substringAfter("\r\n\r\n").removeSuffix("\r\n").toByteArray(Charsets.ISO_8859_1)
                    lastUpload = bytes
                    val parents = meta.getJSONArray("parents").let { a -> (0 until a.length()).map { a.getString(it) } }
                    val id = "f${next++}"
                    files[id] = F(id, meta.getString("name"), parents, meta.optJSONObject("appProperties")?.optString("deviceId"), bytes)
                    send(ex, 200, JSONObject().put("id", id).toString())
                }
                path.startsWith("/drive/v3/files/") -> {
                    val id = path.removePrefix("/drive/v3/files/")
                    val f = files[id] ?: return send(ex, 404, "{}")
                    when (ex.method) {
                        "GET" -> ex.respond(200, f.bytes)
                        "DELETE" -> { files.remove(id); ex.respond(204, ByteArray(0)) }
                        else -> send(ex, 405, "{}")
                    }
                }
                else -> send(ex, 404, "{}")
            }
        }

        private fun send(ex: Exchange, code: Int, body: String) = ex.respond(code, body.toByteArray())
    }

    /** One HTTP/1.1 request on a socket, answered once with Connection: close. */
    class Exchange(val method: String, val uri: URI, val headers: Map<String, String>, val body: ByteArray, private val out: java.io.OutputStream) {
        fun respond(code: Int, bytes: ByteArray) {
            out.write("HTTP/1.1 $code X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }

        companion object {
            fun read(s: java.net.Socket): Exchange {
                val input = java.io.BufferedInputStream(s.getInputStream())
                fun line(): String {
                    val sb = StringBuilder()
                    while (true) {
                        val b = input.read()
                        if (b < 0 || b == '\n'.code) break
                        if (b != '\r'.code) sb.append(b.toChar())
                    }
                    return sb.toString()
                }
                val (method, target) = line().split(" ").let { it[0] to it[1] }
                val headers = HashMap<String, String>()
                while (true) {
                    val l = line()
                    if (l.isEmpty()) break
                    headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
                }
                val n = headers["content-length"]?.toInt() ?: 0
                val body = ByteArray(n)
                var off = 0
                while (off < n) { val r = input.read(body, off, n - off); if (r < 0) break; off += r }
                return Exchange(method, URI(target), headers, body, s.getOutputStream())
            }
        }
    }
}
