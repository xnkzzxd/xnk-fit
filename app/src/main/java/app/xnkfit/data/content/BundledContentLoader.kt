package app.xnkfit.data.content

import org.json.JSONObject

/**
 * Loads the content shipped inside the APK into Room (Design §2 `data.content`).
 * Idempotent and versioned: it does nothing when the installed `contentVersion` is the same or newer
 * (an update already installed may be newer than the APK). A newer APK replaces older content, in one transaction.
 */
class BundledContentLoader(
    private val store: ContentStore,
    private val source: ContentSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data object AlreadyCurrent : Result
        data class Loaded(val contentVersion: Int, val skipped: List<String>) : Result
    }

    /** @throws ContentException when a bundled file is broken: that is a build bug, and nothing is written. */
    suspend fun loadIfNeeded(): Result {
        val marker = try {
            JSONObject(String(source.read(ContentFile.VERSION_FILE), Charsets.UTF_8))
        } catch (e: Exception) {
            throw ContentException("${ContentFile.VERSION_FILE}: unreadable", e)
        }
        val version = marker.getInt("contentVersion")
        val installed = store.meta()?.contentVersion
        if (installed != null && installed >= version) return Result.AlreadyCurrent

        val t = now()
        val hashes = HashMap<String, String>()
        val parsed = ContentFile.entries.map { f ->
            val bytes = source.read(f.fileName)
            hashes[f.fileName] = sha256Hex(bytes)
            ContentParser.parse(f, bytes, t)
        }
        store.apply(parsed, version, "bundled", hashes)
        return Result.Loaded(version, parsed.flatMap { it.skipped })
    }
}
