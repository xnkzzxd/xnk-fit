package app.xnkfit.ui.design

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.ui.common.MinTouch

/*
 * XNK components (docs/Design-Visual.md §2). Colours come only from Xnk.colors; text sizes are sp; no text has a fixed height
 * (boxes use heightIn(min = …) so they grow with the font size); every tap target is at least MinTouch (48 dp).
 */

// ── Bands and cards ───────────────────────────────────────────────────────────────────────────────

/**
 * Black top band (`.topbar` + `.px-band`) with contour lines at the top right. One row: [title] when given (page name, as a
 * heading), otherwise the [brand] mark; then an optional [subtitle] (the date on Hari ini). Kept to one row on the other tabs so
 * small phones (Pixel 2, 731 dp) lose no more height than the old Material bars did.
 */
@Composable
fun TopBand(
    brand: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Xnk.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.band)
            .drawBehind { drawContours(c.onBand) }
            .statusBarsPadding()
            .testTag("fid:pita_atas") // text-ok: test tag
            .padding(start = XnkSpace.page, end = XnkSpace.page, top = 4.dp, bottom = if (subtitle != null) 10.dp else 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
            if (title != null) {
                Text(title, style = XnkType.heroTitle, color = c.onBand, modifier = Modifier.weight(1f).semantics { heading() })
            } else {
                Text(brand, style = XnkType.section, color = c.onBand, modifier = Modifier.weight(1f))
            }
            CompositionLocalProvider(LocalContentColor provides c.onBand) { actions() }
        }
        if (subtitle != null) Text(subtitle, style = XnkType.sm, color = c.onBandMuted)
    }
}

/**
 * Top band of Hari ini (`.topbar` with the brand mark): the X logo on the left, a bell button and a round avatar with the initials
 * on the right (64 dp high as on the portal), then the [date] line and an optional [trailing] note (level, streak). The bell carries
 * no badge: the app has no notifications to count. Both buttons keep a 48 dp touch target around the drawn 44 / 40 dp.
 * Logo: the portal's `--logo-mask` bitmap (demo/index.html), tinted in code.
 */
@Composable
fun HomeTopBand(
    date: String?,
    initials: String,
    bellLabel: String,
    avatarLabel: String,
    onBell: () -> Unit,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val c = Xnk.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.band)
            .drawBehind { drawContours(c.onBand) }
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.testTag("fid:pita_atas").fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 12.dp), // text-ok: test tag
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_logo_x), contentDescription = null, tint = c.onBand, modifier = Modifier.size(width = 21.dp, height = 20.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(MinTouch).clickable(role = Role.Button, onClick = onBell).semantics { contentDescription = bellLabel }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.bandCard).border(1.dp, c.onBand.copy(alpha = 0.18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = c.onBand, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.size(MinTouch).clickable(role = Role.Button, onClick = onAvatar).semantics { contentDescription = avatarLabel }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.onBand), contentAlignment = Alignment.Center) {
                    Text(initials, style = XnkType.button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = c.band)
                }
            }
        }
        if (date != null) {
            Row(Modifier.fillMaxWidth().padding(start = XnkSpace.page, end = XnkSpace.page, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date, style = XnkType.sm, color = c.onBandMuted, maxLines = 1)
                if (trailing != null) Text(trailing, style = XnkType.sm, color = c.onBandMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The band continuing behind the top part of the first card (`.px-band` padding + `.px-hang`). Put it as the first item of a page;
 * [content] (usually a [HeroCard]) is drawn over the band edge. Full width: the caller does not pad it.
 */
@Composable
fun BandHang(modifier: Modifier = Modifier, bandBelow: Dp = 40.dp, sidePadding: Boolean = true, content: @Composable () -> Unit) {
    val c = Xnk.colors
    Box(
        modifier.fillMaxWidth().drawBehind {
            drawRect(c.band, size = Size(size.width, bandBelow.toPx().coerceAtMost(size.height)))
        }.padding(horizontal = if (sidePadding) XnkSpace.page else 0.dp),
    ) { content() }
}

/**
 * Black hero card (`.card-ink.gfx-geo` on the band): rings and a dot at the top right drawn on Canvas, white text, [eyebrow]
 * label, then the caller's content (title, big number, buttons).
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    rings: Boolean = true,
    /** The reference hero is about 210 px tall; a floor keeps the rings clear of the buttons. Text can still make it taller. */
    minHeight: Dp = 196.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Xnk.colors
    val unit = LocalDensity.current.density
    val shape = RoundedCornerShape(XnkRadius.xl)
    Column(
        modifier
            .testTag("fid:kartu_hero_hitam") // text-ok: test tag
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(c.bandCard)
            .border(1.dp, c.onBand.copy(alpha = 0.12f), shape)
            .drawBehind { if (rings) drawRings(c.onBand, c.accentOnInk, unit) }
            .padding(XnkSpace.inkCard),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onBand) {
            if (eyebrow != null) Box(Modifier.padding(vertical = 5.dp)) { Text(eyebrow.uppercase(), style = XnkType.eyebrow, color = c.onBandMuted) }
            content()
        }
    }
}

/** White card (`.card`): surface, 1 dp border, radius 22, padding 18. */
@Composable
fun XnkCard(modifier: Modifier = Modifier, padding: Dp = XnkSpace.card, content: @Composable ColumnScope.() -> Unit) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.lg)
    Column(
        modifier.testTag("fid:kartu_putih").fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(padding), // text-ok: test tag
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { CompositionLocalProvider(LocalContentColor provides c.fg) { content() } }
}

/** Flat grey card (`.card-flat`): surface-2, radius 16, padding 14. For nested blocks and quiet notes. */
@Composable
fun CardFlat(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.md)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { CompositionLocalProvider(LocalContentColor provides c.fg) { content() } }
}

/** Height floor of a section heading row (not a touch target; the action inside has its own 48 dp). */
private val SectionMinHeight = 24.dp

/** Section heading with the short vertical bar (`.px-sec`): title, then a muted [trailing] text or an [action]. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Xnk.colors
    Row(modifier.testTag("fid:px_sec").fillMaxWidth().heightIn(min = SectionMinHeight), verticalAlignment = Alignment.CenterVertically) { // text-ok: test tag
        Box(Modifier.testTag("fid:judul_bagian_garis").width(4.dp).height(18.dp).background(c.fg, RoundedCornerShape(2.dp))) // text-ok: test tag
        Spacer(Modifier.width(10.dp))
        Text(title, style = XnkType.section, color = c.fg, modifier = Modifier.weight(1f).testTag("fid:judul_bagian").semantics { heading() }) // text-ok: test tag
        if (trailing != null) Text(trailing, style = XnkType.sm, color = c.fgMuted, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        action?.invoke()
    }
}

/** Thin progress bar (`.px-bar`): track surface-3 (or translucent on ink), fill accent. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, onInk: Boolean = false) {
    val c = Xnk.colors
    val track = if (onInk) c.onBand.copy(alpha = 0.18f) else c.surface3
    val fill = if (onInk) c.accentOnInk else c.accent
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        drawLine(track, Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), h, StrokeCap.Round)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) drawLine(fill, Offset(h / 2, h / 2), Offset(h / 2 + (size.width - h) * f, h / 2), h, StrokeCap.Round)
    }
}

// ── Buttons ─────────────────────────────────────────────────────────────────────────────────────

private enum class BtnKind { PRIMARY, SECONDARY, TONAL }

@Composable
private fun XnkButton(
    kind: BtnKind,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    onInk: Boolean,
    icon: Painter?,
    enabled: Boolean,
) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.sm)
    val (bg, fg, border) = when (kind) {
        BtnKind.PRIMARY -> if (onInk) Triple(c.onBand, c.band, null) else Triple(c.accent, c.onAccent, null)
        BtnKind.TONAL -> if (onInk) Triple(c.onBand.copy(alpha = 0.12f), c.onBand, null) else Triple(c.surface3, c.fg, null)
        BtnKind.SECONDARY -> if (onInk) Triple(Color.Transparent, c.onBand, c.onBand.copy(alpha = 0.4f)) else Triple(Color.Transparent, c.fg, c.borderStrong)
    }
    val fidTag = when (kind) { BtnKind.PRIMARY -> "fid:tombol_utama"; BtnKind.TONAL -> "fid:tombol_sekunder"; BtnKind.SECONDARY -> "fid:tombol_garis" } // text-ok: test tag
    Row(
        modifier
            .then(if (onInk) Modifier else Modifier.testTag(fidTag)) // the hero buttons are 48 dp (touch), the portal's are 44 and not `.btn`
            .heightIn(min = if (onInk) MinTouch else 52.dp)
            .clip(shape)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.5f))
            .then(if (border != null) Modifier.border(BorderStroke(1.5.dp, border), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) fg else fg.copy(alpha = 0.5f)
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = XnkType.button, color = color, textAlign = TextAlign.Center)
    }
}

/** Primary action (`.btn-primary`): accent fill (black by default); white on an ink card when [onInk]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, onInk: Boolean = false, icon: Painter? = null, enabled: Boolean = true) =
    XnkButton(BtnKind.PRIMARY, text, onClick, modifier, onInk, icon, enabled)

/** Secondary action (`.btn-outline`): transparent with a border. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, onInk: Boolean = false, icon: Painter? = null, enabled: Boolean = true) =
    XnkButton(BtnKind.SECONDARY, text, onClick, modifier, onInk, icon, enabled)

/** Tonal action (`.btn-secondary`): grey fill (surface-3), or translucent white on an ink card. */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, onInk: Boolean = false, icon: Painter? = null, enabled: Boolean = true) =
    XnkButton(BtnKind.TONAL, text, onClick, modifier, onInk, icon, enabled)

// ── Data pieces ─────────────────────────────────────────────────────────────────────────────────

/** KPI tile (`.kpi`): big bold value over a muted label. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.md)
    val bg = if (emphasis) c.accent else c.surface
    val fg = if (emphasis) c.onAccent else c.fg
    Column(
        modifier.testTag("fid:stat_tile").clip(shape).background(bg).border(1.dp, if (emphasis) bg else c.border, shape).heightIn(min = 88.dp).padding(12.dp), // text-ok: test tag
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = XnkType.kpi, color = fg, textAlign = TextAlign.Center)
        Text(label, style = XnkType.xs, color = if (emphasis) fg.copy(alpha = 0.75f) else c.fgMuted, textAlign = TextAlign.Center)
    }
}

/** Ring progress (`.ring-wrap`): track surface-3, accent arc from the top, [center] in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    strokeRatio: Float = 0.1f,
    center: @Composable () -> Unit = {},
) {
    val c = Xnk.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.minDimension * strokeRatio
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(c.surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawArc(c.accent, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

/**
 * A to-do row (`.todo-row`): round check, title and [sub], trailing [action]. Done rows are muted, the check filled with the
 * accent. [stateText] is read out by TalkBack ("selesai" / "belum"), so colour is never the only signal.
 */
@Composable
fun CheckRow(
    title: String,
    done: Boolean,
    stateText: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Xnk.colors
    val line = c.border
    Row(
        modifier
            .testTag("fid:baris_tugas") // text-ok: test tag
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawBehind { if (divider) drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .semantics(mergeDescendants = action == null) { stateDescription = stateText }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.testTag("fid:lingkaran_centang").size(26.dp)) { // text-ok: test tag
            val s = this.size.width
            if (done) {
                drawCircle(c.accent)
                drawCheck(c.onAccent, s, 3.dp.toPx())
            } else {
                drawCircle(c.fg, radius = s / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.testTag("fid:tugas_judul"), // text-ok: test tag
                style = XnkType.rowTitle,
                color = if (done) c.fgMuted else c.fg,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            if (sub != null) Text(sub, style = XnkType.xs, color = c.fgMuted, modifier = Modifier.testTag("fid:tugas_sub")) // text-ok: test tag
        }
        action?.invoke()
    }
}

/** Small action inside a [CheckRow] (`.todo-act`): accent fill when [strong], grey otherwise; 48 dp touch target. */
@Composable
fun RowAction(text: String, onClick: () -> Unit, strong: Boolean = false) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.heightIn(min = MinTouch).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = XnkType.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = if (strong) c.onAccent else c.fg,
            modifier = Modifier.clip(shape).background(if (strong) c.accent else c.surface3).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Filter chip (`.chip`): pill, accent fill when selected. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = MinTouch)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = XnkType.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = if (selected) c.onAccent else c.fg,
            modifier = Modifier
                .testTag("fid:chip") // text-ok: test tag
                .clip(shape)
                .background(if (selected) c.accent else c.surface)
                .border(1.dp, if (selected) c.accent else c.border, shape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Logger cell (`KG` / `REP`): big grey box with a bold value; [label] names it for TalkBack. Tappable when [onClick] is set. */
@Composable
fun KgRepCell(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.sm)
    Box(
        modifier
            .testTag("fid:sel_input") // text-ok: test tag
            .heightIn(min = MinTouch)
            .widthIn(min = 56.dp)
            .clip(shape)
            .background(c.surface3)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" } // text-ok: joins two resource strings
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(value, style = XnkType.lg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = c.fg, maxLines = 1, overflow = TextOverflow.Visible) }
}

/** Square set-done button of the logger: accent with a check when done, grey otherwise. [label] says what it does. */
@Composable
fun CheckButton(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.sm)
    Box(
        modifier
            .testTag("fid:tombol_centang") // text-ok: test tag
            .size(MinTouch)
            .clip(shape)
            .background(if (checked) c.accent else c.surface3)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) { drawCheck(if (checked) c.onAccent else c.fgMuted, size.width, 3.dp.toPx()) }
    }
}

/** Simple line chart: [points] scaled to the box, accent line with round joints. [description] is the text summary for TalkBack. */
@Composable
fun LineChart(points: List<Float>, description: String, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val c = Xnk.colors
    Canvas(modifier.fillMaxWidth().heightIn(min = height).semantics { contentDescription = description }) {
        val pad = 6.dp.toPx()
        for (i in 0..3) {
            val y = pad + (size.height - 2 * pad) * i / 3f
            drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        if (points.size < 2) return@Canvas
        val min = points.min()
        val span = (points.max() - min).takeIf { it > 0f } ?: 1f
        val step = (size.width - 2 * pad) / (points.size - 1)
        val pts = points.mapIndexed { i, v -> Offset(pad + i * step, pad + (size.height - 2 * pad) * (1f - (v - min) / span)) }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(c.accent, 4.dp.toPx(), pts.last())
    }
}

/** Week strip dot (`.week-x`): filled accent when active, grey otherwise; today gets a ring. */
@Composable
fun DayDot(active: Boolean, today: Boolean, modifier: Modifier = Modifier) {
    val c = Xnk.colors
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (active) c.accent else c.surface3)
            .then(if (today) Modifier.border(2.dp, c.fg, CircleShape) else Modifier),
    )
}
