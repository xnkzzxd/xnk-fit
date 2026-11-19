package app.xnkfit.ui.onboarding

import androidx.lifecycle.ViewModel
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.domain.rules.ActivityLevel
import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Profile
import app.xnkfit.domain.rules.R_INPUT_HEIGHT_MAX_CM
import app.xnkfit.domain.rules.R_INPUT_HEIGHT_MIN_CM
import app.xnkfit.domain.rules.R_INPUT_WEIGHT_MAX_KG
import app.xnkfit.domain.rules.R_INPUT_WEIGHT_MIN_KG
import app.xnkfit.domain.rules.Screening
import app.xnkfit.domain.rules.Sex
import app.xnkfit.domain.rules.evaluateTargets
import app.xnkfit.ui.screening.EmptyScreening
import app.xnkfit.ui.screening.ScreeningQuestion
import app.xnkfit.ui.screening.outcomeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Input shaping for the form fields. These are UI limits, not Rules (plausibility is R_INPUT in the domain).
private const val BIRTH_YEAR_DIGITS = 4
private const val NUMBER_TEXT_MAX = 6
private const val NICKNAME_MAX = 30
private const val HEIGHT_START_CM = 160.0
private const val WEIGHT_START_KG = 60.0
private const val HEIGHT_STEP_CM = 1.0
private const val WEIGHT_STEP_KG = 0.5
private const val WEIGHT_TYPE = "weight" // text-ok: stored code, not UI text
private const val KG_UNIT = "kg" // text-ok: stored code, not UI text
private const val YES_CODE = "ya" // text-ok: stored code, not UI text
private const val NO_CODE = "tidak" // text-ok: stored code, not UI text

/**
 * Onboarding and screening flow (PRD O-1…O-6, S-1…S-7). All gate decisions come from
 * `domain.rules.evaluateTargets`; this class only moves between steps and keeps what was typed.
 *
 * Privacy: nothing is saved until [finish]. Under 13 the state is wiped and nothing is ever saved (O-5).
 * No program is made here: a program needs the full screening first (O-2, R_GATE_SCREEN), and the program
 * generator checks the gate itself.
 */
class OnboardingViewModel(
    private val store: ProfileStore,
    private val currentYear: () -> Int,
    private val nowMs: () -> Long,
    private val newId: () -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    // ----- input -----

    fun selectGoal(goal: GoalChoice) = _state.update { it.copy(goal = goal) }

    fun selectSex(sex: Sex) = _state.update { it.copy(sex = sex) }

    fun setBirthYear(text: String) = _state.update { it.copy(birthYearText = digits(text, BIRTH_YEAR_DIGITS)) }

    fun setHeight(text: String) = _state.update { it.copy(heightText = decimal(text)) }

    fun setWeight(text: String) = _state.update { it.copy(weightText = decimal(text)) }

    fun stepHeight(direction: Int) = _state.update {
        val now = parse(it.heightText) ?: HEIGHT_START_CM - direction * HEIGHT_STEP_CM
        it.copy(heightText = format((now + direction * HEIGHT_STEP_CM).coerceIn(R_INPUT_HEIGHT_MIN_CM, R_INPUT_HEIGHT_MAX_CM)))
    }

    fun stepWeight(direction: Int) = _state.update {
        val now = parse(it.weightText) ?: WEIGHT_START_KG - direction * WEIGHT_STEP_KG
        it.copy(weightText = format((now + direction * WEIGHT_STEP_KG).coerceIn(R_INPUT_WEIGHT_MIN_KG, R_INPUT_WEIGHT_MAX_KG)))
    }

    fun selectInterest(interest: Interest) = _state.update { it.copy(interest = interest) }

    fun setNickname(text: String) = _state.update { it.copy(nickname = text.take(NICKNAME_MAX)) }

    fun answer(question: ScreeningQuestion, value: Boolean?) = _state.update {
        it.copy(screening = question.with(it.screening, value), screeningIncomplete = false)
    }

    // ----- navigation -----

    fun start() = goTo(OnboardingStep.WELCOME, OnboardingStep.GOAL)

    /** "Lanjut". Does nothing when the step is not complete; the screen shows what is missing. */
    fun next() {
        val s = _state.value
        when (s.step) {
            OnboardingStep.WELCOME -> start()
            OnboardingStep.GOAL -> if (s.goal != null) goTo(OnboardingStep.GOAL, OnboardingStep.BODY)
            OnboardingStep.BODY -> submitBody()
            OnboardingStep.INTEREST -> if (s.interest != null) goTo(OnboardingStep.INTEREST, OnboardingStep.NAME)
            OnboardingStep.NAME -> goTo(OnboardingStep.NAME, OnboardingStep.HEALTH)
            OnboardingStep.RESULT -> goTo(OnboardingStep.RESULT, OnboardingStep.PERMISSIONS)
            OnboardingStep.HEALTH, OnboardingStep.SCREENING, OnboardingStep.PERMISSIONS, OnboardingStep.UNDER_13 -> Unit
        }
    }

    /** "Lewati" on the nickname step: no nickname (PRD O-1). */
    fun skipNickname() {
        _state.update { it.copy(nickname = "") }
        if (_state.value.step == OnboardingStep.NAME) goTo(OnboardingStep.NAME, OnboardingStep.HEALTH)
    }

    /** The one health question (PRD O-2). "Ya / tidak yakin" opens the full form; "Tidak ada" defers it. */
    fun answerHealth(hasCondition: Boolean) {
        if (_state.value.step != OnboardingStep.HEALTH) return
        if (hasCondition) {
            _state.update { it.copy(step = OnboardingStep.SCREENING, screeningOpened = true) }
        } else {
            _state.update {
                it.copy(step = OnboardingStep.PERMISSIONS, screeningOpened = false, screening = EmptyScreening, outcome = null)
            }
        }
    }

    /** "Simpan" on the form. Unanswered questions keep the user on the form (R_GATE_SCREEN). */
    fun saveScreening() {
        val s = _state.value
        if (s.step != OnboardingStep.SCREENING) return
        when (val r = evaluate(s, s.screening)) {
            is GateResult.Ready ->
                _state.update { it.copy(step = OnboardingStep.RESULT, outcome = outcomeOf(r), screeningIncomplete = false) }
            GateResult.ScreeningNeeded -> _state.update { it.copy(screeningIncomplete = true) }
            GateResult.StopUnder13 -> stopUnder13()
            is GateResult.InvalidInput -> _state.update { it.copy(step = OnboardingStep.BODY, problems = r.problems) }
        }
    }

    /**
     * Phone back button and the "Kembali" button. Returns true when it handled the press; false at the
     * first screen, where the system may leave the app (PRD N-3).
     */
    fun back(): Boolean {
        val s = _state.value
        val previous = when (s.step) {
            OnboardingStep.WELCOME -> return false
            OnboardingStep.GOAL -> OnboardingStep.WELCOME
            OnboardingStep.BODY -> OnboardingStep.GOAL
            OnboardingStep.INTEREST -> OnboardingStep.BODY
            OnboardingStep.NAME -> OnboardingStep.INTEREST
            OnboardingStep.HEALTH -> OnboardingStep.NAME
            OnboardingStep.SCREENING -> OnboardingStep.HEALTH
            OnboardingStep.RESULT -> OnboardingStep.SCREENING
            OnboardingStep.PERMISSIONS -> if (s.screeningDone) OnboardingStep.RESULT else OnboardingStep.HEALTH
            OnboardingStep.UNDER_13 -> OnboardingStep.BODY
        }
        if (s.saving) return true
        _state.update { it.copy(step = previous) }
        return true
    }

    fun permissionHandled(item: PermissionItem) = _state.update { it.copy(permissionsHandled = it.permissionsHandled + item) }

    // ----- finish -----

    /**
     * "Masuk": writes the profile and the first weight. Re-checks every gate first; a child, a missing
     * value or a half-done form writes nothing. Without the form the profile keeps `screeningDone = false`,
     * so the domain still gives no program (O-2).
     */
    suspend fun finish() {
        val s = _state.value
        if (s.step != OnboardingStep.PERMISSIONS || s.saving || s.finished) return
        val screening = if (s.screeningDone) s.screening else null
        val result = evaluate(s, screening)
        val ready = result as? GateResult.Ready
        val weight = parse(s.weightText)
        val ok = weight != null && (ready != null || (!s.screeningDone && result == GateResult.ScreeningNeeded))
        if (!ok) {
            if (result == GateResult.StopUnder13) stopUnder13()
            return
        }
        val now = nowMs()
        val profile = ProfileEntity(
            createdAt = now,
            updatedAt = now,
            sex = s.sex?.name?.lowercase(),
            birthYear = s.birthYearText.toIntOrNull(),
            heightCm = parse(s.heightText),
            goal = s.goal?.code,
            interest = s.interest?.code,
            nickname = s.nickname.trim().ifEmpty { null },
            healthRisk = ready != null && GateNote.RISK_SEE_DOCTOR in ready.notes,
            screeningDone = ready != null,
            pregnant = s.screening.pregnantOrBreastfeeding == true && s.screeningDone,
            eatingHistory = if (ready != null) (if (s.screening.eatingDisorderHistory == true) YES_CODE else NO_CODE) else null,
        )
        val measure = MeasureEntity(
            id = newId(), createdAt = now, updatedAt = now,
            type = WEIGHT_TYPE, value = weight!!, unit = KG_UNIT, takenAt = now,
        )
        _state.update { it.copy(saving = true, saveFailed = false) }
        try {
            store.save(profile, measure)
            _state.update { it.copy(saving = false, finished = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(saving = false, saveFailed = true) }
        }
    }

    // ----- internals -----

    private fun goTo(from: OnboardingStep, to: OnboardingStep) {
        _state.update { if (it.step == from) it.copy(step = to) else it }
    }

    /** R_GATE_U13 → R_INPUT run by the domain. The first valid pass moves on; under 13 wipes everything (O-5). */
    private fun submitBody() {
        val s = _state.value
        when (val r = evaluate(s, null)) {
            GateResult.StopUnder13 -> stopUnder13()
            is GateResult.InvalidInput -> _state.update { it.copy(problems = r.problems) }
            GateResult.ScreeningNeeded -> _state.update { it.copy(problems = emptySet(), step = OnboardingStep.INTEREST) }
            is GateResult.Ready -> Unit // cannot happen: no screening was passed
        }
    }

    /** Under 13: explain and keep nothing. Birth year, body data, goal, nickname, answers: all dropped (O-5). */
    private fun stopUnder13() {
        _state.value = OnboardingState(step = OnboardingStep.UNDER_13)
    }

    /**
     * The gates look at body data and screening only. Activity is not asked in onboarding (PRD O-4: the training
     * profile comes with Latihan), so the lowest level stands in; it cannot change which gate applies.
     */
    private fun evaluate(s: OnboardingState, screening: Screening?): GateResult = evaluateTargets(
        Profile(
            sex = s.sex,
            birthYear = s.birthYearText.toIntOrNull(),
            heightCm = parse(s.heightText),
            weightKg = parse(s.weightText),
            activity = ActivityLevel.JARANG,
            goal = s.goal?.domain,
        ),
        screening,
        currentYear(),
    )

    private fun digits(text: String, max: Int) = text.filter { it.isDigit() }.take(max)

    private fun decimal(text: String): String {
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }.take(NUMBER_TEXT_MAX)
        val firstSeparator = cleaned.indexOfFirst { it == '.' || it == ',' }
        return if (firstSeparator < 0) cleaned else cleaned.substring(0, firstSeparator + 1) +
            cleaned.substring(firstSeparator + 1).filter { it.isDigit() }
    }

    private fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private fun format(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else value.toString()
}
