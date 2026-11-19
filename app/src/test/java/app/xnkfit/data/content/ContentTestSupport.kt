package app.xnkfit.data.content

import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.RepoFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

internal object ContentTestSupport {
    /** The real repo content/ folder, read as the Gradle copy would ship it. */
    val repoSource = ContentSource { name -> RepoFiles.file("content/$name").readBytes() }

    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    fun pubB64(kp: KeyPair): String = Base64.getEncoder().encodeToString(kp.public.encoded)
    fun sign(kp: KeyPair, bytes: ByteArray): ByteArray =
        Base64.getEncoder().encode(Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(bytes); sign() })

    /** Smallest valid set of the seven files; [tag] shows up in names so a test can tell versions apart. */
    fun miniFiles(tag: String): Map<String, ByteArray> {
        fun food(id: String, source: String) = JSONObject()
            .put("id", id).put("source", source).put("name_id", "Nasi $tag $id").put("name_en", JSONObject.NULL)
            .put("kcalPer100", 130).put("protein", 2.7).put("carb", 28).put("fat", 0.3)
            .put("fibre", JSONObject.NULL).put("sodium", JSONObject.NULL)
        val m = mutableMapOf<String, JSONObject>()
        m["exercises.json"] = JSONObject().put("exercises", JSONArray().put(JSONObject()
            .put("id", "SQUAT_$tag").put("name_en", "Squat $tag").put("pattern", "SQUAT").put("level", 1)
            .put("muscles", JSONObject().put("main", JSONArray().put("QUADS")).put("helper", JSONArray()))
            .put("cues_en", JSONArray().put("a").put("b")).put("active", true)))
        m["foods-tkpi.json"] = JSONObject().put("foods", JSONArray().put(food("TKPI_$tag", "tkpi")))
        m["foods-estimates.json"] = JSONObject().put("foods", JSONArray().put(food("W_$tag", "warung-est").put("isEstimate", true)))
        m["foods-regional.json"] = JSONObject().put("rows", JSONArray().put(food("REG-$tag", "warung-est").put("isEstimate", true)))
        m["foods-packaged.json"] = JSONObject().put("foods", JSONArray())
        m["badges.json"] = JSONObject().put("badges", JSONArray().put(JSONObject().put("code", "B_$tag")))
        m["meal-packages.json"] = JSONObject().put("slots", JSONArray().put("pagi")).put("packages", JSONArray().put(
            JSONObject().put("id", "MP_$tag").put("profile", "jawa").put("slot", "pagi")))
        return m.mapValues { it.value.toString().toByteArray() }
    }

    fun marker(version: Int, minApp: Int = 1): ByteArray =
        JSONObject().put("contentVersion", version).put("minAppVersion", minApp).put("schema", 1).toString().toByteArray()

    fun source(files: Map<String, ByteArray>, version: Int) =
        ContentSource { name -> if (name == ContentFile.VERSION_FILE) marker(version) else files[name] ?: throw java.io.FileNotFoundException(name) }

    fun manifest(version: Int, files: Map<String, ByteArray>, minApp: Int = 1, schema: Int = 1, mutate: (JSONObject) -> Unit = {}): ByteArray {
        val arr = JSONArray()
        files.toSortedMap().forEach { (n, b) ->
            arr.put(JSONObject().put("path", n).put("sha256", sha256Hex(b)).put("size", b.size))
        }
        return JSONObject().put("schema", schema).put("contentVersion", version).put("minAppVersion", minApp).put("files", arr)
            .also(mutate).toString().toByteArray()
    }

    /** A host in memory: URL to bytes, counting requests; a missing URL is a network failure. */
    class FakeHost(var base: String = "https://example.test/c/") : ContentFetcher {
        val files = mutableMapOf<String, ByteArray>()
        val requests = mutableListOf<String>()
        var down = false
        override fun fetch(url: String, maxBytes: Long): ByteArray {
            requests += url
            if (down) throw IOException("offline")
            val b = files[url.removePrefix(base)] ?: throw IOException("404")
            if (b.size > maxBytes) throw IOException("too large")
            return b
        }
    }

    fun db(): XnkDatabase = DbTestSupport.inMemory()
}
