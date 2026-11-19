package app.xnkfit.domain.training

import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.training.TrainingTestSupport.TODAY
import app.xnkfit.domain.training.TrainingTestSupport.assertGolden
import app.xnkfit.domain.training.TrainingTestSupport.generated
import app.xnkfit.domain.training.TrainingTestSupport.library
import app.xnkfit.domain.training.TrainingTestSupport.profile
import app.xnkfit.domain.training.TrainingTestSupport.render
import app.xnkfit.domain.training.TrainingTestSupport.riskGate
import app.xnkfit.domain.training.TrainingTestSupport.screeningGate
import app.xnkfit.domain.training.TrainingTestSupport.under18Gate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-06: golden programs per equipment setting and level, and properties every generated program must keep. */
class ProgramGeneratorTest {
    private val gym = Equipment.of(EquipmentSetting.GYM)
    private val homeBw = Equipment.of(EquipmentSetting.HOME_BW)
    private val homeDb = Equipment.of(EquipmentSetting.HOME_DB)
    private val custom = Equipment.of(EquipmentSetting.CUSTOM, setOf("dumbbell", "bench", "bar", "band"))
    private val settings = listOf(gym, homeBw, homeDb, custom)

    // ---------------------------------------------------------------- golden programs (Design §13 "Generator")

    private fun golden(name: String, p: Program) = assertGolden(name, render(p))

    @Test fun golden_R_TRN_GEN_gymLevel1Risk() = golden("gym-l1-risk", generated(profile(riskGate, Experience.LEBIH_SETAHUN), gym))
    @Test fun golden_R_TRN_GEN_gymLevel2Split() =
        golden("gym-l2-4d-60", generated(profile(experience = Experience.BEBERAPA_BULAN, days = 4, minutes = 60), gym, GoalFocus.KEKUATAN))
    @Test fun golden_R_TRN_GEN_homeBwLevel1Risk() = golden("home-bw-l1-risk", generated(profile(riskGate), homeBw))
    @Test fun golden_R_TRN_GEN_homeBwLevel2() =
        golden("home-bw-l2-3d-45", generated(profile(experience = Experience.BEBERAPA_BULAN), homeBw))
    @Test fun golden_R_TRN_GEN_homeDbLevel1Risk() = golden("home-db-l1-risk", generated(profile(riskGate), homeDb))
    @Test fun golden_R_TRN_GEN_homeDbLevel2() =
        golden("home-db-l2-3d-45", generated(profile(experience = Experience.BEBERAPA_BULAN), homeDb))
    @Test fun golden_R_TRN_GEN_customLevel1Risk() = golden("custom-l1-risk", generated(profile(riskGate), custom))
    @Test fun golden_R_TRN_GEN_customLevel3() =
        golden("custom-l3-4d-60", generated(profile(experience = Experience.LEBIH_SETAHUN, days = 4, minutes = 60), custom))

    // ---------------------------------------------------------------- properties over many inputs

    private fun allPrograms(): List<Program> = settings.flatMap { eq ->
        listOf(TrainingTestSupport.readyGate, riskGate, under18Gate).flatMap { gate ->
            Experience.entries.flatMap { exp ->
                (R_TRN_WEEK_BEGINNER_MIN..R_TRN_WEEK_INTERMEDIATE_MAX).flatMap { days ->
                    R_TRN_GEN_SLOTS_BY_MINUTES.keys.flatMap { min ->
                        GoalFocus.entries.map { f -> generated(profile(gate, exp, days, min), eq, f) }
                    }
                }
            }
        }
    }

    private val programs by lazy { allPrograms() }
    private val byId = library.associateBy { it.id }

    @Test
    fun R_TRN_EQUIP_noExerciseOutsideTheEquipment() {
        programs.forEach { p ->
            val eq = settings.first { it.setting == p.setting }
            p.days.flatMap { it.items }.forEach { item ->
                val ex = byId.getValue(item.exerciseId)
                assertTrue("${p.setting} has ${ex.id} needing ${ex.equipment}", eq.allows(ex))
                assertTrue("${ex.id} level ${ex.level} in ${p.level}", ex.fits(p.level))
            }
        }
    }

    @Test
    fun R_TRN_VOL_FILL_everyReachableGroupMeetsItsMinimumAndStaysUnderTheCap() {
        programs.forEach { p ->
            val min = if (p.level == TrainingLevel.L1) R_TRN_VOL_MIN_L1 else R_TRN_VOL_MIN_L2
            MuscleGroup.entries.forEach { g ->
                val sets = p.weeklySets.getValue(g)
                assertTrue("${p.setting} $g $sets over cap", sets <= R_TRN_VOL_MAX)
                if (g in R_TRN_VOL_SCOPE_NO_MINIMUM) return@forEach
                if (g !in p.underMinimum) assertTrue("${p.setting} ${p.level} $g $sets", sets >= min)
                else assertTrue("${p.setting} $g marked under but has $sets", sets < min)
            }
            // The weekly map is what the items add up to.
            assertEquals(weeklySets(p.days.flatMap { it.items }, library), p.weeklySets)
        }
    }

    @Test
    fun R_TRN_VOL_FILL_gymAndHomeDbReachEveryGroupFromThreeDays() {
        // With 2 days at level 2 a group trained by one isolation pattern (CALVES) can stay under 10: it is reported.
        val bad = programs.filter { it.days.size >= R_TRN_WEEK_BEGINNER_MAX }
            .filter { it.setting == EquipmentSetting.GYM || (it.setting == EquipmentSetting.HOME_DB && it.days.size < R_TRN_WEEK_SPLIT_DAYS) }
            .filter { it.underMinimum.isNotEmpty() }
            .map { "${it.setting} ${it.level} ${it.days.size}d ${it.days.first().items.size}it ${it.focus} ${it.underMinimum}" }.distinct()
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * Known content limit (B-06): every home-db hamstring and squat entry has GLUTES as a main muscle, so on a 4-day
     * split the GLUTES cap of 20 stops QUADS or HAMS short of 10. The program reports it (L-17) instead of going over.
     */
    @Test
    fun R_TRN_VOL_FILL_homeDbSplitReportsOnlyLegGroupsCappedByGlutes() {
        programs.filter { it.setting == EquipmentSetting.HOME_DB && it.days.size == R_TRN_WEEK_SPLIT_DAYS }.forEach {
            assertTrue(it.underMinimum.toString(), (it.underMinimum - setOf(MuscleGroup.QUADS, MuscleGroup.HAMS)).isEmpty())
            assertTrue(it.weeklySets.getValue(MuscleGroup.GLUTES) <= R_TRN_VOL_MAX)
        }
    }

    @Test
    fun R_TRN_GEN_noDuplicateDaysAndNoDuplicateExerciseInADay() {
        programs.forEach { p ->
            val lists = p.days.map { d -> d.items.map { it.exerciseId } }
            assertEquals("${p.setting} duplicate days $lists", lists.size, lists.toSet().size)
            lists.forEach { assertEquals("duplicate in day $it", it.size, it.toSet().size) }
            assertEquals(p.days.size, p.days.map { it.name }.toSet().size)
        }
    }

    @Test
    fun R_TRN_SETS_setsAndRepsStayInsideTheLevelTable() {
        programs.forEach { p ->
            p.days.flatMap { it.items }.forEach { item ->
                assertTrue(item.sets in setsRange(p.level))
                assertTrue(repRanges(p.level, item.loadKind).any { it.first == item.repMin && it.last == item.repMax })
                assertTrue(item.restSec in R_TRN_REST_ISOLATION_MIN_S..R_TRN_REST_COMPOUND_MAX_S)
            }
        }
    }

    @Test
    fun R_TRN_NO_WARMUP_programsHaveNoWarmupSets() {
        programs.flatMap { p -> p.days.flatMap { it.items } }.forEach { assertEquals(0, it.warmupSets) }
    }

    @Test
    fun R_TRN_GEN_cardioAndCarriesAreNotInGeneratedPrograms() {
        programs.flatMap { p -> p.days.flatMap { it.items } }.forEach { assertFalse(it.pattern in R_TRN_GEN_EXCLUDED_PATTERNS) }
    }

    @Test
    fun R_TRN_GEN_sameInputGivesTheSameProgram() {
        val a = generated(profile(), gym)
        val b = generated(profile(), gym)
        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- safety gates and level

    @Test
    fun R_GATE_SCREEN_noProgramBeforeScreening() {
        val r = generate(profile(screeningGate), gym, GoalFocus.OTOT, TrainingHistory(), library, TODAY)
        assertEquals(GenerateResult.Blocked(GateResult.ScreeningNeeded), r)
    }

    @Test
    fun R_TRN_LEVEL1_riskAndUnder18GetLevelOneWhateverTheExperience() {
        listOf(riskGate, under18Gate).forEach { gate ->
            val p = generated(profile(gate, Experience.LEBIH_SETAHUN, days = 4, minutes = 60), gym, GoalFocus.KEKUATAN)
            assertEquals(TrainingLevel.L1, p.level)
            assertEquals(R_TRN_WEEK_BEGINNER_MAX, p.days.size)
            p.days.flatMap { it.items }.forEach {
                assertEquals(1, byId.getValue(it.exerciseId).level)
                assertTrue(it.sets in R_TRN_SETS_L1_MIN..R_TRN_SETS_L1_MAX)
            }
        }
    }

    @Test
    fun R_TRN_LEVELS_beginnerMovesToLevelTwoAfterEightWeeksOfLog() {
        val start = TODAY - R_TRN_LEVELS_BEGINNER_WEEKS * R_TRN_LEVELS_DAYS_PER_WEEK
        val history = TrainingHistory(listOf(LoggedSession(start, listOf(LoggedSet("SQUAT_GOB", 10.0, 10)))))
        assertEquals(TrainingLevel.L2, generated(profile(), gym, history = history).level)
        val short = TrainingHistory(listOf(LoggedSession(start + 1, listOf(LoggedSet("SQUAT_GOB", 10.0, 10)))))
        assertEquals(TrainingLevel.L1, generated(profile(), gym, history = short).level)
        // A risk answer keeps level 1 even after the weeks.
        assertEquals(TrainingLevel.L1, generated(profile(riskGate), gym, history = history).level)
    }

    @Test
    fun R_TRN_WEEK_daysFollowTheLevel() {
        assertEquals(R_TRN_WEEK_BEGINNER_MAX, trainingDays(TrainingLevel.L1, 4))
        assertEquals(R_TRN_WEEK_BEGINNER_MIN, trainingDays(TrainingLevel.L1, 1))
        assertEquals(R_TRN_WEEK_INTERMEDIATE_MAX, trainingDays(TrainingLevel.L2, 6))
        val split = generated(profile(experience = Experience.BEBERAPA_BULAN, days = 4), gym)
        assertEquals(listOf(DayName.UPPER_A, DayName.LOWER_A, DayName.UPPER_B, DayName.LOWER_B), split.days.map { it.name })
    }

    @Test
    fun R_TRN_REPS_strengthRangeOnlyForWeightedMainCompoundsAtLevelTwo() {
        val p = generated(profile(experience = Experience.BEBERAPA_BULAN, days = 4), gym, GoalFocus.KEKUATAN)
        val items = p.days.flatMap { it.items }
        val strength = items.filter { it.repMax == R_TRN_REPS_STRENGTH_MAX }
        assertTrue(strength.isNotEmpty())
        strength.forEach {
            assertEquals(ItemRole.MAIN, it.role)
            assertTrue(it.pattern.compound)
            assertEquals(LoadKind.WEIGHTED, it.loadKind)
            assertEquals(R_TRN_REST_COMPOUND_MAX_S, it.restSec)
        }
        // Level 1 ignores the focus.
        generated(profile(), gym, GoalFocus.KEKUATAN).days.flatMap { it.items }
            .forEach { assertTrue(it.repMax != R_TRN_REPS_STRENGTH_MAX) }
    }

    // ---------------------------------------------------------------- L-3 missing patterns

    @Test
    fun R_TRN_EQUIP_homeBodyweightSaysWhatPullsNeed() {
        val p = generated(profile(experience = Experience.BEBERAPA_BULAN), homeBw)
        val pulls = p.missing.filter { it.pattern == Pattern.PULL_H || it.pattern == Pattern.PULL_V }
        assertEquals(setOf(Pattern.PULL_H, Pattern.PULL_V), pulls.map { it.pattern }.toSet())
        assertTrue(p.missing.first { it.pattern == Pattern.PULL_V }.needs.containsAll(setOf("bar", "band")))
        // never filled with a wrong pattern
        p.days.flatMap { it.items }.forEach { assertFalse(it.pattern == Pattern.PULL_H || it.pattern == Pattern.PULL_V) }
        assertTrue(MuscleGroup.LATS in p.underMinimum)
    }

    @Test
    fun R_TRN_EQUIP_fixedSettingsAssumeContentSection2() {
        assertTrue(homeBw.items.containsAll(setOf("none", "mat", "wall", "stairs", "chair")))
        assertFalse("bar" in homeBw.items || "table" in homeBw.items || "band" in homeBw.items)
        assertTrue(homeDb.items.containsAll(setOf("dumbbell", "band")))
        assertFalse("bar" in homeDb.items || "bench" in homeDb.items)
        assertEquals(setOf("none", "mat", "dumbbell"), Equipment.of(EquipmentSetting.CUSTOM, setOf("dumbbell")).items)
        // custom takes entries of any setting
        assertTrue(custom.allows(byId.getValue("PULLV_PULLUP")))
        assertFalse(homeDb.allows(byId.getValue("PULLV_PULLUP")))
    }

    // ---------------------------------------------------------------- small generator rules

    @Test
    fun R_TRN_GEN_slotsAndEstimate() {
        assertEquals(4, slotsFor(30))
        assertEquals(5, slotsFor(45))
        assertEquals(6, slotsFor(60))
        assertEquals(4, slotsFor(20))
        assertEquals(5, slotsFor(50))
        assertEquals(45, R_TRN_GEN_EST_WORK_S)
        assertEquals(1, R_TRN_GEN_VERSION)
        val item = ItemContext(TrainingLevel.L1, GoalFocus.OTOT).item(byId.getValue("SQUAT_GOB"), ItemRole.MAIN)
        // 2 sets × (120 s rest + 45 s) = 330 s → 6 min
        assertEquals(6, estMinutes(listOf(item)))
    }
}
