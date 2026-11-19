package app.xnkfit.domain.logger

import app.xnkfit.domain.training.FormRating
import app.xnkfit.domain.training.LoadKind
import app.xnkfit.domain.training.Pattern
import app.xnkfit.domain.training.R_TRN_BAND_MAX
import app.xnkfit.domain.training.R_TRN_BAND_MIN
import app.xnkfit.domain.training.R_TRN_REST_COMPOUND_MIN_S
import app.xnkfit.domain.training.R_TRN_REST_ISOLATION_MIN_S

// Logger rules (Design §4, PRD L-6…L-8, PR-2, NF-2). Pure Kotlin: what the next set is, how rest is timed, how a
// killed workout is resumed. Numbers are Rules.md R_TRN_LOGGER (👤) unless they come from an older rule.

/** L-6: both modes are first class; the user picks one at the first workout, neither is preselected (D-06). */
enum class LoggerMode(val code: String) {
    DIPANDU("dipandu"), BEBAS("bebas");

    companion object {
        fun of(code: String?): LoggerMode? = entries.firstOrNull { it.code == code }
    }
}

/** L-7 set kinds. There is no warm-up kind (R_TRN_NO_WARMUP): a set is a working set or it is not logged. */
enum class SetKind(val code: String) {
    NORMAL("normal"), DROP("drop"), FAILURE("failure");

    companion object {
        /** Unknown codes (an older backup, a hand-edited file) read as normal; never as a warm-up. */
        fun of(code: String?): SetKind = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

/** What the number fields of a set mean (R_TRN_BAND, R_TRN_LOGGER holds). */
enum class SetMeasure { KG_REPS, REPS, BAND_REPS, HOLD_SECONDS }

// R_TRN_LOGGER: exercises held for time, logged in seconds (Design §6 "open for B-07"; mockup "waktu tahan").
val R_TRN_LOGGER_HOLD_IDS: Set<String> = setOf("CORE_PLANK", "CORE_SIDEPLANK", "CORE_HOLLOW")

// R_TRN_LOGGER: stepper for a hold, seconds; first hold proposed when nothing is logged yet.
const val R_TRN_LOGGER_HOLD_STEP_S = 5
const val R_TRN_LOGGER_HOLD_START_S = 20

// R_TRN_LOGGER: "+15 dtk" on the rest bar.
const val R_TRN_LOGGER_REST_EXTEND_S = 15

// R_TRN_LOGGER: an unfinished workout started today or up to this many days before is offered "Lanjutkan" (Design §4).
const val R_TRN_LOGGER_RESUME_DAYS = 1

// R_TRN_LOGGER: reps stepper and the most reps or seconds a field takes (a typo guard, not a training limit).
const val R_TRN_LOGGER_REPS_STEP = 1
const val R_TRN_LOGGER_MAX_REPS = 100
const val R_TRN_LOGGER_MAX_HOLD_S = 600
const val R_TRN_LOGGER_MAX_KG = 500.0

const val R_TRN_LOGGER_MS_PER_S = 1000L

fun measureOf(exerciseId: String, loadKind: LoadKind, usesBand: Boolean): SetMeasure = when {
    exerciseId in R_TRN_LOGGER_HOLD_IDS -> SetMeasure.HOLD_SECONDS
    usesBand -> SetMeasure.BAND_REPS
    loadKind == LoadKind.WEIGHTED -> SetMeasure.KG_REPS
    else -> SetMeasure.REPS
}

/** R_TRN_REST via R_TRN_GEN: default rest for an exercise added by hand (compound 120 s, isolation 60 s). */
fun defaultRestSec(pattern: Pattern): Int =
    if (pattern.compound) R_TRN_REST_COMPOUND_MIN_S else R_TRN_REST_ISOLATION_MIN_S

/** One set row as the logger sees it. [slot] orders exercises; [index] is the set number inside the exercise (1 up). */
data class LoggerSet(
    val id: String,
    val slot: Int,
    val index: Int,
    val exerciseId: String,
    val kind: SetKind = SetKind.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val bandLevel: Int? = null,
    val holdSec: Int? = null,
    val form: FormRating? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    /** Same number = same superset (L-7). Null = on its own. */
    val supersetGroup: Int? = null,
    /** Rest after this set, seconds. */
    val restSec: Int,
)

/**
 * Dipandu (L-6): the next set to do. Supersets alternate (A1, B1, A2, B2, ...): sets are taken by the first slot of
 * their group, then the set number, then the slot. Exercises outside a superset are done one after another.
 */
fun nextSet(sets: List<LoggerSet>): LoggerSet? {
    val groupStart = sets.filter { it.supersetGroup != null }
        .groupBy { it.supersetGroup }
        .mapValues { (_, rows) -> rows.minOf { it.slot } }
    return sets.filter { !it.done }.minWithOrNull(
        compareBy<LoggerSet>({ it.supersetGroup?.let(groupStart::get) ?: it.slot }, { it.index }, { it.slot }),
    )
}

/**
 * Rest after [justDone]: none when the next set is another exercise of the same superset (the pair is done back to
 * back), else the set's own rest. [after] is the state with [justDone] already marked done.
 */
fun restAfter(justDone: LoggerSet, after: List<LoggerSet>): Int {
    val next = nextSet(after) ?: return justDone.restSec
    // Same round of the same superset, further down the list: go straight on. The round's last exercise rests.
    val sameSuperset = justDone.supersetGroup != null && next.supersetGroup == justDone.supersetGroup &&
        next.index == justDone.index && next.slot > justDone.slot
    return if (sameSuperset) 0 else justDone.restSec
}

/** Rest end as a clock time (Design §4: the end is stored, not a counter, so a killed app resumes it). */
fun restEndsAt(nowMs: Long, restSec: Int): Long? =
    if (restSec <= 0) null else nowMs + restSec * R_TRN_LOGGER_MS_PER_S

/** Seconds left, rounded up; 0 when over or not running. */
fun restRemainingSec(restEndsAt: Long?, nowMs: Long): Int {
    if (restEndsAt == null || restEndsAt <= nowMs) return 0
    return ((restEndsAt - nowMs + R_TRN_LOGGER_MS_PER_S - 1) / R_TRN_LOGGER_MS_PER_S).toInt()
}

/** "+15 dtk": from the current end, or from now when the timer already ran out. */
fun extendRest(restEndsAt: Long?, nowMs: Long): Long =
    maxOf(restEndsAt ?: nowMs, nowMs) + R_TRN_LOGGER_REST_EXTEND_S * R_TRN_LOGGER_MS_PER_S

enum class Unfinished { RESUME, CLOSE }

/** Design §4 step 4: started today or yesterday → "Lanjutkan latihan?"; older → closed with its last set. */
fun unfinishedAction(startedEpochDay: Long, todayEpochDay: Long): Unfinished =
    if (todayEpochDay - startedEpochDay <= R_TRN_LOGGER_RESUME_DAYS) Unfinished.RESUME else Unfinished.CLOSE

/** Close time of a stale workout: its last done set, or its start when nothing was done. */
fun closeTime(startedAt: Long, lastDoneAt: Long?): Long = maxOf(startedAt, lastDoneAt ?: startedAt)

/** Header numbers (mockup: Durasi, Volume kg, Set). Volume counts done weighted sets only. */
data class Totals(val doneSets: Int, val plannedSets: Int, val volumeKg: Double)

fun totals(sets: List<LoggerSet>): Totals = Totals(
    doneSets = sets.count { it.done },
    plannedSets = sets.size,
    volumeKg = sets.filter { it.done }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
)

/** Clamp a typed or stepped value into the field's range (null = empty field stays empty). */
fun clampKg(kg: Double?): Double? = kg?.coerceIn(0.0, R_TRN_LOGGER_MAX_KG)

fun clampReps(reps: Int?): Int? = reps?.coerceIn(0, R_TRN_LOGGER_MAX_REPS)

fun clampHold(sec: Int?): Int? = sec?.coerceIn(0, R_TRN_LOGGER_MAX_HOLD_S)

fun clampBand(level: Int?): Int? = level?.coerceIn(R_TRN_BAND_MIN, R_TRN_BAND_MAX)

/** A set can be ticked when the field its measure needs has a value (reps, seconds; kg may be 0 for an empty bar). */
fun canTick(set: LoggerSet, measure: SetMeasure): Boolean = when (measure) {
    SetMeasure.KG_REPS -> set.reps != null && set.weightKg != null
    SetMeasure.REPS -> set.reps != null
    SetMeasure.BAND_REPS -> set.reps != null && set.bandLevel != null
    SetMeasure.HOLD_SECONDS -> set.holdSec != null
}

/** Values a planned set starts with, so "Selesai set" is one tap when the user did what was proposed. */
data class Prefill(val weightKg: Double?, val reps: Int?, val bandLevel: Int?, val holdSec: Int?)

/**
 * Prefill for set [index] (1 up): the suggestion first (B-06 [app.xnkfit.domain.training.ItemTarget]), then the
 * same set of the previous workout, then the bottom of the rep range. Weight is never invented: with no suggestion
 * and no previous number the kg field stays empty and the user types it (R_TRN_RIR text explains how to choose).
 */
fun prefill(
    measure: SetMeasure,
    index: Int,
    suggestedKg: Double?,
    suggestedBand: Int?,
    repMin: Int?,
    previous: List<LoggerSet>,
): Prefill {
    val prev = previous.getOrNull(index - 1) ?: previous.lastOrNull()
    return when (measure) {
        SetMeasure.KG_REPS -> Prefill(suggestedKg ?: prev?.weightKg, prev?.reps ?: repMin, null, null)
        SetMeasure.REPS -> Prefill(null, prev?.reps ?: repMin, null, null)
        SetMeasure.BAND_REPS -> Prefill(null, prev?.reps ?: repMin, suggestedBand ?: prev?.bandLevel ?: R_TRN_BAND_MIN, null)
        SetMeasure.HOLD_SECONDS -> Prefill(null, null, null, prev?.holdSec ?: R_TRN_LOGGER_HOLD_START_S)
    }
}
