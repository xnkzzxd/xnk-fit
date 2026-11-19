package app.xnkfit.domain.food

// Week planner selection rules (Rules.md §6: R_PLAN_*, R_BUDGET, R_OPTIONS, R_LIMIT_*). B-10.
//
// REVIEW (Opus 5.5): every function here is pure and deterministic, so the whole policy can be read and
// changed in this one file. The weights and counts below are first values written without an owner source (⚠ in
// Rules.md, row in Decisions.md "aturan planner perlu review Opus"). Nothing here touches the safety limits:
// the calorie floor is a hard condition (R_PLAN_FLOOR), and the user's own choices are never blocked (M-4).

// R_PLAN_REPEAT: the same package at most this many times a week in automatic choices.
const val R_PLAN_REPEAT_MAX_PER_WEEK = 2

// R_PLAN_REPEAT: cost (in kcal-equivalents) for each time the package is already in the week.
const val R_PLAN_REPEAT_COST = 150.0

// R_PLAN_FIT: candidates kept per slot before combinations are tried.
const val R_PLAN_FIT_SHORTLIST = 6

// R_PLAN_FIT: a deterministic tie-break in 0 until this value, from the package id and the week seed.
const val R_PLAN_FIT_TIE_RANGE = 10

// R_PLAN_FIT: days in a plan week.
const val R_PLAN_FIT_DAYS_PER_WEEK = 7

// R_PLAN_COOK_ONCE: "masak sekali" repeats one option over this many days (from the chosen day).
const val R_PLAN_COOK_ONCE_DAYS = 3

// R_BUDGET: cost (kcal-equivalents) per tier a package is above / below the user's preference. Weight only, no Rupiah.
const val R_BUDGET_OVER_COST = 300.0
const val R_BUDGET_UNDER_COST = 20.0

// R_PLAN_BUY: cost for a bought option when the user rarely buys food.
const val R_PLAN_BUY_RARELY_COST = 100.0

// R_PLAN_PROT: cost (kcal-equivalents) per gram of protein a day is under / over the R_PROT range. Weight only (⚠).
const val R_PLAN_PROT_SHORT_COST = 10.0
const val R_PLAN_PROT_OVER_COST = 10.0

enum class PackageMode { MASAK, BELI, TANPA_MASAK }

/** "Sering beli di luar?" (Makan screen). */
enum class BuyFrequency { OFTEN, SOMETIMES, RARELY }

data class PackageItem(val foodId: String, val nameId: String, val grams: Double, val measureLabel: String?)

/** One bundled meal package (Content.md §4.5), already read from the file. */
data class MealPackage(
    val id: String,
    val profile: String,
    val slot: MealSlot,
    val nameId: String,
    val mode: PackageMode,
    val nutrients: Nutrients,
    val budget: Budget,
    val halal: Boolean,
    /** Includes LACTOSE when the package has lactose. */
    val allergens: Set<Allergen>,
    val isEstimate: Boolean,
    val santan: Boolean,
    val goreng: Boolean,
    val mainProtein: String?,
    val items: List<PackageItem>,
) {
    val kcal: Double get() = nutrients.kcal

    fun toCandidate() = MealCandidate(id, santan, goreng, mainProtein, allergens, halal)
}

/** One cell of the week: a package on a day and slot. [day] 1 (Monday) to 7. */
data class PlannedMeal(
    val day: Int,
    val slot: MealSlot,
    val packageId: String,
    val locked: Boolean = false,
    val cookOnce: Boolean = false,
    val eatOut: Boolean = false,
)

data class PlannerInput(
    val packages: List<MealPackage>,
    val avoid: Set<Allergen>,
    val budget: Budget,
    val buys: BuyFrequency,
    /** Null when no target exists (owner answer 2026-11-19): the plan is chosen without any kcal aim and shown without numbers. */
    val targetKcal: Int?,
    /**
     * R_FLOOR_F / R_FLOOR_M or BMR, whichever is higher: a planned day never adds up to less (R_PLAN_FLOOR).
     * Null when there is no target: the floor does not apply then, the safety filters and limits still do.
     */
    val floorKcal: Int?,
    /** Profiles (regions, kos, beli) the user allows; null = all. */
    val profiles: Set<String>? = null,
    /** Cells the user locked or picked by hand. They stay, count toward the limits, and are never blocked (M-4). */
    val fixed: List<PlannedMeal> = emptyList(),
    /** Changes the tie-break so each week differs; the same seed always gives the same plan. */
    val seed: Int = 0,
    /** R_PROT range in grams from `domain.rules` (not computed here); null = protein is not aimed at (R_PLAN_PROT). */
    val proteinRangeG: IntRange? = null,
)

/** R_PLAN_FLOOR: the floor to reach, 0 (always met) when there is no target. */
private val PlannerInput.floor: Double get() = floorKcal?.toDouble() ?: 0.0

/** R_PLAN_FIT: distance to the target, 0 when there is no target (only budget, variety and limits choose). */
private fun PlannerInput.gap(kcal: Double): Double = targetKcal?.let { kotlin.math.abs(kcal - it) } ?: 0.0

data class WeekPlan(val meals: List<PlannedMeal>, val daysBelowFloor: Set<Int>)

/** R_HALAL, R_LIMIT_ALLERGY: what may be offered automatically at all. */
fun eligible(p: MealPackage, input: PlannerInput): Boolean =
    p.halal && p.allergens.none { it in input.avoid.withLactose() } && (input.profiles == null || p.profile in input.profiles)

/**
 * R_PLAN_PROT: protein cost of a day of [kcal] and [protein] g. Protein only chooses among combinations that are already
 * safe (floor first, filters and limits before), and never pulls a day above the planner's own upper value
 * max(target, floor): above it every combination pays the same (largest) protein cost, so protein cannot prefer one.
 */
internal fun proteinCost(input: PlannerInput, kcal: Double, protein: Double): Double {
    val r = input.proteinRangeG ?: return 0.0
    val target = input.targetKcal ?: return 0.0
    val p = if (kcal > maxOf(target, input.floorKcal ?: target)) 0.0 else protein
    return when {
        p < r.first -> (r.first - p) * R_PLAN_PROT_SHORT_COST
        p > r.last -> (p - r.last) * R_PLAN_PROT_OVER_COST
        else -> 0.0
    }
}

private fun budgetCost(p: MealPackage, pref: Budget): Double {
    val diff = p.budget.ordinal - pref.ordinal
    return if (diff > 0) diff * R_BUDGET_OVER_COST else -diff * R_BUDGET_UNDER_COST
}

private fun staticCost(p: MealPackage, input: PlannerInput, uses: Map<String, Int>): Double {
    var c = budgetCost(p, input.budget)
    c += (uses[p.id] ?: 0) * R_PLAN_REPEAT_COST
    if (input.buys == BuyFrequency.RARELY && p.mode == PackageMode.BELI) c += R_PLAN_BUY_RARELY_COST
    c += (p.id.hashCode() xor input.seed).mod(R_PLAN_FIT_TIE_RANGE)
    return c
}

/** NONE: shortlist. WIDE: whole pool. NO_CAP: also past the weekly repeat cap. FREE: also past the daily and weekly limits (last resort). */
private enum class Relax { NONE, WIDE, NO_CAP, FREE }

private class Best(var picks: List<MealPackage>, var feasible: Boolean, var total: Double, var cost: Double)

/**
 * R_PLAN_REPEAT, R_PLAN_FIT, R_PLAN_FLOOR, R_BUDGET, R_LIMIT_*: one plan for the week, day by day.
 * For each day the free slots are filled by the combination that (1) adds up to at least the floor, (2) respects
 * the daily and weekly limits against what is already chosen (manual choices count), (3) comes nearest the target
 * after the budget, repeat and "buy" costs. Days that cannot reach the floor take the highest total and are listed.
 */
fun planWeek(input: PlannerInput): WeekPlan {
    val byId = input.packages.associateBy { it.id }
    val pool = input.packages.filter { eligible(it, input) }
    val uses = HashMap<String, Int>()
    input.fixed.forEach { uses.merge(it.packageId, 1, Int::plus) }
    var santanWeek = input.fixed.count { byId[it.packageId]?.santan == true }
    val meals = ArrayList<PlannedMeal>(input.fixed)
    val below = LinkedHashSet<Int>()

    for (day in 1..R_PLAN_FIT_DAYS_PER_WEEK) {
        val fixedToday = input.fixed.filter { it.day == day }
        val freeSlots = MealSlot.entries.filter { s -> fixedToday.none { it.slot == s } }
        if (freeSlots.isEmpty()) continue
        val today = fixedToday.mapNotNull { byId[it.packageId] }
        val fixedKcal = today.sumOf { it.kcal }
        val fixedProtein = today.sumOf { it.nutrients.protein }
        // R_PLAN_FLOOR is hard: widen step by step until a combination reaches the floor (Review-Planner.md F1).
        // If none does, keep the highest total that still respects the limits (FREE only when nothing else exists).
        var best: Best? = null
        for (relax in Relax.entries) {
            val b = bestCombo(input, pool, uses, freeSlots, today, fixedKcal, fixedProtein, santanWeek, relax) ?: continue
            if (b.feasible) { best = b; break }
            if (best == null || (relax != Relax.FREE && b.total >= best.total)) best = b
        }
        val picks = best?.picks.orEmpty()
        picks.forEach { p ->
            meals += PlannedMeal(day, p.slot, p.id)
            uses.merge(p.id, 1, Int::plus)
            if (p.santan) santanWeek++
        }
    }
    // R_PLAN_FLOOR: every day whose planned total stays under the floor is marked, also a day with no option at all.
    for (day in 1..R_PLAN_FIT_DAYS_PER_WEEK) {
        if (input.floorKcal == null) break
        if (meals.filter { it.day == day }.sumOf { byId[it.packageId]?.kcal ?: 0.0 } < input.floor) below += day
    }
    return WeekPlan(meals.sortedWith(compareBy({ it.day }, { it.slot.ordinal })), below)
}

private fun bestCombo(
    input: PlannerInput,
    pool: List<MealPackage>,
    uses: Map<String, Int>,
    freeSlots: List<MealSlot>,
    today: List<MealPackage>,
    fixedKcal: Double,
    fixedProtein: Double,
    santanWeek: Int,
    relax: Relax,
): Best? {
    val lists = freeSlots.map { slot ->
        val all = pool.filter { it.slot == slot }
        val capped = if (relax >= Relax.NO_CAP) all else all.filter { (uses[it.id] ?: 0) < R_PLAN_REPEAT_MAX_PER_WEEK }
        val sorted = capped.sortedWith(compareBy({ staticCost(it, input, uses) }, { it.id }))
        if (relax == Relax.NONE) sorted.take(R_PLAN_FIT_SHORTLIST) else sorted
    }.filter { it.isNotEmpty() }
    if (lists.isEmpty()) return null

    var best: Best? = null
    val chosen = ArrayList<MealPackage>()

    fun leaf() {
        val total = fixedKcal + chosen.sumOf { it.kcal }
        val feasible = total >= input.floor
        val protein = fixedProtein + chosen.sumOf { it.nutrients.protein }
        val cost = chosen.sumOf { staticCost(it, input, uses) } + input.gap(total) +
            proteinCost(input, total, protein)
        val b = best
        val better = when {
            b == null -> true
            feasible != b.feasible -> feasible
            feasible -> cost < b.cost
            else -> total > b.total || (total == b.total && cost < b.cost)
        }
        if (better) best = Best(chosen.toList(), feasible, total, cost)
    }

    fun walk(i: Int) {
        if (i == lists.size) { leaf(); return }
        for (c in lists[i]) {
            if (relax != Relax.FREE) {
                val sofar = PlanSoFar(today.map { it.toCandidate() } + chosen.map { it.toCandidate() }, santanWeek + chosen.count { it.santan })
                if (!allowedAsAutomaticOption(c.toCandidate(), sofar, input.avoid)) continue
            }
            chosen += c
            walk(i + 1)
            chosen.removeAt(chosen.size - 1)
        }
    }
    walk(0)
    return best
}

/** R_OPTIONS, R_PLAN_BUY: up to three options for one cell. The package already in the cell, if any, comes first. */
fun slotOptions(input: PlannerInput, plan: List<PlannedMeal>, day: Int, slot: MealSlot): List<MealPackage> {
    val byId = input.packages.associateBy { it.id }
    val others = plan.filter { it.day == day && it.slot != slot }.mapNotNull { byId[it.packageId] }
    val current = plan.firstOrNull { it.day == day && it.slot == slot }?.let { byId[it.packageId] }
    val uses = HashMap<String, Int>()
    plan.filter { !(it.day == day && it.slot == slot) }.forEach { uses.merge(it.packageId, 1, Int::plus) }
    val santanWeek = plan.count { !(it.day == day && it.slot == slot) && byId[it.packageId]?.santan == true }
    val othersKcal = others.sumOf { it.kcal }
    val othersProtein = others.sumOf { it.nutrients.protein }
    val sofar = PlanSoFar(others.map { it.toCandidate() }, santanWeek)

    val ranked = input.packages
        .filter { it.slot == slot && eligible(it, input) && it.id != current?.id }
        .filter { allowedAsAutomaticOption(it.toCandidate(), sofar, input.avoid) }
        .filter { (uses[it.id] ?: 0) < R_PLAN_REPEAT_MAX_PER_WEEK }
        .sortedWith(
            compareBy<MealPackage>(
                // R_PLAN_FLOOR: options that keep the day at or above the floor come first (Review-Planner.md F3).
                { othersKcal + it.kcal < input.floor },
                {
                    staticCost(it, input, uses) + input.gap(othersKcal + it.kcal) +
                        proteinCost(input, othersKcal + it.kcal, othersProtein + it.nutrients.protein)
                },
                { it.id },
            ),
        )

    val picked = ArrayList<MealPackage>()
    if (current != null) picked += current
    val deferred = ArrayList<MealPackage>()
    for (c in ranked) {
        if (picked.size >= R_OPTIONS) break
        val main = c.mainProtein
        if (main != null && picked.any { it.mainProtein == main }) deferred += c else picked += c
    }
    for (c in deferred) if (picked.size < R_OPTIONS) picked += c

    // R_PLAN_BUY: users who often buy food always see one bought or no-cook option when one exists.
    if (input.buys == BuyFrequency.OFTEN && picked.none { it.mode != PackageMode.MASAK }) {
        val easy = ranked.firstOrNull { it.mode != PackageMode.MASAK && it !in picked }
        if (easy != null) {
            if (picked.size < R_OPTIONS) picked += easy else picked[picked.size - 1] = easy
        }
    }
    return picked
}

/** M-4: a manual choice replaces the cell whatever the limits say. [locked] keeps it through "isi ulang otomatis". */
fun withChoice(plan: List<PlannedMeal>, day: Int, slot: MealSlot, packageId: String, locked: Boolean = true): List<PlannedMeal> =
    (plan.filterNot { it.day == day && it.slot == slot } + PlannedMeal(day, slot, packageId, locked = locked))
        .sortedWith(compareBy({ it.day }, { it.slot.ordinal }))

/** "Masak sekali": the cell's package repeated on the next days of the same slot; locked cells are not overwritten. */
fun withCookOnce(plan: List<PlannedMeal>, day: Int, slot: MealSlot): List<PlannedMeal> {
    val src = plan.firstOrNull { it.day == day && it.slot == slot } ?: return plan
    val last = minOf(R_PLAN_FIT_DAYS_PER_WEEK, day + R_PLAN_COOK_ONCE_DAYS - 1)
    var out = plan
    for (d in day..last) {
        val existing = out.firstOrNull { it.day == d && it.slot == slot }
        if (d != day && existing?.locked == true) continue
        out = out.filterNot { it.day == d && it.slot == slot } + src.copy(day = d, cookOnce = true, eatOut = false)
    }
    return out.sortedWith(compareBy({ it.day }, { it.slot.ordinal }))
}

/** "Makan di luar": the cell becomes the best bought option for that slot (a flagged estimate, R_EST). Null if none. */
fun withEatOut(input: PlannerInput, plan: List<PlannedMeal>, day: Int, slot: MealSlot): List<PlannedMeal>? {
    val byId = input.packages.associateBy { it.id }
    val othersKcal = plan.filter { it.day == day && it.slot != slot }.sumOf { byId[it.packageId]?.kcal ?: 0.0 }
    val pick = input.packages
        .filter { it.slot == slot && it.mode == PackageMode.BELI && eligible(it, input) }
        .minWithOrNull(compareBy({ input.gap(othersKcal + it.kcal) }, { it.id })) ?: return null
    return plan.filterNot { it.day == day && it.slot == slot } + PlannedMeal(day, slot, pick.id, eatOut = true)
}

/** M-4: "Santan sudah 3× minggu ini". Counts every cell, automatic or manual. */
fun santanCount(plan: List<PlannedMeal>, packages: Map<String, MealPackage>): Int =
    plan.count { packages[it.packageId]?.santan == true }

/** Kcal of one planned day, from the packages (for the "±" figure on the planner). */
fun plannedDayKcal(plan: List<PlannedMeal>, packages: Map<String, MealPackage>, day: Int): Double =
    plan.filter { it.day == day }.sumOf { packages[it.packageId]?.kcal ?: 0.0 }

/** Protein g of one planned day (R_PLAN_PROT; shown only when numbers are allowed). */
fun plannedDayProtein(plan: List<PlannedMeal>, packages: Map<String, MealPackage>, day: Int): Double =
    plan.filter { it.day == day }.sumOf { packages[it.packageId]?.nutrients?.protein ?: 0.0 }
