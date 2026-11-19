package app.xnkfit.data.content

import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.content.ContentUpdater.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPair

private const val H = 60 * 60 * 1000L

@RunWith(RobolectricTestRunner::class)
class ContentUpdaterTest {
    private lateinit var db: app.xnkfit.data.db.XnkDatabase
    private lateinit var store: ContentStore
    private val kp: KeyPair = T.keyPair()
    private val host = T.FakeHost()
    private var clock = 100 * H

    @Before fun open() {
        db = T.db()
        store = ContentStore(db) { clock }
        runBlocking { BundledContentLoader(store, T.source(T.miniFiles("v1"), 1)) { clock }.loadIfNeeded() }
    }
    @After fun close() { db.close() }

    private fun updater(appVersion: Int = 1, key: KeyPair? = kp) =
        ContentUpdater(store, host, host.base, key?.let { ManifestVerifier(T.pubB64(it)) }, appVersion) { clock }

    /** Publishes [files] as version [v] on the fake host, signed by [signer]. */
    private fun publish(v: Int, files: Map<String, ByteArray>, signer: KeyPair = kp, minApp: Int = 1, tamper: (ByteArray) -> ByteArray = { it }) {
        val m = T.manifest(v, files, minApp)
        host.files["manifest.json"] = tamper(m)
        host.files["manifest.sig"] = T.sign(signer, m)
        host.files.putAll(files)
    }

    private fun foodIds() = runBlocking { db.foodDao().search("nasi*", 50).map { it.id }.toSet() }
    private suspend fun run() = updater().runIfDue()

    @Test fun newerSignedContentIsInstalledAndOnlyChangedFilesAreDownloaded() = runBlocking {
        val v2 = T.miniFiles("v2").toMutableMap()
        v2["badges.json"] = T.miniFiles("v1")["badges.json"]!! // unchanged file
        publish(2, v2)
        val r = run()
        assertEquals(Outcome.APPLIED, r.outcome)
        assertEquals(setOf("TKPI_v2", "W_v2", "REG-v2"), foodIds())
        assertEquals(2, store.meta()!!.contentVersion)
        assertEquals("update", store.meta()!!.source)
        assertTrue("unchanged badges.json is not fetched", host.requests.none { it.endsWith("badges.json") })
        assertEquals(clock, store.meta()!!.lastCheckAt)
    }

    @Test fun requestIsPlainGetOfTwoUrlsWhenNothingNewer() = runBlocking {
        publish(1, T.miniFiles("v1"))
        assertEquals(Outcome.UP_TO_DATE, run().outcome)
        assertEquals(listOf(host.base + "manifest.json", host.base + "manifest.sig"), host.requests)
    }

    @Test fun wrongSignatureIsRejectedAndOldDataStays() = runBlocking {
        publish(2, T.miniFiles("v2"), signer = T.keyPair())
        val r = run()
        assertEquals(Outcome.REJECTED, r.outcome)
        assertEquals(setOf("TKPI_v1", "W_v1", "REG-v1"), foodIds())
        assertEquals(1, store.meta()!!.contentVersion)
        assertTrue("no file was fetched after a bad signature", host.requests.none { it.endsWith("exercises.json") })
        assertEquals(null, store.meta()!!.lastCheckAt) // a rejected check is not a completed check
    }

    @Test fun tamperedManifestIsRejected() = runBlocking {
        publish(2, T.miniFiles("v2")) { it.copyOf().also { b -> b[b.size - 2] = 'x'.code.toByte() } }
        assertEquals(Outcome.REJECTED, run().outcome)
        assertEquals(1, store.meta()!!.contentVersion)
    }

    @Test fun missingSignatureFileIsAFailedFetch() = runBlocking {
        publish(2, T.miniFiles("v2")); host.files.remove("manifest.sig")
        assertEquals(Outcome.FETCH_FAILED, run().outcome)
        assertEquals(1, store.meta()!!.contentVersion)
    }

    @Test fun wrongChecksumDiscardsEverythingEvenFilesThatWereFine() = runBlocking {
        val v2 = T.miniFiles("v2")
        publish(2, v2)
        // The host serves a file whose bytes differ from the signed hash (same size).
        val bad = v2["foods-tkpi.json"]!!.copyOf().also { it[it.size - 3] = '9'.code.toByte() }
        host.files["foods-tkpi.json"] = bad
        val r = run()
        assertEquals(Outcome.REJECTED, r.outcome)
        assertTrue(r.detail!!.contains("SHA-256"))
        assertEquals(setOf("TKPI_v1", "W_v1", "REG-v1"), foodIds())
        assertEquals(1, db.contentDao().exerciseCount())
        assertEquals("Squat v1", db.programDao().exercisesByPattern("SQUAT").single().nameEn)
        assertEquals(1, store.meta()!!.contentVersion)
    }

    @Test fun wrongSizeIsRejected() = runBlocking {
        val v2 = T.miniFiles("v2")
        publish(2, v2)
        host.files["exercises.json"] = v2["exercises.json"]!!.copyOf(v2["exercises.json"]!!.size - 1) // shorter than the manifest size
        assertEquals(Outcome.REJECTED, run().outcome)
        clock += 7 * H
        host.files["exercises.json"] = v2["exercises.json"]!! + " ".toByteArray() // longer: the read stops at the size limit
        assertEquals(Outcome.FETCH_FAILED, run().outcome)
        assertEquals("Squat v1", db.programDao().exercisesByPattern("SQUAT").single().nameEn)
    }

    @Test fun signedButBrokenContentIsRejectedWholeNotHalfApplied() = runBlocking {
        val v2 = T.miniFiles("v2").toMutableMap()
        v2["foods-regional.json"] = """{"rows":[{"id":"REG-x"}]}""".toByteArray() // signed, but a row misses its fields
        publish(2, v2)
        assertEquals(Outcome.REJECTED, run().outcome)
        assertEquals(setOf("TKPI_v1", "W_v1", "REG-v1"), foodIds())
        assertEquals("Squat v1", db.programDao().exercisesByPattern("SQUAT").single().nameEn)
    }

    @Test fun olderOrEqualVersionIsIgnored() = runBlocking {
        publish(1, T.miniFiles("v2"))
        assertEquals(Outcome.UP_TO_DATE, run().outcome)
        assertEquals(setOf("TKPI_v1", "W_v1", "REG-v1"), foodIds())
    }

    @Test fun appOlderThanMinAppVersionInstallsNothing() = runBlocking {
        publish(2, T.miniFiles("v2"), minApp = 5)
        assertEquals(Outcome.APP_TOO_OLD, updater(appVersion = 4).runIfDue().outcome)
        assertEquals(setOf("TKPI_v1", "W_v1", "REG-v1"), foodIds())
        assertTrue(host.requests.none { it.endsWith("exercises.json") })
        clock += 25 * H
        assertEquals(Outcome.APPLIED, updater(appVersion = 5).runIfDue().outcome)
    }

    @Test fun unknownOrPathLikeFileNamesRejectTheManifest() = runBlocking {
        val v2 = T.miniFiles("v2")
        for (evil in listOf("../x.json", "figures/rig.min.json", "secret.json")) {
            val m = T.manifest(2, v2) { it.getJSONArray("files").put(org.json.JSONObject().put("path", evil).put("sha256", "0".repeat(64)).put("size", 5)) }
            host.files["manifest.json"] = m; host.files["manifest.sig"] = T.sign(kp, m)
            clock += 7 * H
            assertEquals(evil, Outcome.REJECTED, run().outcome)
        }
    }

    @Test fun unsupportedSchemaIsRejected() = runBlocking {
        val m = T.manifest(2, T.miniFiles("v2"), schema = 2)
        host.files["manifest.json"] = m; host.files["manifest.sig"] = T.sign(kp, m)
        assertEquals(Outcome.REJECTED, run().outcome)
    }

    @Test fun networkFailureIsQuietAndRetriedAfterSixHours() = runBlocking {
        host.down = true
        assertEquals(Outcome.FETCH_FAILED, run().outcome)
        clock += 5 * H
        assertEquals(Outcome.THROTTLED_6H, run().outcome)
        host.requests.clear()
        clock += 1 * H + 1
        publish(2, T.miniFiles("v2")); host.down = false
        assertEquals(Outcome.APPLIED, run().outcome)
    }

    @Test fun completedCheckIsNotRepeatedWithin24Hours() = runBlocking {
        publish(1, T.miniFiles("v1"))
        assertEquals(Outcome.UP_TO_DATE, run().outcome)
        val n = host.requests.size
        clock += 23 * H
        assertEquals(Outcome.THROTTLED_24H, run().outcome)
        clock += 1 * H + 1
        assertEquals(Outcome.UP_TO_DATE, run().outcome)
        assertTrue(host.requests.size > n)
    }

    @Test fun clockMovedBackDoesNotLockTheCheckForever() = runBlocking {
        publish(1, T.miniFiles("v1"))
        run()
        clock -= 48 * H
        assertEquals(Outcome.UP_TO_DATE, run().outcome)
    }

    @Test fun placeholderKeyOrPlainHttpDisablesTheUpdate() = runBlocking {
        assertEquals(Outcome.DISABLED, updater(key = null).runIfDue().outcome)
        assertEquals(Outcome.DISABLED, ContentUpdater(store, host, "http://example.test/c/", ManifestVerifier(T.pubB64(kp)), 1) { clock }.runIfDue().outcome)
        assertTrue(host.requests.isEmpty())
    }

    @Test fun bundledPlaceholderKeyIsParseableButFlaggedAsPlaceholder() {
        ManifestVerifier(ContentConfig.PUBLIC_KEY_BASE64) // must be a valid P-256 key, even as a placeholder
        assertTrue(ContentConfig.PUBLIC_KEY_IS_PLACEHOLDER)
        assertTrue(ContentConfig.BASE_URL.startsWith("https://") && ContentConfig.BASE_URL.endsWith("/"))
    }

    @Test fun newerAppBundleAfterUpdateStillWinsByVersion() = runBlocking {
        publish(2, T.miniFiles("v2")); run()
        val loader = BundledContentLoader(store, T.source(T.miniFiles("v3"), 3)) { clock }
        assertTrue(loader.loadIfNeeded() is BundledContentLoader.Result.Loaded)
        assertEquals(setOf("TKPI_v3", "W_v3", "REG-v3"), foodIds())
    }
}
