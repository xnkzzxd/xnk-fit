package app.xnkfit.data.share

import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.progress.ProgressRepository
import app.xnkfit.domain.progress.countPersonalRecords
import app.xnkfit.domain.share.CardFacts
import app.xnkfit.domain.share.ConsistencyFacts
import app.xnkfit.domain.share.FoodFacts
import app.xnkfit.domain.share.LiftFact
import app.xnkfit.domain.share.ProgressFacts
import app.xnkfit.domain.share.R_SHARE_DATA_FOOD_WINDOW_DAYS
import app.xnkfit.domain.share.WorkoutFacts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Reads what the cards say from the phone's own tables (PV-1). Nothing here touches the network. */
class ShareFactsSource(
    private val db: XnkDatabase,
    private val progress: ProgressRepository,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val dao get() = db.progressDao()

    suspend fun load(): CardFacts {
        val summary = progress.summary()
        return CardFacts(
            workout = workout(),
            progress = progressFacts(),
            consistency = ConsistencyFacts(summary.streak.current, summary.level.level, summary.finishedWorkouts)
                .takeIf { summary.finishedWorkouts > 0 || summary.streak.current > 0 || summary.points > 0 },
            food = FoodFacts(foodDays()),
        )
    }

    private suspend fun workout(): WorkoutFacts? {
        val w = dao.lastFinishedWorkout() ?: return null
        val end = w.endedAt ?: return null
        val all = dao.doneSets()
        // Records in this workout = records counted up to its end minus records counted before it began (later sets never change earlier ones).
        val records = countPersonalRecords(all.filter { it.doneAt <= end }) - countPersonalRecords(all.filter { it.doneAt < w.startedAt })
        val sets = db.workoutDao().sets(w.id).filter { it.done && it.reps != null }
        val top = sets.maxWithOrNull(compareBy({ it.weightKg ?: -1.0 }, { it.reps ?: 0 }))
        return WorkoutFacts(
            day = Instant.ofEpochMilli(end).atZone(zone()).toLocalDate(),
            title = w.programDayId?.let { dao.programDayName(it) },
            minutes = ((end - w.startedAt) / MS_PER_MINUTE).toInt(),
            records = records.coerceAtLeast(0),
            topLift = top?.let { LiftFact(dao.exerciseName(it.exerciseId) ?: it.exerciseId, it.weightKg, it.reps) },
        )
    }

    private suspend fun progressFacts(): ProgressFacts? {
        val waist = dao.measures("waist") // text-ok: stored code
        val weight = dao.measures("weight") // text-ok: stored code
        val times = (waist + weight).map { it.takenAt }
        if (times.isEmpty()) return null
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(times.min()).atZone(zone()).toLocalDate(),
            Instant.ofEpochMilli(times.max()).atZone(zone()).toLocalDate(),
        ).toInt()
        fun delta(rows: List<app.xnkfit.data.db.MeasureEntity>) = if (rows.size >= 2) rows.last().value - rows.first().value else null
        return ProgressFacts(delta(waist)?.let { Math.round(it * TENTH) / TENTH }, delta(weight)?.let { Math.round(it * TENTH) / TENTH }, days)
    }

    private suspend fun foodDays(): Int {
        val from = today().minusDays((R_SHARE_DATA_FOOD_WINDOW_DAYS - 1).toLong())
        return dao.foodLogRows().mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
            .filter { !it.isBefore(from) && !it.isAfter(today()) }.toSet().size
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val TENTH = 10.0
    }
}
