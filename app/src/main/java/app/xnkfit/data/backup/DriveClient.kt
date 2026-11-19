package app.xnkfit.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One HTTP exchange. [body] writes the request body of [length] bytes, or is null. */
class HttpCall(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val length: Long = 0,
    val body: ((OutputStream) -> Unit)? = null,
)

/** Answer status; [read] is called once with the response stream (also for errors). */
interface DriveHttp {
    fun <T> exchange(call: HttpCall, read: (code: Int, body: InputStream) -> T): T
}

/**
 * The app's one HTTP client is the platform `HttpURLConnection`, as for the content update (B-04, Design §12).
 * HTTPS only; loopback `http://127.0.0.1` is accepted for the fake Drive server in tests and nowhere else.
 */
class UrlConnectionHttp(private val timeoutMs: Int = 30_000) : DriveHttp {
    override fun <T> exchange(call: HttpCall, read: (Int, InputStream) -> T): T {
        val u = call.url
        if (!u.startsWith("https://") && !u.startsWith("http://127.0.0.1:")) throw IOException("not https")
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.requestMethod = call.method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            call.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (call.body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(call.length)
                c.outputStream.use { call.body.invoke(it) }
            }
            val code = c.responseCode
            val stream = (if (code >= 400) c.errorStream else c.inputStream) ?: java.io.ByteArrayInputStream(ByteArray(0))
            return stream.use { read(code, it) }
        } finally {
            c.disconnect()
        }
    }
}

/** A backup file in the Drive app folder. [deviceId] comes from the file's appProperties (B-6). */
data class DriveBackup(val id: String, val name: String, val createdAt: String, val size: Long?, val deviceId: String?)

/** Token missing or refused: the user must sign in again ("masuk lagi"). */
class DriveAuthException : IOException("auth")

/**
 * Drive REST v3 in the hidden app folder only (`drive.appdata`, B-1, Google-OAuth.md §1). Four calls: list, upload,
 * download, delete. No other folder, no other scope, no metadata beyond the file name and the device id.
 */
class DriveClient(
    private val http: DriveHttp,
    private val token: () -> String,
    private val apiBase: String = "https://www.googleapis.com",
) {
    fun list(): List<DriveBackup> {
        val q = enc("name contains '$NAME_PREFIX' and trashed = false")
        val fields = enc("files(id,name,createdTime,size,appProperties)")
        val url = "$apiBase/drive/v3/files?spaces=appDataFolder&q=$q&fields=$fields&orderBy=createdTime&pageSize=100"
        val json = http.exchange(HttpCall("GET", url, auth())) { code, body -> check(code, body); JSONObject(body.readText()) }
        val arr = json.optJSONArray("files") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            DriveBackup(
                id = f.getString("id"),
                name = f.getString("name"),
                createdAt = f.optString("createdTime"),
                size = f.optString("size").toLongOrNull(),
                deviceId = f.optJSONObject("appProperties")?.optString(PROP_DEVICE)?.takeIf { it.isNotEmpty() },
            )
        }.filter { it.name.startsWith(NAME_PREFIX) && it.name.endsWith(".zip") }.sortedBy { it.name }
    }

    /** Multipart upload into `appDataFolder`; returns the new file id. */
    fun upload(name: String, zip: File, deviceId: String): String {
        val boundary = "xnkfit${System.nanoTime()}"
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject().put(PROP_DEVICE, deviceId))
            .toString()
        val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/zip\r\n\r\n").toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val url = "$apiBase/upload/drive/v3/files?uploadType=multipart&fields=id"
        val call = HttpCall(
            "POST", url, auth() + ("Content-Type" to "multipart/related; boundary=$boundary"),
            length = head.size + zip.length() + tail.size,
        ) { out ->
            out.write(head)
            zip.inputStream().use { it.copyTo(out) }
            out.write(tail)
        }
        return http.exchange(call) { code, body -> check(code, body); JSONObject(body.readText()).getString("id") }
    }

    fun download(id: String, to: File, maxBytes: Long = MAX_TOTAL_BYTES) {
        val url = "$apiBase/drive/v3/files/${enc(id)}?alt=media"
        http.exchange(HttpCall("GET", url, auth())) { code, body ->
            check(code, body)
            to.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                var n = 0L
                while (true) {
                    val r = body.read(buf)
                    if (r < 0) break
                    n += r
                    if (n > maxBytes) throw IOException("too large")
                    out.write(buf, 0, r)
                }
            }
        }
    }

    fun delete(id: String) {
        val url = "$apiBase/drive/v3/files/${enc(id)}"
        http.exchange(HttpCall("DELETE", url, auth())) { code, body -> if (code != 404) check(code, body) }
    }

    private fun auth() = mapOf("Authorization" to "Bearer ${token()}")

    private fun check(code: Int, body: InputStream) {
        if (code == 401) throw DriveAuthException()
        if (code !in 200..299) {
            body.skip(Long.MAX_VALUE)
            throw IOException("HTTP $code")
        }
    }

    private fun InputStream.readText() = readBytes().toString(Charsets.UTF_8)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val NAME_PREFIX = "backup-"
        const val PROP_DEVICE = "deviceId"
    }
}
