package app.xnkfit.data.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Progress photos live in the app's private storage and nowhere else (PV-3, Design §1): `filesDir/photos/<id>.jpg`.
 * Never the media store, never external storage. Backup rules exclude the `file` domain, so the folder is not in
 * Android backup; Drive backup of photos is a separate switch (B-16) and defaults to off (R_PRIV_PHOTO).
 */
class PhotoStore(private val filesDir: File, private val cacheDir: File) {
    constructor(context: Context) : this(context.filesDir, context.cacheDir)

    private val dir: File get() = File(filesDir, DIR_NAME).apply { mkdirs() }

    /** The stored picture for [id]. */
    fun file(id: String): File = File(dir, "$id.jpg")

    /** Where the camera app writes before [import]: the app's private cache, handed out by a FileProvider for that one file. */
    fun captureFile(id: String): File = File(File(cacheDir, CAPTURE_DIR).apply { mkdirs() }, "$id.jpg")

    fun exists(path: String): Boolean = File(path).isFile

    /** True when [path] is inside this store's folder (so a row can never point at a shared location). */
    fun isPrivate(path: String): Boolean = File(path).canonicalFile.startsWith(dir.canonicalFile)

    /**
     * Re-encodes a picture as JPEG with the long side at most [MAX_SIDE_PX] (Design §1) into the private folder.
     * The EXIF block of the source is dropped (it can hold location), because only the pixels are copied.
     * Returns the stored file, or null when the source cannot be decoded.
     */
    fun import(source: File, id: String): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeFile(source.path, opts) ?: return null
        val scaled = scaleDown(decoded)
        val out = file(id)
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return out
    }

    fun delete(path: String) {
        val f = File(path)
        if (isPrivate(path)) f.delete()
    }

    @Suppress("UseKtx") // core-ktx is not in the Design §12 catalog
    private fun scaleDown(b: Bitmap): Bitmap {
        val longSide = maxOf(b.width, b.height)
        if (longSide <= MAX_SIDE_PX) return b
        val k = MAX_SIDE_PX.toFloat() / longSide
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    companion object {
        const val DIR_NAME = "photos"
        const val CAPTURE_DIR = "capture"
        const val MAX_SIDE_PX = 1080
        private const val JPEG_QUALITY = 85

        /** Largest power-of-two subsampling that keeps the long side at or above [MAX_SIDE_PX]. */
        fun sampleSize(width: Int, height: Int): Int {
            var s = 1
            while (maxOf(width, height) / (s * 2) >= MAX_SIDE_PX) s *= 2
            return s
        }
    }
}
