package app.xnkfit.data.content

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentFilesTest {
    /** The Gradle copy ships every top-level JSON file of content/; the loader must know each one, and the node tool must list the same. */
    @Test fun everyShippedJsonIsKnownToTheLoader() {
        val shipped = RepoFiles.file("content").listFiles { f -> f.isFile && f.name.endsWith(".json") }!!.map { it.name }.toSet()
        assertEquals(ContentFile.entries.map { it.fileName }.toSet() + ContentFile.VERSION_FILE, shipped)
    }

    @Test fun nodeToolListsTheSameFiles() {
        val js = RepoFiles.text("tools/content-manifest.mjs")
        val listed = Regex("\"([a-z-]+\\.json)\"").findAll(js.substringAfter("CONTENT_FILES = [").substringBefore("];")).map { it.groupValues[1] }.toSet()
        assertEquals(ContentFile.entries.map { it.fileName }.toSet(), listed)
    }
}
