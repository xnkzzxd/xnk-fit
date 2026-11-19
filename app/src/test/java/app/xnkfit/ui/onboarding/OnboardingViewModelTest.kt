package app.xnkfit.ui.onboarding

import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.InputProblem
import app.xnkfit.domain.rules.Sex
import app.xnkfit.ui.screening.ScreeningOutcome
import app.xnkfit.ui.screening.ScreeningQuestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore(var fail: Boolean = false) : ProfileStore {
    var profile: ProfileEntity? = null
    var weight: MeasureEntity? = null
    var saves = 0

    override suspend fun save(profile: ProfileEntity, firstWeight: MeasureEntity) {
        if (fail) error("disk full")
        saves++
        this.profile = profile
        this.weight = firstWeight
    }
}

/** Flow, gates, input validation, back button and "no program before the form" (task B-05, PRD O-1…O-6, S-1…S-7). */
class OnboardingViewModelTest {
    private val year = 2026
    private val store = FakeStore()
    private val vm = OnboardingViewModel(store, { year }, { 1_000L }, { "id-1" })
    private val s get() = vm.state.value

    private fun fillBody(sex: Sex = Sex.PEREMPUAN, birth: String = "1996", height: String = "158", weight: String = "55") {
        vm.selectSex(sex); vm.setBirthYear(birth); vm.setHeight(height); vm.setWeight(weight)
    }

    /** Walks to the HEALTH step with valid data. */
    private fun toHealth(sex: Sex = Sex.PEREMPUAN, birth: String = "1996") {
        vm.start()
        vm.selectGoal(GoalChoice.TURUN); vm.next()
        fillBody(sex, birth); vm.next()
        vm.selectInterest(Interest.KEDUANYA); vm.next()
        vm.setNickname("Rani"); vm.next()
        assertEquals(OnboardingStep.HEALTH, s.step)
    }

    private fun answerAll(value: Boolean, pregnant: Boolean? = value) {
        ScreeningQuestion.entries.forEach { vm.answer(it, if (it == ScreeningQuestion.PREGNANT) pregnant else value) }
    }

    @Test
    fun shortFlowOrderMatchesPrdO1() {
        assertEquals(OnboardingStep.WELCOME, s.step)
        toHealth()
        vm.answerHealth(false)
        assertEquals(OnboardingStep.PERMISSIONS, s.step)
    }

    @Test
    fun goalAndInterestMustBeChosenBeforeNext() {
        vm.start()
        vm.next()
        assertEquals(OnboardingStep.GOAL, s.step)
        vm.selectGoal(GoalChoice.OTOT); vm.next()
        assertEquals(OnboardingStep.BODY, s.step)
        fillBody(); vm.next()
        vm.next()
        assertEquals(OnboardingStep.INTEREST, s.step)
    }

    @Test
    fun emptyBodyFormShowsEveryMissingFieldAndStays() {
        vm.start(); vm.selectGoal(GoalChoice.JAGA); vm.next()
        vm.next()
        assertEquals(OnboardingStep.BODY, s.step)
        assertTrue(
            s.problems.containsAll(
                setOf(InputProblem.SEX_MISSING, InputProblem.BIRTH_YEAR_MISSING, InputProblem.HEIGHT_MISSING, InputProblem.WEIGHT_MISSING),
            ),
        )
    }

    @Test
    fun implausibleNumbersAreRefusedByTheDomainLimits() {
        vm.start(); vm.selectGoal(GoalChoice.JAGA); vm.next()
        fillBody(height = "20", weight = "900"); vm.next()
        assertEquals(OnboardingStep.BODY, s.step)
        assertTrue(InputProblem.HEIGHT_OUT_OF_RANGE in s.problems)
        assertTrue(InputProblem.WEIGHT_OUT_OF_RANGE in s.problems)
        vm.setBirthYear("1900"); vm.setHeight("160"); vm.setWeight("60"); vm.next()
        assertTrue(InputProblem.AGE_OUT_OF_RANGE in s.problems)
        vm.setBirthYear("2030"); vm.next()
        assertTrue(InputProblem.BIRTH_YEAR_IN_FUTURE in s.problems)
    }

    @Test
    fun textInputIsShapedAndDecimalCommaWorks() {
        vm.setBirthYear("19a9 6x77"); assertEquals("1996", s.birthYearText)
        vm.setHeight("1,5,8"); assertEquals("1,58", s.heightText)
        vm.setWeight("5x5.5"); assertEquals("55.5", s.weightText)
        vm.setNickname("x".repeat(80)); assertEquals(30, s.nickname.length)
        vm.setWeight("Infinity"); assertEquals("", s.weightText.filter { it.isLetter() })
    }

    @Test
    fun steppersStartAtADefaultAndStayInsideLimits() {
        vm.stepHeight(1); assertEquals("160", s.heightText)
        vm.stepWeight(-1); assertEquals("60", s.weightText)
        vm.stepWeight(1); assertEquals("60.5", s.weightText)
        vm.setHeight("250"); vm.stepHeight(1); assertEquals("250", s.heightText)
        vm.setWeight("25"); vm.stepWeight(-1); assertEquals("25", s.weightText)
    }

    @Test
    fun under13StopsWipesEverythingAndNeverSaves() = runBlocking {
        vm.start(); vm.selectGoal(GoalChoice.TURUN); vm.next()
        fillBody(birth = "2015"); vm.next()
        assertEquals(OnboardingStep.UNDER_13, s.step)
        assertEquals(OnboardingState(step = OnboardingStep.UNDER_13), s)
        vm.next(); vm.finish()
        assertEquals(OnboardingStep.UNDER_13, s.step)
        assertEquals(0, store.saves)
        // "Ubah tahun lahir": back to an empty body form
        assertTrue(vm.back())
        assertEquals(OnboardingStep.BODY, s.step)
        assertEquals("", s.birthYearText)
        assertEquals("", s.weightText)
    }

    @Test
    fun youngestPossibleAgeRuleStopsBornThirteenYearsAgo() {
        // Born 2013 in 2026: age 13 on paper, youngest possible 12 (R_AGE) → stopped.
        vm.start(); vm.selectGoal(GoalChoice.JAGA); vm.next()
        fillBody(birth = "2013"); vm.next()
        assertEquals(OnboardingStep.UNDER_13, s.step)
    }

    @Test
    fun healthYesOpensFormAndNoDefersIt() {
        toHealth()
        vm.answerHealth(true)
        assertEquals(OnboardingStep.SCREENING, s.step)
        assertTrue(s.screeningOpened)
    }

    @Test
    fun incompleteFormStaysOnFormAndMakesNoResult() {
        toHealth(); vm.answerHealth(true)
        vm.answer(ScreeningQuestion.HEART, false)
        vm.saveScreening()
        assertEquals(OnboardingStep.SCREENING, s.step)
        assertTrue(s.screeningIncomplete)
        assertNull(s.outcome)
        vm.next()
        assertEquals(OnboardingStep.SCREENING, s.step)
    }

    @Test
    fun womanMustAnswerPregnancyButManMayLeaveIt() {
        toHealth(Sex.PEREMPUAN); vm.answerHealth(true)
        answerAll(false, pregnant = null); vm.saveScreening()
        assertEquals(OnboardingStep.SCREENING, s.step)
    }

    @Test
    fun manMayLeavePregnancyUnanswered() {
        toHealth(Sex.LAKI_LAKI); vm.answerHealth(true)
        answerAll(false, pregnant = null); vm.saveScreening()
        assertEquals(OnboardingStep.RESULT, s.step)
        assertEquals(ScreeningOutcome.Clear, s.outcome)
    }

    @Test
    fun riskAnswerGivesLighterProgramNoteWithTheExactDoctorText() {
        toHealth(); vm.answerHealth(true)
        answerAll(false); vm.answer(ScreeningQuestion.CHEST, true); vm.saveScreening()
        val outcome = s.outcome as ScreeningOutcome.Notes
        assertEquals(listOf(app.xnkfit.domain.rules.GateNote.RISK_SEE_DOCTOR), outcome.cards.map { it.note })
        assertEquals(app.xnkfit.R.string.sc_out_risk_hint, outcome.cards.single().hint)
    }

    @Test
    fun eatingHistoryPregnancyAndUnder18EachShowTheirOwnCard() {
        toHealth(birth = "2010"); vm.answerHealth(true)
        answerAll(false); vm.answer(ScreeningQuestion.EATING, true); vm.answer(ScreeningQuestion.PREGNANT, true)
        vm.saveScreening()
        val notes = (s.outcome as ScreeningOutcome.Notes).cards.map { it.note }
        assertEquals(
            listOf(
                app.xnkfit.domain.rules.GateNote.UNDER_18_LIGHT_MODE,
                app.xnkfit.domain.rules.GateNote.PREGNANT_NO_DEFICIT,
                app.xnkfit.domain.rules.GateNote.EATING_DISORDER_NO_NUMBERS,
            ),
            notes,
        )
    }

    @Test
    fun backButtonWalksTheFlowBackwardsAndLeavesAtTheFirstScreen() {
        toHealth(); vm.answerHealth(true)
        answerAll(false); vm.saveScreening()
        val seen = mutableListOf(s.step)
        while (vm.back()) seen += s.step
        assertEquals(
            listOf(
                OnboardingStep.RESULT, OnboardingStep.SCREENING, OnboardingStep.HEALTH, OnboardingStep.NAME,
                OnboardingStep.INTEREST, OnboardingStep.BODY, OnboardingStep.GOAL, OnboardingStep.WELCOME,
            ),
            seen,
        )
        assertFalse(vm.back())
    }

    @Test
    fun backKeepsWhatWasTyped() {
        toHealth()
        vm.back(); vm.back(); vm.back()
        assertEquals(OnboardingStep.BODY, s.step)
        assertEquals("1996", s.birthYearText)
        assertEquals("158", s.heightText)
    }

    @Test
    fun backFromPermissionsGoesToHealthWhenFormWasDeferredAndToResultWhenDone() {
        toHealth(); vm.answerHealth(false)
        vm.back(); assertEquals(OnboardingStep.HEALTH, s.step)
        vm.answerHealth(true); answerAll(false); vm.saveScreening(); vm.next()
        assertEquals(OnboardingStep.PERMISSIONS, s.step)
        vm.back(); assertEquals(OnboardingStep.RESULT, s.step)
    }

    @Test
    fun nothingIsSavedBeforeTheLastStep() {
        toHealth(); vm.answerHealth(true); answerAll(false); vm.saveScreening(); vm.next()
        assertEquals(0, store.saves)
    }

    @Test
    fun deferredFormSavesProfileWithoutScreeningSoNoProgramCanFollow() = runBlocking {
        toHealth(); vm.answerHealth(false)
        vm.finish()
        val saved = store.profile!!
        assertFalse(saved.screeningDone)
        assertNull(saved.eatingHistory)
        assertEquals("perempuan", saved.sex)
        assertEquals("Rani", saved.nickname)
        assertEquals(55.0, store.weight!!.value, 0.0)
        assertEquals("weight", store.weight!!.type)
        assertTrue(s.finished)
        // The domain still refuses a program until the form is done (R_GATE_SCREEN).
        val gate = app.xnkfit.domain.rules.evaluateTargets(
            app.xnkfit.domain.rules.Profile(Sex.PEREMPUAN, 1996, 158.0, 55.0, app.xnkfit.domain.rules.ActivityLevel.JARANG, app.xnkfit.domain.rules.Goal.TURUN),
            null, year,
        )
        assertEquals(GateResult.ScreeningNeeded, gate)
    }

    @Test
    fun finishedFormIsStoredWithRiskAndAnswers() = runBlocking {
        toHealth(); vm.answerHealth(true)
        answerAll(false); vm.answer(ScreeningQuestion.INJURY, true); vm.answer(ScreeningQuestion.EATING, true)
        vm.saveScreening(); vm.next()
        vm.finish()
        val saved = store.profile!!
        assertTrue(saved.screeningDone)
        assertTrue(saved.healthRisk)
        assertEquals("ya", saved.eatingHistory)
        assertFalse(saved.pregnant)
    }

    @Test
    fun blankNicknameIsStoredAsNull() = runBlocking {
        toHealth(); vm.skipNickname() // already past NAME: no effect on step
        vm.back(); vm.skipNickname()
        assertEquals(OnboardingStep.HEALTH, s.step)
        vm.answerHealth(false); vm.finish()
        assertNull(store.profile!!.nickname)
    }

    @Test
    fun finishOnlyRunsOnTheLastStepAndOnlyOnce() = runBlocking {
        toHealth()
        vm.finish()
        assertEquals(0, store.saves)
        vm.answerHealth(false); vm.finish(); vm.finish()
        assertEquals(1, store.saves)
    }

    @Test
    fun saveFailureIsShownAndCanBeRetried() = runBlocking {
        toHealth(); vm.answerHealth(false)
        store.fail = true; vm.finish()
        assertTrue(s.saveFailed); assertFalse(s.finished); assertFalse(s.saving)
        store.fail = false; vm.finish()
        assertTrue(s.finished)
    }

    @Test
    fun permissionRowsCanAllBeSkipped() = runBlocking {
        toHealth(); vm.answerHealth(false)
        assertEquals(4, PermissionItem.entries.size)
        // Skipping is optional: "Masuk" works with no row answered (PRD O-3).
        vm.finish()
        assertTrue(s.finished)
        assertTrue(s.permissionsHandled.isEmpty())
    }

    @Test
    fun onlyNotificationsAndCameraAreAskedHere() {
        assertEquals(
            setOf(PermissionItem.NOTIFICATIONS, PermissionItem.CAMERA),
            PermissionItem.entries.filter { it.asksHere }.toSet(),
        )
    }

    @Test
    fun everyGoalCardMapsToAGateGoal() {
        assertEquals(app.xnkfit.domain.rules.Goal.NAIK, GoalChoice.OTOT.domain)
        assertEquals(app.xnkfit.domain.rules.Goal.TURUN, GoalChoice.TURUN.domain)
    }
}
