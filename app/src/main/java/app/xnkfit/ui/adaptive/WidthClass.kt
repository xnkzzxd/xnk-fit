package app.xnkfit.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Window width classes (PRD N-4, Design §5). Pure so a JVM test covers the threshold. */
enum class WidthClass {
    /** Under 600 dp: bottom bar, one column. */
    COMPACT,

    /** 600 dp and wider: side rail, two columns. */
    WIDE,
}

/** The breakpoint in dp, the same 600 as the approved mockups (`mock.css`). */
const val WIDE_MIN_DP = 600

fun widthClassOf(widthDp: Int): WidthClass = if (widthDp >= WIDE_MIN_DP) WidthClass.WIDE else WidthClass.COMPACT

/** Rail 88 dp, two 24 dp side paddings, 16 dp between the columns (as in `mock.css`). */
private const val CHROME_DP = 88 + 48 + 16

/** Smallest column, in dp before text scaling, that still shows a card without clipped text. */
private const val MIN_COLUMN_DP = 150f

/**
 * Two columns need room: at font scale 2.0 a 600 dp window gives each column about 116 dp of unscaled text width,
 * so it falls back to one column (D-2 no clipped text). At 1.3 a 600 dp window keeps two columns.
 */
fun useTwoColumns(widthDp: Int, fontScale: Float): Boolean =
    widthClassOf(widthDp) == WidthClass.WIDE && (widthDp - CHROME_DP) / 2f / fontScale.coerceAtLeast(1f) >= MIN_COLUMN_DP

/** Measured from the window the app runs in, so split-screen and rotation are handled (no extra dependency). */
val LocalWidthClass = compositionLocalOf { WidthClass.COMPACT }

@Composable
fun currentWidthClass(): WidthClass = widthClassOf(windowWidthDp())

@Composable
private fun windowWidthDp(): Int = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }

/** Whether a screen body splits in two now; false on phones and on a very large font. */
@Composable
fun currentTwoColumns(): Boolean = useTwoColumns(windowWidthDp(), LocalDensity.current.fontScale)
