package app.xnkfit.domain.today

import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.rules.Macros
import app.xnkfit.domain.rules.PaceResult
import app.xnkfit.domain.rules.R_RECALC_KG
import app.xnkfit.domain.rules.R_WHO_MIN_TARGET
import app.xnkfit.domain.rules.WeeklyWeight
import app.xnkfit.domain.rules.macros
import app.xnkfit.domain.rules.moderateEquivalentMinutes
import app.xnkfit.domain.rules.paceCheck
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

// Hari ini (PRD H-1…H-3, M-2). Pure Kotlin: no Android, no clock, no database. The numbers come from
// `domain.rules` (targets, gates, R_RECALC, R_PACE, R_WHO_MIN); nothing is recalculated here.

// ---- Target card ---------------------------------------------------------------------------------------------

/** One number on the target card; each has its own "(i)" source (PRD S-7, R_DISCLAIMER). */
enum class TargetNumber { KCAL, PROTEIN, CARB, FAT, FIBRE }

/** Sources shown behind "(i)". The text lives in strings.xml; the IDs are the Rules.md §13 list. */
enum class SourceRef { MIFFLIN_S1, PAL_S2, NHLBI_S5, PROTEIN_S6, SURPLUS_S19, AKG_S8 }

/** What a number stands on. Carbohydrate is arithmetic (R_CARB), so it has no source of its own. */
fun sourcesFor(number: TargetNumber, goal: Goal): List<SourceRef> = when (number) {
    TargetNumber.KCAL -> listOf(SourceRef.MIFFLIN_S1, SourceRef.PAL_S2) + when (goal) {
        Goal.TURUN -> listOf(SourceRef.NHLBI_S5)
        Goal.NAIK -> listOf(SourceRef.SURPLUS_S19)
        Goal.JAGA -> emptyList()
    }
    TargetNumber.PROTEIN -> listOf(SourceRef.PROTEIN_S6)
    TargetNumber.FAT, TargetNumber.FIBRE -> listOf(SourceRef.AKG_S8)
    TargetNumber.CARB -> emptyList()
}

sealed interface TargetCard {
    /** No profile or the screening is not done (R_GATE_SCREEN): no number, one line saying why (H-3). */
    data object NeedsScreening : TargetCard

    /** A gate ended without a target (R_INPUT, R_GATE_U13): no number. */
    data object Unavailable : TargetCard

    /** R_GATE_ED: no calories, no macros, no weight talk. Only the note and the disclaimer. */
    data class NoNumbers(val notes: List<GateNote>) : TargetCard

    data class Numbers(
        val kcal: Int,
        val proteinG: Int,
        val carbG: Int,
        val fatG: Int,
        val fibreG: Int,
        /** The goal after every gate. Never [Goal.TURUN] when a gate forbids a deficit. */
        val goal: Goal,
        val notes: List<GateNote>,
        /** True when the target is shown as "perkiraan" because a pace raise was added (R_PACE). */
        val raisedByPace: Boolean,
    ) : TargetCard
}

/** R_PACE accepted by the user: +[raiseKcal] on a deficit target. Not for other goals and never in no-numbers mode. */
fun withPaceRaise(ready: GateResult.Ready, weightKg: Double, raiseKcal: Int): GateResult.Ready {
    if (raiseKcal <= 0 || ready.goalUsed != Goal.TURUN || ready.numbersHidden) return ready
    val kcal = ready.targetKcal + raiseKcal
    return ready.copy(targetKcal = kcal, macros = macros(kcal, weightKg))
}

/** R_DISCLAIMER and R_GATE_ED are decided here once, so the screen only draws what it is given. */
fun targetCardOf(result: GateResult?, raisedByPace: Boolean = false): TargetCard = when (result) {
    null, GateResult.ScreeningNeeded -> TargetCard.NeedsScreening
    GateResult.StopUnder13, is GateResult.InvalidInput -> TargetCard.Unavailable
    is GateResult.Ready ->
        if (result.numbersHidden) {
            TargetCard.NoNumbers(result.notes)
        } else {
            val m: Macros = result.macros
            TargetCard.Numbers(
                kcal = result.targetKcal, proteinG = m.proteinG, carbG = m.carbG, fatG = m.fatG,
                fibreG = result.fibreG, goal = result.goalUsed, notes = result.notes,
                raisedByPace = raisedByPace && result.goalUsed == Goal.TURUN,
            )
        }
}

// ---- Recalculation prompt (M-2) ------------------------------------------------------------------------------

enum class RecalcReason { WEIGHT_DOWN, WEIGHT_UP, GOAL_CHANGED, PACE }

data class RecalcPrompt(
    val reason: RecalcReason,
    /** Weight change in kg since the target was asked about, absolute. Null for the other reasons. */
    val deltaKg: Double? = null,
    /** R_PACE suggestion: calories to add. Null for the other reasons. */
    val raiseKcal: Int? = null,
)

/**
 * M-2, Design §7: ask when the goal differs from the one the target was computed with, when weight moved
 * ≥ R_RECALC_KG from the weight last answered for, or when R_PACE fires. One prompt at a time, in that order.
 * [askedKg] and [askedGoal] move forward when the user answers (accept or keep), so a kept target is not asked again
 * until it changes again. Never asks without a target, and R_PACE never in no-numbers mode.
 */
fun recalcPrompt(
    askedKg: Double?,
    latestKg: Double?,
    askedGoal: Goal?,
    goalNow: Goal?,
    numbersHidden: Boolean,
    pace: PaceResult,
): RecalcPrompt? {
    if (askedGoal != null && goalNow != null && askedGoal != goalNow) return RecalcPrompt(RecalcReason.GOAL_CHANGED)
    if (askedKg != null && latestKg != null && askedKg.isFinite() && latestKg.isFinite()) {
        val diff = latestKg - askedKg
        if (abs(diff) >= R_RECALC_KG - FLOAT_TOLERANCE) {
            return RecalcPrompt(if (diff < 0) RecalcReason.WEIGHT_DOWN else RecalcReason.WEIGHT_UP, abs(diff))
        }
    }
    if (!numbersHidden && pace is PaceResult.SuggestRaise) {
        return RecalcPrompt(RecalcReason.PACE, raiseKcal = pace.raiseKcal)
    }
    return null
}

private const val FLOAT_TOLERANCE = 1e-9 // float noise only, not a rule

/** Weekly weights for R_PACE: the last weight of each calendar week (Monday first), numbered by week. */
fun weeklyWeights(measures: List<Pair<Long, Double>>, zone: ZoneId): List<WeeklyWeight> {
    val origin = LocalDate.ofEpochDay(0).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    return measures.sortedBy { it.first }
        .groupBy { ChronoUnit.WEEKS.between(origin, Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt() }
        .map { (week, rows) -> WeeklyWeight(week, rows.last().second) }
        .sortedBy { it.weekIndex }
}

/** R_PACE read on the stored weights. Weights below the weekly step (one a week) give no suggestion. */
fun paceOf(measures: List<Pair<Long, Double>>, zone: ZoneId): PaceResult = paceCheck(weeklyWeights(measures, zone))

// ---- Workout card (H-1, H-2, H-3) ----------------------------------------------------------------------------

/** A day of the active program as the card needs it. */
data class ProgramDayInfo(val id: String, val name: String, val exercises: Int, val minutes: Int)

sealed interface WorkoutCard {
    /** H-3: no program yet. */
    data object NoProgram : WorkoutCard

    /** The next day to do. */
    data class Next(val day: ProgramDayInfo) : WorkoutCard

    /** An unfinished session exists (Design §4): offer to continue. */
    data class Resume(val day: ProgramDayInfo?) : WorkoutCard

    /** A session was finished today: rest, and say what comes next. */
    data class Rest(val next: ProgramDayInfo) : WorkoutCard
}

/**
 * The days cycle in order. The next day is the one after the last finished workout's day (the first when none is
 * known). A workout finished today makes today a rest day; the card then names the next day.
 */
fun workoutCard(
    days: List<ProgramDayInfo>,
    lastFinishedDayId: String?,
    finishedToday: Boolean,
    unfinishedDayId: String?,
    hasUnfinished: Boolean,
): WorkoutCard {
    if (days.isEmpty()) return WorkoutCard.NoProgram
    if (hasUnfinished) return WorkoutCard.Resume(days.firstOrNull { it.id == unfinishedDayId })
    val last = days.indexOfFirst { it.id == lastFinishedDayId }
    val next = days[(last + 1).mod(days.size)] // last = -1 gives index 0
    return if (finishedToday) WorkoutCard.Rest(next) else WorkoutCard.Next(next)
}

// ---- Weekly activity (H-2) -----------------------------------------------------------------------------------

/** Minutes this week against R_WHO_MIN. [minutes] is moderate-equivalent: vigorous counts double, light does not count. */
data class ActivityWeek(val minutes: Int, val targetMinutes: Int = R_WHO_MIN_TARGET) {
    val fraction: Float get() = (minutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
}

const val R_WHO_MIN_EFFORT_MODERATE = "moderate" // text-ok: stored code (Design §3 activity.effort)
const val R_WHO_MIN_EFFORT_VIGOROUS = "vigorous" // text-ok: stored code (Design §3 activity.effort)

fun activityWeek(rows: List<Pair<String, Int>>): ActivityWeek = ActivityWeek(
    moderateEquivalentMinutes(
        moderateMinutes = rows.filter { it.first == R_WHO_MIN_EFFORT_MODERATE }.sumOf { it.second },
        vigorousMinutes = rows.filter { it.first == R_WHO_MIN_EFFORT_VIGOROUS }.sumOf { it.second },
    ),
)

// ---- Tasks and week strip ------------------------------------------------------------------------------------

enum class TaskKind { WEIGH, WORKOUT, FOOD }

data class TodayTask(val kind: TaskKind, val done: Boolean)

/**
 * "Tugas hari ini": weigh on the weekly weigh day (R_PTS_MEASURE day) until a weight is noted that week; the workout
 * when today has one; food when at least one meal is logged. No other task, no count of misses.
 */
fun todayTasks(
    weighDay: Boolean,
    weighedThisWeek: Boolean,
    workoutToday: Boolean,
    workoutDone: Boolean,
    mealsLogged: Int,
): List<TodayTask> = buildList {
    if (weighDay || weighedThisWeek) add(TodayTask(TaskKind.WEIGH, weighedThisWeek))
    if (workoutToday || workoutDone) add(TodayTask(TaskKind.WORKOUT, workoutDone))
    add(TodayTask(TaskKind.FOOD, mealsLogged > 0))
}

/** Seven flags, Monday first, for the week strip. */
fun weekStrip(active: Set<LocalDate>, today: LocalDate): List<Boolean> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map { monday.plusDays(it) in active }
}
