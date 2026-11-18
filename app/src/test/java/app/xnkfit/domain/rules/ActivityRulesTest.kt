package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rules.md §4 (S12, S13). */
class ActivityRulesTest {
    @Test
    fun R_MET_compendiumValues() {
        val expected = mapOf(
            "jalan" to (3.5 to null), "lari" to (8.3 to 11.0), "sepeda" to (6.8 to 8.0),
            "renang" to (5.8 to 9.8), "angkat" to (3.5 to 6.0), "senam" to (6.5 to 7.3),
            "bulutangkis" to (5.5 to 7.3), "sepak" to (7.0 to 10.0),
        )
        assertEquals(expected.keys, ActivityType.entries.map { it.code }.toSet())
        ActivityType.entries.forEach {
            assertEquals(it.code, expected.getValue(it.code).first, it.moderateMet, 1e-9)
            assertEquals(it.code, expected.getValue(it.code).second, it.vigorousMet)
        }
        assertNull(ActivityType.JALAN.vigorousMet)
    }

    @Test
    fun R_MET_kcalIsMetTimesKgTimesHours() {
        // Lari 8.3 × 70 kg × 0.5 h = 290.5 kcal; vigorous 11.0 × 70 × 0.5 = 385
        assertEquals(290.5, activityKcal(ActivityType.LARI, false, 70.0, 30), 1e-9)
        assertEquals(385.0, activityKcal(ActivityType.LARI, true, 70.0, 30), 1e-9)
        // Jalan has no vigorous value: moderate is used. 3.5 × 60 × 1 = 210
        assertEquals(210.0, activityKcal(ActivityType.JALAN, true, 60.0, 60), 1e-9)
        // Unusable input gives 0, never a negative.
        assertEquals(0.0, activityKcal(ActivityType.LARI, false, Double.NaN, 30), 0.0)
        assertEquals(0.0, activityKcal(ActivityType.LARI, false, 70.0, -5), 0.0)
    }

    @Test
    fun R_WHO_MIN_150to300VigorousDouble() {
        assertEquals(150, R_WHO_MIN_TARGET)
        assertEquals(300, R_WHO_MIN_UPPER)
        assertEquals(2, R_WHO_MIN_VIGOROUS_FACTOR)
        // 90 moderate + 30 vigorous = 90 + 60 = 150
        assertEquals(150, moderateEquivalentMinutes(90, 30))
        assertEquals(0, moderateEquivalentMinutes(-10, -10))
    }
}
