package app.xnkfit.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

val LocalXnkColors = staticCompositionLocalOf { lightXnkColors() }

/** Access to the XNK tokens inside [XnkDesignTheme]: `Xnk.colors.band`, `XnkType.section`, `XnkRadius.lg`. */
object Xnk {
    val colors: XnkColors
        @Composable @ReadOnlyComposable get() = LocalXnkColors.current
}

/**
 * Material 3 stays underneath (dialogs, text fields, sheets of screens not yet redrawn), but every Material colour comes from the
 * XNK tokens: primary = accent (black by default), surfaces = page and card, outline = border.
 */
fun XnkColors.toMaterial(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = surface3, onPrimaryContainer = fg,
        secondary = fg2, onSecondary = bg,
        secondaryContainer = surface3, onSecondaryContainer = fg,
        tertiary = fg2, onTertiary = bg,
        tertiaryContainer = surface3, onTertiaryContainer = fg,
        background = bg, onBackground = fg,
        surface = bg, onSurface = fg,
        surfaceVariant = surface3, onSurfaceVariant = fgMuted,
        surfaceTint = bg,
        inverseSurface = ink, inverseOnSurface = onInk, inversePrimary = accentOnInk,
        error = danger, onError = if (dark) bg else surface,
        errorContainer = dangerSoft, onErrorContainer = danger,
        outline = borderStrong, outlineVariant = border,
        scrim = ink,
        surfaceBright = surface, surfaceDim = surface2,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface2, surfaceContainerHighest = surface3,
    )
}

/** Material type roles mapped onto the XNK scale (system sans-serif, heavier headings). */
private fun xnkTypography(): Typography {
    val t = Typography()
    return t.copy(
        headlineLarge = XnkType.bigNumber,
        headlineMedium = XnkType.pageTitle,
        headlineSmall = XnkType.heroTitle,
        titleLarge = XnkType.heroTitle.copy(fontSize = XnkType.lg.fontSize * 1.25f),
        titleMedium = XnkType.section,
        titleSmall = XnkType.rowTitle,
        bodyLarge = XnkType.lg,
        bodyMedium = XnkType.md,
        bodySmall = XnkType.xs,
        labelLarge = XnkType.button,
        labelMedium = XnkType.xs.copy(fontWeight = FontWeight.Bold),
        labelSmall = XnkType.xs2.copy(fontWeight = FontWeight.Bold),
    )
}

private val XnkShapes = Shapes(
    extraSmall = RoundedCornerShape(XnkRadius.xs),
    small = RoundedCornerShape(XnkRadius.sm),
    medium = RoundedCornerShape(XnkRadius.md),
    large = RoundedCornerShape(XnkRadius.lg),
    extraLarge = RoundedCornerShape(XnkRadius.xl),
)

/** The XNK theme: tokens for [dark] and [accent], provided to the XNK components and mapped onto Material 3. */
@Composable
fun XnkDesignTheme(dark: Boolean, accent: Accent = Accent.INK, content: @Composable () -> Unit) {
    val colors = remember(dark, accent) { if (dark) darkXnkColors(accent) else lightXnkColors(accent) }
    val typography = remember { xnkTypography() }
    CompositionLocalProvider(LocalXnkColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = typography, shapes = XnkShapes, content = content)
    }
}
