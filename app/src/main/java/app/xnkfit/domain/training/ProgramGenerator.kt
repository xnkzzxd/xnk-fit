package app.xnkfit.domain.training

import app.xnkfit.domain.rules.GateResult
import kotlin.math.ceil

// Program generator (Design §6; PRD L-1, L-3, L-5, L-7, L-17; Rules.md §5). Pure function, no clock, no files:
// the library and "today" (epoch day) are passed in. Same input → same program, so golden tests can pin it.

// R_TRN_GEN: version of the generator rules written on `program.rulesVersion` (Design §3).
const val R_TRN_GEN_VERSION = 1

// R_TRN_GEN: exercises per session by the length the user picked (mockup latihan.html: 45 min → 5 exercises).
// Lengths offered: 30, 45, 60 min. A length between two keys takes the lower one; under 30 takes 30.
val R_TRN_GEN_SLOTS_BY_MINUTES: Map<Int, Int> = mapOf(30 to 4, 45 to 5, 60 to 6)

// R_TRN_GEN: seconds of work per set, used only for the "±N menit" estimate on the day card (not a training rule).
const val R_TRN_GEN_EST_WORK_S = 45
const val R_TRN_GEN_SECONDS_PER_MINUTE = 60

// R_TRN_GEN: patterns that take compound rest and may take the strength range (R_TRN_REST, R_TRN_REPS).
val R_TRN_GEN_COMPOUND_PATTERNS: Set<Pattern> =
    setOf(Pattern.SQUAT, Pattern.HINGE, Pattern.PUSH_H, Pattern.PUSH_V, Pattern.PULL_H, Pattern.PULL_V)

// R_TRN_GEN: patterns never put in a generated program. Cardio is logged as activity (L-14); carries train grip
// and trunk, which have no weekly minimum (R_TRN_VOL_SCOPE). Both stay in Jelajahi and in own routines.
val R_TRN_GEN_EXCLUDED_PATTERNS: Set<Pattern> = setOf(Pattern.CARDIO, Pattern.CARRY)

// R_TRN_WEEK: an upper/lower split is used at exactly this many days (level 2 and up only).
const val R_TRN_WEEK_SPLIT_DAYS = R_TRN_WEEK_INTERMEDIATE_MAX

/** What the user answered in the first training profile (mockup latihan.html, 3 questions). */
enum class Experience { BARU_MULAI, BEBERAPA_BULAN, LEBIH_SETAHUN }

/** Rep focus for level 2 and up (R_TRN_REPS); level 1 and bodyweight ignore it. */
enum class GoalFocus { OTOT, KEKUATAN }

/** Inputs about the person. [gate] is the result of the safety gates (Rules.md §3); only `Ready` gets a program. */
data class TrainingProfile(
    val gate: GateResult,
    val experience: Experience,
    val daysPerWeek: Int,
    val sessionMinutes: Int,
)

enum class ProgramSource { AUTO, OWN, JELAJAHI }

enum class DayName { FULL_A, FULL_B, FULL_C, UPPER_A, LOWER_A, UPPER_B, LOWER_B }

/** Why the item is in the program; the UI turns it into text (NF-3). */
enum class ItemRole { MAIN, ACCESSORY, VOLUME_FILL }

data class ProgramItem(
    val exerciseId: String,
    val pattern: Pattern,
    val role: ItemRole,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int,
    val loadKind: LoadKind,
    val usesBand: Boolean,
    /** For [ItemRole.VOLUME_FILL]: the group it was added for. */
    val fillFor: MuscleGroup? = null,
    /** "Kenapa gerakan ini" (L-11), from content. */
    val whyHint: String? = null,
) {
    /** R_TRN_NO_WARMUP: programs carry working sets only; there is no warm-up set kind. */
    val warmupSets: Int get() = R_TRN_NO_WARMUP_SETS
}

data class ProgramDay(val position: Int, val name: DayName, val estMinutes: Int, val items: List<ProgramItem>)

/** L-3: a pattern the equipment cannot carry; [needs] lists equipment that would make it possible. */
data class MissingPattern(val pattern: Pattern, val needs: Set<String>)

data class Program(
    val source: ProgramSource,
    val setting: EquipmentSetting,
    val level: TrainingLevel,
    val focus: GoalFocus,
    val rulesVersion: Int,
    val days: List<ProgramDay>,
    val missing: List<MissingPattern>,
    /** R_TRN_VOL_COUNT: weekly sets per group, all 16 groups. */
    val weeklySets: Map<MuscleGroup, Double>,
    /** R_TRN_VOL_FILL / L-17: groups still under their minimum because the equipment cannot reach them. */
    val underMinimum: Set<MuscleGroup>,
)

sealed interface GenerateResult {
    /** No program: the safety gates did not pass (R_GATE_U13, R_INPUT, R_GATE_SCREEN). */
    data class Blocked(val gate: GateResult) : GenerateResult

    data class Generated(val program: Program) : GenerateResult
}

/** Logged history, as far as the generator needs it. */
data class LoggedSet(
    val exerciseId: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val bandLevel: Int? = null,
    val form: FormRating? = null,
    val done: Boolean = true,
)

data class LoggedSession(val epochDay: Long, val sets: List<LoggedSet>)

data class TrainingHistory(val sessions: List<LoggedSession> = emptyList()) {
    val ordered: List<LoggedSession> get() = sessions.sortedBy { it.epochDay }
    val lastEpochDay: Long? get() = sessions.maxOfOrNull { it.epochDay }
    val firstEpochDay: Long? get() = sessions.minOfOrNull { it.epochDay }

    /** R_TRN_LEVELS: whole weeks since the first logged session. */
    fun weeksTrained(todayEpochDay: Long): Int {
        val first = firstEpochDay ?: return 0
        return ((todayEpochDay - first).coerceAtLeast(0) / R_TRN_LEVELS_DAYS_PER_WEEK).toInt()
    }
}

// R_TRN_LEVELS: days in a week, to turn logged days into weeks trained.
const val R_TRN_LEVELS_DAYS_PER_WEEK = 7

/** R_TRN_LEVELS: start level from the sign-up experience ("Beberapa bulan" is past the 8 beginner weeks). */
fun startLevel(experience: Experience): TrainingLevel = when (experience) {
    Experience.BARU_MULAI -> TrainingLevel.L1
    Experience.BEBERAPA_BULAN -> TrainingLevel.L2
    Experience.LEBIH_SETAHUN -> TrainingLevel.L3
}

/** R_TRN_LEVEL1 / R_GATE_U18: risk answers and under-18 light mode keep level 1; otherwise experience + weeks. */
fun programLevel(gate: GateResult.Ready, experience: Experience, history: TrainingHistory, todayEpochDay: Long): TrainingLevel {
    if (gate.program.levelOneOnly || gate.program.lightMode) return TrainingLevel.L1
    return levelAfterWeeks(startLevel(experience), history.weeksTrained(todayEpochDay))
}

/** R_TRN_WEEK: level 1 is kept at 2–3 days, level 2 and up at 2–4 (fewer days than the table is the safe side). */
fun trainingDays(level: TrainingLevel, wanted: Int): Int {
    val max = if (level == TrainingLevel.L1) R_TRN_WEEK_BEGINNER_MAX else R_TRN_WEEK_INTERMEDIATE_MAX
    return wanted.coerceIn(R_TRN_WEEK_BEGINNER_MIN, max)
}

/** R_TRN_GEN: exercises per session for the picked length. */
fun slotsFor(minutes: Int): Int {
    val keys = R_TRN_GEN_SLOTS_BY_MINUTES.keys.sorted()
    val key = keys.lastOrNull { it <= minutes } ?: keys.first()
    return R_TRN_GEN_SLOTS_BY_MINUTES.getValue(key)
}

private enum class Slot { M, A }

private data class TemplateEntry(val pattern: Pattern, val slot: Slot)

private fun t(pattern: Pattern, slot: Slot) = TemplateEntry(pattern, slot)

// Day templates, most important first; a session takes the first `slotsFor(minutes)` entries.
// Full body: lower, push, pull, second lower, core, then one small muscle (mockup "Full Body C").
private val fullBody: List<Pair<DayName, List<TemplateEntry>>> = listOf(
    DayName.FULL_A to listOf(
        t(Pattern.SQUAT, Slot.M), t(Pattern.PUSH_H, Slot.M), t(Pattern.PULL_H, Slot.M),
        t(Pattern.HINGE, Slot.A), t(Pattern.CORE, Slot.M), t(Pattern.TRI, Slot.M),
    ),
    DayName.FULL_B to listOf(
        t(Pattern.HINGE, Slot.M), t(Pattern.PUSH_V, Slot.M), t(Pattern.PULL_V, Slot.M),
        t(Pattern.SQUAT, Slot.A), t(Pattern.CORE, Slot.A), t(Pattern.CURL, Slot.M),
    ),
    DayName.FULL_C to listOf(
        t(Pattern.SQUAT, Slot.M), t(Pattern.PUSH_H, Slot.A), t(Pattern.PULL_V, Slot.A),
        t(Pattern.HINGE, Slot.M), t(Pattern.CORE, Slot.M), t(Pattern.LEG_ISO, Slot.M),
    ),
)

// Upper/lower split for 4 days (R_TRN_WEEK intermediate).
private val upperLower: List<Pair<DayName, List<TemplateEntry>>> = listOf(
    DayName.UPPER_A to listOf(
        t(Pattern.PUSH_H, Slot.M), t(Pattern.PULL_H, Slot.M), t(Pattern.PUSH_V, Slot.M),
        t(Pattern.PULL_V, Slot.M), t(Pattern.TRI, Slot.M), t(Pattern.CURL, Slot.M),
    ),
    DayName.LOWER_A to listOf(
        t(Pattern.SQUAT, Slot.M), t(Pattern.HINGE, Slot.A), t(Pattern.LEG_ISO, Slot.M),
        t(Pattern.CORE, Slot.M), t(Pattern.SQUAT, Slot.A), t(Pattern.LEG_ISO, Slot.A),
    ),
    DayName.UPPER_B to listOf(
        t(Pattern.PUSH_V, Slot.A), t(Pattern.PULL_V, Slot.A), t(Pattern.PUSH_H, Slot.A),
        t(Pattern.PULL_H, Slot.A), t(Pattern.CURL, Slot.A), t(Pattern.TRI, Slot.A),
    ),
    DayName.LOWER_B to listOf(
        t(Pattern.HINGE, Slot.M), t(Pattern.SQUAT, Slot.A), t(Pattern.CORE, Slot.A),
        t(Pattern.LEG_ISO, Slot.A), t(Pattern.HINGE, Slot.A), t(Pattern.LEG_ISO, Slot.M),
    ),
)

/** Main and accessory choice for each pattern (Design §6 step 2). */
private data class Choice(val main: Exercise, val accessory: Exercise)

/**
 * Design §6: `generate(profile, equipment, goalFocus, history) -> Program`, plus the library and today's epoch day
 * (the domain has no files and no clock).
 */
fun generate(
    profile: TrainingProfile,
    equipment: Equipment,
    goalFocus: GoalFocus,
    history: TrainingHistory,
    library: List<Exercise>,
    todayEpochDay: Long,
): GenerateResult {
    // PR-5 / R_GATE_SCREEN: no program before the gates pass.
    val gate = profile.gate as? GateResult.Ready ?: return GenerateResult.Blocked(profile.gate)
    val level = programLevel(gate, profile.experience, history, todayEpochDay)
    val days = trainingDays(level, profile.daysPerWeek)
    val slots = slotsFor(profile.sessionMinutes)
    val pool = library.filter { it.pattern !in R_TRN_GEN_EXCLUDED_PATTERNS && it.fits(level) && equipment.allows(it) }

    val choices = HashMap<Pattern, Choice>()
    val missing = ArrayList<MissingPattern>()
    val templates = if (days == R_TRN_WEEK_SPLIT_DAYS && level != TrainingLevel.L1) upperLower else fullBody.take(days)
    val ctx = ItemContext(level, goalFocus)
    val dayItems: MutableList<Pair<DayName, MutableList<ProgramItem>>> = templates.map { (name, entries) ->
        val items: MutableList<ProgramItem> = ArrayList()
        for (entry in entries) {
            if (items.size >= slots) break
            val choice = choices[entry.pattern] ?: choose(pool, entry.pattern)?.also { choices[entry.pattern] = it }
            if (choice == null) {
                if (missing.none { it.pattern == entry.pattern }) {
                    missing += MissingPattern(entry.pattern, needsFor(entry.pattern, library, level, equipment))
                }
                continue // L-3: never filled with another pattern
            }
            val ex = if (entry.slot == Slot.M) choice.main else choice.accessory
            if (items.any { it.exerciseId == ex.id }) continue
            items += ctx.item(ex, if (entry.slot == Slot.M) ItemRole.MAIN else ItemRole.ACCESSORY)
        }
        name to items
    }.toMutableList()

    val under = fillVolume(dayItems, pool, ctx)
    val programDays = dayItems.mapIndexed { i, (name, items) -> ProgramDay(i, name, estMinutes(items), items.toList()) }
    val program = Program(
        source = ProgramSource.AUTO,
        setting = equipment.setting,
        level = level,
        focus = goalFocus,
        rulesVersion = R_TRN_GEN_VERSION,
        days = programDays,
        missing = missing,
        weeklySets = weeklySets(programDays.flatMap { it.items }, library),
        underMinimum = under,
    )
    return GenerateResult.Generated(program)
}

/** Sets, reps and rest for one exercise from the level table (Design §6 step 3). */
internal class ItemContext(val level: TrainingLevel, val focus: GoalFocus) {
    val sets: IntRange = setsRange(level)

    fun item(ex: Exercise, role: ItemRole, fillFor: MuscleGroup? = null): ProgramItem {
        val ranges = repRanges(level, ex.loadKind)
        // R_TRN_REPS: the strength range only for a weighted multi-joint main lift at level 2+ with focus KEKUATAN.
        val strength = focus == GoalFocus.KEKUATAN && role == ItemRole.MAIN && ex.pattern.compound && ranges.size > 1
        val reps = if (strength) ranges.first() else ranges.last()
        // R_TRN_REST: compound 2 min (3 min for strength work), isolation 60 s.
        val rest = when {
            !ex.pattern.compound -> R_TRN_REST_ISOLATION_MIN_S
            strength -> R_TRN_REST_COMPOUND_MAX_S
            else -> R_TRN_REST_COMPOUND_MIN_S
        }
        // Level 1 starts at the bottom of R_TRN_SETS (lighter); R_TRN_VOL_FILL may add sets inside the range.
        return ProgramItem(
            exerciseId = ex.id, pattern = ex.pattern, role = role, sets = sets.first,
            repMin = reps.first, repMax = reps.last, restSec = rest, loadKind = ex.loadKind, usesBand = ex.usesBand,
            fillFor = fillFor, whyHint = ex.hintId,
        )
    }
}

/**
 * Design §6 step 2: main = first entry in library order that the level and equipment allow (the library lists the
 * base lift of each pattern first); accessory = the entry whose main muscles overlap least with the main's, first in
 * library order on a tie (else the main again). Less overlap spreads the week over more groups (R_TRN_VOL).
 */
private fun choose(pool: List<Exercise>, pattern: Pattern): Choice? {
    val candidates = pool.filter { it.pattern == pattern }
    if (candidates.isEmpty()) return null
    val main = candidates.first()
    val rest = candidates.filter { it.id != main.id }
    val accessory = rest.minByOrNull { (it.main intersect main.main).size } ?: main
    return Choice(main, accessory)
}

/**
 * L-3: equipment that would open the pattern at this level: the missing items of the entries that need the fewest
 * extra items (home settings look at home entries only), for a sentence like "Butuh palang atau karet".
 */
private fun needsFor(pattern: Pattern, library: List<Exercise>, level: TrainingLevel, equipment: Equipment): Set<String> {
    val home = equipment.setting == EquipmentSetting.HOME_BW || equipment.setting == EquipmentSetting.HOME_DB
    val homeCodes = setOf(EquipmentSetting.HOME_BW.code, EquipmentSetting.HOME_DB.code)
    val lacking = library
        .filter { it.pattern == pattern && it.fits(level) }
        .filter { !home || it.settings.any { s -> s in homeCodes } }
        .map { it.equipment - equipment.items }
        .filter { it.isNotEmpty() }
    val fewest = lacking.minOfOrNull { it.size } ?: return emptySet()
    return lacking.filter { it.size == fewest }.flatten().toSortedSet()
}

/** R_TRN_VOL_COUNT over a week (each program day is done once a week). */
fun weeklySets(items: List<ProgramItem>, library: List<Exercise>): Map<MuscleGroup, Double> {
    val byId = library.associateBy { it.id }
    val totals = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
    for (item in items) {
        val ex = byId[item.exerciseId] ?: continue
        ex.main.forEach { totals[it] = totals.getValue(it) + setsCredited(item.sets, true) }
        (ex.helper - ex.main).forEach { totals[it] = totals.getValue(it) + setsCredited(item.sets, false) }
    }
    return totals
}

private fun credit(ex: Exercise, group: MuscleGroup, sets: Int): Double = when (group) {
    in ex.main -> setsCredited(sets, true)
    in ex.helper -> setsCredited(sets, false)
    else -> 0.0
}

/**
 * R_TRN_VOL_FILL. For each group under its minimum, in muscle-map order:
 * 1. add sets (within R_TRN_SETS) to exercises that already train it, main muscle first, earlier days first;
 * 2. then one accessory exercise that has the group as main muscle, added to the days with the fewest exercises
 *    until the minimum is met or every day has it;
 * 3. a group the equipment still cannot reach is returned (L-17 says so plainly).
 * Before that, a template already over R_TRN_VOL_MAX is cut (accessory sets first); no fill step may go over it.
 */
private fun fillVolume(
    days: MutableList<Pair<DayName, MutableList<ProgramItem>>>,
    pool: List<Exercise>,
    ctx: ItemContext,
): Set<MuscleGroup> {
    val byId = pool.associateBy { it.id }
    fun totals() = weeklySets(days.flatMap { it.second }, pool)
    fun min(group: MuscleGroup): Double? =
        if (volumeStatus(group, ctx.level, 0.0) == VolumeStatus.NO_MINIMUM) null
        else (if (ctx.level == TrainingLevel.L1) R_TRN_VOL_MIN_L1 else R_TRN_VOL_MIN_L2).toDouble()
    fun fitsCap(ex: Exercise, extraSets: Int): Boolean {
        val now = totals()
        return MuscleGroup.entries.all { g -> now.getValue(g) + credit(ex, g, extraSets) <= R_TRN_VOL_MAX }
    }
    fun under(group: MuscleGroup): Boolean { val m = min(group) ?: return false; return totals().getValue(group) < m }
    // Sets an exercise also puts on groups that need no more (spent toward the R_TRN_VOL_MAX cap for nothing).
    fun collateral(ex: Exercise, group: MuscleGroup): Double =
        MuscleGroup.entries.filter { it != group && !under(it) }.sumOf { credit(ex, it, 1) }

    fun raiseSets(group: MuscleGroup) {
        val order = days.flatMap { (_, items) -> items.indices.map { i -> items to i } }
            .filter { (items, i) -> byId[items[i].exerciseId]?.let { credit(it, group, 1) > 0.0 } == true }
            .sortedWith(
                compareByDescending<Pair<MutableList<ProgramItem>, Int>> { (items, i) -> credit(byId.getValue(items[i].exerciseId), group, 1) }
                    .thenBy { (items, i) -> collateral(byId.getValue(items[i].exerciseId), group) },
            )
        var changed = true
        while (under(group) && changed) {
            changed = false
            for ((items, i) in order) {
                if (!under(group)) break
                val item = items[i]
                val ex = byId.getValue(item.exerciseId)
                if (item.sets < ctx.sets.last && fitsCap(ex, 1)) {
                    items[i] = item.copy(sets = item.sets + 1)
                    changed = true
                }
            }
        }
    }

    // First, over R_TRN_VOL_MAX (a 4-day split can start over it): the lowest-priority accessory set goes first
    // (last day, last item backwards); an accessory already at the bottom of R_TRN_SETS is removed. Main lifts are cut last, never below the bottom of the range.
    fun trainsIt(item: ProgramItem, group: MuscleGroup) = byId[item.exerciseId]?.let { credit(it, group, 1) > 0.0 } == true
    for (group in MuscleGroup.entries) {
        while (totals().getValue(group) > R_TRN_VOL_MAX) {
            val spots = days.indices.reversed().flatMap { d -> days[d].second.indices.reversed().map { d to it } }
            val acc = spots.firstOrNull { (d, i) -> days[d].second[i].let { it.role != ItemRole.MAIN && trainsIt(it, group) } }
            val main = spots.firstOrNull { (d, i) -> days[d].second[i].let { it.sets > ctx.sets.first && trainsIt(it, group) } }
            when {
                acc != null -> {
                    val (d, i) = acc
                    val item = days[d].second[i]
                    if (item.sets > ctx.sets.first) days[d].second[i] = item.copy(sets = item.sets - 1)
                    else days[d].second.removeAt(i)
                }
                main != null -> {
                    val (d, i) = main
                    days[d].second[i] = days[d].second[i].copy(sets = days[d].second[i].sets - 1)
                }
                else -> break
            }
        }
    }

    for (group in MuscleGroup.entries) {
        if (!under(group)) continue
        raiseSets(group)
        if (!under(group)) continue
        val inProgram = days.flatMap { d -> d.second.map { it.exerciseId } }.toSet()
        val pick = pool.filter { group in it.main }
            .sortedWith(compareBy<Exercise>({ it.id in inProgram }, { collateral(it, group) }, { it.main.size }, { -it.level }))
            .firstOrNull { fitsCap(it, ctx.sets.first) } ?: continue
        val order = days.indices.sortedWith(compareBy({ days[it].second.size }, { it }))
        for (d in order) {
            if (!under(group)) break
            val items = days[d].second
            if (items.any { it.exerciseId == pick.id } || !fitsCap(pick, ctx.sets.first)) continue
            items += ctx.item(pick, ItemRole.VOLUME_FILL, group)
            raiseSets(group)
        }
    }

    return MuscleGroup.entries.filter { under(it) }.toSet()
}

/** R_TRN_GEN: "±N menit" = sets × (rest + work estimate), rounded up to whole minutes. */
fun estMinutes(items: List<ProgramItem>): Int {
    val seconds = items.sumOf { it.sets * (it.restSec + R_TRN_GEN_EST_WORK_S) }
    return ceil(seconds.toDouble() / R_TRN_GEN_SECONDS_PER_MINUTE).toInt()
}
