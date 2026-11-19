package app.xnkfit.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.xnkfit.data.share.QrCode
import app.xnkfit.domain.share.CardSpec
import app.xnkfit.domain.share.CardStyle
import app.xnkfit.domain.share.CardTemplate

/**
 * Draws a share card on an Android [Canvas] (PRD P-6). Nothing is uploaded: the bitmap goes to the system share sheet.
 * All sizes are written for a 1,080 px wide card and scaled, so the preview and the shared picture look alike.
 * Colours follow the approved mockup (black, white, accent), not the app theme, so a card looks the same in light and dark mode.
 */
object CardRenderer {
    private const val DESIGN_WIDTH = 1080f
    private const val PAD = 76f
    private const val CORNER = 56f
    private const val LABEL_SIZE = 34f
    private const val HEAD_SIZE_GRID = 104f
    private const val HEAD_SIZE_BIG = 170f
    private const val BOX_HEIGHT = 190f
    private const val BOX_GAP = 24f
    private const val BOX_VALUE = 76f
    private const val BOX_LABEL = 34f
    private const val LIFT_SIZE = 48f
    private const val BRAND_SIZE = 60f
    private const val NOTE_SIZE = 36f
    private const val QR_PLATE = 200f
    private const val QR_QUIET = 2
    private const val SOFT_ALPHA = 36
    private const val MUTED_ALPHA = 170
    private const val PHOTO_SHARE = 0.38f

    private class Palette(val bg: Int, val fg: Int, val border: Int?)

    private fun palette(s: CardStyle) = when (s) {
        CardStyle.DARK -> Palette(0xFF0B0B0B.toInt(), Color.WHITE, null)
        CardStyle.LIGHT -> Palette(Color.WHITE, 0xFF0B0B0B.toInt(), 0xFFE7E5E4.toInt())
        CardStyle.ACCENT -> Palette(0xFF1D4ED8.toInt(), Color.WHITE, null)
    }

    /**
     * [photo] must already be mosaicked by the caller when the spec asks for it ([FaceMosaic]); the renderer never decides privacy.
     * [qr] is drawn only when the spec has the brand switch on.
     */
    fun render(spec: CardSpec, texts: CardTexts, qr: QrCode?, photo: Bitmap?, widthPx: Int = spec.size.widthPx): Bitmap {
        val k = widthPx / DESIGN_WIDTH
        val heightPx = widthPx * spec.size.heightPx / spec.size.widthPx
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palette(spec.style)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val pad = PAD * k

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.bg }
        val card = RectF(0f, 0f, w, h)
        c.drawRoundRect(card, CORNER * k, CORNER * k, fill)
        p.border?.let {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * k; color = it }
            c.drawRoundRect(RectF(1.5f * k, 1.5f * k, w - 1.5f * k, h - 1.5f * k), CORNER * k, CORNER * k, line)
        }

        fun text(size: Float, bold: Boolean, alpha: Int = 255) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.fg
            this.alpha = alpha
            textSize = size * k
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        // Top line: kind on the left, date on the right.
        var y = pad
        val label = text(LABEL_SIZE, true, MUTED_ALPHA).apply { letterSpacing = 0.08f }
        c.drawText(texts.label.uppercase(), pad, y + label.textSize, label)
        texts.date?.let {
            val d = text(LABEL_SIZE, true, MUTED_ALPHA)
            c.drawText(it, w - pad - d.measureText(it), y + d.textSize, d)
        }
        y += label.textSize + 40f * k

        // Photo, already mosaicked: a centre-cropped rounded block under the top line.
        if (photo != null && photo.width > 0 && photo.height > 0) {
            val box = RectF(pad, y, w - pad, y + h * PHOTO_SHARE)
            val scale = maxOf(box.width() / photo.width, box.height() / photo.height)
            val sw = box.width() / scale
            val sh = box.height() / scale
            val src = android.graphics.Rect(((photo.width - sw) / 2).toInt(), ((photo.height - sh) / 2).toInt(), ((photo.width + sw) / 2).toInt(), ((photo.height + sh) / 2).toInt())
            val clip = Path().apply { addRoundRect(box, 36f * k, 36f * k, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            c.drawBitmap(photo, src, box, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            y = box.bottom + 40f * k
        }

        val big = spec.template == CardTemplate.BIG
        y += 24f * k
        y = drawBlock(c, texts.headline, text(if (big) HEAD_SIZE_BIG else HEAD_SIZE_GRID, true), pad, y, w - 2 * pad) + 36f * k

        if (big) {
            texts.fields.forEach { (value, name) ->
                val line = "$value $name" // text-ok: joins a number and its label
                y = drawBlock(c, line, text(BOX_VALUE * 0.8f, false), pad, y, w - 2 * pad) + 12f * k
            }
        } else {
            val colW = (w - 2 * pad - BOX_GAP * k) / 2
            val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.fg; alpha = SOFT_ALPHA }
            texts.fields.forEachIndexed { i, (value, name) ->
                val x = pad + (i % 2) * (colW + BOX_GAP * k)
                val top = y + (i / 2) * (BOX_HEIGHT + BOX_GAP) * k
                c.drawRoundRect(RectF(x, top, x + colW, top + BOX_HEIGHT * k), 32f * k, 32f * k, soft)
                val v = text(BOX_VALUE, true)
                c.drawText(value, x + 28f * k, top + 40f * k + v.textSize, v)
                val n = text(BOX_LABEL, false, MUTED_ALPHA)
                c.drawText(name, x + 28f * k, top + (BOX_HEIGHT - 30f) * k, n)
            }
            val rows = (texts.fields.size + 1) / 2
            if (rows > 0) y += rows * (BOX_HEIGHT + BOX_GAP) * k
        }
        texts.lift?.let { y = drawBlock(c, it, text(LIFT_SIZE, true), pad, y + 12f * k, w - 2 * pad) + 12f * k }

        // Bottom line: no-kg note and app name on the left, QR on the right.
        val bottom = h - pad
        texts.noKgNote?.let {
            val n = text(NOTE_SIZE, false, MUTED_ALPHA)
            c.drawText(it, pad, bottom - (if (texts.brand != null) BRAND_SIZE + 24f else 0f) * k, n)
        }
        texts.brand?.let { c.drawText(it, pad, bottom, text(BRAND_SIZE, true)) }
        if (qr != null && spec.brand) drawQr(c, qr, RectF(w - pad - QR_PLATE * k, bottom - QR_PLATE * k, w - pad, bottom), k)
        return bmp
    }

    private fun drawBlock(c: Canvas, s: String, paint: TextPaint, x: Float, y: Float, width: Float): Float {
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return y + layout.height
    }

    /** Dark modules on a white plate with a quiet zone, whatever the card style, so a phone camera can always read it. */
    private fun drawQr(c: Canvas, qr: QrCode, plate: RectF, k: Float) {
        c.drawRoundRect(plate, 24f * k, 24f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val cell = plate.width() / (qr.size + 2 * QR_QUIET)
        val dark = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (yy in 0 until qr.size) for (xx in 0 until qr.size) {
            if (qr.isDark(xx, yy)) {
                val l = plate.left + (xx + QR_QUIET) * cell
                val t = plate.top + (yy + QR_QUIET) * cell
                c.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, dark)
            }
        }
    }
}
