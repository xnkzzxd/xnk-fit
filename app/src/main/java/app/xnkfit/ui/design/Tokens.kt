package app.xnkfit.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.xnkfit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * XNK design tokens (docs/Design-Visual.md). Every value is copied from the live XNK theme
 * (docs/reference/xnk/theme/01-awal.part `:root` and the dark block, 05-portal-x.part accents).
 * This file is the ONLY place in the app with colour literals; screens read [XnkColors] through `Xnk.colors`.
 */

/** The seven accent choices of the XNK portal (05-portal-x.part). [INK] is the default: black in light, near-white in dark. */
enum class Accent { INK, BIRU, HIJAU, TOSKA, UNGU, PINK, ORANYE }

@Immutable
data class XnkColors(
    val dark: Boolean,
    /** Page background (`--bg`). */
    val bg: Color,
    /** Card (`--surface`). */
    val surface: Color,
    val surface2: Color,
    /** Grey fills: secondary buttons, KG/REP cells, bar tracks (`--surface-3`). */
    val surface3: Color,
    val border: Color,
    val borderStrong: Color,
    /** Text (`--fg`, `--fg-2`, `--fg-muted`). */
    val fg: Color,
    val fg2: Color,
    val fgMuted: Color,
    /** Primary button and ink card (`--ink`), with its text (`--on-ink`). */
    val ink: Color,
    val ink3: Color,
    val onInk: Color,
    val onInkMuted: Color,
    /** Top band and bottom bar (`--band`), the hero card on it (`--band-card`), their text (`--on-band`). */
    val band: Color,
    val bandCard: Color,
    val onBand: Color,
    val onBandMuted: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** Accent on light/dark page surfaces (`--accent`, `--on-accent`) and on black surfaces (`--accent-on-ink`). */
    val accent: Color,
    val onAccent: Color,
    val accentOnInk: Color,
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF0B0B0B)

/** `--accent`, `--on-accent`, `--accent-on-ink` for the light page; INK follows `--ink`. */
private fun lightAccent(a: Accent): Triple<Color, Color, Color> = when (a) {
    Accent.INK -> Triple(Black, White, White)
    Accent.BIRU -> Triple(Color(0xFF1D4ED8), White, Color(0xFF60A5FA))
    Accent.HIJAU -> Triple(Color(0xFF15803D), White, Color(0xFF4ADE80))
    Accent.TOSKA -> Triple(Color(0xFF0F766E), White, Color(0xFF2DD4BF))
    Accent.UNGU -> Triple(Color(0xFF6D28D9), White, Color(0xFFA78BFA))
    Accent.PINK -> Triple(Color(0xFFBE185D), White, Color(0xFFF472B6))
    Accent.ORANYE -> Triple(Color(0xFFC2410C), White, Color(0xFFFB923C))
}

/** Dark page: the light-on-ink shade becomes the accent itself, with black text on it. */
private fun darkAccent(a: Accent): Triple<Color, Color, Color> = when (a) {
    Accent.INK -> Triple(Color(0xFFF4F4F2), Black, White)
    else -> lightAccent(a).third.let { Triple(it, Black, it) }
}

fun lightXnkColors(accent: Accent = Accent.INK): XnkColors {
    val (ac, onAc, acInk) = lightAccent(accent)
    return XnkColors(
        dark = false,
        bg = Color(0xFFFAFAF9), surface = White, surface2 = Color(0xFFF5F5F4), surface3 = Color(0xFFECEBE9),
        border = Color(0xFFE7E5E4), borderStrong = Color(0xFFCFCFCB),
        fg = Black, fg2 = Color(0xFF3B3B39), fgMuted = Color(0xFF676764),
        ink = Black, ink3 = Color(0xFF2C2C2A), onInk = White, onInkMuted = Color(0xFFA7A7A4),
        band = Black, bandCard = Color(0xFF1A1A19), onBand = White, onBandMuted = Color(0xFFA7A7A4),
        danger = Color(0xFFC4261B), dangerSoft = Color(0xFFFDECEA),
        accent = ac, onAccent = onAc, accentOnInk = acInk,
    )
}

fun darkXnkColors(accent: Accent = Accent.INK): XnkColors {
    val (ac, onAc, acInk) = darkAccent(accent)
    return XnkColors(
        dark = true,
        bg = Color(0xFF0A0A0A), surface = Color(0xFF141414), surface2 = Color(0xFF1A1A1A), surface3 = Color(0xFF242424),
        border = Color(0xFF272727), borderStrong = Color(0xFF3A3A3A),
        fg = Color(0xFFF4F4F2), fg2 = Color(0xFFCFCFCC), fgMuted = Color(0xFF9B9B97),
        ink = Color(0xFFF4F4F2), ink3 = Color(0xFFD2D2CE), onInk = Black, onInkMuted = Color(0xFF5C5C59),
        band = Color(0xFF161616), bandCard = Color(0xFF2A2A2A), onBand = White, onBandMuted = Color(0xFFA7A7A4),
        danger = Color(0xFFFF6B5E), dangerSoft = Color(0xFF2A1412),
        accent = ac, onAccent = onAc, accentOnInk = acInk,
    )
}

/** Radii (`--r-*`). */
object XnkRadius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    /** Bottom bar pill (`.tabbar` border-radius). */
    val bar = 24.dp
    /** Centre ＋ button (`.tab-fab`). */
    val fab = 18.dp
}

/** Spacing (`--gap`, `--page-pad`, card paddings). */
object XnkSpace {
    val gap = 16.dp
    val page = 16.dp
    val pageWide = 28.dp
    val card = 18.dp
    val inkCard = 22.dp
    /** How far the first card hangs over the black band (`.px-hang` −52 px). */
    val hang = 52.dp
}

/**
 * Type scale (`--fs-*`, rem × 16 = sp). Sizes are sp so they follow the system font size; no text gets a fixed height.
 * Font: Inter, as in XNK (OFL, licence in assets/licenses), bundled as three static weights subset to Latin (about 52 KB each);
 * glyphs outside the subset fall back to the system font (Decisions 2026-11-27, T-01).
 */
object XnkType {
    val family = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )
    private fun s(size: Int, weight: FontWeight, spacing: Double = 0.0, line: Double = 1.3) =
        TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.em, lineHeight = line.em)

    val xs2 = s(11, FontWeight.Normal)
    val xs = s(12, FontWeight.Normal)
    val sm = s(13, FontWeight.Normal)
    val md = s(14, FontWeight.Normal)
    val lg = s(16, FontWeight.Normal)
    /** Eyebrow / label: 11 sp, bold, tracked, set in upper case by the caller's string. */
    val eyebrow = s(11, FontWeight.Bold, 0.08)
    /** Section heading (`.section-title`, `.px-sec h3`). */
    val section = s(16, FontWeight.ExtraBold, -0.01)
    /** Row title (`.todo-title`). */
    val rowTitle = s(14, FontWeight.Bold)
    val button = s(14, FontWeight.Bold, line = 1.2)
    val tab = s(10, FontWeight.Bold, line = 1.2)
    /** Hero title (`.px-hero-t`, 2xl). */
    val heroTitle = s(22, FontWeight.ExtraBold, -0.02, 1.15)
    /** Page title (`.page-title`, 3xl). */
    val pageTitle = s(28, FontWeight.ExtraBold, -0.03, 1.1)
    /** Big number (`.nx-count`, 4xl). */
    val bigNumber = s(36, FontWeight.ExtraBold, -0.03, 1.1)
    /** KPI value (2xl, extra bold). */
    val kpi = s(22, FontWeight.ExtraBold, -0.02, 1.15)
}
