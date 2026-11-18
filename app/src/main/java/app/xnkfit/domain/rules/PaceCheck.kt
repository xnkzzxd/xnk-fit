package app.xnkfit.domain.rules

// R_PACE (PRD S-8, M-2; S18): weight loss faster than 1 % of body weight per week, kept up for two weeks,
// suggests raising the target by 100 kcal. The user decides (M-2); nothing changes by itself.

const val R_PACE_LOSS_FRACTION_PER_WEEK = 0.01
const val R_PACE_WEEKS = 2
const val R_PACE_RAISE_KCAL = 100

/** One weekly weight (the weekly weigh-in, R_REM_HOURS "Timbang"). [weekIndex] counts weeks; consecutive = +1. */
data class WeeklyWeight(val weekIndex: Int, val kg: Double)

sealed interface PaceResult {
    data object NoSuggestion : PaceResult

    /** Suggest raising the calorie target by [raiseKcal]; show the reason and let the user accept or keep. */
    data class SuggestRaise(val raiseKcal: Int, val lossFractionPerWeek: Double) : PaceResult
}

/**
 * R_PACE. Reads the last three weekly weights (two week-to-week steps). Fires when **both** weeks show a loss
 * and the average loss over the two weeks is more than 1 % of the starting weight per week.
 * Gaps (missing weeks), non-finite or non-positive weights give no suggestion.
 */
fun paceCheck(weights: List<WeeklyWeight>): PaceResult {
    if (weights.size < R_PACE_WEEKS + 1) return PaceResult.NoSuggestion
    val last = weights.sortedBy { it.weekIndex }.takeLast(R_PACE_WEEKS + 1)
    if (last.any { !it.kg.isFinite() || it.kg <= 0.0 }) return PaceResult.NoSuggestion
    if (last.zipWithNext().any { (a, b) -> b.weekIndex - a.weekIndex != 1 }) return PaceResult.NoSuggestion
    if (last.zipWithNext().any { (a, b) -> b.kg >= a.kg }) return PaceResult.NoSuggestion
    val start = last.first().kg
    val perWeek = (start - last.last().kg) / start / R_PACE_WEEKS
    return if (perWeek > R_PACE_LOSS_FRACTION_PER_WEEK) {
        PaceResult.SuggestRaise(R_PACE_RAISE_KCAL, perWeek)
    } else {
        PaceResult.NoSuggestion
    }
}
