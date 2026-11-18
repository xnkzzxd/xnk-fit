package app.xnkfit.domain.rules

// Points, levels, streaks, badges (Rules.md §7, PRD P-3…P-5). Product rules; the full logic is B-12.

const val R_PTS_WORKOUT = 10
const val R_PTS_ACT = 3
const val R_PTS_ACT_MINUTES = 30
const val R_PTS_ACT_DAILY_CAP = 1
const val R_PTS_FOOD = 2
const val R_PTS_FOOD_MIN_SLOTS = 3
const val R_PTS_MEASURE = 2
const val R_PTS_STREAK = 5
const val R_PTS_TARGET = 5
const val R_PTS_TARGET_BAND = 0.10

// R_LEVEL: cumulative thresholds, then +600 per level (⚠ tuning, owner decision Rules §12 #3).
const val R_LEVEL_STEP_AFTER_TABLE = 600
val R_LEVEL_THRESHOLDS: List<Int> = listOf(0, 100, 250, 450, 700, 1000, 1400, 1900, 2500)

const val R_STREAK_MIN_ACTIVE_DAYS = 2
const val R_STREAK_GRACE_EMPTY_WEEKS = 1
const val R_STREAK_GRACE_WINDOW_WEEKS = 4
const val R_BADGE_COUNT = 18

/**
 * Things that give points. R_PTS_NOWEIGHT: there is no entry for a weight change; a weigh-in on the
 * scheduled day gives points whatever the number is.
 */
enum class PointSource(val points: Int) {
    WORKOUT_FINISHED(R_PTS_WORKOUT),
    ACTIVITY_30_MIN(R_PTS_ACT),
    FOOD_DAY(R_PTS_FOOD),
    MEASURE_ON_SCHEDULE(R_PTS_MEASURE),
    STREAK_WEEK(R_PTS_STREAK),
    TARGET_WEEK(R_PTS_TARGET),
}

/** R_LEVEL: level (1-based) for cumulative points. R_LEVEL_NOLOSE: points only add, so the level never drops. */
fun levelForPoints(points: Int): Int {
    val p = points.coerceAtLeast(0)
    val inTable = R_LEVEL_THRESHOLDS.count { p >= it }
    if (inTable < R_LEVEL_THRESHOLDS.size) return inTable
    return R_LEVEL_THRESHOLDS.size + (p - R_LEVEL_THRESHOLDS.last()) / R_LEVEL_STEP_AFTER_TABLE
}

/** R_STREAK: a week counts with at least 2 active days (workout, activity or food day). */
fun weekCounts(activeDays: Int): Boolean = activeDays >= R_STREAK_MIN_ACTIVE_DAYS

/** R_PTS_TARGET: weekly logged average within ±10 % of the target. */
fun weekWithinTarget(averageKcal: Double, targetKcal: Int): Boolean {
    if (!averageKcal.isFinite() || targetKcal <= 0) return false
    return kotlin.math.abs(averageKcal - targetKcal) <= R_PTS_TARGET_BAND * targetKcal
}
