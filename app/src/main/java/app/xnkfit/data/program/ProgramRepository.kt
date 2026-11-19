package app.xnkfit.data.program

import androidx.room.withTransaction
import app.xnkfit.data.db.EquipmentEntity
import app.xnkfit.data.db.ProgramDayEntity
import app.xnkfit.data.db.ProgramEntity
import app.xnkfit.data.db.ProgramItemEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.food.readyFrom
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.domain.latihan.JelajahiTemplate
import app.xnkfit.domain.latihan.TrainingSetup
import app.xnkfit.domain.latihan.code
import app.xnkfit.domain.latihan.generatorMayReplace
import app.xnkfit.domain.latihan.programSourceOf
import app.xnkfit.domain.latihan.sourceAfterEdit
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.training.DayName
import app.xnkfit.domain.training.Equipment
import app.xnkfit.domain.training.ItemRole
import app.xnkfit.domain.training.ProgramDay
import app.xnkfit.domain.training.ProgramChange
import app.xnkfit.domain.training.ProgramItem
import app.xnkfit.domain.training.ReviewClock
import app.xnkfit.domain.training.ReviewResult
import app.xnkfit.domain.training.applyChanges
import app.xnkfit.domain.training.estMinutes
import app.xnkfit.domain.training.review
import app.xnkfit.domain.training.weeklySets
import app.xnkfit.domain.training.TrainingLevel
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.domain.training.GoalFocus
import app.xnkfit.domain.training.GenerateResult
import app.xnkfit.domain.training.MissingPattern
import app.xnkfit.domain.training.Program
import app.xnkfit.domain.training.ProgramSource
import app.xnkfit.domain.training.TrainingProfile
import app.xnkfit.domain.training.generate
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId

/** One exercise of a stored day, with its Indonesian name. */
data class ItemView(
    val id: String,
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int,
    val supersetGroup: Int?,
)

data class DayView(val id: String, val position: Int, val name: String, val dayName: DayName?, val estMinutes: Int, val items: List<ItemView>)

data class ProgramView(
    val id: String,
    val source: ProgramSource,
    val setting: String,
    val level: Int,
    val isActive: Boolean,
    val days: List<DayView>,
    val updatedAt: Long,
    val createdAt: Long,
)

sealed interface CreateResult {
    /** Safety gates did not pass: no program is written (PR-5, R_GATE_SCREEN). */
    data class Blocked(val gate: GateResult) : CreateResult

    data class Created(val programId: String, val missing: List<MissingPattern>, val active: Boolean) : CreateResult
}

/**
 * Programs on disk (Design §3: `program`, `program_day`, `program_item`). The generator (B-06) writes `auto` and `jelajahi`
 * rows; the user's edits write `own` rows. L-5: the generator deletes only `auto` rows and never touches an `own` one.
 */
class ProgramRepository(
    private val db: XnkDatabase,
    private val logger: LoggerRepository,
    private val now: () -> Long,
    private val newId: () -> String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val programs get() = db.programDao()

    suspend fun active(): ProgramView? = programs.active()?.let { view(it) }

    suspend fun all(): List<ProgramView> = programs.allPrograms().map { view(it) }

    suspend fun program(id: String): ProgramView? = programs.programById(id)?.let { view(it) }

    private suspend fun view(p: ProgramEntity): ProgramView {
        val names = logger.library().associate { it.first.id to it.first.nameId }
        val days = programs.days(p.id).map { d ->
            DayView(
                d.id, d.position, d.name, DayName.entries.firstOrNull { it.name == d.name }, d.estMinutes,
                programs.items(d.id).map { i ->
                    ItemView(i.id, i.exerciseId, names[i.exerciseId] ?: i.exerciseId, i.setsTarget, i.repMin, i.repMax, i.restSec, i.supersetGroup)
                },
            )
        }
        return ProgramView(p.id, programSourceOf(p.source), p.setting, p.level, p.isActive, days, p.updatedAt, p.createdAt)
    }

    /** The saved place: `equipment` row, else null. */
    suspend fun equipmentSetting(): EquipmentSetting? =
        db.profileDao().equipment().firstOrNull()?.let { r -> EquipmentSetting.entries.firstOrNull { it.code == r.setting } }

    private suspend fun saveEquipment(setup: TrainingSetup) {
        val t = now()
        val existing = db.profileDao().equipment().firstOrNull()
        db.profileDao().upsertEquipment(
            EquipmentEntity(
                existing?.id ?: newId(), existing?.createdAt ?: t, t, setup.setting.code,
                JSONArray(setup.customItems.sorted()).toString(),
            ),
        )
    }

    private suspend fun runGenerator(setup: TrainingSetup): GenerateResult {
        val gate = currentGate()
        val today = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toEpochDay()
        return generate(
            TrainingProfile(gate, setup.experience, setup.days, setup.minutes),
            Equipment.of(setup.setting, setup.customItems), setup.focus, logger.history(),
            logger.library().map { it.second }, today,
        )
    }

    /**
     * Builds the program the first training profile asks for and stores it. An `own` program is never replaced: when one
     * is active the new `auto` program is stored but stays inactive until the user picks it (L-5).
     */
    suspend fun createAuto(setup: TrainingSetup, source: ProgramSource = ProgramSource.AUTO): CreateResult {
        val result = runGenerator(setup)
        val program = when (result) {
            is GenerateResult.Blocked -> return CreateResult.Blocked(result.gate)
            is GenerateResult.Generated -> result.program
        }
        saveEquipment(setup)
        val t = now()
        val pid = newId()
        var makeActive = true
        db.withTransaction {
            val current = programs.active()
            // L-5: the generator never takes the place of an own program. Choosing a Jelajahi card is the user's own act.
            if (source == ProgramSource.AUTO && current != null && !generatorMayReplace(programSourceOf(current.source))) makeActive = false
            programs.deleteProgramsBySource(source.code) // only the rows of this source; `own` is never in this call
            if (makeActive) programs.deactivateAll(t)
            insert(pid, t, source, program, makeActive)
        }
        return CreateResult.Created(pid, program.missing, makeActive)
    }

    /** Which patterns the equipment cannot carry for this setup (L-3), without writing anything. */
    suspend fun missing(setup: TrainingSetup): List<MissingPattern> =
        (runGenerator(setup) as? GenerateResult.Generated)?.program?.missing.orEmpty()

    private suspend fun insert(pid: String, t: Long, source: ProgramSource, program: Program, active: Boolean) {
        programs.insertProgram(ProgramEntity(pid, t, t, source.code, program.setting.code, program.rulesVersion, active, program.level.ordinal + 1))
        program.days.forEach { d ->
            val did = newId()
            programs.insertDays(listOf(ProgramDayEntity(did, t, t, pid, d.position, d.name.name, d.estMinutes)))
            programs.insertItems(
                d.items.mapIndexed { i, it -> ProgramItemEntity(newId(), t, t, did, it.exerciseId, i, it.sets, it.repMin, it.repMax, it.restSec) },
            )
        }
    }

    /** Makes one stored program the active one. */
    suspend fun activate(programId: String) = db.withTransaction {
        val t = now()
        if (programs.programById(programId) == null) return@withTransaction
        programs.deactivateAll(t)
        programs.setActive(programId, t)
    }

    suspend fun source(programId: String): ProgramSource? = programs.programById(programId)?.let { programSourceOf(it.source) }

    // ---- own routines (L-5, L-9) ----

    /** One exercise as the editor holds it. */
    data class EditItem(val exerciseId: String, val sets: Int, val repMin: Int, val repMax: Int, val restSec: Int, val supersetGroup: Int? = null)

    data class EditDay(val name: String, val items: List<EditItem>)

    private suspend fun estimate(items: List<EditItem>): Int {
        val lib = logger.library().associate { it.second.id to it.second }
        return estMinutes(
            items.mapNotNull { i ->
                val ex = lib[i.exerciseId] ?: return@mapNotNull null
                ProgramItem(i.exerciseId, ex.pattern, ItemRole.MAIN, i.sets, i.repMin, i.repMax, i.restSec, ex.loadKind, ex.usesBand)
            },
        )
    }

    private suspend fun writeDays(programId: String, t: Long, days: List<EditDay>) {
        programs.deleteDays(programId)
        days.forEachIndexed { d, day ->
            val did = newId()
            programs.insertDays(listOf(ProgramDayEntity(did, t, t, programId, d, day.name, estimate(day.items))))
            programs.insertItems(
                day.items.mapIndexed { i, it ->
                    ProgramItemEntity(newId(), t, t, did, it.exerciseId, i, it.sets, it.repMin, it.repMax, it.restSec, it.supersetGroup)
                },
            )
        }
    }

    /**
     * L-5: the user's edit of a stored program. Whatever the source was, the program is `own` from this save on, so the
     * generator and the review never touch it again.
     */
    suspend fun saveEdit(programId: String, days: List<EditDay>) {
        val before = programs.programById(programId) ?: return
        db.withTransaction {
            val t = now()
            writeDays(programId, t, days)
            programs.setSource(programId, sourceAfterEdit(programSourceOf(before.source)).code, t)
        }
    }

    suspend fun routines(): List<ProgramView> = programs.routines().map { view(it) }

    /** A new own routine: one day, kept apart from the program list. Returns the program id. */
    suspend fun createRoutine(name: String, items: List<EditItem>): String {
        val t = now()
        val pid = newId()
        db.withTransaction {
            programs.insertProgram(ProgramEntity(pid, t, t, ProgramSource.OWN.code, EquipmentSetting.CUSTOM.code, 1, false, 1, KIND_ROUTINE))
            writeDays(pid, t, listOf(EditDay(name, items)))
        }
        return pid
    }

    suspend fun updateRoutine(programId: String, name: String, items: List<EditItem>) = db.withTransaction {
        val t = now()
        writeDays(programId, t, listOf(EditDay(name, items)))
        programs.setSource(programId, ProgramSource.OWN.code, t)
    }

    suspend fun deleteRoutine(programId: String) = programs.deleteProgram(programId)

    // ---- Jelajahi (L-9) ----

    /** The shelf card built by the same generator with its fixed answers; becomes the active program (source `jelajahi`). */
    suspend fun useTemplate(template: JelajahiTemplate, base: TrainingSetup?): CreateResult =
        createAuto(
            TrainingSetup(template.setting, template.days, template.minutes, base?.experience ?: Experience.BARU_MULAI, base?.focus ?: GoalFocus.OTOT),
            ProgramSource.JELAJAHI,
        )

    // ---- review every 4 weeks (L-4, R_TRN_REVIEW) ----

    /** What a review found, with the program it is about. [programChanges] are indexed in the order the screen lists them. */
    data class ReviewOffer(val programId: String, val changes: List<ProgramChange>, val result: ReviewResult.Proposals, val current: Program)

    private suspend fun toProgram(view: ProgramView, setup: TrainingSetup): Program {
        val lib = logger.library().map { it.second }
        val byId = lib.associateBy { it.id }
        val days = view.days.map { d ->
            ProgramDay(
                d.position, d.dayName ?: DayName.FULL_A, d.estMinutes,
                d.items.mapNotNull { i ->
                    val ex = byId[i.exerciseId] ?: return@mapNotNull null
                    ProgramItem(i.exerciseId, ex.pattern, ItemRole.MAIN, i.sets, i.repMin, i.repMax, i.restSec, ex.loadKind, ex.usesBand, whyHint = ex.hintId)
                },
            )
        }
        return Program(
            view.source, EquipmentSetting.entries.firstOrNull { it.code == view.setting } ?: setup.setting,
            TrainingLevel.entries.getOrElse(view.level - 1) { TrainingLevel.L1 }, setup.focus, 1, days, emptyList(),
            weeklySets(days.flatMap { it.items }, lib), emptySet(),
        )
    }

    /**
     * Runs the review (Rules R_TRN_REVIEW) for the active program. Returns null when nothing is due, when the program is
     * `own` or `jelajahi`, when auto-programming is off (L-5), when the gates do not pass, or when the review has no change.
     */
    suspend fun reviewOffer(setup: TrainingSetup, autoOff: Boolean, answeredAt: Long?): ReviewOffer? {
        val view = active() ?: return null
        if (view.source != ProgramSource.AUTO) return null
        val z = zone()
        val since = maxOf(view.createdAt, answeredAt ?: 0L)
        val clock = ReviewClock(
            Instant.ofEpochMilli(since).atZone(z).toLocalDate().toEpochDay(),
            db.workoutDao().finishedSince(since),
        )
        val gate = currentGate()
        val current = toProgram(view, setup)
        val today = Instant.ofEpochMilli(now()).atZone(z).toLocalDate().toEpochDay()
        val result = review(
            current, !autoOff, TrainingProfile(gate, setup.experience, setup.days, setup.minutes),
            Equipment.of(setup.setting, setup.customItems), logger.history(), logger.library().map { it.second }, today, clock,
        ) as? ReviewResult.Proposals ?: return null
        if (result.changes.isEmpty()) return null
        return ReviewOffer(view.id, result.changes, result, current)
    }

    /** Applies the changes the user accepted; the program stays `auto` (the user did not edit it, L-4). */
    suspend fun applyReview(offer: ReviewOffer, accepted: Set<ProgramChange>) {
        val program = applyChanges(offer.current, offer.result, accepted)
        db.withTransaction {
            val t = now()
            programs.deleteDays(offer.programId)
            program.days.forEach { d ->
                val did = newId()
                programs.insertDays(listOf(ProgramDayEntity(did, t, t, offer.programId, d.position, d.name.name, d.estMinutes)))
                programs.insertItems(d.items.mapIndexed { i, it -> ProgramItemEntity(newId(), t, t, did, it.exerciseId, i, it.sets, it.repMin, it.repMax, it.restSec) })
            }
            programs.setLevel(offer.programId, program.level.ordinal + 1, program.rulesVersion, t)
        }
    }

    private suspend fun currentGate(): GateResult {
        val profile = db.profileDao().get() ?: return GateResult.ScreeningNeeded
        val weight = db.profileDao().latestMeasure("weight")?.value // text-ok: stored code
        return readyFrom(profile, weight, null, Instant.ofEpochMilli(now()).atZone(zone()).year) ?: GateResult.ScreeningNeeded
    }

    companion object {
        const val KIND_PROGRAM = "program" // text-ok: stored code
        const val KIND_ROUTINE = "routine" // text-ok: stored code
    }
}
