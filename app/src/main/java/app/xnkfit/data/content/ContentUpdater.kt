package app.xnkfit.data.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Checksummed, signed content update (PV-2, Content-Update.md §3). Call [runIfDue] when the app opens.
 * Every failure is quiet: the caller gets an [Outcome], never an exception, and the installed content stays.
 * Order of checks: throttle, fetch, signature, shape, schema, app version, content version, per-file size and SHA-256,
 * parse every file, then ONE transaction. Any failed check discards everything downloaded.
 */
class ContentUpdater(
    private val store: ContentStore,
    private val fetcher: ContentFetcher,
    private val baseUrl: String,
    private val verifier: ManifestVerifier?,
    private val appVersionCode: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Outcome {
        /** No usable public key yet (placeholder) or no installed content to compare with. */
        DISABLED,
        /** A check completed less than 24 h ago. */
        THROTTLED_24H,
        /** An attempt started less than 6 h ago. */
        THROTTLED_6H,
        /** Network or HTTP problem; try again after 6 h. */
        FETCH_FAILED,
        /** Signature, shape, checksum, size, or parse check failed; nothing was changed. */
        REJECTED,
        /** The app is older than `minAppVersion`; shows nothing, the next app update brings it. */
        APP_TOO_OLD,
        /** The manifest is genuine but not newer. */
        UP_TO_DATE,
        /** New content installed. */
        APPLIED,
    }

    data class Result(val outcome: Outcome, val detail: String? = null, val contentVersion: Int? = null)

    suspend fun runIfDue(): Result = withContext(Dispatchers.IO) {
        if (verifier == null) return@withContext Result(Outcome.DISABLED, "no public key")
        if (!baseUrl.startsWith("https://")) return@withContext Result(Outcome.DISABLED, "base URL is not https")
        val meta = store.meta() ?: return@withContext Result(Outcome.DISABLED, "no content installed yet")
        val t = now()
        // A clock set backwards gives a negative gap: treat that as due, not as "wait forever".
        meta.lastCheckAt?.let { if (t - it in 0 until ContentConfig.CHECK_INTERVAL_MS) return@withContext Result(Outcome.THROTTLED_24H) }
        meta.lastAttemptAt?.let { if (t - it in 0 until ContentConfig.RETRY_INTERVAL_MS) return@withContext Result(Outcome.THROTTLED_6H) }
        store.recordCheck(attemptAt = t, checkedAt = null)
        try {
            check(meta.contentVersion, store.installedHashes(), verifier, t)
        } catch (e: IOException) {
            Result(Outcome.FETCH_FAILED, e.message)
        } catch (e: ContentException) {
            Result(Outcome.REJECTED, e.message)
        } catch (e: Exception) {
            // Whatever else went wrong (database, parser): still silent, still nothing half-written.
            Result(Outcome.REJECTED, e.javaClass.simpleName)
        }
    }

    private suspend fun check(installedVersion: Int, installed: Map<String, String>, verifier: ManifestVerifier, t: Long): Result {
        val manifestBytes = fetcher.fetch(baseUrl + "manifest.json", Manifest.MAX_MANIFEST_BYTES)
        val sigBytes = fetcher.fetch(baseUrl + "manifest.sig", Manifest.MAX_MANIFEST_BYTES)
        if (!verifier.verify(manifestBytes, sigBytes)) throw ContentException("signature check failed")
        val manifest = Manifest.parse(manifestBytes)
        if (manifest.schema != Manifest.SUPPORTED_SCHEMA) throw ContentException("unsupported schema ${manifest.schema}")
        // From here the manifest is genuine, so this counts as a completed check (24 h rule).
        if (manifest.minAppVersion > appVersionCode) {
            store.recordCheck(attemptAt = null, checkedAt = t)
            return Result(Outcome.APP_TOO_OLD, contentVersion = manifest.contentVersion)
        }
        if (manifest.contentVersion <= installedVersion) {
            store.recordCheck(attemptAt = null, checkedAt = t)
            return Result(Outcome.UP_TO_DATE, contentVersion = installedVersion)
        }
        val changed = manifest.files.filter { installed[it.path] != it.sha256 }
        val parsed = ArrayList<ParsedFile>()
        val hashes = HashMap<String, String>()
        for (f in changed) {
            val bytes = fetcher.fetch(baseUrl + f.path, f.size)
            if (bytes.size.toLong() != f.size) throw ContentException("${f.path}: size ${bytes.size}, manifest ${f.size}")
            if (sha256Hex(bytes) != f.sha256) throw ContentException("${f.path}: SHA-256 differs from the manifest")
            hashes[f.path] = f.sha256
            parsed += ContentParser.parse(ContentFile.byName(f.path)!!, bytes, t)
        }
        store.apply(parsed, manifest.contentVersion, "update", hashes, checkedAt = t)
        return Result(Outcome.APPLIED, contentVersion = manifest.contentVersion)
    }
}
