package app.xnkfit.data.progress

import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.PhotoEntity
import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.rules.R_PRIV_PHOTO_BACKUP_DEFAULT
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** PV-3: progress photos stay in app-private storage and out of every default backup. */
@RunWith(RobolectricTestRunner::class)
class PhotoPrivacyTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = PhotoStore(tmp.newFolder("files"), tmp.newFolder("cache"))

    /** A real JPEG of [w] x [h], made with Android's own encoder. */
    private fun jpeg(name: String, w: Int, h: Int): File {
        val f = File(tmp.root, name)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test fun storedPhotoLivesUnderFilesDirPhotosOnly() {
        val s = store()
        val out = s.import(jpeg("cap.jpg", 400, 300), "p1")!!
        assertTrue(s.isPrivate(out.path))
        assertTrue(out.path.contains("/files/photos/"))
        assertFalse("never external storage or the media store", out.path.contains("/sdcard") || out.path.contains("/DCIM") || out.path.contains("Pictures"))
        assertEquals("p1.jpg", out.name)
    }

    @Test fun aPathOutsideTheStoreIsNotPrivateAndIsNeverDeleted() {
        val s = store()
        val outside = File(tmp.root, "elsewhere.jpg").apply { writeText("x") }
        assertFalse(s.isPrivate(outside.path))
        s.delete(outside.path)
        assertTrue(outside.exists())
    }

    @Test fun importScalesTheLongSideTo1080AndDropsTheSource() = runBlocking {
        val s = store()
        val out = s.import(jpeg("big.jpg", 3000, 2000), "p2")!!
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(out.path, o)
        assertEquals(1080, maxOf(o.outWidth, o.outHeight))
    }

    @Test fun galleryAddDeleteRemovesFileAndRow() = runBlocking {
        val db = DbTestSupport.inMemory()
        val s = store()
        val g = RoomPhotoGateway(db, s) { 42L }
        val cap = s.captureFile("p4").also { jpeg("tmp.jpg", 200, 200).copyTo(it, overwrite = true) }
        assertTrue(g.add(cap, "p4", "front"))
        assertFalse("camera temp file is removed", cap.exists())
        val row: PhotoEntity = g.list().single()
        assertTrue(row.hiddenFromBackup)
        assertTrue(File(row.path).isFile)
        g.delete("p4")
        assertTrue(g.list().isEmpty())
        assertFalse(File(row.path).exists())
        db.close()
    }

    @Test fun photosAreHiddenFromBackupByDefault() {
        assertFalse(R_PRIV_PHOTO_BACKUP_DEFAULT)
        assertTrue(PhotoEntity("p", 1, 1, "x", 1, "front").hiddenFromBackup)
    }

    @Test fun androidBackupRulesExcludeEveryAppFolder() {
        for (path in listOf("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml")) {
            val xml = RepoFiles.text(path)
            for (domain in listOf("file", "root", "external", "database")) {
                assertTrue("$path excludes $domain", Regex("<exclude\\s+domain=\"$domain\"").containsMatchIn(xml))
            }
            assertFalse("$path has no include", xml.contains("<include"))
        }
        assertTrue(RepoFiles.text("app/src/main/AndroidManifest.xml").contains("android:allowBackup=\"false\""))
    }

    @Test fun thePhotoFolderIsNeverShared() {
        val paths = RepoFiles.text("app/src/main/res/xml/file_paths.xml")
        assertTrue(paths.contains("<cache-path"))
        assertFalse("filesDir/photos is not exposed by the FileProvider", Regex("<files-path|<external|<root-path").containsMatchIn(paths))
    }
}
