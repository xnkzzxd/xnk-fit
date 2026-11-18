package app.xnkfit.domain.rules

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/** Rules.md §7 (product rules; full logic in B-12). */
class ProgressRulesTest {
    @Test
    fun R_PTS_WORKOUT_10() = assertEquals(10, PointSource.WORKOUT_FINISHED.points)

    @Test
    fun R_PTS_ACT_3per30MinCapOnePerDay() {
        assertEquals(3, PointSource.ACTIVITY_30_MIN.points)
        assertEquals(30, R_PTS_ACT_MINUTES)
        assertEquals(1, R_PTS_ACT_DAILY_CAP)
    }

    @Test
    fun R_PTS_FOOD_2forThreeSlots() {
        assertEquals(2, PointSource.FOOD_DAY.points)
        assertEquals(3, R_PTS_FOOD_MIN_SLOTS)
    }

    @Test
    fun R_PTS_MEASURE_2() = assertEquals(2, PointSource.MEASURE_ON_SCHEDULE.points)

    @Test
    fun R_PTS_STREAK_5() = assertEquals(5, PointSource.STREAK_WEEK.points)

    @Test
    fun R_PTS_TARGET_5withinTenPercent() {
        assertEquals(5, PointSource.TARGET_WEEK.points)
        assertEquals(0.10, R_PTS_TARGET_BAND, 1e-12)
        assertTrue(weekWithinTarget(2200.0, 2000))
        assertTrue(weekWithinTarget(1800.0, 2000))
        assertFalse(weekWithinTarget(2201.0, 2000))
        assertFalse(weekWithinTarget(Double.NaN, 2000))
    }

    @Test
    fun R_PTS_NOWEIGHT_noPointSourceForWeightChange() {
        // P-3: points never come from the weight number. Only these sources exist.
        assertEquals(
            setOf("WORKOUT_FINISHED", "ACTIVITY_30_MIN", "FOOD_DAY", "MEASURE_ON_SCHEDULE", "STREAK_WEEK", "TARGET_WEEK"),
            PointSource.entries.map { it.name }.toSet(),
        )
    }

    /** ⚠ R_LEVEL: thresholds are a tuning default, owner decision Rules §12 #3 still open. */
    @Ignore("unverified")
    @Test
    fun R_LEVEL_thresholds() {
        assertEquals(listOf(0, 100, 250, 450, 700, 1000, 1400, 1900, 2500), R_LEVEL_THRESHOLDS)
        assertEquals(600, R_LEVEL_STEP_AFTER_TABLE)
        assertEquals(1, levelForPoints(0))
        assertEquals(2, levelForPoints(100))
        assertEquals(9, levelForPoints(2500))
        assertEquals(10, levelForPoints(3100))
    }

    @Test
    fun R_LEVEL_NOLOSE_levelNeverDropsAsPointsAdd() {
        var last = 0
        for (p in 0..10_000 step 7) {
            val l = levelForPoints(p)
            assertTrue(l >= last)
            last = l
        }
        assertEquals(1, levelForPoints(-50)) // never below level 1
    }

    @Test
    fun R_STREAK_twoActiveDays() {
        assertEquals(2, R_STREAK_MIN_ACTIVE_DAYS)
        assertFalse(weekCounts(1))
        assertTrue(weekCounts(2))
    }

    @Test
    fun R_STREAK_GRACE_oneInFour() {
        assertEquals(1, R_STREAK_GRACE_EMPTY_WEEKS)
        assertEquals(4, R_STREAK_GRACE_WINDOW_WEEKS)
    }

    @Test
    fun R_BADGE_COUNT_18MatchesContent() {
        assertEquals(18, R_BADGE_COUNT)
        val json = RepoFiles.text("content/badges.json")
        assertEquals(R_BADGE_COUNT, Regex("\"code\"\\s*:").findAll(json).count())
        assertFalse("no badge for weight lost (P-5)", Regex("\"code\"\\s*:\\s*\"[^\"]*(WEIGHT|BERAT)[^\"]*LOSS").containsMatchIn(json))
    }
}
