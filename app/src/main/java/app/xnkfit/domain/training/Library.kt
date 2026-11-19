package app.xnkfit.domain.training

// Exercise library and equipment as the generator sees them (Design §6, Content.md §2–§3). Pure Kotlin.
// The data layer maps `content/exercises.json` rows into [Exercise]; the generator never reads files.

/** Movement patterns (Content.md §3), same codes as `content/exercises.json`. */
enum class Pattern {
    SQUAT, HINGE, PUSH_H, PUSH_V, PULL_H, PULL_V, CURL, TRI, LEG_ISO, CORE, CARRY, CARDIO;

    /** Multi-joint patterns get the compound rest (R_TRN_REST) and may use the strength rep range. */
    val compound: Boolean get() = this in R_TRN_GEN_COMPOUND_PATTERNS

    /** Patterns whose weight step is the leg step (R_TRN_PROG_LEG). */
    val legs: Boolean get() = this in R_TRN_PROG_LEG_PATTERNS
}

/** Equipment settings (Content.md §2), codes as in Design §3 `equipment.setting`. */
enum class EquipmentSetting(val code: String) {
    GYM("gym"), HOME_BW("home-bw"), HOME_DB("home-db"), CUSTOM("custom");
}

/**
 * What the user has. For the three fixed settings the items are the ones Content.md §2 assumes (R_TRN_EQUIP);
 * for `custom` they are the ticked codes plus the floor items every place has.
 */
data class Equipment(val setting: EquipmentSetting, val items: Set<String>) {
    companion object {
        fun of(setting: EquipmentSetting, ticked: Set<String> = emptySet()): Equipment = when (setting) {
            EquipmentSetting.GYM -> Equipment(setting, R_TRN_EQUIP_GYM)
            EquipmentSetting.HOME_BW -> Equipment(setting, R_TRN_EQUIP_HOME)
            EquipmentSetting.HOME_DB -> Equipment(setting, R_TRN_EQUIP_HOME + R_TRN_EQUIP_HOME_DB)
            EquipmentSetting.CUSTOM -> Equipment(setting, R_TRN_EQUIP_CUSTOM_BASE + ticked)
        }
    }

    /** L-1: an exercise is possible when its setting matches (fixed settings) and every item it needs is here. */
    fun allows(e: Exercise): Boolean {
        if (setting != EquipmentSetting.CUSTOM && setting.code !in e.settings) return false
        return items.containsAll(e.equipment)
    }
}

/** One library entry, as needed by the generator (Content.md §3 fields). */
data class Exercise(
    val id: String,
    val pattern: Pattern,
    /** 1–3 (Content.md §3). */
    val level: Int,
    val settings: Set<String>,
    val equipment: Set<String>,
    val main: Set<MuscleGroup>,
    val helper: Set<MuscleGroup>,
    /** Easier variant this entry builds on (Content.md §3); the harder variant of X is the entry with variantOf = X. */
    val variantOf: String? = null,
    /** "Kenapa gerakan ini" line (L-11), Indonesian, from content. */
    val hintId: String? = null,
) {
    /** R_TRN_BAND / R_TRN_REPS: bands and bodyweight are counted in reps (and band level), not kg. */
    val loadKind: LoadKind
        get() = if (equipment.any { it in R_TRN_PROG_STEP_BARBELL_CODES || it in R_TRN_PROG_STEP_DUMBBELL_CODES }) {
            LoadKind.WEIGHTED
        } else {
            LoadKind.BODYWEIGHT_OR_BAND
        }

    /** Bands are logged as band level + reps (R_TRN_BAND); bodyweight as reps only. */
    val usesBand: Boolean get() = loadKind == LoadKind.BODYWEIGHT_OR_BAND && R_TRN_BAND_CODE in equipment

    /** R_TRN_PROG_STEP: smallest load step of the equipment, null for bodyweight and bands. */
    val equipmentStepKg: Double?
        get() = when {
            equipment.any { it in R_TRN_PROG_STEP_BARBELL_CODES } -> R_TRN_PROG_STEP_BARBELL_KG
            equipment.any { it in R_TRN_PROG_STEP_DUMBBELL_CODES } -> R_TRN_PROG_STEP_DUMBBELL_KG
            else -> null
        }

    /** True when the program's level allows this entry. */
    fun fits(level: TrainingLevel): Boolean = this.level <= level.number
}

/** Level as a number 1–3, matching `level` in the library. */
val TrainingLevel.number: Int get() = ordinal + 1

// R_TRN_EQUIP: equipment each fixed setting assumes (Content.md §2). Floor work (`none`, `mat`) is everywhere.
// Home bodyweight: floor, wall, stairs, chair. A table, door, towel, bar or rope is not assumed (L-3 applies).
val R_TRN_EQUIP_HOME: Set<String> = setOf("none", "mat", "wall", "stairs", "chair")
// Home dumbbell / band: dumbbells and bands on top of the home items. The pull-up bar is "optional" in Content §2,
// so it is not assumed; bench and kettlebell are not listed there either. The user ticks them in `custom`.
val R_TRN_EQUIP_HOME_DB: Set<String> = setOf("dumbbell", "band")
// Gym: machines, barbell, cable, dumbbells, bench and the rest of a full gym.
val R_TRN_EQUIP_GYM: Set<String> = setOf("none", "mat", "barbell", "rack", "smith_machine", "machine", "dumbbell", "bench", "cable", "kettlebell", "trap_bar", "bar", "treadmill", "bike", "rower")
// Custom: only floor work is assumed; everything else is ticked.
val R_TRN_EQUIP_CUSTOM_BASE: Set<String> = setOf("none", "mat")

// R_TRN_PROG_STEP: which equipment codes take the barbell/machine step and which the dumbbell step.
val R_TRN_PROG_STEP_BARBELL_CODES: Set<String> = setOf("barbell", "smith_machine", "machine", "cable", "trap_bar")
val R_TRN_PROG_STEP_DUMBBELL_CODES: Set<String> = setOf("dumbbell", "kettlebell")

// R_TRN_BAND: equipment code of a resistance band.
const val R_TRN_BAND_CODE = "band"

// R_TRN_PROG_LEG: patterns that take the leg step.
val R_TRN_PROG_LEG_PATTERNS: Set<Pattern> = setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.LEG_ISO)
