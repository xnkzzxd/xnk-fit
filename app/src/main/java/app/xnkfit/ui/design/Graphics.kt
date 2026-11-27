package app.xnkfit.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Line art of the XNK black surfaces, drawn with Canvas (no bitmap, static, cheap).
 * Geometry copied from 05-portal-x.part: `.gfx-kontur` (band) and `.card-ink.gfx-geo` (hero card).
 */

/** Five contour curves in the top-right of the band: SVG viewBox 390×130 scaled to the band height, right-aligned. */
fun DrawScope.drawContours(color: Color) {
    val curves = listOf(
        floatArrayOf(160f, -10f, 220f, 30f, 300f, -20f, 400f, 30f) to 0.22f,
        floatArrayOf(150f, 10f, 220f, 52f, 300f, 0f, 400f, 54f) to 0.18f,
        floatArrayOf(140f, 32f, 220f, 76f, 300f, 22f, 400f, 80f) to 0.14f,
        floatArrayOf(130f, 56f, 220f, 102f, 300f, 46f, 400f, 108f) to 0.10f,
        floatArrayOf(120f, 82f, 220f, 130f, 300f, 72f, 400f, 138f) to 0.07f,
    )
    val scale = size.height / 130f
    val dx = size.width - 390f * scale
    val stroke = Stroke(width = 1.2f * scale)
    curves.forEach { (p, alpha) ->
        val path = Path().apply {
            moveTo(dx + p[0] * scale, p[1] * scale)
            cubicTo(dx + p[2] * scale, p[3] * scale, dx + p[4] * scale, p[5] * scale, dx + p[6] * scale, p[7] * scale)
        }
        drawPath(path, color.copy(alpha = alpha), style = stroke)
    }
}

/**
 * Concentric rings and a solid dot near the top-right of an ink card. `.gfx-geo`: a 250 px disc whose centre is 55 px from the
 * right and 95 px from the top, rings every 28.5 px (1.5 px line, 15 % of the text colour), fading out past 30 % of the radius;
 * the 36 px dot sits in the centre in the accent-on-ink colour. [unit] is one CSS px in pixels (the density).
 */
fun DrawScope.drawRings(lineColor: Color, dotColor: Color, unit: Float) {
    val centre = Offset(size.width - 55f * unit, 95f * unit)
    val outer = 125f * unit
    var r = 28.5f * unit
    while (r < outer) {
        val t = r / outer
        // Mask: full below 30 % of the radius, gone at 72 %.
        val fade = ((0.72f - t) / (0.72f - 0.30f)).coerceIn(0f, 1f)
        if (fade > 0f) drawCircle(lineColor.copy(alpha = 0.15f * fade), r, centre, style = Stroke(1.5f * unit))
        r += 28.5f * unit
    }
    drawCircle(dotColor, 18f * unit, centre)
}

/** A check mark inside a box of [side] px, in [color] (todo circle, check button). */
fun DrawScope.drawCheck(color: Color, side: Float, strokePx: Float) {
    val path = Path().apply {
        moveTo(side * 0.24f, side * 0.52f)
        lineTo(side * 0.42f, side * 0.70f)
        lineTo(side * 0.76f, side * 0.32f)
    }
    drawPath(path, color, style = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}
