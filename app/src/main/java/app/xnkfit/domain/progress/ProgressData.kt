package app.xnkfit.domain.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Rows the progress rules read (PRD P-3…P-5, Rules §7). Plain data, so the rules run on the JVM with fixtures.
// There is deliberately no weight value in any row: points and badges cannot depend on a weight number (R_PTS_NOWEIGHT).

/** R_STREAK: a week runs Monday to Sunday, local time (content/badges.json metrics). */
val R_STREAK_WEEK_START: DayOfWeek = DayOfWeek.MONDAY

/** R_WHO_MIN: one vigorous minute counts as two moderate minutes (S13). */
const val R_WHO_MIN_VIGOROUS_FACTOR = 2

const val R_WHO_MIN_VIGOROUS = "vigorous"

data class FinishedWorkout(val id: String, val endedAt: Long)

/** A set the user marked done. [kind] and [form] are the Room codes (`normal`; `rapi`, `ragu`). */
data class DoneSet(
    val workoutId: String,
    val exerciseId: String,
    val kind: String,
    val weightKg: Double?,
    val reps: Int?,
    val bandLevel: Int?,
    val form: String?,
    val doneAt: Long,
)

data class ActivityRow(val at: Long, val minutes: Int, val effort: String)

/** [day] is the local ISO date `yyyy-MM-dd`; the amounts eaten are never read. */
data class FoodLogRow(val day: String, val slot: String)

/** A measure taken. Only the type and the time: the value is never an input (R_PTS_NOWEIGHT). */
data class MeasureRow(val type: String, val takenAt: Long)

data class PlanRow(val weekStart: String, val day: Int)

data class PointRow(val kind: String, val points: Int)

data class ProgressData(
    val workouts: List<FinishedWorkout> = emptyList(),
    val sets: List<DoneSet> = emptyList(),
    val activities: List<ActivityRow> = emptyList(),
    val foodLogs: List<FoodLogRow> = emptyList(),
    val measures: List<MeasureRow> = emptyList(),
    val plans: List<PlanRow> = emptyList(),
    val photoCount: Int = 0,
    /** The stored `points_event` rows. */
    val points: List<PointRow> = emptyList(),
)

/** Context for date maths: the phone's zone and today's date in it. */
data class Clock(val zone: ZoneId, val today: LocalDate)

internal fun Long.toLocalDate(zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

internal fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(R_STREAK_WEEK_START))

internal fun LocalDate.startOfDayMs(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** Days with a finished workout, an activity row or at least one food entry (R_STREAK, PRD P-4). */
fun activeDays(data: ProgressData, zone: ZoneId): Set<LocalDate> {
    val days = HashSet<LocalDate>()
    data.workouts.mapTo(days) { it.endedAt.toLocalDate(zone) }
    data.activities.mapTo(days) { it.at.toLocalDate(zone) }
    data.foodLogs.mapNotNullTo(days) { runCatching { LocalDate.parse(it.day) }.getOrNull() }
    return days
}
