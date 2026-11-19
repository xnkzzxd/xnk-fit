package app.xnkfit.ui.latihan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.data.program.CreateResult
import app.xnkfit.data.program.ProgramRepository
import app.xnkfit.data.program.ProgramView
import app.xnkfit.domain.latihan.TrainingSetup
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
)

/** Latihan tab: program, first profile questions, start of a day (B-08 piece 1). */
class LatihanViewModel(
    private val programs: ProgramRepository,
    private val logger: LoggerRepository,
    private val prefs: LatihanPrefs,
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
        _ui.update {
            it.copy(
                loading = false, program = program, nextDayId = logger.nextPlannedDay()?.programDayId,
                missing = missing, autoOff = prefs.autoOff(), working = false,
            )
        }
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
