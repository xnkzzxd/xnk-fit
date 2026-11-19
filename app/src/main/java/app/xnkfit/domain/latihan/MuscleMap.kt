package app.xnkfit.domain.latihan

import app.xnkfit.domain.training.Exercise
import app.xnkfit.domain.training.MuscleGroup
import app.xnkfit.domain.training.ProgramItem
import app.xnkfit.domain.training.ItemRole
import app.xnkfit.domain.training.TrainingLevel
import app.xnkfit.domain.training.VolumeStatus
import app.xnkfit.domain.training.volumeStatus
import app.xnkfit.domain.training.weeklySets

// Muscle map levels (L-11). A level is 0 (not trained) to 3 (strongest tone); the picture never relies on colour alone,
// the screen lists the numbers too. Pure Kotlin: the region names come from `content/figures/muscle-map.json`.

const val R_MAP_TONE_NONE = 0
const val R_MAP_TONE_LOW = 1
const val R_MAP_TONE_MID = 2
const val R_MAP_TONE_HIGH = 3

/** One exercise: main muscles in the strongest tone, helpers in the lightest (mockup: accent and tint). */
fun exerciseLevels(ex: Exercise): Map<MuscleGroup, Int> =
    (ex.helper.associateWith { R_MAP_TONE_LOW } + ex.main.associateWith { R_MAP_TONE_HIGH })

/**
 * One week of training. [sets] are working sets per group (R_TRN_VOL_COUNT: main 1, helper half). Below the weekly
 * minimum is low, inside the range mid, over R_TRN_VOL_MAX high; a group with no minimum is mid as soon as it is trained.
 */
fun weeklyLevel(group: MuscleGroup, level: TrainingLevel, sets: Double): Int {
    if (sets <= 0.0) return R_MAP_TONE_NONE
    return when (volumeStatus(group, level, sets)) {
        VolumeStatus.UNDER -> R_MAP_TONE_LOW
        VolumeStatus.OK, VolumeStatus.NO_MINIMUM -> R_MAP_TONE_MID
        VolumeStatus.OVER -> R_MAP_TONE_HIGH
    }
}

fun weeklyLevels(sets: Map<MuscleGroup, Double>, level: TrainingLevel): Map<MuscleGroup, Int> =
    sets.mapValues { (g, s) -> weeklyLevel(g, level, s) }.filterValues { it > R_MAP_TONE_NONE }

/** Working sets done in one week per group, from the number of done sets of each exercise. */
fun weeklySetsDone(doneSetsByExercise: Map<String, Int>, library: List<Exercise>): Map<MuscleGroup, Double> {
    val byId = library.associateBy { it.id }
    val items = doneSetsByExercise.mapNotNull { (id, n) ->
        val ex = byId[id] ?: return@mapNotNull null
        ProgramItem(id, ex.pattern, ItemRole.MAIN, n, 0, 0, 0, ex.loadKind, ex.usesBand)
    }
    return weeklySets(items, library)
}

/**
 * Muscle codes to the region keys of the figure ("front:region" / "back:region", from `muscle-map.json` `map`). A region two groups share takes the stronger level
 * (content/figures/muscle-map.json `shared`).
 */
fun regionLevels(levels: Map<MuscleGroup, Int>, regionsOf: Map<MuscleGroup, List<String>>): Map<String, Int> {
    val out = HashMap<String, Int>()
    levels.forEach { (g, lv) -> regionsOf[g].orEmpty().forEach { r -> if (lv > (out[r] ?: 0)) out[r] = lv } }
    return out
}
