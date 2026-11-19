package app.xnkfit.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QrCodeTest {
    private fun rows(q: QrCode) = (0 until q.size).joinToString("\n") { y -> (0 until q.size).joinToString("") { x -> if (q.isDark(x, y)) "1" else "0" } }

    @Test
    fun versionGrowsWithTheText() {
        assertEquals(1, QrCode.encode("A".repeat(14)).version)
        assertEquals(2, QrCode.encode("A".repeat(15)).version)
        assertEquals(10, QrCode.encode("A".repeat(QrCode.MAX_BYTES)).version)
        assertEquals(21, QrCode.encode("A").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooLongTextIsRefused() { QrCode.encode("A".repeat(QrCode.MAX_BYTES + 1)) }

    @Test
    fun fixedPatternsArePresent() {
        val q = QrCode.encode("https://example.org/xnk")
        // Finder pattern corners and the always-dark module.
        assertTrue(q.isDark(0, 0) && q.isDark(6, 0) && q.isDark(0, 6) && q.isDark(q.size - 1, 0) && q.isDark(0, q.size - 1))
        assertFalse(q.isDark(1, 1)) // light ring inside the finder
        assertTrue(q.isDark(2, 2))
        assertTrue(q.isDark(8, q.size - 8))
        assertFalse(q.isDark(-1, 0))
    }

    /** Pinned after OpenCV decoded the same matrix back to the text (QR check in B-13). */
    @Test
    fun linkMatrixIsPinned() {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(rows(QrCode.encode("https://xnkzzxd.github.io/xnk-fit/unduh")).toByteArray())
        assertEquals("5536ee1bb9ab82d676e47d99028d3e9bd249276cca269edeffa442006ea21115", digest.joinToString("") { "%02x".format(it) })
    }

    /** Matrices written for the decoder check in the sandbox (see TODO B-13); set QR_DUMP=dir to produce them. */
    @Test
    fun dumpForExternalDecoder() {
        val dir = System.getenv("QR_DUMP") ?: return
        listOf("https://xnkzzxd.github.io/xnk-fit/unduh", "A".repeat(100), "A".repeat(213), "XNK Fit").forEachIndexed { i, t ->
            File(dir, "qr$i.txt").writeText(t + "\n" + rows(QrCode.encode(t)))
        }
    }
}
