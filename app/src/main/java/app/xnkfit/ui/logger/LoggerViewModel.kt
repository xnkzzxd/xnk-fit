package app.xnkfit.ui.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.db.ExerciseEntity
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.data.logger.PlannedDay
import app.xnkfit.domain.logger.LoggerMode
import app.xnkfit.domain.logger.LoggerSet
import app.xnkfit.domain.logger.SetKind
import app.xnkfit.domain.logger.SetMeasure
import app.xnkfit.domain.logger.Totals
import app.xnkfit.domain.logger.canTick
import app.xnkfit.domain.logger.defaultRestSec
import app.xnkfit.domain.logger.extendRest
import app.xnkfit.domain.logger.measureOf
import app.xnkfit.domain.logger.nextSet
import app.xnkfit.domain.logger.prefill
import app.xnkfit.domain.logger.restAfter
import app.xnkfit.domain.logger.restEndsAt
import app.xnkfit.domain.logger.restRemainingSec
import app.xnkfit.domain.logger.totals
import app.xnkfit.domain.training.DayName
import app.xnkfit.domain.training.Equipment
import app.xnkfit.domain.training.Exercise
import app.xnkfit.domain.training.FormRating
import app.xnkfit.domain.training.ItemTarget
import app.xnkfit.domain.training.TrainingLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LoggerPhase { LOADING, START, RESUME, ACTIVE, DONE }

/** Sheets of the mockup (logger.html): one open at a time. */
sealed interface LoggerSheet {
    data class Swap(val slot: Int, val exerciseId: String, val candidates: List<ExerciseOption>) : LoggerSheet
    data class Kind(val setId: String) : LoggerSheet
    data class ExerciseMenu(val slot: Int, val exerciseId: String) : LoggerSheet
    data class Rest(val slot: Int, val exerciseId: String) : LoggerSheet
    data class Add(val options: List<ExerciseOption>) : LoggerSheet
    data object ExerciseList : LoggerSheet
    data object Menu : LoggerSheet
    data object Finish : LoggerSheet
    data object Cancel : LoggerSheet
}

data class ExerciseOption(val id: String, val name: String, val pattern: String, val equipment: Set<String>)

/** One exercise block: the rows of one exercise in one slot (a swap leaves the done rows under the old exercise). */
data class ExerciseBlock(
    val slot: Int,
    val exerciseId: String,
    val name: String,
    val measure: SetMeasure,
    val stepKg: Double?,
    val sets: List<LoggerSet>,
    val previous: List<LoggerSet>,
    val target: ItemTarget?,
    val repMin: Int?,
    val repMax: Int?,
    val supersetGroup: Int?,
    val whyHint: String?,
)

/** Rest timer: a notice when the alarm cannot ring outside the app (L-8 fallback). */
enum class AlarmNotice { IN_APP_ONLY }

data class LoggerUi(
    val phase: LoggerPhase = LoggerPhase.LOADING,
    /** Remembered mode; null before the first workout (no preselection, D-06). */
    val chosenMode: LoggerMode? = null,
    val plannedDay: DayName? = null,
    val hasProgram: Boolean = false,
    val workoutId: String? = null,
    val mode: LoggerMode? = null,
    val dayName: DayName? = null,
    val startedAt: Long = 0,
    val blocks: List<ExerciseBlock> = emptyList(),
    val restEndsAt: Long? = null,
    val nowMs: Long = 0,
    val alarmNotice: AlarmNotice? = null,
    val sheet: LoggerSheet? = null,
    val doneTotals: Totals? = null,
) {
    val allSets: List<LoggerSet> get() = blocks.flatMap { it.sets }
    val totals: Totals get() = totals(allSets)
    val next: LoggerSet? get() = nextSet(allSets)
    val restLeftSec: Int get() = restRemainingSec(restEndsAt, nowMs)
    fun blockOf(set: LoggerSet): ExerciseBlock? = blocks.firstOrNull { b -> b.sets.any { it.id == set.id } }
}

/** What the screen must ask the system for before the first rest alarm (Design §1, §14: asked at first use). */
enum class AlarmAccess { READY, NEEDS_NOTIFICATIONS, NEEDS_EXACT_ALARM }

/** Exact alarm for the rest end (L-8). Android side in [AndroidRestAlarms]; a fake in tests. */
interface RestAlarms {
    fun access(): AlarmAccess
    fun schedule(atMs: Long)
    fun cancel()
}

interface LoggerPrefs {
    suspend fun mode(): LoggerMode?
    suspend fun setMode(mode: LoggerMode)
    suspend fun alarmAsked(): Boolean
    suspend fun setAlarmAsked()
}

/**
 * Logger (L-6…L-8, PR-2, NF-2). Every tap changes the state and writes Room at once, in tap order (one mutex);
 * nothing is kept only in memory. A killed process finds everything on disk: [LoggerRepository.recoverUnfinished].
 */
class LoggerViewModel(
    private val repo: LoggerRepository,
    private val prefs: LoggerPrefs,
    private val alarms: RestAlarms,
    private val now: () -> Long,
    private val newId: () -> String,
    /** Seconds tick of the rest timer; tests pass false and drive [refreshClock]. */
    private val tickClock: Boolean = true,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoggerUi())
    val ui: StateFlow<LoggerUi> = _ui.asStateFlow()

    /** Set when the first rest timer starts and the system must be asked (once). The screen asks and calls back. */
    private val _ask = MutableStateFlow<AlarmAccess?>(null)
    val ask: StateFlow<AlarmAccess?> = _ask.asStateFlow()

    private val writes = Mutex()
    private var plan: PlannedDay? = null
    private var library: Map<String, Pair<ExerciseEntity, Exercise>> = emptyMap()
    private var equipment: Equipment? = null
    private var level: TrainingLevel? = null
    private var clockJob: Job? = null

    /** Last write, for tests that must wait for the disk. */
    var lastWrite: Job? = null
        private set

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        library = repo.library().associateBy { it.first.id }
        equipment = repo.equipment()
        level = repo.activeLevel()
        val open = repo.recoverUnfinished()
        val chosen = prefs.mode()
        plan = repo.nextPlannedDay()
        _ui.update {
            it.copy(
                phase = if (open != null) LoggerPhase.RESUME else LoggerPhase.START,
                chosenMode = chosen, plannedDay = plan?.dayName, hasProgram = plan != null,
                workoutId = open?.id, mode = LoggerMode.of(open?.mode), nowMs = now(),
            )
        }
    }

    // ---- start, resume, finish ----

    fun chooseMode(mode: LoggerMode) = _ui.update { it.copy(chosenMode = mode) }

    fun start() {
        val mode = _ui.value.chosenMode ?: return
        write {
            prefs.setMode(mode)
            plan = repo.nextPlannedDay()
            val id = repo.start(mode, plan)
            open(id)
        }
    }

    fun resume() {
        val id = _ui.value.workoutId ?: return
        write { open(id) }
    }

    /** "Selesaikan" on the resume prompt: the workout ends at its last state. */
    fun finishResumed() {
        val id = _ui.value.workoutId ?: return
        write {
            repo.finish(id)
            alarms.cancel()
            backToStart()
        }
    }

    private suspend fun open(id: String) {
        val loaded = repo.load(id) ?: return backToStart()
        val mode = LoggerMode.of(loaded.workout.mode) ?: LoggerMode.BEBAS
        val p = plan?.takeIf { it.programDayId == loaded.workout.programDayId }
        val blocks = buildBlocks(loaded.sets, p)
        _ui.update {
            it.copy(
                phase = LoggerPhase.ACTIVE, workoutId = id, mode = mode, dayName = p?.dayName,
                startedAt = loaded.workout.startedAt, blocks = blocks, restEndsAt = loaded.workout.restEndsAt,
                nowMs = now(), sheet = null,
            )
        }
        startClock()
    }

    private suspend fun backToStart() {
        plan = repo.nextPlannedDay()
        clockJob?.cancel()
        _ui.update {
            LoggerUi(phase = LoggerPhase.START, chosenMode = prefs.mode(), plannedDay = plan?.dayName, hasProgram = plan != null, nowMs = now())
        }
    }

    fun finish() {
        val id = _ui.value.workoutId ?: return
        write {
            val t = _ui.value.totals
            repo.finish(id)
            alarms.cancel()
            clockJob?.cancel()
            _ui.update { it.copy(phase = LoggerPhase.DONE, doneTotals = t, sheet = null, restEndsAt = null) }
        }
    }

    fun cancelWorkout() {
        val id = _ui.value.workoutId ?: return
        write {
            repo.cancel(id)
            alarms.cancel()
            backToStart()
        }
    }

    fun closeDone() = write { backToStart() }

    fun switchMode(mode: LoggerMode) {
        val id = _ui.value.workoutId ?: return
        _ui.update { it.copy(mode = mode, sheet = null) }
        write {
            repo.setMode(id, mode)
            prefs.setMode(mode)
        }
    }

    // ---- sets ----

    /** Any field change: written at once (PR-2). */
    fun edit(setId: String, change: (LoggerSet) -> LoggerSet) {
        val set = _ui.value.allSets.firstOrNull { it.id == setId } ?: return
        val updated = change(set)
        if (updated == set) return
        replaceSets(listOf(updated))
        val id = _ui.value.workoutId ?: return
        val rest = _ui.value.restEndsAt
        write { repo.saveSet(id, updated, rest) }
    }

    fun setKind(setId: String, kind: SetKind) {
        edit(setId) { it.copy(kind = kind) }
        closeSheet()
    }

    fun setForm(setId: String, form: FormRating) = edit(setId) { it.copy(form = if (it.form == form) null else form) }

    /** "Selesai set" / ✓. Ticking starts the rest timer; un-ticking (Bebas) keeps the numbers. */
    fun toggleDone(setId: String) {
        val ui = _ui.value
        val set = ui.allSets.firstOrNull { it.id == setId } ?: return
        val block = ui.blockOf(set) ?: return
        if (!set.done && !canTick(set, block.measure)) return
        val t = now()
        val updated = if (set.done) set.copy(done = false, doneAt = null) else set.copy(done = true, doneAt = t)
        replaceSets(listOf(updated))
        val rest = if (updated.done) restEndsAt(t, restAfter(updated, _ui.value.allSets)) else _ui.value.restEndsAt
        _ui.update { it.copy(restEndsAt = rest, nowMs = t) }
        val id = ui.workoutId ?: return
        write {
            repo.saveSet(id, updated, rest)
            if (updated.done) armAlarm(rest)
        }
    }

    fun addSet(slot: Int, exerciseId: String) {
        val block = _ui.value.blocks.firstOrNull { it.slot == slot && it.exerciseId == exerciseId } ?: return
        val last = block.sets.maxByOrNull { it.index }
        val index = (_ui.value.allSets.filter { it.slot == slot }.maxOfOrNull { it.index } ?: 0) + 1
        val set = (last ?: emptySet(slot, exerciseId, index, block)).copy(
            id = newId(), index = index, done = false, doneAt = null, form = null, kind = SetKind.NORMAL,
        )
        insertSets(listOf(set))
    }

    fun deleteSet(setId: String) {
        val id = _ui.value.workoutId ?: return
        _ui.update { ui -> ui.copy(blocks = ui.blocks.map { b -> b.copy(sets = b.sets.filterNot { it.id == setId }) }.filter { it.sets.isNotEmpty() }, sheet = null) }
        val rest = _ui.value.restEndsAt
        write { repo.deleteSets(id, listOf(setId), rest) }
    }

    // ---- exercises ----

    fun openAdd() = _ui.update { it.copy(sheet = LoggerSheet.Add(options { true })) }

    fun addExercise(exerciseId: String) {
        val slot = (_ui.value.allSets.maxOfOrNull { it.slot } ?: -1) + 1
        write {
            val block = blockFor(slot, exerciseId, emptyList(), null)
            val count = maxOf(block.previous.size, 1)
            val sets = (1..count).map { n -> emptySet(slot, exerciseId, n, block) }
            _ui.update { it.copy(blocks = it.blocks + block.copy(sets = sets), sheet = null) }
            val id = _ui.value.workoutId ?: return@write
            repo.saveSets(id, sets, _ui.value.restEndsAt)
        }
    }

    fun openSwap(slot: Int, exerciseId: String) {
        val current = library[exerciseId]?.second ?: return
        val candidates = options { it.pattern == current.pattern && it.id != exerciseId }
        _ui.update { it.copy(sheet = LoggerSheet.Swap(slot, exerciseId, candidates)) }
    }

    /** "Hanya hari ini": the not-done sets of the block move to the new exercise; done sets keep their history. */
    fun swap(slot: Int, fromId: String, toId: String) {
        write {
            val block = _ui.value.blocks.firstOrNull { it.slot == slot && it.exerciseId == fromId } ?: return@write
            val newBlock = blockFor(slot, toId, emptyList(), null)
            val moved = block.sets.filter { !it.done }.map { s ->
                val p = prefill(newBlock.measure, s.index, null, null, block.repMin, newBlock.previous)
                s.copy(exerciseId = toId, weightKg = p.weightKg, reps = p.reps, bandLevel = p.bandLevel, holdSec = p.holdSec, form = null)
            }
            if (moved.isEmpty()) {
                _ui.update { it.copy(sheet = null) }
                return@write
            }
            val keep = block.sets.filter { it.done }
            _ui.update { ui ->
                val others = ui.blocks.filterNot { it === block || (it.slot == slot && it.exerciseId == fromId) }
                val rebuilt = listOfNotNull(
                    keep.takeIf { it.isNotEmpty() }?.let { block.copy(sets = it) },
                    newBlock.copy(sets = moved, repMin = block.repMin, repMax = block.repMax, supersetGroup = block.supersetGroup),
                )
                ui.copy(blocks = (others + rebuilt).sortedWith(compareBy({ it.slot }, { it.sets.minOf { s -> s.index } })), sheet = null)
            }
            val id = _ui.value.workoutId ?: return@write
            repo.saveSets(id, moved, _ui.value.restEndsAt)
        }
    }

    /** "Jadikan superset": this exercise and the next one alternate; a second tap undoes it. */
    fun toggleSuperset(slot: Int) {
        val ui = _ui.value
        val slots = ui.blocks.map { it.slot }.distinct().sorted()
        val here = ui.blocks.filter { it.slot == slot }
        val grouped = here.firstOrNull()?.supersetGroup
        val changed: List<LoggerSet> = if (grouped != null) {
            ui.allSets.filter { it.supersetGroup == grouped }.map { it.copy(supersetGroup = null) }
        } else {
            val nextSlot = slots.firstOrNull { it > slot } ?: return closeSheet()
            ui.allSets.filter { it.slot == slot || it.slot == nextSlot }.map { it.copy(supersetGroup = slot) }
        }
        replaceSets(changed)
        _ui.update { it.copy(sheet = null) }
        val id = ui.workoutId ?: return
        write { repo.saveSets(id, changed, _ui.value.restEndsAt) }
    }

    fun setRest(slot: Int, exerciseId: String, seconds: Int) {
        val ui = _ui.value
        val changed = ui.allSets.filter { it.slot == slot && it.exerciseId == exerciseId }.map { it.copy(restSec = seconds) }
        replaceSets(changed)
        _ui.update { it.copy(sheet = null) }
        val id = ui.workoutId ?: return
        write { repo.saveSets(id, changed, _ui.value.restEndsAt) }
    }

    fun removeExercise(slot: Int, exerciseId: String) {
        val ui = _ui.value
        val ids = ui.allSets.filter { it.slot == slot && it.exerciseId == exerciseId }.map { it.id }
        _ui.update { u -> u.copy(blocks = u.blocks.filterNot { it.slot == slot && it.exerciseId == exerciseId }, sheet = null) }
        val id = ui.workoutId ?: return
        write { repo.deleteSets(id, ids, _ui.value.restEndsAt) }
    }

    // ---- rest timer ----

    fun extendRest() {
        val rest = extendRest(_ui.value.restEndsAt, now())
        _ui.update { it.copy(restEndsAt = rest, nowMs = now()) }
        val id = _ui.value.workoutId ?: return
        write {
            repo.setRest(id, rest)
            armAlarm(rest)
        }
    }

    fun skipRest() {
        _ui.update { it.copy(restEndsAt = null) }
        val id = _ui.value.workoutId ?: return
        write {
            repo.setRest(id, null)
            alarms.cancel()
        }
    }

    /** Called by the screen after the system dialogs (notifications, exact alarm) closed. */
    fun onAccessAnswered() {
        _ask.value = null
        val access = alarms.access()
        if (access == AlarmAccess.READY) {
            _ui.update { it.copy(alarmNotice = null) }
            _ui.value.restEndsAt?.let { if (it > now()) alarms.schedule(it) }
        } else {
            _ui.update { it.copy(alarmNotice = AlarmNotice.IN_APP_ONLY) }
        }
    }

    /** "Izinkan" on the notice: ask again (the user's own tap). */
    fun askAgain() {
        val access = alarms.access()
        if (access == AlarmAccess.READY) onAccessAnswered() else _ask.value = access
    }

    private suspend fun armAlarm(rest: Long?) {
        if (rest == null) {
            alarms.cancel()
            return
        }
        when (val access = alarms.access()) {
            AlarmAccess.READY -> {
                alarms.schedule(rest)
                _ui.update { it.copy(alarmNotice = null) }
            }
            else -> {
                // Design §1/§14: asked only when the timer is first used; afterwards the in-app timer and the notice.
                if (!prefs.alarmAsked()) {
                    prefs.setAlarmAsked()
                    _ask.value = access
                } else {
                    _ui.update { it.copy(alarmNotice = AlarmNotice.IN_APP_ONLY) }
                }
            }
        }
    }

    fun refreshClock() = _ui.update { it.copy(nowMs = now()) }

    private fun startClock() {
        if (!tickClock) return
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive) {
                refreshClock()
                delay(CLOCK_TICK_MS)
            }
        }
    }

    // ---- sheets ----

    fun openSheet(sheet: LoggerSheet) = _ui.update { it.copy(sheet = sheet) }

    fun closeSheet() = _ui.update { it.copy(sheet = null) }

    // ---- helpers ----

    private fun options(filter: (Exercise) -> Boolean): List<ExerciseOption> {
        val eq = equipment
        val lvl = level
        return library.values.asSequence()
            .filter { (_, ex) -> filter(ex) && (eq == null || eq.allows(ex)) && (lvl == null || ex.fits(lvl)) }
            .map { (e, ex) -> ExerciseOption(ex.id, e.nameId, ex.pattern.name, ex.equipment) }
            .sortedBy { it.name }
            .toList()
    }

    private suspend fun buildBlocks(sets: List<LoggerSet>, p: PlannedDay?): List<ExerciseBlock> =
        sets.groupBy { it.slot to it.exerciseId }
            .map { (key, rows) -> blockFor(key.first, key.second, rows.sortedBy { it.index }, p) }
            .sortedWith(compareBy({ it.slot }, { it.sets.minOf { s -> s.index } }))

    private suspend fun blockFor(slot: Int, exerciseId: String, sets: List<LoggerSet>, p: PlannedDay?): ExerciseBlock {
        val (entity, ex) = library[exerciseId] ?: (null to null)
        val itemIndex = p?.day?.items?.indexOfFirst { it.exerciseId == exerciseId }?.takeIf { it >= 0 }
        val item = itemIndex?.let { p.day.items[it] }
        val target = itemIndex?.let { p.targets.getOrNull(it) }
        return ExerciseBlock(
            slot = slot, exerciseId = exerciseId, name = entity?.nameId ?: exerciseId,
            measure = if (ex != null) measureOf(exerciseId, ex.loadKind, ex.usesBand) else SetMeasure.KG_REPS,
            stepKg = ex?.equipmentStepKg, sets = sets, previous = repo.previous(exerciseId), target = target,
            repMin = item?.repMin, repMax = item?.repMax, supersetGroup = sets.firstOrNull()?.supersetGroup,
            whyHint = ex?.hintId,
        )
    }

    private fun emptySet(slot: Int, exerciseId: String, index: Int, block: ExerciseBlock): LoggerSet {
        val p = prefill(block.measure, index, null, null, block.repMin, block.previous)
        val rest = library[exerciseId]?.second?.let { defaultRestSec(it.pattern) } ?: 0
        return LoggerSet(
            id = newId(), slot = slot, index = index, exerciseId = exerciseId, weightKg = p.weightKg, reps = p.reps,
            bandLevel = p.bandLevel, holdSec = p.holdSec, restSec = block.sets.firstOrNull()?.restSec ?: rest,
            supersetGroup = block.supersetGroup,
        )
    }

    private fun insertSets(sets: List<LoggerSet>) {
        _ui.update { ui ->
            ui.copy(blocks = ui.blocks.map { b ->
                val add = sets.filter { it.slot == b.slot && it.exerciseId == b.exerciseId }
                if (add.isEmpty()) b else b.copy(sets = (b.sets + add).sortedBy { it.index })
            })
        }
        val id = _ui.value.workoutId ?: return
        val rest = _ui.value.restEndsAt
        write { repo.saveSets(id, sets, rest) }
    }

    private fun replaceSets(changed: List<LoggerSet>) {
        val byId = changed.associateBy { it.id }
        _ui.update { ui ->
            ui.copy(blocks = ui.blocks.map { b ->
                val sets = b.sets.map { byId[it.id] ?: it }
                b.copy(sets = sets, supersetGroup = sets.firstOrNull()?.supersetGroup)
            })
        }
    }

    /** Runs writes in tap order; each is its own Room transaction (Design §4). */
    private fun write(block: suspend () -> Unit) {
        lastWrite = viewModelScope.launch { writes.withLock { block() } }
    }

    private companion object {
        const val CLOCK_TICK_MS = 1000L
    }
}
