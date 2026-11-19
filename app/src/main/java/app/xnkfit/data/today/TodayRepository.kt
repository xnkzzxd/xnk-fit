package app.xnkfit.data.today

import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.today.ProgramDayInfo
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The last finished workout, for "which day is next" and "already trained today". */
data class LastWorkout(val programDayId: String?, val endedAt: Long)

/** An unfinished session that is still recent enough to offer "Lanjutkan" (Design §4). */
data class OpenWorkout(val programDayId: String?)

/**
 * Reads for Hari ini that no other repository has. Everything else (food log, level, streak, targets) comes from the
 * repositories of the other screens. Read-only: Hari ini writes nothing but the target basis (DataStore).
 */
class TodayRepository(
    private val db: XnkDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun profile(): ProfileEntity? = db.profileDao().get()

    /** Weights oldest first: (takenAt, kg). */
    suspend fun weights(): List<Pair<Long, Double>> =
        db.profileDao().observeMeasures(WEIGHT).first().map { it.takenAt to it.value }

    /** The active program's days in order, or empty when there is no program (H-3). */
    suspend fun programDays(): List<ProgramDayInfo> {
        val program = db.programDao().observeActive().first() ?: return emptyList()
        return db.programDao().days(program.id).map { d ->
            ProgramDayInfo(d.id, d.name, db.programDao().items(d.id).size, d.estMinutes)
        }
    }

    suspend fun lastWorkout(): LastWorkout? =
        db.workoutDao().observeFinished(1).first().firstOrNull()?.let { LastWorkout(it.programDayId, it.endedAt ?: it.startedAt) }

    suspend fun openWorkout(): OpenWorkout? =
        db.workoutDao().unfinishedSince(now() - RESUME_WINDOW_MS).firstOrNull()?.let { OpenWorkout(it.programDayId) }

    /** (effort, minutes) of the activities logged this calendar week, Monday first (R_WHO_MIN). */
    suspend fun weekActivities(today: LocalDate): List<Pair<String, Int>> {
        val z = zone()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = monday.atStartOfDay(z).toInstant().toEpochMilli()
        val to = monday.plusDays(DAYS_IN_WEEK).atStartOfDay(z).toInstant().toEpochMilli()
        return db.workoutDao().activitiesBetween(from, to).map { it.effort to it.minutes }
    }

    private companion object {
        const val WEIGHT = "weight" // text-ok: stored code
        const val DAYS_IN_WEEK = 7L

        /** A session left open longer than this is not offered for resume here (the logger decides what to do with it). */
        const val RESUME_WINDOW_MS = 12L * 60 * 60 * 1000
    }
}
