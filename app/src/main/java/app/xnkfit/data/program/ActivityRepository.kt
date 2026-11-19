package app.xnkfit.data.program

import app.xnkfit.data.db.ActivityEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.latihan.ActivityChoice
import app.xnkfit.domain.latihan.Effort
import app.xnkfit.domain.latihan.LoggedActivity
import app.xnkfit.domain.latihan.WeekActivity
import app.xnkfit.domain.latihan.kcalFor
import app.xnkfit.domain.latihan.validMinutes
import app.xnkfit.domain.latihan.weekActivity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** This week's activity rows and their moderate-equivalent total (L-14). */
data class ActivityWeekView(val rows: List<LoggedActivity>, val week: WeekActivity)

/** Activity log (L-14): kcal is computed once from the MET table and stored (Design §3), never added to the food target. */
class ActivityRepository(
    private val db: XnkDatabase,
    private val now: () -> Long,
    private val newId: () -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val workouts get() = db.workoutDao()

    /** Returns false for a typo (minutes outside 1–600); nothing is written then. */
    suspend fun add(choice: ActivityChoice, effort: Effort, minutes: Int): Boolean {
        if (!validMinutes(minutes)) return false
        val t = now()
        val weight = db.profileDao().latestMeasure("weight")?.value // text-ok: stored code
        workouts.upsertActivity(
            ActivityEntity(newId(), t, t, choice.code, minutes, effort.code, kcalFor(choice, effort, weight, minutes), t),
        )
        return true
    }

    suspend fun delete(id: String) = workouts.deleteActivity(id)

    /** Rows of the calendar week that holds [day], Monday first, newest last. */
    suspend fun week(day: LocalDate = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()): ActivityWeekView {
        val z = zone()
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = monday.atStartOfDay(z).toInstant().toEpochMilli()
        val to = monday.plusDays(DAYS_IN_WEEK).atStartOfDay(z).toInstant().toEpochMilli()
        val rows = workouts.activitiesBetween(from, to).map { LoggedActivity(it.id, it.type, it.minutes, it.effort, it.kcal, it.at) }
        return ActivityWeekView(rows, weekActivity(rows))
    }

    private companion object { const val DAYS_IN_WEEK = 7L }
}
