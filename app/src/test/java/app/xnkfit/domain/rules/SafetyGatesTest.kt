package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

object GateFixtures {
    const val YEAR = 2026

    /** Man, 30 y, 170 cm, 70 kg, sedang. */
    val man30 = Profile(Sex.LAKI_LAKI, 1996, 170.0, 70.0, ActivityLevel.SEDANG, Goal.TURUN)

    /** Woman, 25 y, 158 cm, 55 kg, ringan. */
    val woman25 = Profile(Sex.PEREMPUAN, 2001, 158.0, 55.0, ActivityLevel.RINGAN, Goal.TURUN)

    /** Every answer "Tidak"; pregnancy answered. */
    val clear = Screening(false, false, false, false, false, false, false)
}

/** Rules.md §3 and PR-5: order, effects and fail-safe behaviour. */
class SafetyGatesTest {
    private val year = GateFixtures.YEAR
    private val clear = GateFixtures.clear

    private fun ready(p: Profile, s: Screening = clear): GateResult.Ready {
        val r = evaluateTargets(p, s, year)
        assertTrue("expected Ready, got $r", r is GateResult.Ready)
        return r as GateResult.Ready
    }

    // --- R_GATE_U13 (PRD O-5) ---

    @Test
    fun R_GATE_U13_under13Stops() {
        assertEquals(13, R_GATE_U13_MIN_AGE)
        val child = GateFixtures.man30.copy(birthYear = 2014) // 12
        assertEquals(GateResult.StopUnder13, evaluateTargets(child, clear, year))
        // Even with nothing else typed and no screening: stop, never a target.
        val bare = Profile(null, 2014, null, null, null, null)
        assertEquals(GateResult.StopUnder13, evaluateTargets(bare, null, year))
    }

    @Test
    fun R_GATE_U13_thirteenContinues() {
        val teen = GateFixtures.man30.copy(birthYear = 2013) // 13
        assertTrue(evaluateTargets(teen, clear, year) is GateResult.Ready)
    }

    // --- R_INPUT: fail-safe ---

    @Test
    fun R_INPUT_missingValuesGiveNoTarget() {
        val empty = Profile(null, null, null, null, null, null)
        val r = evaluateTargets(empty, clear, year)
        assertTrue(r is GateResult.InvalidInput)
        assertEquals(
            setOf(
                InputProblem.SEX_MISSING, InputProblem.BIRTH_YEAR_MISSING, InputProblem.HEIGHT_MISSING,
                InputProblem.WEIGHT_MISSING, InputProblem.ACTIVITY_MISSING, InputProblem.GOAL_MISSING,
            ),
            (r as GateResult.InvalidInput).problems,
        )
    }

    @Test
    fun R_INPUT_eachMissingFieldAloneBlocks() {
        val p = GateFixtures.man30
        listOf(
            p.copy(sex = null), p.copy(birthYear = null), p.copy(heightCm = null),
            p.copy(weightKg = null), p.copy(activity = null), p.copy(goal = null),
        ).forEach { assertTrue("$it", evaluateTargets(it, clear, year) is GateResult.InvalidInput) }
    }

    @Test
    fun R_INPUT_unreadableOrImplausibleValuesGiveNoTarget() {
        val p = GateFixtures.man30
        val bad = listOf(
            p.copy(heightCm = Double.NaN), p.copy(heightCm = Double.POSITIVE_INFINITY), p.copy(heightCm = -170.0),
            p.copy(heightCm = 0.0), p.copy(heightCm = 99.9), p.copy(heightCm = 250.1), p.copy(heightCm = 1.7),
            p.copy(weightKg = Double.NaN), p.copy(weightKg = 0.0), p.copy(weightKg = -70.0),
            p.copy(weightKg = 24.9), p.copy(weightKg = 300.1), p.copy(weightKg = 70000.0),
            p.copy(birthYear = 2027), p.copy(birthYear = 1925),
        )
        bad.forEach { assertTrue("$it", evaluateTargets(it, clear, year) is GateResult.InvalidInput) }
        // Edges are accepted.
        assertTrue(evaluateTargets(p.copy(heightCm = 100.0, weightKg = 25.0), clear, year) is GateResult.Ready)
        assertTrue(evaluateTargets(p.copy(birthYear = 1926), clear, year) is GateResult.Ready)
    }

    // --- R_GATE_SCREEN ---

    @Test
    fun R_GATE_SCREEN_noScreeningNoTargetNoProgram() {
        assertEquals(GateResult.ScreeningNeeded, evaluateTargets(GateFixtures.man30, null, year))
    }

    @Test
    fun R_GATE_SCREEN_anyUnansweredQuestionBlocks() {
        val s = clear
        listOf(
            s.copy(heartOrBloodPressure = null), s.copy(chestPainOnActivity = null), s.copy(ongoingInjury = null),
            s.copy(otherTreatedCondition = null), s.copy(doctorRestriction = null), s.copy(eatingDisorderHistory = null),
        ).forEach { assertEquals("$it", GateResult.ScreeningNeeded, evaluateTargets(GateFixtures.man30, it, year)) }
    }

    @Test
    fun R_GATE_SCREEN_pregnancyQuestionRequiredForWomenOnly() {
        val unanswered = clear.copy(pregnantOrBreastfeeding = null)
        assertEquals(GateResult.ScreeningNeeded, evaluateTargets(GateFixtures.woman25, unanswered, year))
        assertTrue(evaluateTargets(GateFixtures.man30, unanswered, year) is GateResult.Ready)
    }

    // --- R_GATE_RISK (with R_DEF_CAP and R_TRN_LEVEL1) ---

    @Test
    fun R_GATE_RISK_anyYesGivesLevelOneAndDoctorNote() {
        val s = clear
        listOf(
            s.copy(heartOrBloodPressure = true), s.copy(chestPainOnActivity = true), s.copy(ongoingInjury = true),
            s.copy(otherTreatedCondition = true), s.copy(doctorRestriction = true),
        ).forEach {
            val r = ready(GateFixtures.man30, it)
            assertTrue(r.program.levelOneOnly)
            assertTrue(GateNote.RISK_SEE_DOCTOR in r.notes)
        }
        val none = ready(GateFixtures.man30)
        assertFalse(none.program.levelOneOnly)
        assertFalse(GateNote.RISK_SEE_DOCTOR in none.notes)
    }

    @Test
    fun R_DEF_CAP_riskGroupDeficitAtMostTenPercent() {
        // Man 40 y, 175 cm, 80 kg, jarang, turun, heart "yes".
        // BMR = 800 + 1093.75 − 200 + 5 = 1698.75; maintenance = 1698.75 × 1.40 = 2378.25
        // Cap = 10 % = 237.825 < 500 → target 2378.25 − 237.825 = 2140.425 → 2140
        val p = Profile(Sex.LAKI_LAKI, 1986, 175.0, 80.0, ActivityLevel.JARANG, Goal.TURUN)
        val r = ready(p, clear.copy(heartOrBloodPressure = true))
        assertEquals(2140, r.targetKcal)
        assertTrue(GateNote.DEFICIT_CAPPED in r.notes)
        // Without the risk answer: −500 → 1878.25 → 1880.
        assertEquals(1880, ready(p).targetKcal)
        assertFalse(GateNote.DEFICIT_CAPPED in ready(p).notes)
    }

    @Test
    fun R_DEF_CAP_doesNotTouchNaik() {
        val p = GateFixtures.man30.copy(goal = Goal.NAIK)
        assertEquals(ready(p).targetKcal, ready(p, clear.copy(ongoingInjury = true)).targetKcal)
    }

    // --- R_GATE_U18 ---

    @Test
    fun R_GATE_U18_noDeficitAndLightMode() {
        assertEquals(18, R_GATE_U18_ADULT_AGE)
        // Girl 16 y, 160 cm, 50 kg, sedang, asks "turun".
        // BMR = 500 + 1000 − 80 − 161 = 1259; × 1.70 = 2140.3 → 2140 (jaga)
        val p = Profile(Sex.PEREMPUAN, 2010, 160.0, 50.0, ActivityLevel.SEDANG, Goal.TURUN)
        val r = ready(p)
        assertEquals(Goal.JAGA, r.goalUsed)
        assertEquals(2140, r.targetKcal)
        assertFalse(Goal.TURUN in r.goalsOffered)
        assertTrue(Goal.NAIK in r.goalsOffered)
        assertTrue(r.program.lightMode)
        assertEquals(
            listOf(GateNote.UNDER_18_LIGHT_MODE, GateNote.GOAL_CHANGED_TO_JAGA),
            r.notes,
        )
    }

    @Test
    fun R_GATE_U18_eighteenIsAdult() {
        val r = ready(GateFixtures.man30.copy(birthYear = 2008))
        assertFalse(r.program.lightMode)
        assertEquals(Goal.TURUN, r.goalUsed)
    }

    // --- R_GATE_PREG ---

    @Test
    fun R_GATE_PREG_noDeficit() {
        val r = ready(GateFixtures.woman25, clear.copy(pregnantOrBreastfeeding = true))
        assertEquals(Goal.JAGA, r.goalUsed)
        assertFalse(Goal.TURUN in r.goalsOffered)
        assertTrue(GateNote.PREGNANT_NO_DEFICIT in r.notes)
        // 1251.5 × 1.55 = 1939.825 → 1940 = maintenance, nothing taken off.
        assertEquals(r.maintenanceKcal, r.targetKcal)
    }

    @Test
    fun R_GATE_PREG_naikStaysAllowed() {
        val r = ready(GateFixtures.woman25.copy(goal = Goal.NAIK), clear.copy(pregnantOrBreastfeeding = true))
        assertEquals(Goal.NAIK, r.goalUsed)
    }

    // --- R_GATE_ED ---

    @Test
    fun R_GATE_ED_maintainAndNoNumbers() {
        listOf(Goal.TURUN, Goal.NAIK, Goal.JAGA).forEach { goal ->
            val r = ready(GateFixtures.woman25.copy(goal = goal), clear.copy(eatingDisorderHistory = true))
            assertEquals(Goal.JAGA, r.goalUsed)
            assertEquals(setOf(Goal.JAGA), r.goalsOffered)
            assertTrue(r.numbersHidden)
            assertTrue(GateNote.EATING_DISORDER_NO_NUMBERS in r.notes)
        }
        assertFalse(ready(GateFixtures.woman25).numbersHidden)
    }

    // --- R_GATE_BMI ---

    @Test
    fun R_GATE_BMI_noTurunBelow18point5() {
        // 165 cm, 48 kg → BMI 17.6
        val p = Profile(Sex.PEREMPUAN, 2000, 165.0, 48.0, ActivityLevel.RINGAN, Goal.TURUN)
        val r = ready(p)
        assertFalse(Goal.TURUN in r.goalsOffered)
        assertEquals(Goal.JAGA, r.goalUsed)
        assertTrue(GateNote.BMI_UNDER_NO_TURUN in r.notes)
    }

    // --- R_GATE_FLOOR, R_FLOOR_F, R_FLOOR_M, R_FLOOR_BMR ---

    @Test
    fun R_GATE_FLOOR_raisedToWomenFloor() {
        // Woman 45 y, 150 cm, 48 kg, jarang, turun. BMR = 480 + 937.5 − 225 − 161 = 1031.5
        // Maintenance 1031.5 × 1.40 = 1444.1; − 500 = 944.1 → 940 < 1,200 → 1,200
        val p = Profile(Sex.PEREMPUAN, 1981, 150.0, 48.0, ActivityLevel.JARANG, Goal.TURUN)
        val r = ready(p)
        assertEquals(1200, r.targetKcal)
        assertTrue(GateNote.FLOOR_RAISED in r.notes)
    }

    @Test
    fun R_FLOOR_M_menNeverBelow1500() {
        // Man 70 y, 155 cm, 50 kg, jarang, turun. BMR = 500 + 968.75 − 350 + 5 = 1123.75
        // 1123.75 × 1.40 = 1573.25; − 500 = 1073.25 → 1070 < 1,500 → 1,500
        val p = Profile(Sex.LAKI_LAKI, 1956, 155.0, 50.0, ActivityLevel.JARANG, Goal.TURUN)
        assertEquals(1500, ready(p).targetKcal)
    }

    @Test
    fun R_FLOOR_BMR_targetNeverBelowBmr() {
        // Woman 42 y, 160 cm, 60 kg, jarang, turun. BMR = 600 + 1000 − 210 − 161 = 1229
        // 1229 × 1.40 = 1720.6; − 500 = 1220.6 → 1220. Above 1,200 but below BMR → BMR rounded up = 1230.
        val p = Profile(Sex.PEREMPUAN, 1984, 160.0, 60.0, ActivityLevel.JARANG, Goal.TURUN)
        val r = ready(p)
        assertEquals(1229.0, r.bmrKcal, 1e-9)
        assertEquals(1230, r.targetKcal)
        assertTrue(GateNote.FLOOR_RAISED in r.notes)
        assertTrue(r.targetKcal >= r.bmrKcal)
    }

    @Test
    fun R_GATE_FLOOR_neverBelowFloorAcrossManyProfiles() {
        // Sweep: every Ready target is ≥ sex floor and ≥ BMR, whatever the inputs.
        for (sex in Sex.entries) for (h in 140..200 step 15) for (w in 35..140 step 15)
            for (age in listOf(13, 17, 18, 40, 80)) for (act in ActivityLevel.entries) for (goal in Goal.entries) {
                val r = evaluateTargets(Profile(sex, year - age, h.toDouble(), w.toDouble(), act, goal), clear, year)
                if (r !is GateResult.Ready) continue
                assertTrue("$r", r.targetKcal >= sexFloorKcal(sex))
                assertTrue("$r", r.targetKcal >= r.bmrKcal)
                assertEquals(0, r.targetKcal % R_ROUND_KCAL)
                if (age < 18) assertTrue(r.targetKcal >= r.maintenanceKcal - R_ROUND_KCAL)
            }
    }

    // --- R_DISCLAIMER ---

    @Test
    fun R_DISCLAIMER_alwaysWithTarget() {
        assertTrue(R_DISCLAIMER_ALWAYS)
        assertTrue(ready(GateFixtures.man30).showDisclaimer)
        assertTrue(ready(GateFixtures.woman25, clear.copy(eatingDisorderHistory = true)).showDisclaimer)
    }

    // --- Order (Rules.md §3) ---

    @Test
    fun R_GATE_SCREEN_comesBeforeRiskAndTargets() {
        // Unfinished screening wins even when a risk answer is already "yes".
        val partial = clear.copy(heartOrBloodPressure = true, eatingDisorderHistory = null)
        assertEquals(GateResult.ScreeningNeeded, evaluateTargets(GateFixtures.man30, partial, year))
    }

    @Test
    fun R_GATE_RISK_notesFollowGateOrder() {
        // 16-year-old girl, BMI under, pregnant answer yes, ED yes, risk yes: notes in Rules §3 order.
        val p = Profile(Sex.PEREMPUAN, 2010, 165.0, 48.0, ActivityLevel.RINGAN, Goal.TURUN)
        val s = Screening(true, false, false, false, false, true, true)
        val r = ready(p, s)
        assertEquals(
            listOf(
                GateNote.RISK_SEE_DOCTOR,
                GateNote.UNDER_18_LIGHT_MODE,
                GateNote.PREGNANT_NO_DEFICIT,
                GateNote.EATING_DISORDER_NO_NUMBERS,
                GateNote.BMI_UNDER_NO_TURUN,
                GateNote.GOAL_CHANGED_TO_JAGA,
            ),
            r.notes,
        )
        assertTrue(r.program.levelOneOnly && r.program.lightMode && r.numbersHidden)
    }
}
