package app.xnkfit.ui.share

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.xnkfit.data.share.QrCode
import app.xnkfit.data.share.SHARE_URL
import app.xnkfit.data.share.SHARE_URL_IS_PLACEHOLDER
import app.xnkfit.domain.share.CardSpec
import java.io.File

/** Makes the picture for a card: texts from resources, the link QR, and the photo with its mosaic when asked (R_SHARE_FACE). */
class CardBitmaps(private val res: Resources) {
    private val qr by lazy { QrCode.encode(SHARE_URL) }
    private var cachedPhoto: Triple<String, Boolean, Bitmap?>? = null

    /** Heavy work (decode, face search, drawing): call off the main thread. */
    fun make(spec: CardSpec, widthPx: Int): Bitmap =
        CardRenderer.render(spec, cardTexts(res, spec), if (spec.brand && !SHARE_URL_IS_PLACEHOLDER) qr else null, photoFor(spec), widthPx)

    @Synchronized
    private fun photoFor(spec: CardSpec): Bitmap? {
        val path = spec.photoPath ?: return null
        cachedPhoto?.let { if (it.first == path && it.second == spec.blurFaces) return it.third }
        val file = File(path)
        if (!file.isFile) return null
        val raw = BitmapFactory.decodeFile(path) ?: return null
        // R_SHARE_FACE: the mosaic is part of making the picture, so no path can draw an unblurred photo when the switch is on.
        val shown = if (spec.blurFaces) FaceMosaic.apply(raw) else raw
        cachedPhoto = Triple(path, spec.blurFaces, shown)
        return shown
    }
}
