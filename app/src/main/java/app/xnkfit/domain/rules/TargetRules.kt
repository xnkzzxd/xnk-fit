package app.xnkfit.domain.rules

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// Calorie target and macros (Rules.md §2).

enum class Goal { TURUN, NAIK, JAGA }

// R_GOAL_*: daily adjustment to maintenance, kcal (S5, S19, product rule).
const val R_GOAL_TURUN_KCAL = -500
const val R_GOAL_NAIK_KCAL = 300
const val R_GOAL_JAGA_KCAL = 0

// R_DEF_CAP: for the "other safety" group (risk answers, R_GATE_RISK) the deficit is at most 10 % of maintenance.
const val R_DEF_CAP_FRACTION = 0.10

// R_FLOOR_F / R_FLOOR_M: calorie floors, kcal/day (owner caution; S5 p. 26).
const val R_FLOOR_F_KCAL = 1200
const val R_FLOOR_M_KCAL = 1500

// R_ROUND: calorie target rounded to 10 kcal.
const val R_ROUND_KCAL = 10

// R_PROT: protein g per kg body weight, kept within 1.4–2.0 (S6).
const val R_PROT_G_PER_KG = 1.6
const val R_PROT_MIN_G_PER_KG = 1.4
const val R_PROT_MAX_G_PER_KG = 2.0

// R_FAT: 25 % of target kcal, never below 0.8 g/kg (S7, S19).
const val R_FAT_SHARE = 0.25
const val R_FAT_MIN_G_PER_KG = 0.8

// R_KCAL_G: Atwater factors (S9).
const val R_KCAL_G_PROTEIN = 4.0
const val R_KCAL_G_CARB = 4.0
const val R_KCAL_G_FAT = 9.0

// R_RECALC: ask to recalculate when weight differs from the value used by this much (PRD M-2, Design §7).
const val R_RECALC_KG = 3.0

// R_FIBRE: AKG 2019 Tabel 1 (S8), g/day, by sex and age group. Group lower bounds in years.
const val R_FIBRE_AGE_13 = 13
const val R_FIBRE_AGE_16 = 16
const val R_FIBRE_AGE_19 = 19
const val R_FIBRE_AGE_30 = 30
const val R_FIBRE_AGE_50 = 50
const val R_FIBRE_AGE_65 = 65
const val R_FIBRE_AGE_OVER_80 = 81 // "80+" starts after the 65–80 group
const val R_FIBRE_M_13_15 = 34
const val R_FIBRE_M_16_18 = 37
const val R_FIBRE_M_19_29 = 37
const val R_FIBRE_M_30_49 = 36
const val R_FIBRE_M_50_64 = 30
const val R_FIBRE_M_65_80 = 25
const val R_FIBRE_M_80_PLUS = 22
const val R_FIBRE_F_13_15 = 29
const val R_FIBRE_F_16_18 = 29
const val R_FIBRE_F_19_29 = 32
const val R_FIBRE_F_30_49 = 30
const val R_FIBRE_F_50_64 = 25
const val R_FIBRE_F_65_80 = 22
const val R_FIBRE_F_80_PLUS = 20

/** R_GOAL_*: kcal added to maintenance for a goal. */
fun goalAdjustmentKcal(goal: Goal): Int = when (goal) {
    Goal.TURUN -> R_GOAL_TURUN_KCAL
    Goal.NAIK -> R_GOAL_NAIK_KCAL
    Goal.JAGA -> R_GOAL_JAGA_KCAL
}

/** R_FLOOR_F / R_FLOOR_M. */
fun sexFloorKcal(sex: Sex): Int = when (sex) {
    Sex.LAKI_LAKI -> R_FLOOR_M_KCAL
    Sex.PEREMPUAN -> R_FLOOR_F_KCAL
}

/** R_ROUND: nearest 10 kcal, halves up. */
fun roundKcal(kcal: Double): Int = (floor(kcal / R_ROUND_KCAL + HALF) * R_ROUND_KCAL).roundToInt()

/** R_ROUND applied to a floor: up to the next 10 kcal, so rounding never takes the target under the floor. */
fun roundKcalUp(kcal: Double): Int = (ceil(kcal / R_ROUND_KCAL - FLOAT_TOLERANCE) * R_ROUND_KCAL).roundToInt()

private const val HALF = 0.5 // rounding helper, not a rule

/** Daily macro targets in grams (rounded to whole grams for display). */
data class Macros(
    val proteinG: Int,
    val fatG: Int,
    val carbG: Int,
    /** Protein factor used, g/kg (R_PROT). Lower than the default only when the target is too small for it. */
    val proteinPerKg: Double,
)

/**
 * R_PROT, R_FAT, R_CARB, R_KCAL_G.
 *
 * Protein = 1.6 g/kg; fat = the larger of 25 % of kcal and 0.8 g/kg; carbohydrate = what is left.
 * If what is left is negative (very small target, very large body), protein steps down to its floor 1.4 g/kg;
 * if it is still negative, carbohydrate is 0 (never negative). Fat is never cut below its floor.
 */
fun macros(targetKcal: Int, weightKg: Double, proteinPerKg: Double = R_PROT_G_PER_KG): Macros {
    val fatKcal = max(R_FAT_SHARE * targetKcal, R_FAT_MIN_G_PER_KG * weightKg * R_KCAL_G_FAT)
    var perKg = proteinPerKg.coerceIn(R_PROT_MIN_G_PER_KG, R_PROT_MAX_G_PER_KG)
    var carbKcal = targetKcal - fatKcal - perKg * weightKg * R_KCAL_G_PROTEIN
    if (carbKcal < 0) {
        perKg = R_PROT_MIN_G_PER_KG
        carbKcal = max(0.0, targetKcal - fatKcal - perKg * weightKg * R_KCAL_G_PROTEIN)
    }
    return Macros(
        proteinG = (perKg * weightKg).roundToInt(),
        fatG = (fatKcal / R_KCAL_G_FAT).roundToInt(),
        carbG = (carbKcal / R_KCAL_G_CARB).roundToInt(),
        proteinPerKg = perKg,
    )
}

/** R_FIBRE: AKG 2019 fibre for sex and age (not derived from the target). Null below 13 (the app stops, O-5). */
fun fibreG(sex: Sex, ageYears: Int): Int? {
    val male = sex == Sex.LAKI_LAKI
    return when {
        ageYears < R_FIBRE_AGE_13 -> null
        ageYears < R_FIBRE_AGE_16 -> if (male) R_FIBRE_M_13_15 else R_FIBRE_F_13_15
        ageYears < R_FIBRE_AGE_19 -> if (male) R_FIBRE_M_16_18 else R_FIBRE_F_16_18
        ageYears < R_FIBRE_AGE_30 -> if (male) R_FIBRE_M_19_29 else R_FIBRE_F_19_29
        ageYears < R_FIBRE_AGE_50 -> if (male) R_FIBRE_M_30_49 else R_FIBRE_F_30_49
        ageYears < R_FIBRE_AGE_65 -> if (male) R_FIBRE_M_50_64 else R_FIBRE_F_50_64
        ageYears < R_FIBRE_AGE_OVER_80 -> if (male) R_FIBRE_M_65_80 else R_FIBRE_F_65_80
        else -> if (male) R_FIBRE_M_80_PLUS else R_FIBRE_F_80_PLUS
    }
}

/** R_RECALC (PRD M-2): weight moved ≥ 3 kg from the weight the target was computed with. */
fun recalculationSuggested(weightUsedKg: Double, currentWeightKg: Double): Boolean {
    if (!weightUsedKg.isFinite() || !currentWeightKg.isFinite()) return false
    return kotlin.math.abs(currentWeightKg - weightUsedKg) >= R_RECALC_KG - FLOAT_TOLERANCE
}
