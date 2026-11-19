package app.xnkfit.data.content

import app.xnkfit.data.content.ContentTestSupport as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

/**
 * ECDSA P-256 checks on the JVM. Not covered here: the same verification on a real API 26 device or emulator
 * (no emulator in the sandbox); it uses only `KeyFactory("EC")`, `X509EncodedKeySpec` and `SHA256withECDSA`,
 * all in the platform since API 1, and `java.util.Base64` (API 26). Check it on the reference phone.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestVerifierTest {
    private fun fixture(name: String) =
        ManifestVerifierTest::class.java.getResourceAsStream("/content-fixture/$name")!!.readBytes()

    @Test fun signatureMadeByTheNodeToolVerifiesOnTheJvm() {
        val v = ManifestVerifier(String(fixture("pubkey.b64")).trim())
        val manifest = fixture("manifest.json")
        assertTrue(v.verify(manifest, fixture("manifest.sig")))
        val m = Manifest.parse(manifest)
        assertEquals(7, m.files.size)
        assertEquals(1, m.schema)
    }

    @Test fun nodeSignatureFailsForChangedManifestOrOtherKey() {
        val v = ManifestVerifier(String(fixture("pubkey.b64")).trim())
        val manifest = fixture("manifest.json")
        val changed = manifest.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertFalse(v.verify(changed, fixture("manifest.sig")))
        assertFalse(ManifestVerifier(ContentConfig.PUBLIC_KEY_BASE64).verify(manifest, fixture("manifest.sig")))
    }

    @Test fun roundTripWithAnInProcessKey() {
        val kp = T.keyPair()
        val bytes = "manifest".toByteArray()
        val sig = T.sign(kp, bytes)
        val v = ManifestVerifier(T.pubB64(kp))
        assertTrue(v.verify(bytes, sig))
        assertFalse(v.verify("manifesT".toByteArray(), sig))
        assertFalse(ManifestVerifier(T.pubB64(T.keyPair())).verify(bytes, sig))
    }

    @Test fun garbageSignatureNeverThrows() {
        val v = ManifestVerifier(T.pubB64(T.keyPair()))
        assertFalse(v.verify("x".toByteArray(), "".toByteArray()))
        assertFalse(v.verify("x".toByteArray(), "!!! not base64 !!!".toByteArray()))
        assertFalse(v.verify("x".toByteArray(), Base64.getEncoder().encode(ByteArray(64))))
    }

    @Test fun unreadableKeyIsAnError() {
        try { ManifestVerifier("not a key"); fail() } catch (e: ContentException) { /* expected */ }
    }

    @Test fun manifestShapeChecks() {
        val good = T.manifest(2, T.miniFiles("a"))
        assertEquals(2, Manifest.parse(good).contentVersion)
        for (bad in listOf("{}", "not json", """{"schema":1,"contentVersion":2,"minAppVersion":1,"files":[{"path":"exercises.json","sha256":"zz","size":3}]}""")) {
            try { Manifest.parse(bad.toByteArray()); fail(bad) } catch (e: ContentException) { /* expected */ }
        }
    }
}
