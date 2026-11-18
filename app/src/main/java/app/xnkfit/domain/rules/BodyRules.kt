package app.xnkfit.domain.rules

import kotlin.math.floor

// Body and energy rules (Rules.md §1). Pure Kotlin: no Android imports (Design §2).
// Every number is a named constant whose name starts with its Rules.md ID (PR-6).

enum class Sex { LAKI_LAKI, PEREMPUAN }

// R_MSJ_M / R_MSJ_F: Mifflin–St Jeor BMR, kcal/day (S1).
const val R_MSJ_M_PER_KG = 10.0
const val R_MSJ_M_PER_CM = 6.25
const val R_MSJ_M_PER_YEAR = 5.0
const val R_MSJ_M_CONSTANT = 5.0
const val R_MSJ_F_PER_KG = 10.0
const val R_MSJ_F_PER_CM = 6.25
const val R_MSJ_F_PER_YEAR = 5.0
const val R_MSJ_F_CONSTANT = -161.0

// R_ACT: activity factor (PAL), FAO/WHO/UNU 2004 Table 5.3 values (S2), mapping to days is a product rule.
const val R_ACT_JARANG = 1.40
const val R_ACT_RINGAN = 1.55
const val R_ACT_SEDANG = 1.70
const val R_ACT_AKTIF = 1.85
const val R_ACT_SANGAT_AKTIF = 2.00

/** Weekly activity answer (PRD L-14). */
enum class ActivityLevel(val pal: Double) {
    JARANG(R_ACT_JARANG),
    RINGAN(R_ACT_RINGAN),
    SEDANG(R_ACT_SEDANG),
    AKTIF(R_ACT_AKTIF),
    SANGAT_AKTIF(R_ACT_SANGAT_AKTIF),
}

// R_BMI_UNDER: WHO underweight cut-off (S3).
const val R_BMI_UNDER = 18.5

// R_WHTR: waist-to-height boundary (S4), shown in Bantuan only.
const val R_WHTR_FLAG = 0.5

// R_INPUT: plausibility limits for typed values (product rule). Outside them the input is
// treated as a typo: no target and no program (fail-safe, PR-5). These are not health limits.
const val R_INPUT_HEIGHT_MIN_CM = 100.0
const val R_INPUT_HEIGHT_MAX_CM = 250.0
const val R_INPUT_WEIGHT_MIN_KG = 25.0
const val R_INPUT_WEIGHT_MAX_KG = 300.0
const val R_INPUT_WAIST_MIN_CM = 40.0
const val R_INPUT_WAIST_MAX_CM = 250.0
const val R_INPUT_AGE_MAX = 100

private const val CM_PER_M = 100.0 // unit conversion, not a rule

// R_AGE: with a birth year only, the birthday may not have passed yet. The age gates (R_GATE_U13, R_GATE_U18)
// use the youngest possible age, one year less (Decisions.md, 2026-11-18, safe side).
const val R_AGE_GATE_MARGIN_YEARS = 1

/** R_AGE: age from birth year = current year − birth year (product rule). Used for BMR, fibre and display. */
fun ageFromBirthYear(birthYear: Int, currentYear: Int): Int = currentYear - birthYear

/** R_AGE: youngest age the person can be this year; the age gates use this one. */
fun youngestPossibleAge(birthYear: Int, currentYear: Int): Int =
    ageFromBirthYear(birthYear, currentYear) - R_AGE_GATE_MARGIN_YEARS

/** R_MSJ_M / R_MSJ_F: BMR in kcal/day. */
fun bmrKcal(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double = when (sex) {
    Sex.LAKI_LAKI ->
        R_MSJ_M_PER_KG * weightKg + R_MSJ_M_PER_CM * heightCm - R_MSJ_M_PER_YEAR * ageYears + R_MSJ_M_CONSTANT
    Sex.PEREMPUAN ->
        R_MSJ_F_PER_KG * weightKg + R_MSJ_F_PER_CM * heightCm - R_MSJ_F_PER_YEAR * ageYears + R_MSJ_F_CONSTANT
}

/** R_TDEE: maintenance = BMR × activity factor. Activity calories are never added on top (M-1). */
fun maintenanceKcal(bmrKcal: Double, activity: ActivityLevel): Double = bmrKcal * activity.pal

/** R_BMI: kg / m². */
fun bmi(weightKg: Double, heightCm: Double): Double {
    val m = heightCm / CM_PER_M
    return weightKg / (m * m)
}

/** R_BMI_UNDER: true when goal "turun" must not be offered. */
fun isUnderweight(bmi: Double): Boolean = bmi < R_BMI_UNDER

/** R_WHTR: waist ÷ height. */
fun waistToHeight(waistCm: Double, heightCm: Double): Double = waistCm / heightCm

/** R_WHTR: ≥ 0.5 is shown as "perhatikan" (kind wording). Null when the input is not usable. */
fun whtrFlag(waistCm: Double?, heightCm: Double?): Boolean? {
    if (waistCm == null || heightCm == null) return null
    if (!waistCm.isFinite() || waistCm !in R_INPUT_WAIST_MIN_CM..R_INPUT_WAIST_MAX_CM) return null
    if (!heightCm.isFinite() || heightCm !in R_INPUT_HEIGHT_MIN_CM..R_INPUT_HEIGHT_MAX_CM) return null
    return waistToHeight(waistCm, heightCm) >= R_WHTR_FLAG
}

/** Floor to a multiple of [step] with a small tolerance for binary fractions (2.5 / 2.5 = 0.999…). */
internal fun floorToStep(value: Double, step: Double): Double = floor(value / step + FLOAT_TOLERANCE) * step

internal const val FLOAT_TOLERANCE = 1e-9 // numeric tolerance, not a rule
