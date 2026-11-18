package app.xnkfit.domain.training

import app.xnkfit.domain.rules.floorToStep
import kotlin.math.min

// Training numbers (Rules.md §5). The generator itself is B-06; these are the numbers and the small
// pure rules it uses. Pure Kotlin, no Android imports.

// R_TRN_WEEK: strength sessions per week (S14 Table 2).
const val R_TRN_WEEK_BEGINNER_MIN = 2
const val R_TRN_WEEK_BEGINNER_MAX = 3
const val R_TRN_WEEK_INTERMEDIATE_MIN = 3
const val R_TRN_WEEK_INTERMEDIATE_MAX = 4

// R_TRN_SETS: working sets per exercise.
const val R_TRN_SETS_L1_MIN = 2
const val R_TRN_SETS_L1_MAX = 3
const val R_TRN_SETS_L2_MIN = 3
const val R_TRN_SETS_L2_MAX = 4

// R_TRN_REPS: rep ranges.
const val R_TRN_REPS_HYPERTROPHY_MIN = 8
const val R_TRN_REPS_HYPERTROPHY_MAX = 12
const val R_TRN_REPS_STRENGTH_MIN = 3
const val R_TRN_REPS_STRENGTH_MAX = 5
const val R_TRN_REPS_BODYWEIGHT_MIN = 10
const val R_TRN_REPS_BODYWEIGHT_MAX = 15

// R_TRN_REST: rest between sets, seconds.
const val R_TRN_REST_ISOLATION_MIN_S = 60
const val R_TRN_REST_ISOLATION_MAX_S = 90
const val R_TRN_REST_COMPOUND_MIN_S = 120
const val R_TRN_REST_COMPOUND_MAX_S = 180

// R_TRN_RIR: target reps in reserve.
const val R_TRN_RIR_MIN = 1
const val R_TRN_RIR_MAX = 3

// R_TRN_PROG_UP / R_TRN_PROG_LEG: fixed load steps, kg.
const val R_TRN_PROG_UP_KG = 2.5
const val R_TRN_PROG_LEG_KG = 5.0

// R_TRN_PROG_STEP: step limit and equipment steps.
const val R_TRN_PROG_STEP_MAX_FRACTION = 0.10
const val R_TRN_PROG_STEP_DUMBBELL_KG = 1.0
const val R_TRN_PROG_STEP_BARBELL_KG = 2.5
const val R_TRN_PROG_STEP_EXTRA_REPS = 2

// R_TRN_VOL: weekly working sets per muscle group.
const val R_TRN_VOL_MIN_L1 = 5
const val R_TRN_VOL_MIN_L2 = 10
const val R_TRN_VOL_MAX = 20

// R_TRN_VOL_COUNT: main muscle 1 set, each helper 0.5.
const val R_TRN_VOL_COUNT_MAIN = 1.0
const val R_TRN_VOL_COUNT_HELPER = 0.5

// R_TRN_VOL_SCOPE: groups counted and shown with no minimum.
val R_TRN_VOL_SCOPE_NO_MINIMUM: Set<MuscleGroup> =
    setOf(MuscleGroup.FOREARM, MuscleGroup.LOWER_BACK, MuscleGroup.OBLIQUES, MuscleGroup.TRAPS)

// R_TRN_BAND: band levels.
const val R_TRN_BAND_MIN = 1
const val R_TRN_BAND_MAX = 5

// R_TRN_FORM: "rapi" in a row needed to release a "ragu" hold.
const val R_TRN_FORM_RAPI_IN_ROW = 2

// R_TRN_REVIEW: automatic review.
const val R_TRN_REVIEW_WEEKS = 4
const val R_TRN_REVIEW_SESSIONS = 3

// R_TRN_RETURN (⚠ G9, owner rule without a source): return after absence.
const val R_TRN_RETURN_GAP_DAYS = 14
const val R_TRN_RETURN_LOAD_FRACTION = 0.60
const val R_TRN_RETURN_SET_DROP = 1

// R_TRN_NO_WARMUP: warm-up sets in programs and the logger.
const val R_TRN_NO_WARMUP_SETS = 0

// R_TRN_LEVELS: level 1 lasts the first 8 weeks (or the sign-up experience sets the start level).
const val R_TRN_LEVELS_BEGINNER_WEEKS = 8

/** 16 groups of the muscle map (Content.md §5). */
enum class MuscleGroup {
    CHEST, FRONT_DELT, SIDE_DELT, REAR_DELT, BICEPS, TRICEPS, LATS, TRAPS,
    QUADS, HAMS, GLUTES, CALVES, ABS, OBLIQUES, LOWER_BACK, FOREARM,
}

/** Training level (Rules.md §5 note): 1 beginner, 2 intermediate, 3 advanced. */
enum class TrainingLevel { L1, L2, L3 }

/** R_TRN_SETS. */
fun setsRange(level: TrainingLevel): IntRange =
    if (level == TrainingLevel.L1) R_TRN_SETS_L1_MIN..R_TRN_SETS_L1_MAX else R_TRN_SETS_L2_MIN..R_TRN_SETS_L2_MAX

enum class LoadKind { WEIGHTED, BODYWEIGHT_OR_BAND }

/** R_TRN_REPS: rep ranges allowed for a level and load kind. */
fun repRanges(level: TrainingLevel, kind: LoadKind): List<IntRange> = when {
    kind == LoadKind.BODYWEIGHT_OR_BAND -> listOf(R_TRN_REPS_BODYWEIGHT_MIN..R_TRN_REPS_BODYWEIGHT_MAX)
    level == TrainingLevel.L1 -> listOf(R_TRN_REPS_HYPERTROPHY_MIN..R_TRN_REPS_HYPERTROPHY_MAX)
    else -> listOf(
        R_TRN_REPS_STRENGTH_MIN..R_TRN_REPS_STRENGTH_MAX,
        R_TRN_REPS_HYPERTROPHY_MIN..R_TRN_REPS_HYPERTROPHY_MAX,
    )
}

/** Proposed load step. [extraRepsNeeded] > 0: propose only after the top of the range + that many reps on all sets. */
data class LoadStep(val kg: Double, val extraRepsNeeded: Int)

/**
 * R_TRN_PROG_UP / R_TRN_PROG_LEG / R_TRN_PROG_STEP: step = min(fixed step, 10 % of the load), rounded down to the
 * equipment's smallest step. If even the smallest step is over 10 %, it is proposed only after top + 2 reps.
 * Null for unusable input.
 */
fun loadStep(currentKg: Double, legs: Boolean, equipmentStepKg: Double): LoadStep? {
    if (!currentKg.isFinite() || currentKg <= 0.0 || !equipmentStepKg.isFinite() || equipmentStepKg <= 0.0) return null
    val fixed = if (legs) R_TRN_PROG_LEG_KG else R_TRN_PROG_UP_KG
    val limit = min(fixed, R_TRN_PROG_STEP_MAX_FRACTION * currentKg)
    val step = floorToStep(limit, equipmentStepKg)
    return if (step >= equipmentStepKg) LoadStep(step, 0) else LoadStep(equipmentStepKg, R_TRN_PROG_STEP_EXTRA_REPS)
}

/** R_TRN_VOL_COUNT: weekly sets credited to a group from one exercise. */
fun setsCredited(workingSets: Int, isMainMuscle: Boolean): Double =
    workingSets * if (isMainMuscle) R_TRN_VOL_COUNT_MAIN else R_TRN_VOL_COUNT_HELPER

enum class VolumeStatus { NO_MINIMUM, UNDER, OK, OVER }

/** R_TRN_VOL / R_TRN_VOL_SCOPE / R_TRN_VOL_FILL trigger: where a group stands against its weekly range. */
fun volumeStatus(group: MuscleGroup, level: TrainingLevel, weeklySets: Double): VolumeStatus {
    if (weeklySets > R_TRN_VOL_MAX) return VolumeStatus.OVER
    if (group in R_TRN_VOL_SCOPE_NO_MINIMUM) return VolumeStatus.NO_MINIMUM
    val min = if (level == TrainingLevel.L1) R_TRN_VOL_MIN_L1 else R_TRN_VOL_MIN_L2
    return if (weeklySets < min) VolumeStatus.UNDER else VolumeStatus.OK
}

enum class BodyweightStep { MORE_REPS, ADD_SET, HARDER_VARIANT }

/** R_TRN_PROG_BW: reps to the top → +1 set → next harder variant. */
fun bodyweightStep(allSetsAtTop: Boolean, sets: Int, maxSets: Int): BodyweightStep = when {
    !allSetsAtTop -> BodyweightStep.MORE_REPS
    sets < maxSets -> BodyweightStep.ADD_SET
    else -> BodyweightStep.HARDER_VARIANT
}

/** R_TRN_BAND: a band level is valid when it is 1–5. */
fun isValidBandLevel(level: Int): Boolean = level in R_TRN_BAND_MIN..R_TRN_BAND_MAX

enum class FormRating { RAPI, RAGU }

/** R_TRN_FORM: load may rise when there is no "ragu", or two "rapi" in a row after the last "ragu". */
fun loadMayRise(ratingsOldestFirst: List<FormRating>): Boolean {
    val lastRagu = ratingsOldestFirst.lastIndexOf(FormRating.RAGU)
    if (lastRagu < 0) return ratingsOldestFirst.isNotEmpty()
    return ratingsOldestFirst.size - 1 - lastRagu >= R_TRN_FORM_RAPI_IN_ROW
}

/** R_TRN_REVIEW: every 4 weeks or after 3 completed sessions since the last review. */
fun reviewDue(weeksSinceReview: Int, sessionsSinceReview: Int): Boolean =
    weeksSinceReview >= R_TRN_REVIEW_WEEKS || sessionsSinceReview >= R_TRN_REVIEW_SESSIONS

/** First session after a break. */
data class ReturnPlan(val loadKg: Double, val sets: Int)

/**
 * R_TRN_RETURN (⚠ G9): gap ≥ 14 days → 60 % of the last load, rounded down to the equipment step, one set fewer
 * (never below one set). Null when there is no eased restart.
 */
fun returnAfterAbsence(daysSinceLast: Int, lastLoadKg: Double, lastSets: Int, equipmentStepKg: Double): ReturnPlan? {
    if (daysSinceLast < R_TRN_RETURN_GAP_DAYS) return null
    if (!lastLoadKg.isFinite() || lastLoadKg < 0.0 || !equipmentStepKg.isFinite() || equipmentStepKg <= 0.0) return null
    val load = floorToStep(R_TRN_RETURN_LOAD_FRACTION * lastLoadKg, equipmentStepKg)
    return ReturnPlan(load, (lastSets - R_TRN_RETURN_SET_DROP).coerceAtLeast(1))
}

/** R_TRN_LEVELS: level 1 for the first 8 weeks unless sign-up experience started higher. */
fun levelAfterWeeks(startLevel: TrainingLevel, weeksTrained: Int): TrainingLevel =
    if (startLevel == TrainingLevel.L1 && weeksTrained >= R_TRN_LEVELS_BEGINNER_WEEKS) TrainingLevel.L2 else startLevel
