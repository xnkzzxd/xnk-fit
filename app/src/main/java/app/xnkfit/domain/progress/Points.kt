package app.xnkfit.domain.progress

import app.xnkfit.domain.rules.PointSource
import app.xnkfit.domain.rules.R_PTS_ACT_DAILY_CAP
import app.xnkfit.domain.rules.R_PTS_ACT_MINUTES
import app.xnkfit.domain.rules.R_PTS_FOOD_MIN_SLOTS
import app.xnkfit.domain.rules.R_REM_HOURS_TIMBANG_ISO_DAY
import app.xnkfit.domain.rules.loggedWeek
import java.time.DayOfWeek
import java.time.LocalDate

/** R_PTS_MEASURE: the weekly weigh-in day, Sunday by default (R_REM_HOURS). */
val R_PTS_MEASURE_DEFAULT_DAY: DayOfWeek = DayOfWeek.of(R_REM_HOURS_TIMBANG_ISO_DAY)

/** R_PTS_MEASURE: only weight counts, never another measure type. */
const val R_PTS_MEASURE_TYPE = "weight"

/**
 * One reward. [key] is stable and unique (`workout:<id>`, `foodDay:<date>` ...), so writing the same event twice
 * is a no-op and a recount never doubles points. [kind] is the `points_event.kind` code (Design §3).
 */
data class PointEventSpec(val key: String, val at: Long, val kind: String, val points: Int)

/**
 * Everything that earns points, from the rows (Rules §7, PRD P-3). Nothing here reads a weight number
 * (R_PTS_NOWEIGHT): a weigh-in on the scheduled day pays [PointSource.MEASURE_ON_SCHEDULE] whatever it says.
 */
fun derivePointEvents(
    data: ProgressData,
    clock: Clock,
    weighInDay: DayOfWeek = R_PTS_MEASURE_DEFAULT_DAY,
): List<PointEventSpec> {
    val zone = clock.zone
    val out = ArrayList<PointEventSpec>()

    // R_PTS_WORKOUT: each finished workout.
    data.workouts.forEach {
        out += PointEventSpec("workout:${it.id}", it.endedAt, "workout", PointSource.WORKOUT_FINISHED.points)
    }

    // R_PTS_ACT: 30 activity minutes in a day, at most R_PTS_ACT_DAILY_CAP award per day, any type or effort.
    data.activities.groupBy { it.at.toLocalDate(zone) }.forEach { (day, rows) ->
        val awards = (rows.sumOf { it.minutes } / R_PTS_ACT_MINUTES).coerceAtMost(R_PTS_ACT_DAILY_CAP)
        if (awards > 0) {
            out += PointEventSpec("activity:$day", rows.maxOf { it.at }, "activity", PointSource.ACTIVITY_30_MIN.points * awards)
        }
    }

    // R_PTS_FOOD: a day with food logged in at least 3 slots.
    data.foodLogs.groupBy { it.day }.forEach { (day, rows) ->
        val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@forEach
        if (rows.map { it.slot }.toSet().size >= R_PTS_FOOD_MIN_SLOTS) {
            out += PointEventSpec("foodDay:$day", date.startOfDayMs(zone), "foodDay", PointSource.FOOD_DAY.points)
        }
    }

    // R_PTS_MEASURE: the first weight measure of a week taken on the scheduled weekday.
    data.measures
        .filter { it.type == R_PTS_MEASURE_TYPE && it.takenAt.toLocalDate(zone).dayOfWeek == weighInDay }
        .groupBy { it.takenAt.toLocalDate(zone).weekStart() }
        .forEach { (week, rows) ->
            out += PointEventSpec("measure:$week", rows.minOf { it.takenAt }, "measure", PointSource.MEASURE_ON_SCHEDULE.points)
        }

    // R_PTS_STREAK: each week that counts toward the streak (R_STREAK).
    val days = activeDays(data, zone)
    computeStreak(days, clock.today).weekStarts.forEach { week ->
        out += PointEventSpec("streakWeek:$week", week.startOfDayMs(zone), "streakWeek", PointSource.STREAK_WEEK.points)
    }

    // R_PTS_TARGET: a week with food logged on at least 5 days. Counting days only, never the amounts (PR-5).
    data.foodLogs.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        .distinct()
        .groupBy { it.weekStart() }
        .forEach { (week, daysLogged) ->
            if (loggedWeek(daysLogged.size)) {
                out += PointEventSpec("loggedWeek:$week", week.startOfDayMs(zone), "loggedWeek", PointSource.LOGGED_WEEK.points)
            }
        }
    return out
}
