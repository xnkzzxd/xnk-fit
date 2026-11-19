package app.xnkfit.ui.latihan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.data.program.CreateResult
import app.xnkfit.data.program.ProgramRepository
import app.xnkfit.data.program.ProgramView
import app.xnkfit.data.program.ProgramRepository.EditDay
import app.xnkfit.data.program.ProgramRepository.EditItem
import app.xnkfit.data.program.ProgramRepository.ReviewOffer
import app.xnkfit.domain.latihan.DraftItem
import app.xnkfit.domain.latihan.R_JEL_SHELF
import app.xnkfit.domain.latihan.TrainingSetup
import app.xnkfit.domain.latihan.defaultItem
import app.xnkfit.domain.latihan.moved
import app.xnkfit.domain.latihan.withAdded
import app.xnkfit.domain.latihan.withReps
import app.xnkfit.domain.latihan.withRest
import app.xnkfit.domain.latihan.withSets
import app.xnkfit.domain.latihan.withoutAt
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.domain.training.MissingPattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Draft answers of the first training profile (mockup: place, days, minutes, experience). Nothing is stored until "Buat program". */
data class ProfileDraft(
    val setting: EquipmentSetting? = null,
    val days: Int? = null,
    val minutes: Int? = null,
    val experience: Experience? = null,
) {
    val complete: Boolean get() = setting != null && days != null && minutes != null && experience != null
    fun toSetup(): TrainingSetup? =
        if (setting != null && days != null && minutes != null && experience != null) TrainingSetup(setting, days, minutes, experience) else null
}

/** Why no program could be built; the screen turns it into a neutral line (PR-5). */
enum class NoProgram { SCREENING_NEEDED, STOPPED }

data class LatihanUi(
    val loading: Boolean = true,
    val program: ProgramView? = null,
    /** Day the rotation offers next (Design §4 and B-11). */
    val nextDayId: String? = null,
    val draft: ProfileDraft = ProfileDraft(),
    val noProgram: NoProgram? = null,
    /** L-3: patterns the equipment cannot carry, shown as a hint with what would make them possible. */
    val missing: List<MissingPattern> = emptyList(),
    val autoOff: Boolean = false,
    val working: Boolean = false,
    /** Own routines (L-9), one day each. */
    val routines: List<ProgramView> = emptyList(),
    val review: ReviewUi? = null,
    val editor: EditorUi? = null,
)

/** One review the user may answer (L-4): the offer and the indexes of the changes ticked so far (all ticked at the start). */
data class ReviewUi(val offer: ReviewOffer, val names: Map<String, String>, val ticked: Set<Int>)

data class PickOption(val id: String, val name: String)

/**
 * The routine or program day being edited. [programId] null = a new routine; [dayId] is set when the item list is one day of the
 * active program (saving it turns that program into `own`, L-5).
 */
data class EditorUi(
    val programId: String?,
    val dayId: String?,
    val name: String,
    val items: List<DraftItem>,
    val picker: List<PickOption>? = null,
    val fromProgram: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && items.isNotEmpty()
}

/** Latihan tab: program, first profile questions, start of a day (B-08 piece 1). */
class LatihanViewModel(
    private val programs: ProgramRepository,
    private val logger: LoggerRepository,
    private val prefs: LatihanPrefs,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _ui = MutableStateFlow(LatihanUi())
    val ui: StateFlow<LatihanUi> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val program = programs.active()
        val setup = prefs.setup()
        val missing = if (program != null && setup != null) programs.missing(setup) else emptyList()
        val autoOff = prefs.autoOff()
        val offer = if (setup != null) programs.reviewOffer(setup, autoOff, prefs.reviewAnsweredAt()) else null
        val names = logger.library().associate { it.second.id to it.first.nameId }
        _ui.update {
            it.copy(
                loading = false, program = program, nextDayId = logger.nextPlannedDay()?.programDayId,
                missing = missing, autoOff = autoOff, working = false, routines = programs.routines(),
                review = offer?.let { o -> ReviewUi(o, names, o.changes.indices.toSet()) },
            )
        }
    }

    // ---- Jelajahi (L-9) ----

    fun useTemplate(id: String) {
        val template = R_JEL_SHELF.firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(working = true) }
        viewModelScope.launch {
            val r = programs.useTemplate(template, prefs.setup())
            if (r is CreateResult.Blocked) {
                _ui.update { it.copy(working = false, noProgram = if (r.gate is GateResult.ScreeningNeeded) NoProgram.SCREENING_NEEDED else NoProgram.STOPPED) }
            } else load()
        }
    }

    /** L-5: switch automatic programming off or on. Off keeps the program as it is and silences the review. */
    fun setAutoOff(off: Boolean) {
        viewModelScope.launch { prefs.setAutoOff(off); load() }
    }

    // ---- review every 4 weeks (L-4) ----

    fun toggleChange(index: Int) = _ui.update { ui ->
        ui.copy(review = ui.review?.let { r -> r.copy(ticked = if (index in r.ticked) r.ticked - index else r.ticked + index) })
    }

    fun applyReview() {
        val r = _ui.value.review ?: return
        viewModelScope.launch {
            programs.applyReview(r.offer, r.ticked.map { r.offer.changes[it] }.toSet())
            prefs.setReviewAnsweredAt(now())
            load()
        }
    }

    /** "Nanti saja": nothing changes, the 4-week count starts again. */
    fun declineReview() {
        viewModelScope.launch { prefs.setReviewAnsweredAt(now()); load() }
    }

    // ---- editor: own routines, and one day of the program (L-5) ----

    private fun editor(change: (EditorUi) -> EditorUi) = _ui.update { ui -> ui.editor?.let { ui.copy(editor = change(it)) } ?: ui }

    private fun draftOf(items: List<app.xnkfit.data.program.ItemView>) =
        items.map { DraftItem(it.exerciseId, it.name, it.sets, it.repMin, it.repMax, it.restSec, it.supersetGroup) }

    fun newRoutine() = _ui.update { it.copy(editor = EditorUi(null, null, "", emptyList())) }

    fun editRoutine(programId: String) {
        val r = _ui.value.routines.firstOrNull { it.id == programId } ?: return
        val day = r.days.firstOrNull() ?: return
        _ui.update { it.copy(editor = EditorUi(programId, day.id, day.name, draftOf(day.items))) }
    }

    fun editDay(dayId: String, title: String) {
        val p = _ui.value.program ?: return
        val day = p.days.firstOrNull { it.id == dayId } ?: return
        _ui.update { it.copy(editor = EditorUi(p.id, dayId, title, draftOf(day.items), fromProgram = true)) }
    }

    fun rename(name: String) = editor { it.copy(name = name) }
    fun changeSets(i: Int, delta: Int) = editor { e -> e.copy(items = e.items.mapIndexed { n, x -> if (n == i) x.withSets(delta) else x }) }
    fun changeReps(i: Int, delta: Int) = editor { e -> e.copy(items = e.items.mapIndexed { n, x -> if (n == i) x.withReps(delta) else x }) }
    fun changeRest(i: Int, delta: Int) = editor { e -> e.copy(items = e.items.mapIndexed { n, x -> if (n == i) x.withRest(delta) else x }) }
    fun moveItem(i: Int, delta: Int) = editor { it.copy(items = it.items.moved(i, delta)) }
    fun removeItem(i: Int) = editor { it.copy(items = it.items.withoutAt(i)) }
    fun closeEditor() = _ui.update { it.copy(editor = null) }

    fun openPicker() {
        viewModelScope.launch {
            val equipment = logger.equipment()
            val options = logger.library().filter { (_, ex) -> equipment == null || equipment.allows(ex) }
                .map { PickOption(it.second.id, it.first.nameId) }.sortedBy { it.name }
            editor { it.copy(picker = options) }
        }
    }

    fun closePicker() = editor { it.copy(picker = null) }

    fun pick(exerciseId: String) {
        viewModelScope.launch {
            val (entity, ex) = logger.library().firstOrNull { it.second.id == exerciseId } ?: return@launch
            editor { it.copy(items = it.items.withAdded(defaultItem(ex, entity.nameId)), picker = null) }
        }
    }

    fun saveEditor() {
        val e = _ui.value.editor?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            val items = e.items.map { EditItem(it.exerciseId, it.sets, it.repMin, it.repMax, it.restSec, it.supersetGroup) }
            val program = _ui.value.program
            when {
                e.programId == null -> programs.createRoutine(e.name.trim(), items)
                e.fromProgram && program != null -> programs.saveEdit(
                    program.id,
                    program.days.map { d ->
                        if (d.id == e.dayId) EditDay(d.name, items)
                        else EditDay(d.name, d.items.map { EditItem(it.exerciseId, it.sets, it.repMin, it.repMax, it.restSec, it.supersetGroup) })
                    },
                )
                else -> programs.updateRoutine(e.programId, e.name.trim(), items)
            }
            _ui.update { it.copy(editor = null) }
            load()
        }
    }

    fun deleteRoutine(programId: String) {
        viewModelScope.launch { programs.deleteRoutine(programId); load() }
    }

    fun draft(change: (ProfileDraft) -> ProfileDraft) = _ui.update { it.copy(draft = change(it.draft), noProgram = null) }

    /** "Buat program": stores the answers, runs the generator. Blocked gates write nothing (PR-5). */
    fun createProgram() {
        val setup = _ui.value.draft.toSetup() ?: return
        _ui.update { it.copy(working = true) }
        viewModelScope.launch {
            prefs.saveSetup(setup)
            when (val r = programs.createAuto(setup)) {
                is CreateResult.Blocked -> _ui.update {
                    it.copy(working = false, noProgram = if (r.gate is GateResult.ScreeningNeeded) NoProgram.SCREENING_NEEDED else NoProgram.STOPPED)
                }
                is CreateResult.Created -> load()
            }
        }
    }

    fun cancelEdit() = refresh()

    /** Back to the three questions with the earlier answers filled in; the stored program stays until a new one is built. */
    fun editProfile() {
        viewModelScope.launch {
            val s = prefs.setup()
            _ui.update { it.copy(program = null, draft = ProfileDraft(s?.setting, s?.days, s?.minutes, s?.experience)) }
        }
    }
}
