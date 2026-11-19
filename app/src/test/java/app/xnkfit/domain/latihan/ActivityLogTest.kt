package app.xnkfit.domain.latihan

import app.xnkfit.domain.rules.R_MET_JALAN
import app.xnkfit.domain.rules.R_MET_LARI_VIGOROUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L-14: kcal from the MET table, vigorous counts double, the 300-minute note appears only at the upper range. */
class ActivityLogTest {
    private fun row(effort: Effort, min: Int, code: String = "jalan") = LoggedActivity("x", code, min, effort.code, 0.0, 0)

    @Test fun kcalIsMetTimesKgTimesHours() {
        assertEquals(R_MET_JALAN * 60.0 * 0.5, kcalFor(ActivityChoice.JALAN, Effort.SEDANG, 60.0, 30), 1e-9)
        assertEquals(R_MET_LARI_VIGOROUS * 70.0 * 1.0, kcalFor(ActivityChoice.LARI, Effort.BERAT, 70.0, 60), 1e-9)
    }

    @Test fun lightEffortAndMissingWeightGiveNoNumber() {
        assertEquals(0.0, kcalFor(ActivityChoice.LARI, Effort.RINGAN, 70.0, 30), 0.0)
        assertEquals(0.0, kcalFor(ActivityChoice.LARI, Effort.SEDANG, null, 30), 0.0)
    }

    @Test fun otherUsesTheLowestMetRowNotAHigherOne() {
        val lain = kcalFor(ActivityChoice.LAIN, Effort.SEDANG, 70.0, 30)
        ActivityChoice.entries.filter { it != ActivityChoice.LAIN }.forEach {
            assertTrue(lain <= kcalFor(it, Effort.SEDANG, 70.0, 30) + 1e-9)
        }
    }

    @Test fun vigorousCountsDoubleAndLightNotAtAll() {
        val w = weekActivity(listOf(row(Effort.SEDANG, 60), row(Effort.BERAT, 30), row(Effort.RINGAN, 500)))
        assertEquals(120, w.counted)
        assertEquals(0.8f, w.fraction, 1e-6f)
    }

    @Test fun theUpperRangeNoteComesOnlyAtThreeHundred() {
        assertFalse(weekActivity(listOf(row(Effort.SEDANG, 299))).atUpper)
        assertTrue(weekActivity(listOf(row(Effort.SEDANG, 300))).atUpper)
        assertTrue(weekActivity(listOf(row(Effort.BERAT, 150))).atUpper)
        assertEquals(1f, weekActivity(listOf(row(Effort.SEDANG, 400))).fraction, 0f)
    }

    @Test
    fun R_ACT_MINUTES_minutesAreCheckedForTypos() {
        assertFalse(validMinutes(0)); assertTrue(validMinutes(30)); assertFalse(validMinutes(R_ACT_MINUTES_MAX + 1))
    }
}
