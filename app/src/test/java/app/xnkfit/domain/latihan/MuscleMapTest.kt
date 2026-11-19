package app.xnkfit.domain.latihan

import app.xnkfit.domain.training.MuscleGroup
import app.xnkfit.domain.training.TrainingLevel
import app.xnkfit.domain.training.TrainingTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L-11: muscle map levels from content muscles and from a week of done sets. */
class MuscleMapTest {
    private val lib = TrainingTestSupport.library

    @Test fun mainIsStrongestAndHelperLightest() {
        val squat = lib.first { it.id == "SQUAT_BACK" }
        val lv = exerciseLevels(squat)
        squat.main.forEach { assertEquals(R_MAP_TONE_HIGH, lv.getValue(it)) }
        (squat.helper - squat.main).forEach { assertEquals(R_MAP_TONE_LOW, lv.getValue(it)) }
    }

    @Test
    fun R_MAP_TONE_weeklyLevelsFollowTheVolumeRange() {
        assertEquals(R_MAP_TONE_NONE, weeklyLevel(MuscleGroup.CHEST, TrainingLevel.L1, 0.0))
        assertEquals(R_MAP_TONE_LOW, weeklyLevel(MuscleGroup.CHEST, TrainingLevel.L1, 3.0))
        assertEquals(R_MAP_TONE_MID, weeklyLevel(MuscleGroup.CHEST, TrainingLevel.L1, 8.0))
        assertEquals(R_MAP_TONE_LOW, weeklyLevel(MuscleGroup.CHEST, TrainingLevel.L2, 8.0))
        assertEquals(R_MAP_TONE_HIGH, weeklyLevel(MuscleGroup.CHEST, TrainingLevel.L2, 21.0))
    }

    @Test fun weekCountsDoneSetsOfMainAndHelperMuscles() {
        val squat = lib.first { it.id == "SQUAT_BACK" }
        val sets = weeklySetsDone(mapOf("SQUAT_BACK" to 4, "NOT_IN_LIBRARY" to 9), lib)
        squat.main.forEach { assertTrue(sets.getValue(it) >= 4.0) }
        assertEquals(0.0, sets.getValue(MuscleGroup.BICEPS), 0.0)
        val levels = weeklyLevels(sets, TrainingLevel.L1)
        assertTrue(MuscleGroup.BICEPS !in levels)
    }

    @Test fun aSharedRegionTakesTheStrongerLevel() {
        val regions = mapOf(
            MuscleGroup.FRONT_DELT to listOf("delts"), MuscleGroup.REAR_DELT to listOf("delts"),
            MuscleGroup.LATS to listOf("lats"),
        )
        val r = regionLevels(mapOf(MuscleGroup.FRONT_DELT to R_MAP_TONE_LOW, MuscleGroup.REAR_DELT to R_MAP_TONE_HIGH), regions)
        assertEquals(mapOf("delts" to R_MAP_TONE_HIGH), r)
    }
}
