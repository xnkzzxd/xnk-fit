package app.xnkfit.ui.latihan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.figures.BodyFigure
import app.xnkfit.data.figures.FigureRepository
import app.xnkfit.data.figures.RigData
import app.xnkfit.data.figures.RigPattern
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.data.program.ActivityRepository
import app.xnkfit.data.program.ActivityWeekView
import app.xnkfit.data.program.WeekMap
import app.xnkfit.data.program.WeekMapRepository
import app.xnkfit.domain.latihan.ActivityChoice
import app.xnkfit.domain.latihan.Effort
import app.xnkfit.domain.latihan.exerciseLevels
import app.xnkfit.domain.latihan.regionLevels
import app.xnkfit.domain.training.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The map of the week and the figures are loaded after the first frame, so the list appears with no image at first (Design §10). */
data class FiguresUi(val body: BodyFigure? = null, val regionLevels: Map<String, Int> = emptyMap(), val sets: Map<MuscleGroup, Double> = emptyMap())

/** One exercise's detail: muscles (main strongest), the hint, the cues, and a rig only when the exercise has one. */
data class DetailUi(
    val name: String,
    val body: BodyFigure,
    val regionLevels: Map<String, Int>,
    val main: List<MuscleGroup>,
    val helper: List<MuscleGroup>,
    val hint: String?,
    val cues: List<String>,
    val rig: RigPattern?,
    val rigData: RigData?,
)

data class ActivityUi(
    val view: ActivityWeekView? = null,
    val sheetOpen: Boolean = false,
    val choice: ActivityChoice = ActivityChoice.JALAN,
    val minutes: Int = DEFAULT_MINUTES,
    val effort: Effort = Effort.SEDANG,
    val weightKnown: Boolean = true,
) {
    companion object { const val DEFAULT_MINUTES = 30 }
}

data class InsightsUi(
    val figures: FiguresUi = FiguresUi(),
    val activity: ActivityUi = ActivityUi(),
    val detail: DetailUi? = null,
    val sex: String = "f",
)

/** Weekly muscle map (L-11), exercise detail and activity log (L-14) of the Latihan tab. */
class InsightsViewModel(
    private val logger: LoggerRepository,
    private val figures: FigureRepository,
    private val week: WeekMapRepository,
    private val activity: ActivityRepository,
    private val sexOf: suspend () -> String,
) : ViewModel() {
    private val _ui = MutableStateFlow(InsightsUi())
    val ui: StateFlow<InsightsUi> = _ui.asStateFlow()

    /** Called once the first frame is on screen. Heavy parsing runs on a background thread. */
    fun load() {
        viewModelScope.launch {
            val sex = sexOf()
            val act = activity.week()
            _ui.update { it.copy(sex = sex, activity = it.activity.copy(view = act)) }
            val (body, map) = withContext(Dispatchers.Default) { figures.body(sex) to week.thisWeek() }
            val levels = regionLevels(map.levels, figures.regionsOf)
            _ui.update { it.copy(figures = FiguresUi(body, levels, map.sets)) }
        }
    }

    // ---- exercise detail ----

    fun openDetail(exerciseId: String) {
        viewModelScope.launch {
            val (entity, ex) = logger.library().firstOrNull { it.second.id == exerciseId } ?: return@launch
            val sex = _ui.value.sex
            val built = withContext(Dispatchers.Default) {
                val body = figures.body(sex)
                val rig = figures.rigFor(exerciseId)
                DetailUi(
                    name = entity.nameId, body = body, regionLevels = regionLevels(exerciseLevels(ex), figures.regionsOf),
                    main = ex.main.toList(), helper = (ex.helper - ex.main).toList(), hint = entity.hintId,
                    cues = entity.cuesEn?.let(::cuesOf).orEmpty(), rig = rig, rigData = if (rig != null) figures.rigData() else null,
                )
            }
            _ui.update { it.copy(detail = built) }
        }
    }

    fun closeDetail() = _ui.update { it.copy(detail = null) }

    private fun cuesOf(raw: String): List<String> =
        raw.split('\n').filter { it.isNotBlank() }

    // ---- activity log ----

    fun openActivity() = _ui.update { it.copy(activity = it.activity.copy(sheetOpen = true, minutes = ActivityUi.DEFAULT_MINUTES)) }
    fun closeActivity() = _ui.update { it.copy(activity = it.activity.copy(sheetOpen = false)) }
    fun chooseActivity(c: ActivityChoice) = _ui.update { it.copy(activity = it.activity.copy(choice = c)) }
    fun chooseEffort(e: Effort) = _ui.update { it.copy(activity = it.activity.copy(effort = e)) }
    fun stepMinutes(delta: Int) = _ui.update { it.copy(activity = it.activity.copy(minutes = (it.activity.minutes + delta).coerceIn(MIN_STEP, MAX_MINUTES))) }

    fun saveActivity() {
        val a = _ui.value.activity
        viewModelScope.launch {
            if (activity.add(a.choice, a.effort, a.minutes)) {
                _ui.update { it.copy(activity = it.activity.copy(view = activity.week(), sheetOpen = false)) }
            }
        }
    }

    fun deleteActivity(id: String) {
        viewModelScope.launch { activity.delete(id); _ui.update { it.copy(activity = it.activity.copy(view = activity.week())) } }
    }

    private companion object {
        const val MIN_STEP = 5
        const val MAX_MINUTES = app.xnkfit.domain.latihan.ACTIVITY_MINUTES_MAX
    }
}
