package app.xnkfit.domain.progress

import app.xnkfit.domain.rules.R_STREAK_GRACE_EMPTY_WEEKS
import app.xnkfit.domain.rules.R_STREAK_GRACE_WINDOW_WEEKS
import app.xnkfit.domain.rules.weekCounts
import java.time.LocalDate

/**
 * [current]: weeks in the running streak (counting weeks only; a forgiven week is neither counted nor a break).
 * [best]: the longest value [current] ever had; the streak badges use it, so a badge is never lost to a later break.
 * [weekStarts]: every Monday of a week that counts, for the streak-week points.
 */
data class StreakResult(val current: Int, val best: Int, val weekStarts: List<LocalDate>)

/**
 * R_STREAK and R_STREAK_GRACE (PRD P-4). A week counts with at least 2 active days. An empty week is forgiven
 * when no other week was forgiven in the three weeks before it (one empty week in every four). A second empty
 * week inside that window ends the streak. The week that holds [today] is still open: counting adds to the
 * streak, not counting yet changes nothing.
 */
fun computeStreak(activeDays: Set<LocalDate>, today: LocalDate): StreakResult {
    val counts = HashMap<LocalDate, Int>()
    activeDays.filter { !it.isAfter(today) }.forEach { counts.merge(it.weekStart(), 1, Int::plus) }
    if (counts.isEmpty()) return StreakResult(0, 0, emptyList())
    val last = today.weekStart()
    var week = counts.keys.min()
    var current = 0
    var best = 0
    val forgiven = ArrayDeque<LocalDate>()
    val counting = ArrayList<LocalDate>()
    while (!week.isAfter(last)) {
        val ok = weekCounts(counts[week] ?: 0)
        if (ok) {
            current += 1
            best = maxOf(best, current)
            counting += week
        } else if (week != last) {
            val windowStart = week.minusWeeks((R_STREAK_GRACE_WINDOW_WEEKS - 1).toLong())
            while (forgiven.isNotEmpty() && forgiven.first() < windowStart) forgiven.removeFirst()
            if (current > 0 && forgiven.size < R_STREAK_GRACE_EMPTY_WEEKS) {
                forgiven.addLast(week)
            } else {
                current = 0
                forgiven.clear()
            }
        }
        week = week.plusWeeks(1)
    }
    return StreakResult(current, best, counting)
}
