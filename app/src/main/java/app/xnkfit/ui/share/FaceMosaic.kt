package app.xnkfit.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.FaceDetector
import app.xnkfit.domain.share.Box
import app.xnkfit.domain.share.mosaicBoxes

/**
 * Face mosaic for a photo on a share card (R_SHARE_FACE). Uses the framework's own offline [FaceDetector]: no library,
 * no network. That detector finds frontal faces only, so when it finds none the whole picture is pixelated:
 * a card must never leak a face the detector missed (PR-5).
 */
@Suppress("UseKtx") // text-ok: lint id; core-ktx is not in the Design §12 catalog
object FaceMosaic {
    private const val DETECT_MAX_SIDE = 640
    private const val MAX_FACES = 8
    private const val EYE_SPAN = 1.6f
    private const val BLOCKS_ACROSS = 24
    private const val WHOLE_PICTURE_BLOCKS = 16

    /** A new bitmap; [src] is not changed. */
    fun apply(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val boxes = mosaicBoxes(detect(src), src.width, src.height)
        if (boxes.isEmpty()) {
            pixelate(out, Box(0, 0, out.width, out.height), WHOLE_PICTURE_BLOCKS)
        } else {
            boxes.forEach { pixelate(out, it, BLOCKS_ACROSS) }
        }
        return out
    }

    internal fun detect(src: Bitmap): List<Box> = try {
        val k = minOf(1f, DETECT_MAX_SIDE.toFloat() / maxOf(src.width, src.height))
        val w = ((src.width * k).toInt() / 2 * 2).coerceAtLeast(2) // the detector needs an even width
        val h = (src.height * k).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val n = FaceDetector(w, h, MAX_FACES).findFaces(small, faces)
        (0 until n).mapNotNull { faces[it] }.map { f ->
            val mid = android.graphics.PointF().also { f.getMidPoint(it) }
            val r = f.eyesDistance() * EYE_SPAN / k
            Box(((mid.x / k) - r).toInt(), ((mid.y / k) - r).toInt(), ((mid.x / k) + r).toInt(), ((mid.y / k) + r).toInt())
        }
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** Replaces [box] with big flat blocks: shrink the region, then enlarge it without smoothing. */
    internal fun pixelate(bmp: Bitmap, box: Box, blocksAcross: Int) {
        val l = box.left.coerceIn(0, bmp.width)
        val t = box.top.coerceIn(0, bmp.height)
        val r = box.right.coerceIn(l, bmp.width)
        val b = box.bottom.coerceIn(t, bmp.height)
        if (r - l < 2 || b - t < 2) return
        val block = maxOf(2, maxOf(r - l, b - t) / blocksAcross)
        val region = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        val tiny = Bitmap.createScaledBitmap(region, maxOf(1, (r - l) / block), maxOf(1, (b - t) / block), true)
        Canvas(bmp).drawBitmap(tiny, null, Rect(l, t, r, b), Paint().apply { isFilterBitmap = false })
    }
}
