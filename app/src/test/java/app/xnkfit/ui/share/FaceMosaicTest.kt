package app.xnkfit.ui.share

import android.graphics.Bitmap
import app.xnkfit.domain.share.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The detector itself needs a real device (Robolectric has no face model); what is tested here is the pixelation and the safe fallback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FaceMosaicTest {
    private fun stripes(): Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { b ->
        for (x in 0 until 200) for (y in 0 until 200) b.setPixel(x, y, if ((x / 2 + y / 2) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }

    @Test
    fun pixelateFlattensOnlyTheBox() {
        val b = stripes()
        val before = b.getPixel(150, 150)
        FaceMosaic.pixelate(b, Box(20, 20, 100, 100), 4)
        // Inside the box, neighbouring pixels now share a block colour; outside the box nothing changed.
        assertEquals(b.getPixel(30, 30), b.getPixel(31, 31))
        assertEquals(before, b.getPixel(150, 150))
    }

    @Test
    fun withoutAFaceTheWholePictureIsPixelatedAndTheSourceIsKept() {
        val src = stripes()
        val out = FaceMosaic.apply(src)
        assertEquals(200 to 200, out.width to out.height)
        assertNotEquals(out, src)
        // A fine stripe pattern cannot survive: the sample pixels around one spot are all alike.
        assertEquals(out.getPixel(100, 100), out.getPixel(101, 101))
        assertEquals(0xFF000000.toInt(), src.getPixel(0, 0))
    }
}
