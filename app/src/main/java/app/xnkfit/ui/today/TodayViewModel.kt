package app.xnkfit.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.data.food.TargetBasis
import app.xnkfit.data.food.TargetBasisStore
import app.xnkfit.data.food.foodTargetsOf
import app.xnkfit.data.food.goalFromCode
import app.xnkfit.data.food.readyFrom
import app.xnkfit.data.progress.ProgressRepository
import app.xnkfit.data.today.TodayRepository
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.food.daySummary
import app.xnkfit.domain.progress.R_PTS_MEASURE_DEFAULT_DAY
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.PaceResult
import app.xnkfit.domain.today.ActivityWeek
import app.xnkfit.domain.today.RecalcPrompt
import app.xnkfit.domain.today.RecalcReason
import app.xnkfit.domain.today.TargetCard
import app.xnkfit.domain.today.TodayTask
import app.xnkfit.domain.today.WorkoutCard
import app.xnkfit.domain.today.activityWeek
import app.xnkfit.domain.today.paceOf
import app.xnkfit.domain.today.recalcPrompt
import app.xnkfit.domain.today.targetCardOf
import app.xnkfit.domain.today.todayTasks
import app.xnkfit.domain.today.weekStrip
import app.xnkfit.domain.today.weeklyWeights
import app.xnkfit.domain.today.withPaceRaise
import app.xnkfit.domain.today.workoutCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Interest code stored by onboarding for people who do not lift (PRD H-2). */
private const val INTEREST_FOOD_ONLY = "makan" // text-ok: stored code

data class TodayState(
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val day: LocalDate? = null,
    val nickname: String? = null,
    val level: Int = 1,
    val streakWeeks: Int = 0,
    /** Null when the person does not lift (H-2): the weekly activity card stands in its place. */
    val workout: WorkoutCard? = null,
    val activity: ActivityWeek = ActivityWeek(0),
    val tasks: List<TodayTask> = emptyList(),
    val food: DaySummary? = null,
    val target: TargetCard = TargetCard.NeedsScreening,
    val recalc: RecalcPrompt? = null,
    /** Monday first; true = an active day (workout, activity or food log). */
    val week: List<Boolean> = List(7) { false },
)

/**
 * Hari ini (PRD H-1…H-3, M-2). Thin: targets and gates come from `domain.rules`, the cards from `domain.today`,
 * the day log from the food repository, level and streak from the progress repository. The only thing it writes is
 * the target basis (what the target was calculated with and what the user answered to the prompt).
 */
class TodayViewModel(
    private val repo: TodayRepository,
    private val food: FoodRepository,
    private val progress: ProgressRepository,
    private val basisStore: TargetBasisStore,
    private val today: () -> LocalDate,
    private val currentYear: () -> Int,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { run { reload() } }
    }

    /** "Hitung ulang target": the target is calculated again with today's weight and goal. */
    fun acceptRecalc() = answer(accept = true)

    /** "Pertahankan": the target stays; the prompt waits for the next change. */
    fun keepTarget() = answer(accept = false)

    private fun answer(accept: Boolean) {
        viewModelScope.launch {
            run {
                val prompt = _state.value.recalc ?: return@run
                val profile = repo.profile() ?: return@run
                val weights = repo.weights()
                val latest = weights.lastOrNull()?.second
                val b = basisStore.load()
                val next = if (prompt.reason == RecalcReason.PACE) {
                    val week = weeklyWeights(weights, zone()).lastOrNull()?.weekIndex
                    b.copy(raiseKcal = b.raiseKcal + if (accept) prompt.raiseKcal ?: 0 else 0, paceHandledWeek = week)
                } else if (accept) {
                    // A fresh calculation: today's weight and goal, and no earlier pace raise on top.
                    b.copy(weightKg = latest, goal = profile.goal, askedKg = latest, askedGoal = profile.goal, raiseKcal = 0)
                } else {
                    b.copy(askedKg = latest, askedGoal = profile.goal)
                }
                basisStore.save(next)
                reload()
            }
        }
    }

    private suspend fun run(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loaded = true, failed = true) } }
    }

    private suspend fun reload() {
        val day = today()
        val profile = repo.profile()
        if (profile == null) {
            _state.update { it.copy(loaded = true, failed = false, day = day, target = TargetCard.NeedsScreening) }
            return
        }
        val weights = repo.weights()
        val latest = weights.lastOrNull()?.second
        val basis = ensureBasis(weights.firstOrNull()?.second, profile.goal)
        val weightUsed = basis.weightKg ?: latest

        // Target: the weight and goal it was last calculated with (R_RECALC keeps them until the user accepts).
        val ready = readyFrom(profile, weightUsed, basis.goal, currentYear())
        val adjusted = if (ready is GateResult.Ready && weightUsed != null) withPaceRaise(ready, weightUsed, basis.raiseKcal) else ready
        val hidden = (ready as? GateResult.Ready)?.numbersHidden == true
        val target = targetCardOf(adjusted, raisedByPace = basis.raiseKcal > 0)

        val prompt = if (ready is GateResult.Ready) {
            val lastWeek = weeklyWeights(weights, zone()).lastOrNull()?.weekIndex
            val pace = if (lastWeek != null && lastWeek == basis.paceHandledWeek) PaceResult.NoSuggestion else paceOf(weights, zone())
            recalcPrompt(
                askedKg = basis.askedKg, latestKg = latest,
                askedGoal = goalFromCode(basis.askedGoal ?: basis.goal), goalNow = goalFromCode(profile.goal),
                numbersHidden = hidden, pace = pace,
            )
        } else {
            null
        }

        // Food today. R_GATE_ED: with numbers hidden the summary is ticks only (decided in `daySummary`).
        val rows = food.logOfDay(day.toString())
        val dayTargets = (adjusted as? GateResult.Ready)?.let { r -> weightUsed?.let { foodTargetsOf(r, it, 0, profile.sex) } }
        val summary = daySummary(rows, dayTargets?.targets, hidden, null)
        val mealsLogged = rows.map { it.slot }.toSet().size

        // Workout card, or the weekly activity card for people who do not lift (H-2).
        val lifts = profile.interest != INTEREST_FOOD_ONLY
        val days = repo.programDays()
        val last = repo.lastWorkout()
        val finishedToday = last != null && java.time.Instant.ofEpochMilli(last.endedAt).atZone(zone()).toLocalDate() == day
        val open = repo.openWorkout()
        val workout = if (lifts) {
            workoutCard(days, last?.programDayId, finishedToday, open?.programDayId, open != null)
        } else {
            null
        }

        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weighedThisWeek = weights.any { (at, _) -> java.time.Instant.ofEpochMilli(at).atZone(zone()).toLocalDate() >= monday }
        val workoutToday = workout is WorkoutCard.Next || workout is WorkoutCard.Resume
        val tasks = todayTasks(
            weighDay = day.dayOfWeek == R_PTS_MEASURE_DEFAULT_DAY, weighedThisWeek = weighedThisWeek,
            workoutToday = workoutToday, workoutDone = lifts && finishedToday, mealsLogged = mealsLogged,
        )
        val summaryOfProgress = progress.summary()
        val active = progress.activeDays()

        _state.update {
            it.copy(
                loaded = true, failed = false, day = day, nickname = profile.nickname,
                level = summaryOfProgress.level.level, streakWeeks = summaryOfProgress.streak.current,
                workout = workout, activity = activityWeek(repo.weekActivities(day)), tasks = tasks,
                food = summary, target = target, recalc = prompt, week = weekStrip(active, day),
            )
        }
    }

    /** The first time, the weight and goal of onboarding are the ones the target stands on; remembered from then on. */
    private suspend fun ensureBasis(firstWeight: Double?, goalCode: String?): TargetBasis {
        val b = basisStore.load()
        if (b.weightKg != null && b.goal != null) return b
        val filled = b.copy(
            weightKg = b.weightKg ?: firstWeight, goal = b.goal ?: goalCode,
            askedKg = b.askedKg ?: firstWeight, askedGoal = b.askedGoal ?: goalCode,
        )
        if (filled != b && filled.weightKg != null) basisStore.save(filled)
        return filled
    }
}
