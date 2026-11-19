package app.xnkfit.domain.progress

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The metrics `content/badges.json` names. Each one is a deterministic count from the Room tables,
 * defined in that file. A metric the app does not know is skipped when the file is read, never guessed.
 */
enum class BadgeMetric(val key: String) {
    FINISHED_WORKOUTS("finishedWorkouts"),
    PERSONAL_RECORDS("personalRecords"),
    STREAK_WEEKS("streakWeeks"),
    ACTIVITY_MINUTES_WEEK("activityMinutesWeek"),
    FOOD_DAYS("foodDays"),
    LOGGED_WEEKS("loggedWeeks"),
    SCHEDULED_MEASURES("scheduledMeasures"),
    PHOTOS("photos"),
    RETURN_AFTER_BREAK("returnAfterBreak"),
    LEVEL("level"),
    PLANNED_DAYS("plannedDays"),
    ;

    companion object {
        fun fromKey(key: String): BadgeMetric? = entries.firstOrNull { it.key == key }
    }
}

/** One badge condition: [metric] reaches [atLeast] (the file only uses `>=`). */
data class BadgeDefinition(val code: String, val order: Int, val metric: BadgeMetric, val atLeast: Int)

/** `workout_set.kind` and `form` values that can make a record (badges.json `personalRecords`). */
private const val prKind = "normal"
private val prForms = setOf("rapi", "ragu")

/**
 * `personalRecords`: done normal sets with form rapi or ragu. For one exercise, by `doneAt`, a set is a record when
 * it beats every earlier set: more weight, or the same weight and more reps; a bodyweight set (no weight, no band)
 * with more reps than every earlier bodyweight set. The first set is the baseline, not a record. At most one
 * record per exercise per workout. Band sets have no record rule in the file and never count.
 */
fun countPersonalRecords(sets: List<DoneSet>): Int {
    val eligible = sets.filter { it.kind == prKind && it.form in prForms && it.reps != null }
    val records = HashSet<Pair<String, String>>()
    eligible.groupBy { it.exerciseId }.forEach { (exercise, rows) ->
        var bestWeighted: Pair<Double, Int>? = null
        var bestBodyweight: Int? = null
        rows.sortedBy { it.doneAt }.forEach { s ->
            val reps = s.reps ?: return@forEach
            when {
                s.weightKg != null -> {
                    val best = bestWeighted
                    val candidate = s.weightKg to reps
                    if (best == null) {
                        bestWeighted = candidate
                    } else if (candidate.first > best.first || (candidate.first == best.first && candidate.second > best.second)) {
                        records += exercise to s.workoutId
                        bestWeighted = candidate
                    }
                }
                s.bandLevel == null -> {
                    val best = bestBodyweight
                    if (best == null) {
                        bestBodyweight = reps
                    } else if (reps > best) {
                        records += exercise to s.workoutId
                        bestBodyweight = reps
                    }
                }
            }
        }
    }
    return records.size
}

/** `activityMinutesWeek`: the best calendar week, vigorous minutes counted double (R_WHO_MIN). */
fun bestActivityWeekMinutes(activities: List<ActivityRow>, clock: Clock): Int =
    activities.groupBy { it.at.toLocalDate(clock.zone).weekStart() }.values.maxOfOrNull { rows ->
        rows.sumOf { if (it.effort == R_WHO_MIN_VIGOROUS) it.minutes * R_WHO_MIN_VIGOROUS_FACTOR else it.minutes }
    } ?: 0

/** `returnAfterBreak`: the longest gap in whole days between two finished workouts in a row. */
fun longestBreakDays(workouts: List<FinishedWorkout>): Int =
    workouts.map { it.endedAt }.sorted().zipWithNext { a, b ->
        Duration.between(Instant.ofEpochMilli(a), Instant.ofEpochMilli(b)).toDays().toInt()
    }.maxOrNull() ?: 0

/** The number a metric has right now, from the rows. */
fun metricValue(metric: BadgeMetric, data: ProgressData, clock: Clock): Int = when (metric) {
    BadgeMetric.FINISHED_WORKOUTS -> data.workouts.size
    BadgeMetric.PERSONAL_RECORDS -> countPersonalRecords(data.sets)
    BadgeMetric.STREAK_WEEKS -> computeStreak(activeDays(data, clock.zone), clock.today).best
    BadgeMetric.ACTIVITY_MINUTES_WEEK -> bestActivityWeekMinutes(data.activities, clock)
    BadgeMetric.FOOD_DAYS -> data.foodLogs.map { it.day }.toSet().size
    BadgeMetric.LOGGED_WEEKS -> data.foodLogs.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        .distinct().groupBy { it.weekStart() }.count { (_, days) -> app.xnkfit.domain.rules.loggedWeek(days.size) }
    BadgeMetric.SCHEDULED_MEASURES -> data.points.count { it.kind == "measure" }
    BadgeMetric.PHOTOS -> data.photoCount
    BadgeMetric.RETURN_AFTER_BREAK -> longestBreakDays(data.workouts)
    BadgeMetric.LEVEL -> levelProgress(data.points.sumOf { it.points }).level
    BadgeMetric.PLANNED_DAYS -> data.plans.groupBy { it.weekStart }.values.maxOfOrNull { rows -> rows.map { it.day }.toSet().size } ?: 0
}

/**
 * Badges whose condition holds and that are not earned yet, in file order (PRD P-5). A badge is earned once and
 * never taken back: the caller stores `earned` and passes it in, and a lower metric later changes nothing.
 */
fun badgesToAward(
    definitions: List<BadgeDefinition>,
    data: ProgressData,
    clock: Clock,
    earned: Set<String>,
): List<BadgeDefinition> {
    val cache = HashMap<BadgeMetric, Int>()
    return definitions.sortedBy { it.order }.filter { d ->
        d.code !in earned && cache.getOrPut(d.metric) { metricValue(d.metric, data, clock) } >= d.atLeast
    }
}
