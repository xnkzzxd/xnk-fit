package app.xnkfit.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StepsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private fun d(back: Int, steps: Long) = DaySteps(today.minusDays(back.toLong()), steps)

    @Test fun averageCountsDaysWithData() {
        val c = summarize(listOf(d(0, 4000), d(1, 8000), d(2, 0), d(3, 6000)), today)!!
        assertEquals(4000L, c.today)
        assertEquals(6000L, c.weeklyAverage)
    }

    @Test fun daysOutsideTheWeekAreIgnored() {
        val c = summarize(listOf(d(0, 1000), d(7, 90000), d(-1, 90000)), today)!!
        assertEquals(1000L, c.weeklyAverage)
    }

    @Test fun todayWithoutDataStillShowsZeroWhenWeekHasData() {
        val c = summarize(listOf(d(1, 5000)), today)!!
        assertEquals(0L, c.today)
        assertEquals(5000L, c.weeklyAverage)
    }

    @Test fun noDataGivesNull() {
        assertNull(summarize(emptyList(), today))
        assertNull(summarize(listOf(d(0, 0), d(2, 0)), today))
    }

    @Test fun weekWindowIsSevenDaysEndingToday() {
        val w = weekWindow(today)
        assertEquals(today.minusDays(6), w.start)
        assertEquals(today, w.endInclusive)
    }

    @Test fun hiddenWhenHealthConnectMissingOrOld() {
        listOf(HealthAvailability.MISSING, HealthAvailability.NEEDS_UPDATE).forEach {
            assertEquals(StepsCard.Hidden, stepsCard(it, true, true, listOf(d(0, 100)), today))
            assertEquals(StepsCard.Hidden, stepsCard(it, false, false, emptyList(), today))
        }
    }

    @Test fun asksOnceThenHidesAfterRefusal() {
        assertEquals(StepsCard.AskPermission, stepsCard(HealthAvailability.AVAILABLE, false, false, emptyList(), today))
        assertEquals(StepsCard.Hidden, stepsCard(HealthAvailability.AVAILABLE, false, true, emptyList(), today))
    }

    @Test fun grantedWithNoDataIsHidden() {
        assertEquals(StepsCard.Hidden, stepsCard(HealthAvailability.AVAILABLE, true, true, emptyList(), today))
    }

    @Test fun grantedWithDataShows() {
        assertEquals(StepsCard.Shown(100, 100), stepsCard(HealthAvailability.AVAILABLE, true, true, listOf(d(0, 100)), today))
    }
}
