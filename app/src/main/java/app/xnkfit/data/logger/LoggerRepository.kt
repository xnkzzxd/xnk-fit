package app.xnkfit.data.logger

import androidx.room.withTransaction
import app.xnkfit.data.db.ExerciseEntity
import app.xnkfit.data.db.ProgramDayEntity
import app.xnkfit.data.db.ProgramEntity
import app.xnkfit.data.db.ProgramItemEntity
import app.xnkfit.data.db.WorkoutEntity
import app.xnkfit.data.db.WorkoutSetEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.logger.LoggerMode
import app.xnkfit.domain.logger.LoggerSet
import app.xnkfit.domain.logger.SetKind
import app.xnkfit.domain.logger.Unfinished
import app.xnkfit.domain.logger.closeTime
import app.xnkfit.domain.logger.measureOf
import app.xnkfit.domain.logger.prefill
import app.xnkfit.domain.logger.unfinishedAction
import app.xnkfit.domain.training.DayName
import app.xnkfit.domain.training.Equipment
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Exercise
import app.xnkfit.domain.training.FormRating
import app.xnkfit.domain.training.ItemRole
import app.xnkfit.domain.training.ItemTarget
import app.xnkfit.domain.training.LoggedSession
import app.xnkfit.domain.training.LoggedSet
import app.xnkfit.domain.training.MuscleGroup
import app.xnkfit.domain.training.Pattern
import app.xnkfit.domain.training.Program
import app.xnkfit.domain.training.ProgramDay
import app.xnkfit.domain.training.ProgramItem
import app.xnkfit.domain.training.TrainingHistory
import app.xnkfit.domain.training.TrainingLevel
import app.xnkfit.domain.training.number
import app.xnkfit.domain.training.sessionTargets
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Set rows are ordered by `position = slot × SLOT_SPAN + set number`, so a set can be added to any exercise. */
internal const val SLOT_SPAN = 100

/** The program day the next Dipandu workout follows, with the B-06 suggestions for it. */
data class PlannedDay(
    val programDayId: String,
    val dayName: DayName?,
    val day: ProgramDay,
    val level: TrainingLevel,
    val targets: List<ItemTarget>,
    /** Superset group per exercise from `program_item.supersetGroup`. */
    val supersets: Map<String, Int?> = emptyMap(),
)

/** A loaded workout: the row and its sets in logger form. */
data class LoadedWorkout(val workout: WorkoutEntity, val sets: List<LoggerSet>)

/**
 * Logger storage (PR-2, NF-2, Design §4). Every public write is one Room transaction that ends on disk before it
 * returns: no queue, no draft, no batching. `endedAt == null` marks an unfinished workout.
 */
class LoggerRepository(
    private val db: XnkDatabase,
    private val now: () -> Long,
    private val newId: () -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val workouts get() = db.workoutDao()
    private val programs get() = db.programDao()

    @Volatile private var libraryCache: List<Pair<ExerciseEntity, Exercise>>? = null

    /** Bundled exercises as the domain sees them, with the display name (Indonesian, English fallback). */
    suspend fun library(): List<Pair<ExerciseEntity, Exercise>> =
        libraryCache ?: programs.exercises().mapNotNull { e -> toDomain(e)?.let { e to it } }
            .also { if (it.isNotEmpty()) libraryCache = it }

    /** What the user has: the saved equipment row, else the active program's setting, else null (no filter). */
    suspend fun equipment(): Equipment? {
        val row = db.profileDao().equipment().firstOrNull()
        val setting = row?.setting ?: programs.active()?.setting ?: return null
        val s = EquipmentSetting.entries.firstOrNull { it.code == setting } ?: return null
        return Equipment.of(s, row?.let { strings(it.itemsJson) }.orEmpty())
    }

    suspend fun activeLevel(): TrainingLevel? = programs.active()?.let { levelOf(it.level) }

    suspend fun history(): TrainingHistory {
        val z = zone()
        val sessions = workouts.finishedSetHistory().groupBy { it.startedAt }.map { (start, rows) ->
            LoggedSession(
                epochDay = Instant.ofEpochMilli(start).atZone(z).toLocalDate().toEpochDay(),
                sets = rows.map {
                    LoggedSet(it.exerciseId, it.weightKg, it.reps, it.bandLevel, formOf(it.form))
                },
            )
        }
        return TrainingHistory(sessions)
    }

    /** Next day of the active program: the one after the day of the last finished workout (rotation). */
    suspend fun nextPlannedDay(forcedDayId: String? = null): PlannedDay? {
        // B-08: "Mulai" on a day of the Latihan list, or on an own routine, starts that day instead of the rotation's next one.
        val forced = forcedDayId?.let { programs.dayById(it) }
        val program = forced?.let { programs.programById(it.programId) } ?: programs.active() ?: return null
        val days = programs.days(program.id)
        if (days.isEmpty()) return null
        val lastId = workouts.lastFinishedProgramDayId()
        val lastIndex = days.indexOfFirst { it.id == lastId }
        val dayRow = forced ?: days[(lastIndex + 1).mod(days.size)]
        val lib = library().map { it.second }.associateBy { it.id }
        val items = programs.items(dayRow.id).mapNotNull { item ->
            val ex = lib[item.exerciseId] ?: return@mapNotNull null
            ProgramItem(
                exerciseId = item.exerciseId, pattern = ex.pattern, role = ItemRole.MAIN, sets = item.setsTarget,
                repMin = item.repMin, repMax = item.repMax, restSec = item.restSec, loadKind = ex.loadKind,
                usesBand = ex.usesBand, whyHint = ex.hintId,
            )
        }
        val day = ProgramDay(dayRow.position, DayName.entries.firstOrNull { it.name == dayRow.name } ?: DayName.FULL_A, dayRow.estMinutes, items)
        val level = levelOf(program.level)
        val today = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toEpochDay()
        val targets = sessionTargets(day, level, history(), today, lib.values.toList())
        val supersets = programs.items(dayRow.id).associate { it.exerciseId to it.supersetGroup }
        return PlannedDay(dayRow.id, DayName.entries.firstOrNull { it.name == dayRow.name }, day, level, targets, supersets)
    }

    /**
     * Design §4 step 1: opening a workout inserts the row with `endedAt = null`. A Dipandu or Bebas workout that follows
     * a program day also writes the planned sets (not done, prefilled) in the same transaction, so a kill one tap
     * later already finds the whole plan on disk.
     */
    suspend fun start(mode: LoggerMode, plan: PlannedDay?): String {
        val t = now()
        val id = newId()
        val rows = ArrayList<WorkoutSetEntity>()
        if (plan != null) {
            plan.day.items.forEachIndexed { i, item ->
                val target = plan.targets.getOrNull(i)
                val measure = measureOf(item.exerciseId, item.loadKind, item.usesBand)
                val previous = previous(item.exerciseId)
                repeat(target?.sets ?: item.sets) { n ->
                    val p = prefill(measure, n + 1, target?.loadKg, target?.bandLevel, item.repMin, previous)
                    rows += WorkoutSetEntity(
                        id = newId(), createdAt = t, updatedAt = t, workoutId = id, exerciseId = item.exerciseId,
                        position = i * SLOT_SPAN + n + 1, weightKg = p.weightKg, reps = p.reps, bandLevel = p.bandLevel,
                        holdSec = p.holdSec, supersetGroup = plan.supersets[item.exerciseId], restSec = item.restSec,
                    )
                }
            }
        }
        db.withTransaction {
            workouts.startWorkout(
                WorkoutEntity(id, t, t, programDayId = plan?.programDayId, mode = mode.code, startedAt = t),
            )
            rows.forEach { workouts.insertSetRow(it) }
        }
        return id
    }

    suspend fun load(workoutId: String): LoadedWorkout? {
        val w = workouts.workout(workoutId) ?: return null
        return LoadedWorkout(w, workouts.sets(workoutId).map(::toLogger))
    }

    /** One tap = one transaction (Design §4 step 2, §10 budget 50 ms). */
    suspend fun saveSet(workoutId: String, set: LoggerSet, restEndsAt: Long?) =
        workouts.saveSet(toEntity(workoutId, set), now(), restEndsAt)

    suspend fun saveSets(workoutId: String, sets: List<LoggerSet>, restEndsAt: Long?) =
        workouts.saveSets(sets.map { toEntity(workoutId, it) }, workoutId, now(), restEndsAt)

    suspend fun deleteSets(workoutId: String, ids: List<String>, restEndsAt: Long?) =
        workouts.deleteSets(ids, workoutId, now(), restEndsAt)

    suspend fun setRest(workoutId: String, restEndsAt: Long?) = workouts.touch(workoutId, now(), restEndsAt)

    suspend fun setMode(workoutId: String, mode: LoggerMode) = workouts.setMode(workoutId, mode.code, now())

    /** Design §4 step 3. Points are counted from finished workouts by B-12's recount, so only `endedAt` is set here. */
    suspend fun finish(workoutId: String) = workouts.finish(workoutId, now(), null, null)

    /** L-13: feeling 1–5 and a note on the summary; both optional. */
    suspend fun setFeelingNote(workoutId: String, feeling: Int?, note: String?) =
        workouts.setFeelingNote(workoutId, feeling?.takeIf { it in 1..5 }, note?.trim()?.takeIf { it.isNotEmpty() }, now())

    suspend fun cancel(workoutId: String) = workouts.deleteWorkout(workoutId)

    /** Done sets of the last finished workout with this exercise ("sebelumnya"). */
    suspend fun previous(exerciseId: String): List<LoggerSet> = workouts.previousSets(exerciseId).map(::toLogger)

    suspend fun hasAnyWorkout(): Boolean = workouts.workoutCount() > 0

    /**
     * Design §4 step 4, run at launch: unfinished workouts older than yesterday are closed at their last done set
     * (no note text is invented; the summary shows none); the newest unfinished one from today or yesterday is
     * returned for "Lanjutkan latihan?".
     */
    suspend fun recoverUnfinished(): WorkoutEntity? {
        val z = zone()
        val today = Instant.ofEpochMilli(now()).atZone(z).toLocalDate().toEpochDay()
        val open = workouts.unfinishedSince(Long.MIN_VALUE)
        var resume: WorkoutEntity? = null
        for (w in open) {
            val day = Instant.ofEpochMilli(w.startedAt).atZone(z).toLocalDate().toEpochDay()
            when (unfinishedAction(day, today)) {
                Unfinished.RESUME -> if (resume == null) resume = w
                Unfinished.CLOSE -> workouts.finish(w.id, closeTime(w.startedAt, workouts.lastDoneAt(w.id)), null, null)
            }
        }
        return resume
    }

    /** Stores a generated program as the active one (used by tests today; the Latihan screen in B-08). */
    suspend fun saveProgram(program: Program, source: String = "auto"): String {
        val t = now()
        val pid = newId()
        db.withTransaction {
            programs.deactivateAll(t)
            programs.insertProgram(ProgramEntity(pid, t, t, source, program.setting.code, program.rulesVersion, true, program.level.number))
            program.days.forEach { d ->
                val did = newId()
                programs.insertDays(listOf(ProgramDayEntity(did, t, t, pid, d.position, d.name.name, d.estMinutes)))
                programs.insertItems(
                    d.items.mapIndexed { i, it ->
                        ProgramItemEntity(newId(), t, t, did, it.exerciseId, i, it.sets, it.repMin, it.repMax, it.restSec)
                    },
                )
            }
        }
        return pid
    }

    private fun toEntity(workoutId: String, s: LoggerSet): WorkoutSetEntity {
        val t = now()
        return WorkoutSetEntity(
            id = s.id, createdAt = t, updatedAt = t, workoutId = workoutId, exerciseId = s.exerciseId,
            position = s.slot * SLOT_SPAN + s.index, kind = s.kind.code, weightKg = s.weightKg, reps = s.reps,
            bandLevel = s.bandLevel, form = s.form?.name?.lowercase(), done = s.done, doneAt = s.doneAt,
            holdSec = s.holdSec, supersetGroup = s.supersetGroup, restSec = s.restSec,
        )
    }

    private fun toLogger(e: WorkoutSetEntity) = LoggerSet(
        id = e.id, slot = e.position / SLOT_SPAN, index = e.position % SLOT_SPAN, exerciseId = e.exerciseId,
        kind = SetKind.of(e.kind), weightKg = e.weightKg, reps = e.reps, bandLevel = e.bandLevel, holdSec = e.holdSec,
        form = formOf(e.form), done = e.done, doneAt = e.doneAt, supersetGroup = e.supersetGroup, restSec = e.restSec ?: 0,
    )

    companion object {
        fun formOf(code: String?): FormRating? = FormRating.entries.firstOrNull { it.name.equals(code, ignoreCase = true) }

        fun levelOf(n: Int): TrainingLevel = TrainingLevel.entries.firstOrNull { it.number == n } ?: TrainingLevel.L1

        private fun strings(json: String): Set<String> =
            runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet())

        private fun groups(a: JSONArray?): Set<MuscleGroup> = if (a == null) emptySet() else
            (0 until a.length()).mapNotNull { i -> MuscleGroup.entries.firstOrNull { it.name == a.getString(i) } }.toSet()

        /** Room row → generator entry; a row whose pattern or level the code does not know is left out. */
        fun toDomain(e: ExerciseEntity): Exercise? {
            val pattern = Pattern.entries.firstOrNull { it.name == e.pattern } ?: return null
            val level = e.level.toIntOrNull() ?: return null
            val muscles = runCatching { JSONObject(e.musclesJson) }.getOrNull()
            return Exercise(
                id = e.id, pattern = pattern, level = level, settings = strings(e.settingsJson),
                equipment = strings(e.equipmentJson), main = groups(muscles?.optJSONArray("main")),
                helper = groups(muscles?.optJSONArray("helper")), variantOf = e.variantOf, hintId = e.hintId,
            )
        }
    }
}
