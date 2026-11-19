package app.xnkfit.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun bmp() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun oneCardFileInTheShareFolderAndOlderOnesAreRemoved() {
        val dir = File(context.cacheDir, ShareFiles.DIR).apply { mkdirs() }
        File(dir, "old.png").writeText("x")
        val f = ShareFiles.write(context.cacheDir, bmp())
        assertTrue(f.isFile && f.length() > 0)
        assertEquals(listOf(ShareFiles.FILE_NAME), dir.list()!!.toList())
    }

    @Test
    fun chooserSendsOnePngThroughTheFileProviderWithAReadGrant() {
        val f = ShareFiles.write(context.cacheDir, bmp())
        val chooser = ShareFiles.chooser(context, f, "t")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".captures", uri.authority)
        assertTrue(uri.path!!.endsWith(ShareFiles.FILE_NAME))
    }
}
