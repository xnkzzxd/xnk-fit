package app.xnkfit.domain.logger

import app.xnkfit.domain.training.LoadKind
import app.xnkfit.domain.training.Pattern
import app.xnkfit.domain.training.R_TRN_NO_WARMUP_SETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerTest {
    private fun s(slot: Int, index: Int, done: Boolean = false, group: Int? = null, rest: Int = 90, ex: String = "E$slot") =
        LoggerSet(id = "$slot-$index", slot = slot, index = index, exerciseId = ex, done = done, supersetGroup = group, restSec = rest, reps = 10, weightKg = 20.0)

    @Test
    fun R_TRN_NO_WARMUP_loggerHasNoWarmupKind() {
        assertEquals(0, R_TRN_NO_WARMUP_SETS)
        assertTrue(SetKind.entries.none { it.code.contains("warm") })
        assertEquals(SetKind.NORMAL, SetKind.of("warmup"))
    }

    @Test
    fun R_TRN_LOGGER_setKindsAreNormalDropFailure() {
        assertEquals(listOf("normal", "drop", "failure"), SetKind.entries.map { it.code })
    }

    @Test
    fun R_TRN_LOGGER_holdsAreSeconds() {
        assertEquals(SetMeasure.HOLD_SECONDS, measureOf("CORE_PLANK", LoadKind.BODYWEIGHT_OR_BAND, false))
        assertEquals(SetMeasure.REPS, measureOf("CORE_DEADBUG", LoadKind.BODYWEIGHT_OR_BAND, false))
        assertEquals(SetMeasure.BAND_REPS, measureOf("ROW_BAND", LoadKind.BODYWEIGHT_OR_BAND, true))
        assertEquals(SetMeasure.KG_REPS, measureOf("SQUAT_GOB", LoadKind.WEIGHTED, false))
        assertEquals(5, R_TRN_LOGGER_HOLD_STEP_S)
        assertEquals(20, prefill(SetMeasure.HOLD_SECONDS, 1, null, null, 10, emptyList()).holdSec)
    }

    @Test
    fun R_TRN_LOGGER_restExtendIs15s() {
        assertEquals(15, R_TRN_LOGGER_REST_EXTEND_S)
        assertEquals(1_000L + 15_000L, extendRest(null, 1_000L))
        assertEquals(50_000L + 15_000L, extendRest(50_000L, 1_000L))
        assertEquals(90_000L + 15_000L, extendRest(10_000L, 90_000L)) // ran out: counts from now
    }

    @Test
    fun R_TRN_LOGGER_resumeTodayOrYesterdayElseClose() {
        assertEquals(Unfinished.RESUME, unfinishedAction(100, 100))
        assertEquals(Unfinished.RESUME, unfinishedAction(99, 100))
        assertEquals(Unfinished.CLOSE, unfinishedAction(98, 100))
        assertEquals(5_000L, closeTime(1_000L, 5_000L))
        assertEquals(1_000L, closeTime(1_000L, null))
    }

    @Test
    fun R_TRN_LOGGER_fieldLimits() {
        assertEquals(100, clampReps(400))
        assertEquals(600, clampHold(9_999))
        assertEquals(500.0, clampKg(1_000.0)!!, 0.0)
        assertEquals(0.0, clampKg(-5.0)!!, 0.0)
        assertNull(clampKg(null))
        assertEquals(5, clampBand(9))
    }

    @Test
    fun R_TRN_LOGGER_prefillNeverInventsKg() {
        val p = prefill(SetMeasure.KG_REPS, 1, null, null, 8, emptyList())
        assertNull(p.weightKg)
        assertEquals(8, p.reps)
        val prev = listOf(s(0, 1).copy(weightKg = 14.0, reps = 12))
        assertEquals(14.0, prefill(SetMeasure.KG_REPS, 2, null, null, 8, prev).weightKg!!, 0.0)
        assertEquals(16.0, prefill(SetMeasure.KG_REPS, 1, 16.0, null, 8, prev).weightKg!!, 0.0)
    }

    @Test
    fun dipanduGoesExerciseByExercise() {
        val sets = listOf(s(0, 1, done = true), s(0, 2), s(1, 1))
        assertEquals("0-2", nextSet(sets)!!.id)
        assertNull(nextSet(sets.map { it.copy(done = true) }))
    }

    @Test
    fun supersetAlternatesAndRestsAfterThePair() {
        val sets = listOf(s(0, 1, group = 1), s(0, 2, group = 1), s(1, 1, group = 1), s(1, 2, group = 1), s(2, 1))
        assertEquals("0-1", nextSet(sets)!!.id)
        val a1 = sets.map { if (it.id == "0-1") it.copy(done = true) else it }
        assertEquals("1-1", nextSet(a1)!!.id)
        assertEquals(0, restAfter(a1.first { it.id == "0-1" }, a1))
        val b1 = a1.map { if (it.id == "1-1") it.copy(done = true) else it }
        assertEquals("0-2", nextSet(b1)!!.id)
        assertEquals(90, restAfter(b1.first { it.id == "1-1" }, b1))
    }

    @Test
    fun restIsStoredAsEndTime() {
        assertEquals(91_000L, restEndsAt(1_000L, 90))
        assertNull(restEndsAt(1_000L, 0))
        assertEquals(90, restRemainingSec(91_000L, 1_000L))
        assertEquals(1, restRemainingSec(91_000L, 90_500L))
        assertEquals(0, restRemainingSec(91_000L, 95_000L))
        assertEquals(0, restRemainingSec(null, 0L))
    }

    @Test
    fun defaultRestFollowsRtrnRest() {
        assertEquals(120, defaultRestSec(Pattern.SQUAT))
        assertEquals(60, defaultRestSec(Pattern.CURL))
    }

    @Test
    fun totalsAndTick() {
        val sets = listOf(s(0, 1, done = true), s(0, 2))
        val t = totals(sets)
        assertEquals(1, t.doneSets); assertEquals(2, t.plannedSets); assertEquals(200.0, t.volumeKg, 0.0)
        assertTrue(canTick(s(0, 1), SetMeasure.KG_REPS))
        assertFalse(canTick(s(0, 1).copy(weightKg = null), SetMeasure.KG_REPS))
        assertFalse(canTick(s(0, 1), SetMeasure.HOLD_SECONDS))
        assertEquals(LoggerMode.DIPANDU, LoggerMode.of("dipandu"))
        assertNull(LoggerMode.of(null))
    }
}
