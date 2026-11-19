package app.xnkfit.domain.latihan

import app.xnkfit.domain.training.Exercise
import app.xnkfit.domain.training.LoadKind
import app.xnkfit.domain.training.R_TRN_REPS_BODYWEIGHT_MAX
import app.xnkfit.domain.training.R_TRN_REPS_BODYWEIGHT_MIN
import app.xnkfit.domain.training.R_TRN_REPS_HYPERTROPHY_MAX
import app.xnkfit.domain.training.R_TRN_REPS_HYPERTROPHY_MIN
import app.xnkfit.domain.training.R_TRN_REST_COMPOUND_MIN_S
import app.xnkfit.domain.training.R_TRN_REST_ISOLATION_MIN_S
import app.xnkfit.domain.training.R_TRN_SETS_L1_MAX

// Editing an own routine (L-5, L-9). The user's numbers win: these are starting values and typo guards, not rules. Pure Kotlin.

/** Typo guard for the "sets" stepper (👤 product limit, Decisions B-08); the weekly cap R_TRN_VOL_MAX is shown by the generator, not enforced here. */
const val EDIT_SETS_MAX = 10
const val EDIT_REPS_MAX = 50
const val EDIT_REST_STEP_S = 15
const val EDIT_REST_MAX_S = 600

/** One exercise in the editor, with its name for display. */
data class DraftItem(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int,
    val supersetGroup: Int? = null,
)

/** Starting numbers for an exercise added by hand: the top of the level-1 sets, the usual rep range, compound or isolation rest. */
fun defaultItem(ex: Exercise, name: String): DraftItem {
    val reps = if (ex.loadKind == LoadKind.BODYWEIGHT_OR_BAND) R_TRN_REPS_BODYWEIGHT_MIN..R_TRN_REPS_BODYWEIGHT_MAX
    else R_TRN_REPS_HYPERTROPHY_MIN..R_TRN_REPS_HYPERTROPHY_MAX
    val rest = if (ex.pattern.compound) R_TRN_REST_COMPOUND_MIN_S else R_TRN_REST_ISOLATION_MIN_S
    return DraftItem(ex.id, name, R_TRN_SETS_L1_MAX, reps.first, reps.last, rest)
}

fun List<DraftItem>.withAdded(item: DraftItem): List<DraftItem> = if (any { it.exerciseId == item.exerciseId }) this else this + item

fun List<DraftItem>.withoutAt(index: Int): List<DraftItem> = filterIndexed { i, _ -> i != index }

/** Moves one item by [delta] places (−1 up, +1 down); a move past either end changes nothing. */
fun List<DraftItem>.moved(index: Int, delta: Int): List<DraftItem> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().also { val x = it.removeAt(index); it.add(to, x) }
}

fun DraftItem.withSets(delta: Int): DraftItem = copy(sets = (sets + delta).coerceIn(1, EDIT_SETS_MAX))

/** Moves both ends of the rep range by [delta], never below 1 or above [EDIT_REPS_MAX]. */
fun DraftItem.withReps(delta: Int): DraftItem {
    val span = repMax - repMin
    val min = (repMin + delta).coerceIn(1, EDIT_REPS_MAX - span)
    return copy(repMin = min, repMax = min + span)
}

fun DraftItem.withRest(delta: Int): DraftItem = copy(restSec = (restSec + delta * EDIT_REST_STEP_S).coerceIn(0, EDIT_REST_MAX_S))
