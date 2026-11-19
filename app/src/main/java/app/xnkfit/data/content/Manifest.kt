package app.xnkfit.data.content

import org.json.JSONException
import org.json.JSONObject
import java.security.KeyFactory
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class ManifestFile(val path: String, val sha256: String, val size: Long)

/** The signed list of content files (Content-Update.md §3). Built only by [parse]; check the signature first. */
class Manifest(val contentVersion: Int, val minAppVersion: Int, val schema: Int, val files: List<ManifestFile>) {
    companion object {
        const val SUPPORTED_SCHEMA = 1

        /** Largest single file or manifest the app will read; a bigger claim rejects the update. */
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64L * 1024

        /** Parses and shape-checks the manifest; does NOT check the signature (see [ManifestVerifier]). */
        fun parse(bytes: ByteArray): Manifest {
            try {
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                val files = o.getJSONArray("files")
                val list = (0 until files.length()).map {
                    val f = files.getJSONObject(it)
                    val path = f.getString("path")
                    val sha = f.getString("sha256")
                    val size = f.getLong("size")
                    // Only known flat names: no path can point outside the content set.
                    if (ContentFile.byName(path) == null) throw ContentException("manifest: unknown file $path")
                    if (!Regex("[0-9a-f]{64}").matches(sha)) throw ContentException("manifest: bad sha256 for $path")
                    if (size < 1 || size > MAX_FILE_BYTES) throw ContentException("manifest: bad size for $path")
                    ManifestFile(path, sha, size)
                }
                if (list.map { it.path }.toSet().size != list.size) throw ContentException("manifest: duplicate file")
                return Manifest(o.getInt("contentVersion"), o.getInt("minAppVersion"), o.getInt("schema"), list)
            } catch (e: JSONException) {
                throw ContentException("manifest: ${e.message}", e)
            }
        }
    }
}

/** ECDSA P-256 over the exact manifest bytes (`SHA256withECDSA`, DER signature), public key baked into the app. */
class ManifestVerifier(publicKeyBase64: String) {
    private val key = try {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
    } catch (e: Exception) {
        throw ContentException("public key unreadable", e)
    }

    /** True only when [signatureBase64] (the text of `manifest.sig`) is a valid signature of [manifest]. Never throws. */
    fun verify(manifest: ByteArray, signatureBase64: ByteArray): Boolean = try {
        val sig = Base64.getMimeDecoder().decode(String(signatureBase64, Charsets.US_ASCII).trim())
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(manifest)
            verify(sig)
        }
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
