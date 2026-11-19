package app.xnkfit.domain.latihan

import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.domain.training.GoalFocus
import app.xnkfit.domain.training.ProgramSource

// Latihan rules that are not about numbers of sets: who owns a program (L-5) and the Jelajahi shelf (L-9). Pure Kotlin.

/** Stored code of a program source (`program.source`, Design §3). */
val ProgramSource.code: String
    get() = when (this) {
        ProgramSource.AUTO -> "auto"
        ProgramSource.OWN -> "own"
        ProgramSource.JELAJAHI -> "jelajahi"
    }

fun programSourceOf(code: String?): ProgramSource = ProgramSource.entries.firstOrNull { it.code == code } ?: ProgramSource.OWN

/** L-5: saving any edit makes the program the user's own, whatever it was before. */
fun sourceAfterEdit(@Suppress("UNUSED_PARAMETER") before: ProgramSource): ProgramSource = ProgramSource.OWN

/** L-5: only `auto` rows are ever replaced by the generator; `own` and `jelajahi` rows are left alone. */
fun generatorMayReplace(source: ProgramSource): Boolean = source == ProgramSource.AUTO

/** What the first training profile and the generator need (mockup latihan.html: 3 questions plus the place). */
data class TrainingSetup(
    val setting: EquipmentSetting,
    val days: Int,
    val minutes: Int,
    val experience: Experience,
    val focus: GoalFocus = GoalFocus.OTOT,
    /** Ticked equipment codes for `custom`. */
    val customItems: Set<String> = emptySet(),
)

/** One card on the Jelajahi shelf (L-9). The program is built by the same generator, with these fixed answers. */
data class JelajahiTemplate(val id: String, val setting: EquipmentSetting, val days: Int, val minutes: Int)

/** L-9: full body 3×, upper / lower, home bodyweight, dumbbell only. IDs are stable; texts are string resources. */
val JELAJAHI_SHELF: List<JelajahiTemplate> = listOf(
    JelajahiTemplate("full_body_gym", EquipmentSetting.GYM, 3, 45),
    JelajahiTemplate("upper_lower_gym", EquipmentSetting.GYM, 4, 45),
    JelajahiTemplate("home_bodyweight", EquipmentSetting.HOME_BW, 3, 45),
    JelajahiTemplate("home_dumbbell", EquipmentSetting.HOME_DB, 3, 45),
)
