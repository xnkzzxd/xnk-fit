package app.xnkfit.domain.progress

import app.xnkfit.domain.rules.R_LEVEL_STEP_AFTER_TABLE
import app.xnkfit.domain.rules.R_LEVEL_THRESHOLDS
import app.xnkfit.domain.rules.levelForPoints

/** Where a point total sits: [level], points at its start ([floor]) and at the next level ([next]). */
data class LevelProgress(val level: Int, val points: Int, val floor: Int, val next: Int) {
    /** 0f..1f across this level, for the bar. */
    val fraction: Float get() = ((points - floor).toFloat() / (next - floor)).coerceIn(0f, 1f)
}

/** R_LEVEL: points needed to start [level] (1-based). Uses the written thresholds, then +R_LEVEL_STEP_AFTER_TABLE each. */
fun levelStart(level: Int): Int {
    val table = R_LEVEL_THRESHOLDS
    return if (level <= table.size) table[(level - 1).coerceAtLeast(0)] else table.last() + (level - table.size) * R_LEVEL_STEP_AFTER_TABLE
}

/** R_LEVEL, R_LEVEL_NOLOSE: the level never drops because points only add. */
fun levelProgress(points: Int): LevelProgress {
    val p = points.coerceAtLeast(0)
    val level = levelForPoints(p)
    return LevelProgress(level, p, levelStart(level), levelStart(level + 1))
}
