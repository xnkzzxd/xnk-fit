package app.xnkfit.ui.saya

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.db.PhotoEntity
import app.xnkfit.data.progress.BadgeInfo
import app.xnkfit.data.progress.PhotoGateway
import app.xnkfit.data.progress.ProgressRepository
import app.xnkfit.data.progress.ProgressSummary
import app.xnkfit.domain.rules.R_INPUT_WEIGHT_MAX_KG
import app.xnkfit.domain.rules.R_INPUT_WEIGHT_MIN_KG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Measure types the screen can record. The six opt-in types arrive with the settings screen (B-14). */
enum class MeasureType(val code: String, val unit: String, val min: Double, val max: Double) {
    WEIGHT("weight", "kg", R_INPUT_WEIGHT_MIN_KG, R_INPUT_WEIGHT_MAX_KG), // text-ok: stored codes
    WAIST("waist", "cm", 30.0, 250.0), // text-ok: stored codes
}

data class SayaState(
    val loaded: Boolean = false,
    val nickname: String? = null,
    val summary: ProgressSummary? = null,
    val weight: WeightTrend = WeightTrend(null, null, emptyList()),
    val waist: Double? = null,
    val weightHidden: Boolean = false,
    val photos: List<PhotoEntity> = emptyList(),
    /** Badges earned by the last refresh; shown once with the message from the content file. */
    val celebrate: List<BadgeInfo> = emptyList(),
    val photoFailed: Boolean = false,
    val measureInvalid: Boolean = false,
)

/**
 * Saya (PRD P-1…P-5). Thin: every rule lives in `domain.progress`, every query in [ProgressRepository].
 * [loadNickname] is a lambda so the view model needs no Room type.
 */
class SayaViewModel(
    private val repo: ProgressRepository,
    private val photos: PhotoGateway,
    private val prefs: SayaPrefs,
    private val loadNickname: suspend () -> String?,
    private val newId: () -> String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(SayaState())
    val state: StateFlow<SayaState> = _state.asStateFlow()

    init { refresh() }

    /** Recounts points and badges, then reloads what the screen shows. Called on open and after every change. */
    fun refresh() {
        viewModelScope.launch { reload(recount = true) }
    }

    private suspend fun reload(recount: Boolean) {
        try {
            val newly = if (recount) repo.refresh() else emptyList()
            val summary = repo.summary()
            val weights = repo.measures(MeasureType.WEIGHT.code)
            val waist = repo.measures(MeasureType.WAIST.code).lastOrNull()?.value
            _state.update {
                it.copy(
                    loaded = true,
                    nickname = loadNickname(),
                    summary = summary,
                    weight = weightTrend(weights, nowMs()),
                    waist = waist,
                    weightHidden = prefs.weightHidden(),
                    photos = photos.list(),
                    celebrate = it.celebrate + newly,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A read failure must not crash the tab; show what loaded and try again on the next open.
            _state.update { it.copy(loaded = true) }
        }
    }

    fun setWeightHidden(hidden: Boolean) {
        viewModelScope.launch {
            prefs.setWeightHidden(hidden)
            _state.update { it.copy(weightHidden = hidden) }
        }
    }

    /** [text] is what the user typed; a comma is accepted as the decimal mark. */
    fun addMeasure(type: MeasureType, text: String) {
        val value = text.trim().replace(',', '.').toDoubleOrNull()
        if (value == null || value < type.min || value > type.max) {
            _state.update { it.copy(measureInvalid = true) }
            return
        }
        _state.update { it.copy(measureInvalid = false) }
        viewModelScope.launch {
            repo.addMeasure(newId(), type.code, value, type.unit)
            reload(recount = true)
        }
    }

    fun dismissMeasureError() = _state.update { it.copy(measureInvalid = false) }

    fun addNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addNote(newId(), text)
            reload(recount = false)
        }
    }

    fun newPhotoId(): String = newId()

    fun captureFile(id: String): File = photos.captureFile(id)

    /** The camera finished. [ok] false means the user backed out: nothing is stored. */
    fun photoCaptured(id: String, kind: String, ok: Boolean) {
        viewModelScope.launch {
            val file = photos.captureFile(id)
            if (!ok) {
                file.delete()
                return@launch
            }
            val saved = photos.add(file, id, kind)
            _state.update { it.copy(photoFailed = !saved) }
            reload(recount = true)
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            photos.delete(id)
            reload(recount = false)
        }
    }

    fun celebrationShown() = _state.update { it.copy(celebrate = emptyList()) }

    fun dismissPhotoError() = _state.update { it.copy(photoFailed = false) }
}
