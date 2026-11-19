package app.xnkfit.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private fun days(vararg weeksWithDays: Pair<Int, Int>): Set<LocalDate> =
        weeksWithDays.flatMap { (w, n) -> (0 until n).map { F.week(w).plusDays(it.toLong()) } }.toSet()

    @Test fun noActivityIsZero() {
        val r = computeStreak(emptySet(), F.monday)
        assertEquals(0, r.current); assertEquals(0, r.best)
    }

    @Test fun oneActiveDayDoesNotCountTwoDo() {
        assertEquals(0, computeStreak(days(0 to 1), F.week(0).plusDays(6)).current)
        assertEquals(1, computeStreak(days(0 to 2), F.week(0).plusDays(6)).current)
    }

    @Test fun consecutiveWeeksAdd() {
        val r = computeStreak(days(0 to 2, 1 to 3, 2 to 2, 3 to 5), F.week(3).plusDays(6))
        assertEquals(4, r.current); assertEquals(4, r.best)
        assertEquals(listOf(F.week(0), F.week(1), F.week(2), F.week(3)), r.weekStarts)
    }

    @Test fun oneEmptyWeekInFourIsForgiven() {
        // weeks 0,1 active, week 2 empty (forgiven), weeks 3,4 active: 4 counting weeks, no break.
        val r = computeStreak(days(0 to 2, 1 to 2, 3 to 2, 4 to 2), F.week(4).plusDays(6))
        assertEquals(4, r.current)
    }

    @Test fun aWeekWithOneActiveDayIsAnEmptyWeekAndIsForgivenOnce() {
        val r = computeStreak(days(0 to 2, 1 to 1, 2 to 2), F.week(2).plusDays(6))
        assertEquals(2, r.current)
    }

    @Test fun secondEmptyWeekInsideTheFourWeekWindowBreaksTheStreak() {
        // active 0,1; empty 2 (forgiven); empty 3 inside the same four weeks -> break; active 4.
        val r = computeStreak(days(0 to 2, 1 to 2, 4 to 2), F.week(4).plusDays(6))
        assertEquals(1, r.current)
        assertEquals(2, r.best) // the best value stays, so a streak badge is never lost to a later break
    }

    @Test fun forgivenessComesBackAfterFourWeeks() {
        // empty at week 2 and again at week 6: the first forgiveness is more than three weeks back.
        val r = computeStreak(days(0 to 2, 1 to 2, 3 to 2, 4 to 2, 5 to 2, 7 to 2), F.week(7).plusDays(6))
        assertEquals(6, r.current)
    }

    @Test fun theOpenWeekNeverBreaksAStreak() {
        // today is Tuesday of week 3 and nothing is logged yet this week.
        val r = computeStreak(days(0 to 2, 1 to 2, 2 to 2), F.week(3).plusDays(1))
        assertEquals(3, r.current)
    }

    @Test fun anEmptyStreakStartDoesNotUseUpForgiveness() {
        val r = computeStreak(days(2 to 2, 3 to 2), F.week(3).plusDays(6))
        assertEquals(2, r.current)
    }

    @Test fun weeksRunMondayToSunday() {
        // Sunday of week 0 and Monday of week 1 are two different weeks.
        val d = setOf(F.week(0).plusDays(6), F.week(1))
        assertEquals(0, computeStreak(d, F.week(1).plusDays(6)).current)
    }

    @Test fun activeDaysCollectWorkoutActivityAndFoodOnly() {
        val data = ProgressData(
            workouts = listOf(F.workout("a", F.week(0))),
            activities = listOf(F.act(F.week(0).plusDays(1), 20)),
            foodLogs = listOf(F.food(F.week(0).plusDays(2)), F.food(F.week(0).plusDays(2), "siang")),
            measures = listOf(MeasureRow("weight", F.ms(F.week(0).plusDays(3)))),
        )
        assertEquals(3, activeDays(data, F.zone).size)
    }
}
