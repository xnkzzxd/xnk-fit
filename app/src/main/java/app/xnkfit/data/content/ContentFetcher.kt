package app.xnkfit.data.content

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One plain HTTPS GET. Tests replace it with a fake. */
fun interface ContentFetcher {
    /** @throws IOException on any failure, a non-200 answer, or more than [maxBytes] bytes. */
    fun fetch(url: String, maxBytes: Long): ByteArray
}

/**
 * GET only: no body, no cookies, no custom header, no redirect (PV-2). The platform adds its own default
 * headers (Host, User-Agent); we add none. Only `https://` is accepted.
 */
class HttpsContentFetcher(private val timeoutMs: Int = ContentConfig.TIMEOUT_MS) : ContentFetcher {
    override fun fetch(url: String, maxBytes: Long): ByteArray {
        if (!url.startsWith("https://")) throw IOException("not https")
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.useCaches = false
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("too large")
                    if (System.nanoTime() > deadline) throw IOException("timeout")
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }
}
