package app.xnkfit.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsTest {
    private val clock = F.clock(F.week(0).plusDays(6))
    private fun kinds(d: ProgressData, kind: String) = derivePointEvents(d, clock).filter { it.kind == kind }

    @Test fun finishedWorkoutGivesTen() {
        val e = kinds(ProgressData(workouts = listOf(F.workout("w1", F.week(0)))), "workout")
        assertEquals(listOf(10), e.map { it.points })
    }

    @Test fun activityGivesThreePerThirtyMinutesCappedAtOnePerDay() {
        val d = F.week(0)
        val data = ProgressData(activities = listOf(F.act(d, 20), F.act(d, 20), F.act(d.plusDays(1), 29), F.act(d.plusDays(2), 90)))
        val e = kinds(data, "activity")
        assertEquals(listOf(3, 3), e.map { it.points }) // day 0: 40 min, day 2: 90 min (cap 1), day 1: 29 min gives none
        assertEquals(2, e.size)
    }

    @Test fun foodDayNeedsThreeSlots() {
        val d = F.week(0)
        val two = ProgressData(foodLogs = listOf(F.food(d, "pagi"), F.food(d, "siang"), F.food(d, "siang")))
        val three = ProgressData(foodLogs = listOf(F.food(d, "pagi"), F.food(d, "siang"), F.food(d, "camilan")))
        assertTrue(kinds(two, "foodDay").isEmpty())
        assertEquals(listOf(2), kinds(three, "foodDay").map { it.points })
    }

    @Test fun measureOnTheScheduledSundayGivesTwoOncePerWeek() {
        val sun = F.week(0).plusDays(6)
        val data = ProgressData(measures = listOf(
            MeasureRow("weight", F.ms(sun, 7)), MeasureRow("weight", F.ms(sun, 20)),
            MeasureRow("weight", F.ms(F.week(0).plusDays(2))), // a Wednesday: not the scheduled day
            MeasureRow("waist", F.ms(F.week(1).plusDays(6))), // not a weight measure
        ))
        assertEquals(listOf(2), kinds(data, "measure").map { it.points })
    }

    @Test fun streakWeekGivesFiveForEveryCountingWeek() {
        val e = kinds(F.dataWithWeeks(F.week(-2), F.week(-1), F.week(0)), "streakWeek")
        assertEquals(3, e.size); assertTrue(e.all { it.points == 5 })
    }

    @Test fun loggedWeekIsFiveDaysOfFoodAndNeverLooksAtAmounts() {
        val four = ProgressData(foodLogs = (0 until 4).map { F.food(F.week(0).plusDays(it.toLong())) })
        val five = ProgressData(foodLogs = (0 until 5).map { F.food(F.week(0).plusDays(it.toLong())) })
        assertTrue(kinds(four, "loggedWeek").isEmpty())
        assertEquals(listOf(5), kinds(five, "loggedWeek").map { it.points })
    }

    @Test fun keysAreStableSoARecountIsANoOp() {
        val data = ProgressData(workouts = listOf(F.workout("w1", F.week(0))), foodLogs = F.activeWeek(F.week(0)))
        val a = derivePointEvents(data, clock).map { it.key }
        val b = derivePointEvents(data, clock).map { it.key }
        assertEquals(a, b)
        assertEquals(a.size, a.toSet().size)
    }

    @Test fun noPointKindExistsForWeightChange() {
        // R_PTS_NOWEIGHT: the rows carry no weight value, and the only kinds that can come out are these.
        val data = ProgressData(
            workouts = listOf(F.workout("w", F.week(0))), activities = listOf(F.act(F.week(0), 60)),
            foodLogs = (0 until 7).flatMap { d -> listOf("pagi", "siang", "malam").map { F.food(F.week(0).plusDays(d.toLong()), it) } },
            measures = listOf(MeasureRow("weight", F.ms(F.week(0).plusDays(6)))),
        )
        val kinds = derivePointEvents(data, clock).map { it.kind }.toSet()
        assertEquals(setOf("workout", "activity", "foodDay", "measure", "streakWeek", "loggedWeek"), kinds)
        assertFalse(MeasureRow::class.java.declaredFields.any { it.type == Double::class.javaPrimitiveType })
    }

    @Test fun theSameWeighInPaysTheSameWhateverTheNumberIs() {
        // The row has no value field, so two weigh-ins with very different numbers are the same input.
        val a = ProgressData(measures = listOf(MeasureRow("weight", F.ms(F.week(0).plusDays(6)))))
        assertEquals(derivePointEvents(a, clock), derivePointEvents(a.copy(), clock))
    }
}
