package app.xnkfit.domain.rules

import kotlin.math.max

// Safety gates (Rules.md §3, PRD PR-5, S-1…S-8). Order is fixed and written in Rules.md §3:
// R_GATE_U13 → R_INPUT → R_GATE_SCREEN → R_GATE_RISK → R_GATE_U18 → R_GATE_PREG → R_GATE_ED
// → R_GATE_BMI → R_GATE_FLOOR. Anything missing, unreadable or out of range gives no target and no program.

// R_GATE_U13: the app is for 13 and up (PRD O-5).
const val R_GATE_U13_MIN_AGE = 13

// R_GATE_U18: under this age no deficit and light mode (PRD S-2).
const val R_GATE_U18_ADULT_AGE = 18

/** What the user typed in onboarding. Null means "not answered". */
data class Profile(
    val sex: Sex?,
    val birthYear: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val activity: ActivityLevel?,
    val goal: Goal?,
)

/**
 * The full screening form (PRD S-1, mockup `screening.html`). Null means "not answered".
 * [pregnantOrBreastfeeding] may stay null for men ("—" in the form); for women it must be answered.
 */
data class Screening(
    val heartOrBloodPressure: Boolean?,
    val chestPainOnActivity: Boolean?,
    val ongoingInjury: Boolean?,
    val otherTreatedCondition: Boolean?,
    val doctorRestriction: Boolean?,
    val pregnantOrBreastfeeding: Boolean?,
    val eatingDisorderHistory: Boolean?,
)

enum class InputProblem {
    SEX_MISSING,
    BIRTH_YEAR_MISSING,
    BIRTH_YEAR_IN_FUTURE,
    AGE_OUT_OF_RANGE,
    HEIGHT_MISSING,
    HEIGHT_OUT_OF_RANGE,
    WEIGHT_MISSING,
    WEIGHT_OUT_OF_RANGE,
    ACTIVITY_MISSING,
    GOAL_MISSING,
}

/** Notes the target and program screens show, in gate order. Text lives in strings.xml (NF-3). */
enum class GateNote {
    /** R_GATE_RISK: "Konsultasi dokter sebelum latihan". */
    RISK_SEE_DOCTOR,

    /** R_DEF_CAP: deficit made smaller (at most 10 % of maintenance). */
    DEFICIT_CAPPED,

    /** R_GATE_U18: light mode, technique first, train with an adult. */
    UNDER_18_LIGHT_MODE,

    /** R_GATE_PREG: no deficit, ask a doctor or midwife. */
    PREGNANT_NO_DEFICIT,

    /** R_GATE_ED: maintain target, no-numbers mode, gentle note to talk to a doctor. */
    EATING_DISORDER_NO_NUMBERS,

    /** R_GATE_BMI: "turun" is not offered. */
    BMI_UNDER_NO_TURUN,

    /** A requested "turun" (or "naik" under R_GATE_ED) was changed to "jaga" by a gate above. */
    GOAL_CHANGED_TO_JAGA,

    /** R_GATE_FLOOR: target raised to R_FLOOR_* or BMR. */
    FLOOR_RAISED,
}

/** How the program generator must be limited. */
data class ProgramGate(
    /** R_TRN_LEVEL1 (R_GATE_RISK): level 1 programs only. */
    val levelOneOnly: Boolean,
    /** R_GATE_U18: light mode, technique first. */
    val lightMode: Boolean,
)

/** Result of the gates. Only [Ready] carries a target or a program permission. */
sealed interface GateResult {
    /** R_GATE_U13: the app explains it is for 13 and up and does not continue. */
    data object StopUnder13 : GateResult

    /** R_INPUT: something is missing or not plausible. No target, no program. */
    data class InvalidInput(val problems: Set<InputProblem>) : GateResult

    /** R_GATE_SCREEN: the full screening is not done. No target, no program; screening opens. */
    data object ScreeningNeeded : GateResult

    data class Ready(
        val ageYears: Int,
        val bmi: Double,
        val bmrKcal: Double,
        /** R_TDEE, shown rounded (R_ROUND). */
        val maintenanceKcal: Int,
        val goalRequested: Goal,
        val goalUsed: Goal,
        val goalsOffered: Set<Goal>,
        /** Final calorie target after every gate (R_ROUND, R_GATE_FLOOR). */
        val targetKcal: Int,
        val macros: Macros,
        val fibreG: Int,
        /** R_GATE_ED: hide calories and macros; show meal plan and ticks only. */
        val numbersHidden: Boolean,
        val program: ProgramGate,
        val notes: List<GateNote>,
    ) : GateResult {
        /** R_DISCLAIMER: always shown on the target card and program screens. */
        val showDisclaimer: Boolean get() = R_DISCLAIMER_ALWAYS
    }
}

// R_DISCLAIMER: "Bukan pengganti dokter / ahli gizi" is always shown with a target or program.
const val R_DISCLAIMER_ALWAYS = true

/**
 * Runs every gate in the order of Rules.md §3. [currentYear] is passed in (the domain has no clock).
 * Fail-safe: any missing, non-finite or out-of-range value ends without a target or program.
 */
fun evaluateTargets(profile: Profile, screening: Screening?, currentYear: Int): GateResult {
    // R_GATE_U13 first: a child stops here whatever else was typed.
    val birthYear = profile.birthYear
    if (birthYear != null && birthYear <= currentYear &&
        ageFromBirthYear(birthYear, currentYear) < R_GATE_U13_MIN_AGE
    ) {
        return GateResult.StopUnder13
    }

    // R_INPUT
    val problems = validate(profile, currentYear)
    if (problems.isNotEmpty()) return GateResult.InvalidInput(problems)
    val sex = profile.sex!!
    val age = ageFromBirthYear(profile.birthYear!!, currentYear)
    val heightCm = profile.heightCm!!
    val weightKg = profile.weightKg!!
    val activity = profile.activity!!
    val goalRequested = profile.goal!!

    // R_GATE_SCREEN
    if (screening == null || !isComplete(screening, sex)) return GateResult.ScreeningNeeded

    val notes = mutableListOf<GateNote>()
    val goalsOffered = Goal.entries.toMutableSet()

    // R_GATE_RISK (and R_DEF_CAP, R_TRN_LEVEL1)
    val risk = listOf(
        screening.heartOrBloodPressure,
        screening.chestPainOnActivity,
        screening.ongoingInjury,
        screening.otherTreatedCondition,
        screening.doctorRestriction,
    ).any { it == true }
    if (risk) notes += GateNote.RISK_SEE_DOCTOR

    // R_GATE_U18
    val under18 = age < R_GATE_U18_ADULT_AGE
    if (under18) {
        notes += GateNote.UNDER_18_LIGHT_MODE
        goalsOffered -= Goal.TURUN
    }

    // R_GATE_PREG (asked of everyone who answers it; a "yes" always counts)
    if (screening.pregnantOrBreastfeeding == true) {
        notes += GateNote.PREGNANT_NO_DEFICIT
        goalsOffered -= Goal.TURUN
    }

    // R_GATE_ED: maintain target only, no numbers.
    val eatingDisorder = screening.eatingDisorderHistory == true
    if (eatingDisorder) {
        notes += GateNote.EATING_DISORDER_NO_NUMBERS
        goalsOffered.retainAll(setOf(Goal.JAGA))
    }

    // R_GATE_BMI (R_BMI_UNDER)
    val bmiValue = bmi(weightKg, heightCm)
    if (isUnderweight(bmiValue)) {
        notes += GateNote.BMI_UNDER_NO_TURUN
        goalsOffered -= Goal.TURUN
    }

    val goalUsed = if (goalRequested in goalsOffered) goalRequested else Goal.JAGA
    if (goalUsed != goalRequested) notes += GateNote.GOAL_CHANGED_TO_JAGA

    // R_TDEE and R_GOAL_*
    val bmr = bmrKcal(sex, weightKg, heightCm, age)
    val maintenance = maintenanceKcal(bmr, activity)
    var adjustment = goalAdjustmentKcal(goalUsed).toDouble()
    if (risk && adjustment < 0) {
        // R_DEF_CAP: never more than 10 % of maintenance for the "other safety" group.
        val cap = -R_DEF_CAP_FRACTION * maintenance
        if (cap > adjustment) {
            adjustment = cap
            notes += GateNote.DEFICIT_CAPPED
        }
    }

    // R_ROUND, then R_GATE_FLOOR (R_FLOOR_F / R_FLOOR_M / R_FLOOR_BMR). The floor is rounded up.
    var target = roundKcal(maintenance + adjustment)
    val floor = max(sexFloorKcal(sex).toDouble(), bmr)
    if (target < floor) {
        target = roundKcalUp(floor)
        notes += GateNote.FLOOR_RAISED
    }

    return GateResult.Ready(
        ageYears = age,
        bmi = bmiValue,
        bmrKcal = bmr,
        maintenanceKcal = roundKcal(maintenance),
        goalRequested = goalRequested,
        goalUsed = goalUsed,
        goalsOffered = goalsOffered.toSet(),
        targetKcal = target,
        macros = macros(target, weightKg),
        fibreG = fibreG(sex, age)!!, // age ≥ 13 here
        numbersHidden = eatingDisorder,
        program = ProgramGate(levelOneOnly = risk, lightMode = under18),
        notes = notes.toList(),
    )
}

private fun validate(p: Profile, currentYear: Int): Set<InputProblem> {
    val out = mutableSetOf<InputProblem>()
    if (p.sex == null) out += InputProblem.SEX_MISSING
    when {
        p.birthYear == null -> out += InputProblem.BIRTH_YEAR_MISSING
        p.birthYear > currentYear -> out += InputProblem.BIRTH_YEAR_IN_FUTURE
        ageFromBirthYear(p.birthYear, currentYear) > R_INPUT_AGE_MAX -> out += InputProblem.AGE_OUT_OF_RANGE
    }
    when {
        p.heightCm == null -> out += InputProblem.HEIGHT_MISSING
        !p.heightCm.isFinite() || p.heightCm !in R_INPUT_HEIGHT_MIN_CM..R_INPUT_HEIGHT_MAX_CM ->
            out += InputProblem.HEIGHT_OUT_OF_RANGE
    }
    when {
        p.weightKg == null -> out += InputProblem.WEIGHT_MISSING
        !p.weightKg.isFinite() || p.weightKg !in R_INPUT_WEIGHT_MIN_KG..R_INPUT_WEIGHT_MAX_KG ->
            out += InputProblem.WEIGHT_OUT_OF_RANGE
    }
    if (p.activity == null) out += InputProblem.ACTIVITY_MISSING
    if (p.goal == null) out += InputProblem.GOAL_MISSING
    return out
}

private fun isComplete(s: Screening, sex: Sex): Boolean {
    val always = listOf(
        s.heartOrBloodPressure,
        s.chestPainOnActivity,
        s.ongoingInjury,
        s.otherTreatedCondition,
        s.doctorRestriction,
        s.eatingDisorderHistory,
    )
    if (always.any { it == null }) return false
    if (sex == Sex.PEREMPUAN && s.pregnantOrBreastfeeding == null) return false
    return true
}
