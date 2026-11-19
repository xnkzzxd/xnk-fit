package app.xnkfit.ui.screening

import androidx.annotation.StringRes
import app.xnkfit.R
import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Screening

/**
 * The seven screening questions in the order of the mockup `screening.html` (PRD S-1).
 * Every answer lives in the domain [Screening]; nothing here decides a gate (Rules §3 is in `domain.rules`).
 */
enum class ScreeningQuestion(@StringRes val label: Int, val section: ScreeningSection) {
    HEART(R.string.sc_q_heart, ScreeningSection.HEART_AND_INJURY),
    CHEST(R.string.sc_q_chest, ScreeningSection.HEART_AND_INJURY),
    INJURY(R.string.sc_q_injury, ScreeningSection.HEART_AND_INJURY),
    OTHER(R.string.sc_q_other, ScreeningSection.HEART_AND_INJURY),
    PREGNANT(R.string.sc_q_pregnant, ScreeningSection.FOOD_AND_OTHER),
    EATING(R.string.sc_q_eating, ScreeningSection.FOOD_AND_OTHER),
    DOCTOR(R.string.sc_q_doctor, ScreeningSection.FOOD_AND_OTHER),
    ;

    fun answer(s: Screening): Boolean? = when (this) {
        HEART -> s.heartOrBloodPressure
        CHEST -> s.chestPainOnActivity
        INJURY -> s.ongoingInjury
        OTHER -> s.otherTreatedCondition
        PREGNANT -> s.pregnantOrBreastfeeding
        EATING -> s.eatingDisorderHistory
        DOCTOR -> s.doctorRestriction
    }

    fun with(s: Screening, value: Boolean?): Screening = when (this) {
        HEART -> s.copy(heartOrBloodPressure = value)
        CHEST -> s.copy(chestPainOnActivity = value)
        INJURY -> s.copy(ongoingInjury = value)
        OTHER -> s.copy(otherTreatedCondition = value)
        PREGNANT -> s.copy(pregnantOrBreastfeeding = value)
        EATING -> s.copy(eatingDisorderHistory = value)
        DOCTOR -> s.copy(doctorRestriction = value)
    }
}

enum class ScreeningSection(@StringRes val title: Int) {
    HEART_AND_INJURY(R.string.sc_section_heart),
    FOOD_AND_OTHER(R.string.sc_section_food),
}

/** No answer yet: the form is not complete and the domain refuses a target (R_GATE_SCREEN). */
val EmptyScreening = Screening(null, null, null, null, null, null, null)

/** One card of the result screen. Text only comes from strings.xml. */
data class OutcomeCard(
    val note: GateNote,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val hint: Int,
)

/** What the result screen shows after "Simpan" (mockup: aman, risiko, gangguan makan, di bawah 18). */
sealed interface ScreeningOutcome {
    /** Nothing to note. */
    data object Clear : ScreeningOutcome

    /** One card per gate that applies, in gate order. */
    data class Notes(val cards: List<OutcomeCard>) : ScreeningOutcome
}

/**
 * Text for each gate note the screening result shows. Notes that belong to the target card
 * (deficit cap, floor, goal changed, underweight) are not part of the screening result: null.
 * Wording: mockup `screening.html`; R_GATE_RISK text "Konsultasi dokter sebelum latihan" is exact (Rules §3).
 */
fun outcomeCardFor(note: GateNote): OutcomeCard? = when (note) {
    GateNote.RISK_SEE_DOCTOR ->
        OutcomeCard(note, R.string.sc_out_risk_title, R.string.sc_out_risk_body, R.string.sc_out_risk_hint)
    GateNote.UNDER_18_LIGHT_MODE ->
        OutcomeCard(note, R.string.sc_out_u18_title, R.string.sc_out_u18_body, R.string.sc_out_u18_hint)
    GateNote.PREGNANT_NO_DEFICIT ->
        OutcomeCard(note, R.string.sc_out_preg_title, R.string.sc_out_preg_body, R.string.sc_out_preg_hint)
    GateNote.EATING_DISORDER_NO_NUMBERS ->
        OutcomeCard(note, R.string.sc_out_ed_title, R.string.sc_out_ed_body, R.string.sc_out_ed_hint)
    GateNote.DEFICIT_CAPPED,
    GateNote.BMI_UNDER_NO_TURUN,
    GateNote.GOAL_CHANGED_TO_JAGA,
    GateNote.FLOOR_RAISED,
    -> null
}

/** Turns the domain result into result-screen cards. Anything but [GateResult.Ready] has no outcome. */
fun outcomeOf(result: GateResult): ScreeningOutcome? {
    if (result !is GateResult.Ready) return null
    val cards = result.notes.mapNotNull(::outcomeCardFor)
    return if (cards.isEmpty()) ScreeningOutcome.Clear else ScreeningOutcome.Notes(cards)
}
