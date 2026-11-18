package app.xnkfit.domain.food

// Food rules (Rules.md §6). Limits apply to automatic options only; manual choices are never blocked (M-4).
// The planner itself is B-10.

// R_LIMIT_SANTAN: santan dishes in automatic options.
const val R_LIMIT_SANTAN_PER_DAY = 1
const val R_LIMIT_SANTAN_PER_WEEK = 3

// R_LIMIT_GORENG: fried mains per day in automatic options.
const val R_LIMIT_GORENG_PER_DAY = 1

// R_LIMIT_MAIN: the same main protein at most once a day in automatic options.
const val R_LIMIT_MAIN_PER_DAY = 1

// R_OPTIONS: meal options per slot.
const val R_OPTIONS = 3

// R_HALAL: ingredients never present in bundled foods.
val R_HALAL_EXCLUDED: List<String> = listOf("babi", "pork", "alkohol", "alcohol", "arak", "bir", "wine", "ham", "bacon", "lard")

/** R_MEAL_SLOTS. */
enum class MealSlot { PAGI, SIANG, MALAM, CAMILAN }

/** R_LIMIT_ALLERGY: filters removed from automatic options. */
enum class Allergen { EGG, SEAFOOD, NUTS, MILK, LACTOSE }

/** R_BUDGET: weight only, no Rupiah amounts. */
enum class Budget { HEMAT, STANDAR, BEBAS }

/** The parts of a candidate meal the limits look at. */
data class MealCandidate(
    val id: String,
    val santan: Boolean,
    val friedMain: Boolean,
    val mainProtein: String?,
    val allergens: Set<Allergen>,
    val halal: Boolean,
)

/** What is already chosen today and this week (automatic and manual picks both count). */
data class PlanSoFar(
    val today: List<MealCandidate>,
    val santanThisWeek: Int,
)

/**
 * R_LIMIT_SANTAN, R_LIMIT_GORENG, R_LIMIT_MAIN, R_LIMIT_ALLERGY, R_HALAL: may the planner offer [c] automatically?
 */
fun allowedAsAutomaticOption(c: MealCandidate, plan: PlanSoFar, avoid: Set<Allergen>): Boolean {
    if (!c.halal) return false
    if (c.allergens.any { it in avoid }) return false
    if (c.santan) {
        if (plan.today.count { it.santan } >= R_LIMIT_SANTAN_PER_DAY) return false
        if (plan.santanThisWeek >= R_LIMIT_SANTAN_PER_WEEK) return false
    }
    if (c.friedMain && plan.today.count { it.friedMain } >= R_LIMIT_GORENG_PER_DAY) return false
    val main = c.mainProtein
    if (main != null && plan.today.count { it.mainProtein == main } >= R_LIMIT_MAIN_PER_DAY) return false
    return true
}
