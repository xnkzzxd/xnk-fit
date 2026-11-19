package app.xnkfit.ui.latihan

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.xnkfit.data.figures.BodyFigure
import app.xnkfit.data.figures.BodySide
import app.xnkfit.data.figures.RigData
import app.xnkfit.data.figures.RigFrame
import app.xnkfit.data.figures.RigMark
import app.xnkfit.data.figures.RigPattern
import app.xnkfit.data.figures.SegState
import app.xnkfit.domain.latihan.R_MAP_TONE_HIGH
import app.xnkfit.domain.latihan.R_MAP_TONE_LOW
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Drawing of the XNK muscle map and exercise rig (Content §5.1). Shapes come from the bundled figure files; every tone has a
// text twin on the screen, so colour is never the only signal (Design §15).

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

/** Three tones of one accent: low, mid, high. */
@Composable
private fun tone(level: Int): Color {
    val base = MaterialTheme.colorScheme.primary
    return when {
        level >= R_MAP_TONE_HIGH -> base
        level == R_MAP_TONE_LOW -> base.copy(alpha = 0.35f)
        else -> base.copy(alpha = 0.65f)
    }
}

/** Front and back of the body with [levels] per region name. [description] is read by a screen reader. */
@Composable
fun MuscleMapView(body: BodyFigure, levels: Map<String, Int>, description: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(max = 280.dp).semantics { contentDescription = description }, Arrangement.spacedBy(8.dp)) {
        BodySideView(body.front, levels, Modifier.weight(1f))
        BodySideView(body.back, levels, Modifier.weight(1f))
    }
}

@Composable
private fun BodySideView(side: BodySide, levels: Map<String, Int>, modifier: Modifier) {
    val outline = remember(side) { parse(side.outline) }
    val base = remember(side) { side.base.map(::parse) }
    val parts = remember(side) { side.parts.mapValues { (_, v) -> v.map(::parse) } }
    val bodyColor = MaterialTheme.colorScheme.surfaceVariant
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val partColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val tones = listOf(R_MAP_TONE_LOW, 2, R_MAP_TONE_HIGH).associateWith { tone(it) }
    Canvas(modifier.aspectRatio(side.viewBox[2] / side.viewBox[3])) {
        val sc = size.width / side.viewBox[2]
        scale(sc, sc, Offset.Zero) {
            translate(-side.viewBox[0], -side.viewBox[1]) {
                drawPath(outline, bodyColor)
                base.forEach { drawPath(it, baseColor) }
                parts.forEach { (region, shapes) ->
                    val lv = levels[region] ?: 0
                    shapes.forEach { drawPath(it, if (lv > 0) tones.getValue(lv.coerceIn(R_MAP_TONE_LOW, R_MAP_TONE_HIGH)) else partColor) }
                }
            }
        }
    }
}

/** True when the user switched system animations off; the rig then stays on its last frame. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun blend(frames: List<RigFrame>, t: Float): RigFrame {
    val pos = t.coerceIn(0f, 1f) * (frames.size - 1)
    val i = pos.toInt().coerceAtMost(frames.size - 2).coerceAtLeast(0)
    val a = frames[i]
    val b = frames.getOrElse(i + 1) { a }
    val f = pos - i
    val segs = a.segments.mapValues { (k, s) ->
        val o = b.segments[k] ?: s
        SegState(lerp(s.x, o.x, f), lerp(s.y, o.y, f), lerp(s.rot, o.rot, f), lerp(s.sx, o.sx, f))
    }
    val pts = a.points.mapValues { (k, p) -> val o = b.points[k] ?: p; floatArrayOf(lerp(p[0], o[0], f), lerp(p[1], o[1], f)) }
    val cab = a.cables.mapIndexed { n, c -> val o = b.cables.getOrElse(n) { c }; FloatArray(4) { lerp(c[it], o[it], f) } }
    return RigFrame(segs, pts, cab)
}

/**
 * The animated rig of an exercise that has one (`rig` in exercises.json). Loops over the 13 frames; a still last frame when
 * system animations are off.
 */
@Composable
fun RigView(pattern: RigPattern, data: RigData, sex: String, modifier: Modifier = Modifier) {
    val off = animationsOff()
    val t = if (off) 1f else rememberInfiniteTransition(label = "rig").animateFloat( // text-ok: label of an animation, SVG segment name
        0f, 1f, infiniteRepeatable(tween(RIG_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rig-t", // text-ok: label of an animation, SVG segment name
    ).value
    val frame = remember(pattern, t) { blend(pattern.frames, t) }
    val shapes = remember(pattern) { pattern.equip.map { e -> e.d?.let(::parse) } }
    val body = MaterialTheme.colorScheme.surfaceVariant
    val equip = MaterialTheme.colorScheme.outline
    val strong = MaterialTheme.colorScheme.primary
    val soft = strong.copy(alpha = 0.4f)
    Canvas(modifier.aspectRatio(1f)) {
        val sc = size.width / RIG_BOX
        scale(sc, sc, Offset.Zero) { drawRig(pattern, data, sex, frame, shapes, body, equip, strong, soft) }
    }
}

private fun DrawScope.drawRig(
    p: RigPattern, data: RigData, sex: String, frame: RigFrame, shapes: List<Path?>,
    body: Color, equip: Color, strong: Color, soft: Color,
) {
    val widths = (if (p.front && data.widthsFront.isNotEmpty()) data.widthsFront else data.widths)
    val w = widths[sex] ?: widths.values.first()
    var cableIx = 0
    p.equip.forEachIndexed { k, e ->
        if (e.cable) {
            val c = frame.cables.getOrNull(cableIx++) ?: return@forEachIndexed
            val rad = Math.toRadians(c[2].toDouble())
            drawLine(equip, Offset(c[0], c[1]), Offset(c[0] + (cos(rad) * c[3]).toFloat(), c[1] + (sin(rad) * c[3]).toFloat()), strokeWidth = if (e.thick) 2f else 1f)
            return@forEachIndexed
        }
        val path = shapes[k] ?: return@forEachIndexed
        val at = e.follows?.let { frame.points[it] }
        val draw: DrawScope.() -> Unit = { if (e.line) drawPath(path, equip, style = Stroke(1.5f)) else drawPath(path, if (e.soft) equip.copy(alpha = 0.4f) else equip) }
        if (at != null) translate(at[0], at[1]) { rotate(e.rot, Offset.Zero) { draw() } } else draw()
    }
    p.order.forEach { sg ->
        val s = frame.segments[sg] ?: return@forEach
        val b = sg.removeSuffix("L").removeSuffix("R") // text-ok: label of an animation, SVG segment name
        val wd = w[b] ?: 8f
        translate(s.x, s.y) {
            rotate(s.rot, Offset.Zero) {
                scale(s.sx, 1f, Offset.Zero) {
                    if (b == "head") drawCircle(body, wd, Offset(data.lengths.getValue("head"), 0f)) // text-ok: label of an animation, SVG segment name
                    else {
                        val len = data.lengths[b] ?: 0f
                        drawRoundRect(body, Offset(0f, -wd / 2), Size(len, wd), CornerRadius(wd / 2))
                        val marks = if (p.front) p.frontMarks.filter { it.segment == sg } else data.marks.filter { it.segment == b }
                        marks.forEach { m -> drawMark(m, b, wd, len, p, strong, soft) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMark(m: RigMark, base: String, wd: Float, len: Float, p: RigPattern, strong: Color, soft: Color) {
    val color = when (m.region) { in p.main -> strong; in p.helper -> soft; else -> return }
    val flip = if (p.front) 1f else if (base == "torso") 1f else -1f // text-ok: label of an animation, SVG segment name
    val cy = m.side * flip * wd * 0.28f
    val rx = m.rx
    val ry = min(m.ry, wd * 0.42f)
    drawOval(color, Offset(m.along * len - rx, cy - ry), Size(rx * 2, ry * 2))
}

private const val RIG_BOX = 200f
private const val RIG_MS = 1700
