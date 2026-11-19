package app.xnkfit.data.program

import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.domain.latihan.weeklyLevels
import app.xnkfit.domain.latihan.weeklySetsDone
import app.xnkfit.domain.training.MuscleGroup
import app.xnkfit.domain.training.TrainingLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Weekly muscle map input (L-11): working sets per group this calendar week and the tone of each. */
data class WeekMap(val sets: Map<MuscleGroup, Double>, val levels: Map<MuscleGroup, Int>)

class WeekMapRepository(
    private val db: XnkDatabase,
    private val logger: LoggerRepository,
    private val now: () -> Long,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Done sets of workouts started since Monday; the level scale is the active program's level (level 1 when none). */
    suspend fun thisWeek(): WeekMap {
        val z = zone()
        val monday = Instant.ofEpochMilli(now()).atZone(z).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = monday.atStartOfDay(z).toInstant().toEpochMilli()
        val done = db.workoutDao().finishedSetHistory().filter { it.startedAt >= from }.groupingBy { it.exerciseId }.eachCount()
        val sets = weeklySetsDone(done, logger.library().map { it.second })
        val level = logger.activeLevel() ?: TrainingLevel.L1
        return WeekMap(sets, weeklyLevels(sets, level))
    }
}
