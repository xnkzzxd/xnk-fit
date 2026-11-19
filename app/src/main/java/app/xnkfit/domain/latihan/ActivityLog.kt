package app.xnkfit.domain.latihan

import app.xnkfit.domain.rules.ActivityType
import app.xnkfit.domain.rules.R_WHO_MIN_TARGET
import app.xnkfit.domain.rules.R_WHO_MIN_UPPER
import app.xnkfit.domain.rules.activityKcal
import app.xnkfit.domain.rules.moderateEquivalentMinutes

// Activity log (L-14, Rules §4, Content §7). Pure Kotlin.

/** Effort as stored in `activity.effort` (Design §3). Light is stored and shown but does not count toward the weekly minutes. */
enum class Effort(val code: String) {
    RINGAN("light"), SEDANG("moderate"), BERAT("vigorous");

    companion object { fun of(code: String?): Effort? = entries.firstOrNull { it.code == code } }
}

/**
 * The choices of the activity sheet (Content §7). `lain` ("Lainnya") takes the MET of the lowest row, walking, so the
 * number never overstates the effort (Decisions B-08).
 */
enum class ActivityChoice(val code: String, val met: ActivityType) {
    JALAN("jalan", ActivityType.JALAN),
    LARI("lari", ActivityType.LARI),
    SEPEDA("sepeda", ActivityType.SEPEDA),
    RENANG("renang", ActivityType.RENANG),
    ANGKAT("angkat", ActivityType.ANGKAT),
    SENAM("senam", ActivityType.SENAM),
    BULUTANGKIS("bulutangkis", ActivityType.BULUTANGKIS),
    SEPAK("sepak", ActivityType.SEPAK),
    LAIN("lain", ActivityType.JALAN),
    ;

    companion object { fun of(code: String?): ActivityChoice? = entries.firstOrNull { it.code == code } }
}

/**
 * R_MET: kcal = MET × kg × hours, from the published value for the type and effort (S12). Light effort has no published value
 * in the table, so it gets none (0): nothing is invented. Shown only, never added to the target (M-1).
 */
fun kcalFor(choice: ActivityChoice, effort: Effort, weightKg: Double?, minutes: Int): Double =
    if (effort == Effort.RINGAN || weightKg == null) 0.0
    else activityKcal(choice.met, effort == Effort.BERAT, weightKg, minutes)

/** A logged activity as the week summary needs it. */
data class LoggedActivity(val id: String, val code: String, val minutes: Int, val effort: String, val kcal: Double, val at: Long)

/** Minutes of the week against R_WHO_MIN: moderate-equivalent, vigorous counted twice (L-14), light not counted (B-11). */
data class WeekActivity(
    val counted: Int,
    val target: Int = R_WHO_MIN_TARGET,
    val upper: Int = R_WHO_MIN_UPPER,
) {
    /** Progress toward the 150-minute target, 0..1. */
    val fraction: Float get() = (counted.toFloat() / target).coerceIn(0f, 1f)

    /** The note about the 300-minute upper range appears only once it is reached (the "batas 300 menit"). */
    val atUpper: Boolean get() = counted >= upper
}

fun weekActivity(rows: List<LoggedActivity>): WeekActivity = WeekActivity(
    moderateEquivalentMinutes(
        rows.filter { it.effort == Effort.SEDANG.code }.sumOf { it.minutes },
        rows.filter { it.effort == Effort.BERAT.code }.sumOf { it.minutes },
    ),
)

/** Typo guard for minutes (👤): one entry is 1–600 minutes. */
const val R_ACT_MINUTES_MAX = 600
fun validMinutes(m: Int): Boolean = m in 1..R_ACT_MINUTES_MAX
