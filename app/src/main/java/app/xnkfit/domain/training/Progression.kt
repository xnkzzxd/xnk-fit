package app.xnkfit.domain.training

import app.xnkfit.domain.rules.GateResult

// Progression, return after absence, review and own routines (Design §6; PRD L-2, L-4, L-5, L-12, L-16).
// Pure Kotlin. Every proposal carries a [Reason] the UI shows as one line (text in strings.xml, NF-3).

/** One-line reason codes (L-4). Numbers needed by the text travel in the [Reason] fields. */
enum class ReasonCode {
    /** No log yet for this exercise: start inside the range, pick a weight with 1–3 reps in reserve (R_TRN_RIR). */
    FIRST_TIME,
    /** Not every working set reached the top of the range yet: same load, aim for more reps. */
    MORE_REPS,
    /** Every set at the top with form "rapi": load goes up by [Reason.stepKg] (R_TRN_PROG_UP/LEG/STEP). */
    ADD_LOAD,
    /** The smallest step is over 10 %: needs the top + [Reason.reps] reps on every set first (R_TRN_PROG_STEP). */
    NEED_EXTRA_REPS,
    /** "Ragu" in the last sessions: load held until two "rapi" in a row (R_TRN_FORM). Reps and sets may rise. */
    FORM_HOLD,
    /** Bodyweight or band at the top: one more set (R_TRN_PROG_BW). */
    ADD_SET,
    /** At the top with the most sets: the next harder variant (R_TRN_PROG_BW). */
    HARDER_VARIANT,
    /** Band at the top: next band level (R_TRN_BAND). */
    BAND_UP,
    /** First session after a break: lighter load, one set fewer (R_TRN_RETURN ⚠ G9). Shown quietly, no banner (L-16). */
    RETURN_AFTER_BREAK,
    /** Review: the training level went up (R_TRN_LEVELS), so the program follows the new level. */
    LEVEL_UP,
    /** Review: the rules or the equipment give a different program than the current one. */
    PROGRAM_UPDATED,
}

data class Reason(val code: ReasonCode, val stepKg: Double? = null, val reps: Int? = null, val sessions: Int? = null)

/** What the logger proposes for one exercise in the next session (weight suggestion, L-7). */
data class ItemTarget(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    /** Weighted lifts only; null when there is no log yet (the user picks). */
    val loadKg: Double?,
    /** Bands only (R_TRN_BAND). */
    val bandLevel: Int?,
    val reason: Reason,
)

/** Working sets of one exercise in one session. */
private data class Done(val epochDay: Long, val sets: List<LoggedSet>) {
    val rating: FormRating = if (sets.any { it.form == FormRating.RAGU }) FormRating.RAGU else FormRating.RAPI
    val topLoad: Double? = sets.mapNotNull { it.weightKg }.maxOrNull()
    val band: Int? = sets.mapNotNull { it.bandLevel }.maxOrNull()
    fun allAtLeast(reps: Int, count: Int): Boolean = sets.size >= count && sets.all { (it.reps ?: 0) >= reps }
}

private fun doneFor(history: TrainingHistory, exerciseId: String): List<Done> = history.ordered.mapNotNull { s ->
    val sets = s.sets.filter { it.exerciseId == exerciseId && it.done }
    if (sets.isEmpty()) null else Done(s.epochDay, sets)
}

/**
 * Targets for the next session of [day]. Uses the last log of each exercise; after a gap of R_TRN_RETURN days since
 * the last session of any kind, the eased restart replaces progression for that session.
 */
fun sessionTargets(
    day: ProgramDay,
    level: TrainingLevel,
    history: TrainingHistory,
    todayEpochDay: Long,
    library: List<Exercise>,
): List<ItemTarget> {
    val byId = library.associateBy { it.id }
    val gap = history.lastEpochDay?.let { (todayEpochDay - it).toInt() }
    return day.items.map { item ->
        val ex = byId[item.exerciseId]
        val log = doneFor(history, item.exerciseId)
        val last = log.lastOrNull()
        when {
            ex == null || last == null ->
                ItemTarget(item.exerciseId, item.sets, item.repMin, item.repMax, null, null, Reason(ReasonCode.FIRST_TIME))
            gap != null && gap >= R_TRN_RETURN_GAP_DAYS -> returnTarget(item, ex, last, gap)
            else -> progressTarget(item, ex, log, level)
        }
    }
}

/** R_TRN_RETURN (⚠ G9): 60 % of the last load, rounded down to the equipment step, one set fewer (never below one). */
private fun returnTarget(item: ProgramItem, ex: Exercise, last: Done, gap: Int): ItemTarget {
    val step = ex.equipmentStepKg
    val lastSets = maxOf(item.sets, last.sets.size)
    val load = last.topLoad
    val plan = returnAfterAbsence(gap, load ?: 0.0, lastSets, step ?: R_TRN_PROG_STEP_DUMBBELL_KG)
    val sets = plan?.sets ?: lastSets
    return ItemTarget(
        exerciseId = item.exerciseId, sets = sets, repMin = item.repMin, repMax = item.repMax,
        loadKg = if (ex.loadKind == LoadKind.WEIGHTED && load != null) plan?.loadKg else null,
        bandLevel = last.band, reason = Reason(ReasonCode.RETURN_AFTER_BREAK),
    )
}

/** Double progression for weights, reps → set → variant for bodyweight, band level + reps for bands. */
private fun progressTarget(item: ProgramItem, ex: Exercise, log: List<Done>, level: TrainingLevel): ItemTarget {
    val last = log.last()
    val maxSets = setsRange(level).last
    val sets = maxOf(item.sets, last.sets.size).coerceAtMost(maxSets)
    val atTop = last.allAtLeast(item.repMax, item.sets)
    val mayRise = loadMayRise(log.map { it.rating })
    fun target(sets: Int, load: Double?, band: Int?, reason: Reason) =
        ItemTarget(item.exerciseId, sets, item.repMin, item.repMax, load, band, reason)

    if (ex.loadKind == LoadKind.WEIGHTED) {
        val load = last.topLoad
        val step = ex.equipmentStepKg
        if (load == null || step == null || !atTop) return target(sets, load, null, Reason(ReasonCode.MORE_REPS))
        if (!mayRise) return target(sets, load, null, Reason(ReasonCode.FORM_HOLD))
        val proposal = loadStep(load, ex.pattern.legs, step) ?: return target(sets, load, null, Reason(ReasonCode.MORE_REPS))
        if (proposal.extraRepsNeeded > 0 && !last.allAtLeast(item.repMax + proposal.extraRepsNeeded, item.sets)) {
            return target(sets, load, null, Reason(ReasonCode.NEED_EXTRA_REPS, proposal.kg, proposal.extraRepsNeeded))
        }
        return target(sets, load + proposal.kg, null, Reason(ReasonCode.ADD_LOAD, proposal.kg))
    }

    // Bands: band level first (it is the "load"), then the bodyweight steps once the strongest band is reached.
    if (ex.usesBand) {
        val band = last.band ?: R_TRN_BAND_MIN
        if (!atTop) return target(sets, null, band, Reason(ReasonCode.MORE_REPS))
        if (band < R_TRN_BAND_MAX) {
            return if (mayRise) target(sets, null, band + 1, Reason(ReasonCode.BAND_UP))
            else target(sets, null, band, Reason(ReasonCode.FORM_HOLD))
        }
    }
    val band = if (ex.usesBand) last.band ?: R_TRN_BAND_MIN else null
    return when (bodyweightStep(atTop, sets, maxSets)) {
        BodyweightStep.MORE_REPS -> target(sets, null, band, Reason(ReasonCode.MORE_REPS))
        BodyweightStep.ADD_SET -> target(sets + 1, null, band, Reason(ReasonCode.ADD_SET))
        // The swap itself is a review change (the user accepts it); until then the session stays as it is.
        BodyweightStep.HARDER_VARIANT ->
            target(sets, null, band, Reason(if (mayRise) ReasonCode.HARDER_VARIANT else ReasonCode.FORM_HOLD))
    }
}

/** R_TRN_PROG_BW: the harder variant of [ex] that the equipment and the program level allow, if any. */
fun harderVariant(ex: Exercise, library: List<Exercise>, equipment: Equipment, level: TrainingLevel): Exercise? =
    library.firstOrNull { it.variantOf == ex.id && it.pattern == ex.pattern && it.fits(level) && equipment.allows(it) }

// ---------------------------------------------------------------- Review (L-4, R_TRN_REVIEW)

enum class ChangeKind { SWAP_EXERCISE, SETS, REPS, REPLACE_PROGRAM }

/** One proposed change; the user accepts or declines each (L-4). Positions refer to the current program. */
data class ProgramChange(
    val kind: ChangeKind,
    val dayPosition: Int,
    val itemPosition: Int,
    val fromExerciseId: String? = null,
    val toExerciseId: String? = null,
    val fromSets: Int? = null,
    val toSets: Int? = null,
    val fromReps: IntRange? = null,
    val toReps: IntRange? = null,
    val reason: Reason,
)

sealed interface ReviewResult {
    /** L-5: a program with source `own` is never changed by the generator. */
    data object OwnRoutine : ReviewResult

    /** L-5: auto-programming turned off by the user. */
    data object AutoOff : ReviewResult

    /** R_TRN_REVIEW: not yet 4 weeks and not yet 3 sessions since the last review. */
    data object NotDue : ReviewResult

    /** The gates no longer pass (for example screening reopened): nothing is proposed. */
    data class Blocked(val gate: GateResult) : ReviewResult

    /** [proposed] is the program after every change is accepted; [changes] may be empty. */
    data class Proposals(val changes: List<ProgramChange>, val proposed: Program) : ReviewResult
}

/** State of the review clock (stored with the program). */
data class ReviewClock(val lastReviewEpochDay: Long, val sessionsSinceReview: Int)

/**
 * R_TRN_REVIEW: every 4 weeks or after 3 completed sessions, rerun the generator with the log and return a diff
 * (Design §6). Bodyweight and band items at the top with the most sets get their harder variant proposed.
 */
fun review(
    current: Program,
    autoEnabled: Boolean,
    profile: TrainingProfile,
    equipment: Equipment,
    history: TrainingHistory,
    library: List<Exercise>,
    todayEpochDay: Long,
    clock: ReviewClock,
): ReviewResult {
    if (current.source == ProgramSource.OWN) return ReviewResult.OwnRoutine
    if (!autoEnabled) return ReviewResult.AutoOff
    val weeks = ((todayEpochDay - clock.lastReviewEpochDay).coerceAtLeast(0) / R_TRN_LEVELS_DAYS_PER_WEEK).toInt()
    if (!reviewDue(weeks, clock.sessionsSinceReview)) return ReviewResult.NotDue
    val fresh = when (val r = generate(profile, equipment, current.focus, history, library, todayEpochDay)) {
        is GenerateResult.Blocked -> return ReviewResult.Blocked(r.gate)
        is GenerateResult.Generated -> r.program
    }

    val sameShape = fresh.days.size == current.days.size &&
        fresh.days.zip(current.days).all { (a, b) -> a.name == b.name && a.items.size == b.items.size }
    val generalReason = Reason(if (fresh.level.ordinal > current.level.ordinal) ReasonCode.LEVEL_UP else ReasonCode.PROGRAM_UPDATED)
    if (!sameShape) {
        return ReviewResult.Proposals(listOf(ProgramChange(ChangeKind.REPLACE_PROGRAM, 0, 0, reason = generalReason)), fresh)
    }

    val byId = library.associateBy { it.id }
    val changes = ArrayList<ProgramChange>()
    val proposedDays = current.days.mapIndexed { d, day ->
        val items = day.items.mapIndexed { i, old ->
            var new = fresh.days[d].items[i]
            // Keep the user's progress on sets when the generator would give fewer for the same exercise.
            if (new.exerciseId == old.exerciseId && old.sets > new.sets && old.sets <= setsRange(fresh.level).last) {
                new = new.copy(sets = old.sets)
            }
            // R_TRN_PROG_BW: harder variant for a bodyweight/band item that is ready for it.
            val ex = byId[old.exerciseId]
            if (ex != null && new.exerciseId == old.exerciseId && old.loadKind == LoadKind.BODYWEIGHT_OR_BAND) {
                val ready = readyForHarderVariant(old, history, fresh.level)
                val harder = if (ready != null) harderVariant(ex, library, equipment, fresh.level) else null
                if (harder != null && ready != null) {
                    new = ItemContext(fresh.level, current.focus).item(harder, old.role, old.fillFor)
                    changes += ProgramChange(
                        ChangeKind.SWAP_EXERCISE, d, i, old.exerciseId, harder.id,
                        reason = Reason(ReasonCode.HARDER_VARIANT, reps = old.repMax, sessions = ready),
                    )
                    return@mapIndexed new
                }
            }
            if (new.exerciseId != old.exerciseId) {
                changes += ProgramChange(ChangeKind.SWAP_EXERCISE, d, i, old.exerciseId, new.exerciseId, reason = generalReason)
            } else {
                if (new.sets != old.sets) {
                    changes += ProgramChange(ChangeKind.SETS, d, i, fromSets = old.sets, toSets = new.sets, reason = generalReason)
                }
                if (new.repMin != old.repMin || new.repMax != old.repMax) {
                    changes += ProgramChange(
                        ChangeKind.REPS, d, i, fromReps = old.repMin..old.repMax, toReps = new.repMin..new.repMax,
                        reason = generalReason,
                    )
                }
            }
            new
        }
        day.copy(items = items, estMinutes = estMinutes(items))
    }
    val proposedItems = proposedDays.flatMap { it.items }
    val proposed = fresh.copy(days = proposedDays, weeklySets = weeklySets(proposedItems, library))
    return ReviewResult.Proposals(changes, proposed)
}

/**
 * Sessions in a row (most recent first) where every working set of the item reached the top with "rapi" and the
 * most sets of the level were done; null when the last session was not like that.
 */
private fun readyForHarderVariant(item: ProgramItem, history: TrainingHistory, level: TrainingLevel): Int? {
    val maxSets = setsRange(level).last
    val log = doneFor(history, item.exerciseId)
    if (log.isEmpty() || !loadMayRise(log.map { it.rating })) return null
    val inRow = log.reversed().takeWhile { it.allAtLeast(item.repMax, maxSets) && it.rating == FormRating.RAPI }.size
    return inRow.takeIf { it > 0 }
}

/** Applies the accepted changes of a review; declined ones keep the current item (L-4). */
fun applyChanges(current: Program, review: ReviewResult.Proposals, accepted: Set<ProgramChange>): Program {
    if (current.source == ProgramSource.OWN) return current // L-5
    if (review.changes.any { it.kind == ChangeKind.REPLACE_PROGRAM && it in accepted }) return review.proposed
    val days = current.days.mapIndexed { d, day ->
        val items = day.items.mapIndexed { i, old ->
            val mine = review.changes.filter { it.dayPosition == d && it.itemPosition == i && it in accepted }
            if (mine.isEmpty()) return@mapIndexed old
            val proposed = review.proposed.days[d].items[i]
            var item = old
            for (c in mine) {
                item = when (c.kind) {
                    ChangeKind.SWAP_EXERCISE -> proposed
                    ChangeKind.SETS -> item.copy(sets = proposed.sets)
                    ChangeKind.REPS -> item.copy(repMin = proposed.repMin, repMax = proposed.repMax, restSec = proposed.restSec)
                    ChangeKind.REPLACE_PROGRAM -> item
                }
            }
            item
        }
        day.copy(items = items, estMinutes = estMinutes(items))
    }
    val anyAccepted = review.changes.any { it in accepted }
    return current.copy(
        days = days,
        level = if (anyAccepted) review.proposed.level else current.level,
        rulesVersion = if (anyAccepted) review.proposed.rulesVersion else current.rulesVersion,
    )
}

// ---------------------------------------------------------------- Own routines (L-5)

/** L-5: editing an `auto` program turns it into `own` on save; `own` stays `own`. */
fun saveEdited(program: Program): Program = program.copy(source = ProgramSource.OWN)

/**
 * L-5: the program to keep after a regenerate request. An `own` (or Jelajahi) program, or auto-programming off,
 * keeps the active program exactly as it is; otherwise the newly generated auto program replaces it (null when the
 * gates block a program).
 */
fun regenerateOrKeep(active: Program?, autoEnabled: Boolean, generated: GenerateResult): Program? {
    if (active != null && (active.source != ProgramSource.AUTO || !autoEnabled)) return active
    // PR-5: when the gates no longer pass, no auto program is kept or made.
    return (generated as? GenerateResult.Generated)?.program
}
