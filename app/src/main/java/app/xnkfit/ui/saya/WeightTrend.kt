package app.xnkfit.ui.saya

import app.xnkfit.data.db.MeasureEntity

private const val MS_PER_DAY = 86_400_000L
private const val TREND_DAYS = 28
private const val CHART_WEEKS = 8
private const val DAYS_PER_WEEK = 7

/** One weight reading for the chart. */
data class WeightPoint(val takenAt: Long, val value: Double)

/**
 * A plain trend, with no verdict (PRD P-1): the latest value and the change since the reading nearest to four weeks
 * earlier. [change] is null with fewer than two readings. Nothing here decides whether a change is good or bad.
 */
data class WeightTrend(val latest: Double?, val change: Double?, val points: List<WeightPoint>)

fun weightTrend(rows: List<MeasureEntity>, nowMs: Long): WeightTrend {
    val all = rows.sortedBy { it.takenAt }.map { WeightPoint(it.takenAt, it.value) }
    if (all.isEmpty()) return WeightTrend(null, null, emptyList())
    val latest = all.last()
    val windowStart = nowMs - CHART_WEEKS * DAYS_PER_WEEK * MS_PER_DAY
    val shown = all.filter { it.takenAt >= windowStart }.ifEmpty { listOf(latest) }
    val target = latest.takenAt - TREND_DAYS * MS_PER_DAY
    val reference = all.dropLast(1).minByOrNull { kotlin.math.abs(it.takenAt - target) }
    return WeightTrend(latest.value, reference?.let { latest.value - it.value }, shown)
}
