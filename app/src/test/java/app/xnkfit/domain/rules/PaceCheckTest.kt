package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules.md §3 R_PACE (PRD S-8; S18 0.5–1 %/week). */
class PaceCheckTest {
    private fun w(vararg kg: Double) = kg.mapIndexed { i, v -> WeeklyWeight(i, v) }

    @Test
    fun R_PACE_numbers() {
        assertEquals(0.01, R_PACE_LOSS_FRACTION_PER_WEEK, 1e-12)
        assertEquals(2, R_PACE_WEEKS)
        assertEquals(100, R_PACE_RAISE_KCAL)
    }

    @Test
    fun R_PACE_fastLossForTwoWeeksSuggestsPlus100() {
        // 80 → 79 → 78: (80 − 78) / 80 / 2 = 1.25 %/week > 1 %, both weeks down.
        val r = paceCheck(w(80.0, 79.0, 78.0))
        assertTrue(r is PaceResult.SuggestRaise)
        assertEquals(100, (r as PaceResult.SuggestRaise).raiseKcal)
        assertEquals(0.0125, r.lossFractionPerWeek, 1e-12)
    }

    @Test
    fun R_PACE_exactlyOnePercentDoesNotFire() {
        // 100 → 99 → 98: exactly 1 %/week, not faster.
        assertEquals(PaceResult.NoSuggestion, paceCheck(w(100.0, 99.0, 98.0)))
    }

    @Test
    fun R_PACE_oneFastWeekOnlyDoesNotFire() {
        // 80 → 78 → 78.2: week 2 is a gain, so the pace is not kept up for two weeks.
        assertEquals(PaceResult.NoSuggestion, paceCheck(w(80.0, 78.0, 78.2)))
        // Only one week of data.
        assertEquals(PaceResult.NoSuggestion, paceCheck(w(80.0, 78.0)))
    }

    @Test
    fun R_PACE_usesTheLastTwoWeeks() {
        // Older slow weeks do not hide a fast recent pair: 90, 89.8, 89.6, then 88.5, 87.4.
        assertTrue(paceCheck(w(90.0, 89.8, 89.6, 88.5, 87.4)) is PaceResult.SuggestRaise)
        // Order of the list does not matter, week index does.
        val shuffled = listOf(WeeklyWeight(2, 78.0), WeeklyWeight(0, 80.0), WeeklyWeight(1, 79.0))
        assertTrue(paceCheck(shuffled) is PaceResult.SuggestRaise)
    }

    @Test
    fun R_PACE_gapsAndBadValuesGiveNoSuggestion() {
        val gap = listOf(WeeklyWeight(0, 80.0), WeeklyWeight(1, 79.0), WeeklyWeight(3, 77.0))
        assertEquals(PaceResult.NoSuggestion, paceCheck(gap))
        assertEquals(PaceResult.NoSuggestion, paceCheck(w(80.0, Double.NaN, 78.0)))
        assertEquals(PaceResult.NoSuggestion, paceCheck(w(80.0, 0.0, -1.0)))
        assertEquals(PaceResult.NoSuggestion, paceCheck(emptyList()))
    }
}
