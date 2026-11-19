package app.xnkfit.data.content

import android.content.res.AssetManager
import java.io.FileNotFoundException

/** Where the bundled content bytes come from. The app reads assets; tests pass a map. */
fun interface ContentSource {
    /** @throws FileNotFoundException when the file is not bundled. */
    fun read(name: String): ByteArray
}

/** Assets under `content/`, copied from the repo's `content/` folder by the Gradle task `copyContentAssets`. */
class AssetContentSource(private val assets: AssetManager) : ContentSource {
    override fun read(name: String): ByteArray = try {
        assets.open("content/$name").use { it.readBytes() }
    } catch (e: java.io.IOException) {
        throw FileNotFoundException("content/$name: ${e.message}")
    }
}
