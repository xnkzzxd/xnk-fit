package app.xnkfit.domain.training

import app.xnkfit.domain.training.TrainingTestSupport.TODAY
import app.xnkfit.domain.training.TrainingTestSupport.generated
import app.xnkfit.domain.training.TrainingTestSupport.library
import app.xnkfit.domain.training.TrainingTestSupport.profile
import app.xnkfit.domain.training.TrainingTestSupport.screeningGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-06: progression (L-2, L-4), form hold (L-12), return after absence (L-16), review and own routines (L-5). */
class ProgressionTest {
    private val gym = Equipment.of(EquipmentSetting.GYM)
    private val homeBw = Equipment.of(EquipmentSetting.HOME_BW)
    private val homeDb = Equipment.of(EquipmentSetting.HOME_DB)
    private val byId = library.associateBy { it.id }

    private fun item(id: String, level: TrainingLevel = TrainingLevel.L1, role: ItemRole = ItemRole.MAIN) =
        ItemContext(level, GoalFocus.OTOT).item(byId.getValue(id), role)

    private fun day(vararg items: ProgramItem) = ProgramDay(0, DayName.FULL_A, estMinutes(items.toList()), items.toList())

    private fun session(daysAgo: Long, id: String, sets: Int, kg: Double?, reps: Int, form: FormRating = FormRating.RAPI, band: Int? = null) =
        LoggedSession(TODAY - daysAgo, List(sets) { LoggedSet(id, kg, reps, band, form) })

    private fun target(item: ProgramItem, vararg sessions: LoggedSession, level: TrainingLevel = TrainingLevel.L1) =
        sessionTargets(day(item), level, TrainingHistory(sessions.toList()), TODAY, library).single()

    // ---------------------------------------------------------------- weights (R_TRN_PROG_*)

    @Test
    fun R_TRN_PROG_UP_topOfRangeWithRapiAddsTheStep() {
        // Machine chest press 30 kg, 2 × 12 rapi: min(2.5, 3.0) = 2.5 on the 2.5 kg machine step.
        val t = target(item("PUSHH_CHEST_MC"), session(2, "PUSHH_CHEST_MC", 2, 30.0, 12))
        assertEquals(32.5, t.loadKg!!, 1e-9)
        assertEquals(Reason(ReasonCode.ADD_LOAD, 2.5), t.reason)
    }

    @Test
    fun R_TRN_PROG_LEG_legsTakeTheLegStep() {
        val t = target(item("LEG_PRESS"), session(2, "LEG_PRESS", 2, 100.0, 12))
        assertEquals(105.0, t.loadKg!!, 1e-9)
    }

    @Test
    fun R_TRN_PROG_STEP_smallLoadsNeedExtraRepsFirst() {
        // 8 kg dumbbell: 10 % = 0.8 kg, under the 1 kg step → only after 12 + 2 reps on every set.
        val gob = item("SQUAT_GOB")
        val t = target(gob, session(2, "SQUAT_GOB", 2, 8.0, 12))
        assertEquals(8.0, t.loadKg!!, 1e-9)
        assertEquals(ReasonCode.NEED_EXTRA_REPS, t.reason.code)
        assertEquals(R_TRN_PROG_STEP_EXTRA_REPS, t.reason.reps)
        val t2 = target(gob, session(2, "SQUAT_GOB", 2, 8.0, 14))
        assertEquals(9.0, t2.loadKg!!, 1e-9)
    }

    @Test
    fun R_TRN_PROG_STEP_notAtTopKeepsTheLoad() {
        val t = target(item("PUSHH_CHEST_MC"), LoggedSession(TODAY - 2, listOf(
            LoggedSet("PUSHH_CHEST_MC", 30.0, 12, form = FormRating.RAPI),
            LoggedSet("PUSHH_CHEST_MC", 30.0, 10, form = FormRating.RAPI),
        )))
        assertEquals(30.0, t.loadKg!!, 1e-9)
        assertEquals(ReasonCode.MORE_REPS, t.reason.code)
    }

    @Test
    fun R_TRN_FORM_raguHoldsTheLoadUntilTwoRapiInARow() {
        val it = item("PUSHH_CHEST_MC")
        val ragu = session(6, "PUSHH_CHEST_MC", 2, 30.0, 12, FormRating.RAGU)
        val rapi1 = session(4, "PUSHH_CHEST_MC", 2, 30.0, 12)
        val rapi2 = session(2, "PUSHH_CHEST_MC", 2, 30.0, 12)
        assertEquals(ReasonCode.FORM_HOLD, target(it, ragu).reason.code)
        assertEquals(30.0, target(it, ragu, rapi1).loadKg!!, 1e-9)
        assertEquals(ReasonCode.FORM_HOLD, target(it, ragu, rapi1).reason.code)
        assertEquals(32.5, target(it, ragu, rapi1, rapi2).loadKg!!, 1e-9)
    }

    // ---------------------------------------------------------------- bodyweight and bands (R_TRN_PROG_BW, R_TRN_BAND)

    @Test
    fun R_TRN_PROG_BW_repsThenSetThenHarderVariant() {
        val squat = item("SQUAT_BW") // 2 × 10–15 at level 1
        assertEquals(ReasonCode.MORE_REPS, target(squat, session(2, "SQUAT_BW", 2, null, 12)).reason.code)
        val addSet = target(squat, session(2, "SQUAT_BW", 2, null, 15))
        assertEquals(ReasonCode.ADD_SET, addSet.reason.code)
        assertEquals(3, addSet.sets)
        val harder = target(squat, session(2, "SQUAT_BW", 3, null, 15))
        assertEquals(ReasonCode.HARDER_VARIANT, harder.reason.code)
        assertEquals(3, harder.sets)
        assertNull(harder.loadKg)
    }

    @Test
    fun R_TRN_BAND_bandLevelThenReps() {
        val band = item("SQUAT_BAND")
        val up = target(band, session(2, "SQUAT_BAND", 2, null, 15, band = 2))
        assertEquals(3, up.bandLevel)
        assertEquals(ReasonCode.BAND_UP, up.reason.code)
        assertNull(up.loadKg)
        val notYet = target(band, session(2, "SQUAT_BAND", 2, null, 11, band = 2))
        assertEquals(2, notYet.bandLevel)
        // Strongest band: the bodyweight steps take over.
        val top = target(band, session(2, "SQUAT_BAND", 2, null, 15, band = R_TRN_BAND_MAX))
        assertEquals(R_TRN_BAND_MAX, top.bandLevel)
        assertEquals(ReasonCode.ADD_SET, top.reason.code)
    }

    @Test
    fun R_TRN_RIR_firstSessionHasNoLoadAndSaysFirstTime() {
        val t = target(item("PUSHH_CHEST_MC"))
        assertNull(t.loadKg)
        assertEquals(ReasonCode.FIRST_TIME, t.reason.code)
    }

    // ---------------------------------------------------------------- return after absence (L-16)

    /**
     * Wiring only: the session after a gap uses exactly what `returnAfterAbsence` (R_TRN_RETURN, ⚠ G9) returns. The
     * rule's own numbers are asserted in the ignored `R_TRN_RETURN_*` test until G9 is closed.
     */
    @Test
    fun sessionTargets_useTheReturnRuleAfterAGap() {
        val press = item("PUSHH_CHEST_MC")
        val old = session(R_TRN_RETURN_GAP_DAYS.toLong(), "PUSHH_CHEST_MC", 3, 40.0, 12)
        val t = target(press, old)
        val plan = returnAfterAbsence(R_TRN_RETURN_GAP_DAYS, 40.0, 3, R_TRN_PROG_STEP_BARBELL_KG)!!
        assertEquals(plan.loadKg, t.loadKg!!, 1e-9)
        assertEquals(plan.sets, t.sets)
        assertEquals(ReasonCode.RETURN_AFTER_BREAK, t.reason.code)
        // One day short of the gap: normal progression.
        val recent = session(R_TRN_RETURN_GAP_DAYS.toLong() - 1, "PUSHH_CHEST_MC", 3, 40.0, 12)
        assertEquals(ReasonCode.ADD_LOAD, target(press, recent).reason.code)
        // Bodyweight: one set fewer, no load.
        val bw = target(item("SQUAT_BW"), session(30, "SQUAT_BW", 3, null, 15))
        assertEquals(returnAfterAbsence(30, 0.0, 3, R_TRN_PROG_STEP_DUMBBELL_KG)!!.sets, bw.sets)
        assertNull(bw.loadKg)
    }

    // ---------------------------------------------------------------- review (R_TRN_REVIEW, L-4)

    private val clockFresh = ReviewClock(TODAY, 0)

    @Test
    fun R_TRN_REVIEW_notDueBeforeFourWeeksOrThreeSessions() {
        val p = generated(profile(), gym)
        val r = review(p, true, profile(), gym, TrainingHistory(), library, TODAY, ReviewClock(TODAY - 27, 2))
        assertSame(ReviewResult.NotDue, r)
        val due = review(p, true, profile(), gym, TrainingHistory(), library, TODAY, ReviewClock(TODAY - 1, R_TRN_REVIEW_SESSIONS))
        assertTrue(due is ReviewResult.Proposals)
        assertEquals(emptyList<ProgramChange>(), (due as ReviewResult.Proposals).changes)
        val dueWeeks = review(p, true, profile(), gym, TrainingHistory(), library, TODAY, ReviewClock(TODAY - 28, 0))
        assertTrue(dueWeeks is ReviewResult.Proposals)
    }

    @Test
    fun R_TRN_REVIEW_bodyweightAtTopProposesTheHarderVariantWithAReason() {
        val p = generated(profile(), homeBw)
        val dayIdx = p.days.indexOfFirst { d -> d.items.any { it.exerciseId == "PUSHH_PUSHUP_INCL" } }
        val itemIdx = p.days[dayIdx].items.indexOfFirst { it.exerciseId == "PUSHH_PUSHUP_INCL" }
        val max = R_TRN_SETS_L1_MAX
        val history = TrainingHistory(listOf(session(4, "PUSHH_PUSHUP_INCL", max, null, 15), session(2, "PUSHH_PUSHUP_INCL", max, null, 15)))
        val r = review(p, true, profile(), homeBw, history, library, TODAY, ReviewClock(TODAY - 7, R_TRN_REVIEW_SESSIONS))
        r as ReviewResult.Proposals
        val swap = r.changes.single { it.kind == ChangeKind.SWAP_EXERCISE && it.fromExerciseId == "PUSHH_PUSHUP_INCL" }
        val expected = harderVariant(byId.getValue("PUSHH_PUSHUP_INCL"), library, homeBw, TrainingLevel.L1)!!
        assertEquals("PUSHH_PUSHUP_KNEE", expected.id)
        assertEquals(expected.id, swap.toExerciseId)
        assertEquals(Reason(ReasonCode.HARDER_VARIANT, reps = 15, sessions = 2), swap.reason)
        assertEquals(dayIdx, swap.dayPosition)
        assertEquals(itemIdx, swap.itemPosition)

        // Accept → swapped; decline → unchanged.
        val accepted = applyChanges(p, r, setOf(swap))
        assertEquals(expected.id, accepted.days[dayIdx].items[itemIdx].exerciseId)
        assertEquals(p, applyChanges(p, r, emptySet()))
    }

    @Test
    fun R_TRN_REVIEW_levelUpAfterEightWeeksIsProposedNotForced() {
        val p = generated(profile(), gym)
        val start = TODAY - R_TRN_LEVELS_BEGINNER_WEEKS * R_TRN_LEVELS_DAYS_PER_WEEK
        val history = TrainingHistory(listOf(LoggedSession(start, listOf(LoggedSet("LEG_PRESS", 80.0, 10)))))
        val r = review(p, true, profile(), gym, history, library, TODAY, ReviewClock(TODAY - 28, 0)) as ReviewResult.Proposals
        assertTrue(r.changes.isNotEmpty())
        assertTrue(r.changes.all { it.reason.code == ReasonCode.LEVEL_UP })
        assertEquals(TrainingLevel.L2, r.proposed.level)
        assertEquals(TrainingLevel.L1, applyChanges(p, r, emptySet()).level)
    }

    @Test
    fun R_TRN_REVIEW_blockedWhenScreeningIsOpenAgain() {
        val p = generated(profile(), gym)
        val r = review(p, true, profile(screeningGate), gym, TrainingHistory(), library, TODAY, ReviewClock(TODAY - 30, 0))
        assertTrue(r is ReviewResult.Blocked)
    }

    // ---------------------------------------------------------------- own routines (L-5)

    @Test
    fun R_TRN_REVIEW_ownRoutineIsNeverChanged() {
        val own = saveEdited(generated(profile(), gym))
        assertEquals(ProgramSource.OWN, own.source)
        assertSame(ReviewResult.OwnRoutine, review(own, true, profile(), gym, TrainingHistory(), library, TODAY, ReviewClock(TODAY - 60, 9)))
        assertSame(ReviewResult.AutoOff, review(generated(profile(), gym), false, profile(), gym, TrainingHistory(), library, TODAY, clockFresh))
        val regenerated = generate(profile(), homeDb, GoalFocus.OTOT, TrainingHistory(), library, TODAY)
        assertSame(own, regenerateOrKeep(own, true, regenerated))
        val auto = generated(profile(), gym)
        assertSame(auto, regenerateOrKeep(auto, false, regenerated))
        assertEquals((regenerated as GenerateResult.Generated).program, regenerateOrKeep(auto, true, regenerated))
        // PR-5: a blocked regenerate keeps no auto program.
        assertNull(regenerateOrKeep(auto, true, generate(profile(screeningGate), gym, GoalFocus.OTOT, TrainingHistory(), library, TODAY)))
    }
}
