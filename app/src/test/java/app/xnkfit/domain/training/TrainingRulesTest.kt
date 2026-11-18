package app.xnkfit.domain.training

import app.xnkfit.domain.rules.GateFixtures
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.evaluateTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/** Rules.md §5. */
class TrainingRulesTest {
    private val eps = 1e-9

    @Test
    fun R_TRN_WEEK_sessionsPerLevel() {
        assertEquals(2..3, R_TRN_WEEK_BEGINNER_MIN..R_TRN_WEEK_BEGINNER_MAX)
        assertEquals(3..4, R_TRN_WEEK_INTERMEDIATE_MIN..R_TRN_WEEK_INTERMEDIATE_MAX)
    }

    @Test
    fun R_TRN_SETS_byLevel() {
        assertEquals(2..3, setsRange(TrainingLevel.L1))
        assertEquals(3..4, setsRange(TrainingLevel.L2))
        assertEquals(3..4, setsRange(TrainingLevel.L3))
    }

    @Test
    fun R_TRN_REPS_byLevelAndLoad() {
        assertEquals(listOf(8..12), repRanges(TrainingLevel.L1, LoadKind.WEIGHTED))
        assertEquals(listOf(3..5, 8..12), repRanges(TrainingLevel.L2, LoadKind.WEIGHTED))
        TrainingLevel.entries.forEach { assertEquals(listOf(10..15), repRanges(it, LoadKind.BODYWEIGHT_OR_BAND)) }
    }

    @Test
    fun R_TRN_REST_seconds() {
        assertEquals(60..90, R_TRN_REST_ISOLATION_MIN_S..R_TRN_REST_ISOLATION_MAX_S)
        assertEquals(120..180, R_TRN_REST_COMPOUND_MIN_S..R_TRN_REST_COMPOUND_MAX_S)
    }

    @Test
    fun R_TRN_RIR_oneToThree() = assertEquals(1..3, R_TRN_RIR_MIN..R_TRN_RIR_MAX)

    @Test
    fun R_TRN_PROG_UP_2point5() {
        assertEquals(2.5, R_TRN_PROG_UP_KG, eps)
        // From 25 kg upper body the fixed step applies unchanged (10 % = 2.5).
        assertEquals(LoadStep(2.5, 0), loadStep(25.0, legs = false, equipmentStepKg = 2.5))
    }

    @Test
    fun R_TRN_PROG_LEG_5() {
        assertEquals(5.0, R_TRN_PROG_LEG_KG, eps)
        assertEquals(LoadStep(5.0, 0), loadStep(50.0, legs = true, equipmentStepKg = 2.5))
    }

    @Test
    fun R_TRN_PROG_STEP_tenPercentRoundedDown() {
        assertEquals(0.10, R_TRN_PROG_STEP_MAX_FRACTION, eps)
        assertEquals(1.0, R_TRN_PROG_STEP_DUMBBELL_KG, eps)
        assertEquals(2.5, R_TRN_PROG_STEP_BARBELL_KG, eps)
        assertEquals(2, R_TRN_PROG_STEP_EXTRA_REPS)
        // Rules example: 12 kg dumbbell press: 10 % = 1.2 kg → 1 kg (not 2.5).
        assertEquals(LoadStep(1.0, 0), loadStep(12.0, legs = false, equipmentStepKg = R_TRN_PROG_STEP_DUMBBELL_KG))
        // Rules example: 40 kg squat: 10 % = 4 kg → 2.5 kg (not 5).
        assertEquals(LoadStep(2.5, 0), loadStep(40.0, legs = true, equipmentStepKg = R_TRN_PROG_STEP_BARBELL_KG))
        // 8 kg dumbbell: 10 % = 0.8 < 1 kg → 1 kg only after top of range + 2 reps.
        assertEquals(LoadStep(1.0, 2), loadStep(8.0, legs = false, equipmentStepKg = 1.0))
        assertNull(loadStep(Double.NaN, legs = false, equipmentStepKg = 1.0))
        assertNull(loadStep(20.0, legs = false, equipmentStepKg = 0.0))
    }

    @Test
    fun R_TRN_VOL_weeklyRange() {
        assertEquals(5, R_TRN_VOL_MIN_L1)
        assertEquals(10, R_TRN_VOL_MIN_L2)
        assertEquals(20, R_TRN_VOL_MAX)
        assertEquals(VolumeStatus.UNDER, volumeStatus(MuscleGroup.QUADS, TrainingLevel.L1, 4.5))
        assertEquals(VolumeStatus.OK, volumeStatus(MuscleGroup.QUADS, TrainingLevel.L1, 5.0))
        assertEquals(VolumeStatus.UNDER, volumeStatus(MuscleGroup.QUADS, TrainingLevel.L2, 9.5))
        assertEquals(VolumeStatus.OK, volumeStatus(MuscleGroup.QUADS, TrainingLevel.L3, 20.0))
    }

    @Test
    fun R_TRN_VOL_COUNT_mainOneHelperHalf() {
        // Rules example: goblet squat 3 sets, 3 days: QUADS 9, GLUTES 4.5 per week.
        assertEquals(9.0, setsCredited(3, isMainMuscle = true) * 3, eps)
        assertEquals(4.5, setsCredited(3, isMainMuscle = false) * 3, eps)
    }

    @Test
    fun R_TRN_VOL_SCOPE_fourGroupsWithoutMinimum() {
        assertEquals(16, MuscleGroup.entries.size)
        assertEquals(
            setOf(MuscleGroup.FOREARM, MuscleGroup.LOWER_BACK, MuscleGroup.OBLIQUES, MuscleGroup.TRAPS),
            R_TRN_VOL_SCOPE_NO_MINIMUM,
        )
        assertEquals(VolumeStatus.NO_MINIMUM, volumeStatus(MuscleGroup.TRAPS, TrainingLevel.L2, 0.0))
    }

    @Test
    fun R_TRN_VOL_FILL_triggersUnderMinimumAndOverTwenty() {
        // The generator fills when UNDER and trims when OVER (also for groups with no minimum).
        assertEquals(VolumeStatus.OVER, volumeStatus(MuscleGroup.CHEST, TrainingLevel.L2, 20.5))
        assertEquals(VolumeStatus.OVER, volumeStatus(MuscleGroup.FOREARM, TrainingLevel.L1, 21.0))
        assertEquals(VolumeStatus.UNDER, volumeStatus(MuscleGroup.CHEST, TrainingLevel.L1, 0.0))
    }

    @Test
    fun R_TRN_PROG_BW_repsThenSetThenVariant() {
        assertEquals(BodyweightStep.MORE_REPS, bodyweightStep(allSetsAtTop = false, sets = 2, maxSets = 3))
        assertEquals(BodyweightStep.ADD_SET, bodyweightStep(allSetsAtTop = true, sets = 2, maxSets = 3))
        assertEquals(BodyweightStep.HARDER_VARIANT, bodyweightStep(allSetsAtTop = true, sets = 3, maxSets = 3))
    }

    @Test
    fun R_TRN_BAND_levelsOneToFive() {
        assertTrue((1..5).all(::isValidBandLevel))
        assertFalse(isValidBandLevel(0))
        assertFalse(isValidBandLevel(6))
    }

    @Test
    fun R_TRN_FORM_twoRapiInARowReleaseHold() {
        assertEquals(2, R_TRN_FORM_RAPI_IN_ROW)
        val rapi = FormRating.RAPI
        val ragu = FormRating.RAGU
        assertFalse(loadMayRise(listOf(rapi, ragu)))
        assertFalse(loadMayRise(listOf(ragu, rapi)))
        assertTrue(loadMayRise(listOf(ragu, rapi, rapi)))
        assertTrue(loadMayRise(listOf(rapi)))
        assertFalse(loadMayRise(emptyList()))
    }

    @Test
    fun R_TRN_REVIEW_fourWeeksOrThreeSessions() {
        assertFalse(reviewDue(weeksSinceReview = 3, sessionsSinceReview = 2))
        assertTrue(reviewDue(weeksSinceReview = 4, sessionsSinceReview = 0))
        assertTrue(reviewDue(weeksSinceReview = 0, sessionsSinceReview = 3))
    }

    /** ⚠ G9: no source prescribes a return load; 60 % and one fewer set is an owner rule (Rules §10). */
    @Ignore("unverified")
    @Test
    fun R_TRN_RETURN_sixtyPercentOneFewerSet() {
        assertEquals(14, R_TRN_RETURN_GAP_DAYS)
        assertEquals(0.60, R_TRN_RETURN_LOAD_FRACTION, eps)
        assertEquals(1, R_TRN_RETURN_SET_DROP)
        assertNull(returnAfterAbsence(13, 50.0, 3, 2.5))
        // 60 % of 50 kg = 30 kg; 3 sets → 2
        assertEquals(ReturnPlan(30.0, 2), returnAfterAbsence(14, 50.0, 3, 2.5))
        // 60 % of 12 kg = 7.2 → 7 kg on 1 kg dumbbell steps; never below one set
        assertEquals(ReturnPlan(7.0, 1), returnAfterAbsence(30, 12.0, 1, 1.0))
    }

    @Test
    fun R_TRN_LEVEL1_riskAnswerLimitsToLevelOne() {
        val r = evaluateTargets(GateFixtures.man30, GateFixtures.clear.copy(chestPainOnActivity = true), 2026)
        assertTrue((r as GateResult.Ready).program.levelOneOnly)
    }

    @Test
    fun R_TRN_NO_WARMUP_zeroWarmUpSets() = assertEquals(0, R_TRN_NO_WARMUP_SETS)

    @Test
    fun R_TRN_LEVELS_beginnerForEightWeeks() {
        assertEquals(8, R_TRN_LEVELS_BEGINNER_WEEKS)
        assertEquals(TrainingLevel.L1, levelAfterWeeks(TrainingLevel.L1, 7))
        assertEquals(TrainingLevel.L2, levelAfterWeeks(TrainingLevel.L1, 8))
        assertEquals(TrainingLevel.L3, levelAfterWeeks(TrainingLevel.L3, 0))
    }
}
