package app.xnkfit.domain.food

import kotlin.math.floor
import kotlin.math.roundToInt

// Food log and household measures (Rules.md §6: R_LOG, R_HH, R_GATE_ED display). B-10.
// Pure Kotlin: no Android, no clock, no database.

// R_LOG: kcal and macros are copied into the log when it is written, rounded to one decimal.
const val R_LOG_DECIMAL_FACTOR = 10.0

// R_HH: a household count moves in half steps (½ centong), grams always shown beside with "±".
const val R_HH_STEP = 0.5

// R_HH: a shopping amount is rounded up to this many grams.
const val R_HH_SHOP_ROUND_G = 50.0

/** Energy and macros, kcal and grams. */
data class Nutrients(val kcal: Double, val protein: Double, val carb: Double, val fat: Double) {
    operator fun plus(o: Nutrients) = Nutrients(kcal + o.kcal, protein + o.protein, carb + o.carb, fat + o.fat)

    companion object {
        val Empty = Nutrients(0.0, 0.0, 0.0, 0.0)
    }
}

/** One household measure of a food (R_HH). [estimate] true = our estimate (⚠ G7), false = printed in PMK 41/2014. */
data class HouseholdMeasure(
    val code: String,
    val label: String,
    val qty: Double,
    val grams: Double,
    val estimate: Boolean,
) {
    /** Grams of exactly one [code] (a centong, a potong). */
    val gramsPerUnit: Double get() = grams / qty
}

/** A food as the log needs it. Values are per 100 g. */
data class FoodItem(
    val id: String,
    val nameId: String,
    val source: String,
    val per100: Nutrients,
    val household: List<HouseholdMeasure>,
    val isEstimate: Boolean,
    val halal: Boolean?,
    val allergens: Set<Allergen>,
)

private fun round1(v: Double): Double = (v * R_LOG_DECIMAL_FACTOR).roundToInt() / R_LOG_DECIMAL_FACTOR

/** R_LOG: kcal and macros for [grams] of a food, copied at write time. Non-finite or negative grams give zero. */
fun nutrientsFor(per100: Nutrients, grams: Double): Nutrients {
    if (!grams.isFinite() || grams <= 0.0) return Nutrients.Empty
    val f = grams / R_LOG_PER_100_G
    return Nutrients(round1(per100.kcal * f), round1(per100.protein * f), round1(per100.carb * f), round1(per100.fat * f))
}

// R_LOG: food values are per 100 g.
private const val R_LOG_PER_100_G = 100.0

/** R_LOG: quick-add stores the kcal and no macros. */
fun quickNutrients(kcal: Double): Nutrients =
    if (kcal.isFinite() && kcal > 0.0) Nutrients(round1(kcal), 0.0, 0.0, 0.0) else Nutrients.Empty

/** R_HH: grams of [count] units of a measure. [count] is a number of the measure's own unit (1.5 centong). */
fun gramsOf(measure: HouseholdMeasure, count: Double): Double =
    if (count.isFinite() && count > 0.0) measure.gramsPerUnit * count else 0.0

/** R_HH: the next count when the user taps + or −; never below one step. */
fun stepCount(count: Double, up: Boolean): Double {
    val next = if (up) count + R_HH_STEP else count - R_HH_STEP
    return if (next < R_HH_STEP) R_HH_STEP else next
}

/** R_HH: what the user sees: "1½", "centong", "±100 g" are built from these three parts (text in strings.xml). */
data class PortionDisplay(val quantity: String, val unit: String, val grams: Int)

private val leadingQuantity = Regex("^[\\d¼½¾⅓⅔.,\\s]+")

/** "1 centong" → "centong"; "¾ gelas" → "gelas"; "2 potong sedang" → "potong sedang". */
fun measureUnit(label: String): String = label.replace(leadingQuantity, "").trim().ifEmpty { label }

private const val R_HH_QUARTER = 0.25
private const val R_HH_THIRD = 1.0 / 3.0
private const val R_HH_TWO_THIRDS = 2.0 / 3.0
private const val R_HH_THREE_QUARTERS = 0.75
private const val R_HH_FRACTION_TOLERANCE = 0.02

/** "1½", "¾", "2"; falls back to one decimal when no common fraction fits. */
fun quantityText(q: Double): String {
    if (!q.isFinite() || q <= 0.0) return "0"
    val whole = floor(q + R_HH_FRACTION_TOLERANCE)
    val rest = q - whole
    val frac = when {
        rest < R_HH_FRACTION_TOLERANCE -> ""
        kotlin.math.abs(rest - R_HH_QUARTER) < R_HH_FRACTION_TOLERANCE -> "¼"
        kotlin.math.abs(rest - R_HH_THIRD) < R_HH_FRACTION_TOLERANCE -> "⅓"
        kotlin.math.abs(rest - R_HH_STEP) < R_HH_FRACTION_TOLERANCE -> "½"
        kotlin.math.abs(rest - R_HH_TWO_THIRDS) < R_HH_FRACTION_TOLERANCE -> "⅔"
        kotlin.math.abs(rest - R_HH_THREE_QUARTERS) < R_HH_FRACTION_TOLERANCE -> "¾"
        else -> null
    }
    if (frac == null) return ((q * R_LOG_DECIMAL_FACTOR).roundToInt() / R_LOG_DECIMAL_FACTOR).toString()
    val w = whole.toInt()
    return if (w == 0) frac else "$w$frac"
}

/** R_HH: display of [count] units of [measure], for example 1.5 centong → ("1½", "centong", 150). */
fun portionDisplay(measure: HouseholdMeasure, count: Double): PortionDisplay =
    PortionDisplay(
        quantity = quantityText(count),
        unit = measureUnit(measure.label),
        grams = gramsOf(measure, count).roundToInt(),
    )

/** What a food can be logged in, household measures first (M-6); grams is always last. */
sealed interface PortionChoice {
    data class Household(val measure: HouseholdMeasure) : PortionChoice
    data object Grams : PortionChoice
}

fun portionChoices(food: FoodItem): List<PortionChoice> =
    food.household.map { PortionChoice.Household(it) } + PortionChoice.Grams

/** The choice shown first and its starting count: the first measure at its own printed quantity, else grams. */
fun defaultPortion(food: FoodItem): Pair<PortionChoice, Double> {
    val m = food.household.firstOrNull() ?: return PortionChoice.Grams to R_LOG_PER_100_G
    return PortionChoice.Household(m) to m.qty
}

// ---- Day log -------------------------------------------------------------------------------------------------

/** One stored log row, read back. Values were copied at write time and are never recomputed. */
data class LogRow(val id: String, val slot: MealSlot, val nutrients: Nutrients, val quick: Boolean)

/** Sum of the rows. Quick-add rows add kcal only. */
fun dayTotals(rows: List<LogRow>): Nutrients = rows.fold(Nutrients.Empty) { a, r -> a + r.nutrients }

/** Daily targets as the target card holds them (calories and macros in grams). */
data class DayTargets(val kcal: Int, val proteinG: Int, val carbG: Int, val fatG: Int)

/** What the Makan and Hari ini cards may show. R_GATE_ED: numbers hidden means [Ticks] only. */
sealed interface DaySummary {
    /** [targets] null when no target exists yet (gates not passed): only what was eaten is shown. */
    data class Numbers(
        val eatenKcal: Int,
        val targets: DayTargets?,
        val remainingKcal: Int?,
        val overKcal: Int?,
        val protein: Int,
        val carb: Int,
        val fat: Int,
        /** Shown beside, never added to the target (M-1). */
        val activityKcal: Int?,
    ) : DaySummary

    /** R_GATE_ED: no calorie or macro number anywhere; just how many meals were noted. */
    data class Ticks(val mealsLogged: Int, val mealsTotal: Int) : DaySummary
}

/** R_GATE_ED, M-1: builds the summary. With [numbersHidden] no figure is read from the rows at all. */
fun daySummary(rows: List<LogRow>, targets: DayTargets?, numbersHidden: Boolean, activityKcal: Int?): DaySummary {
    if (numbersHidden) return DaySummary.Ticks(rows.map { it.slot }.toSet().size, MealSlot.entries.size)
    val t = dayTotals(rows)
    val eaten = t.kcal.roundToInt()
    val diff = targets?.let { it.kcal - eaten }
    return DaySummary.Numbers(
        eatenKcal = eaten,
        targets = targets,
        remainingKcal = diff?.takeIf { it > 0 },
        overKcal = diff?.takeIf { it < 0 }?.let { -it },
        protein = t.protein.roundToInt(),
        carb = t.carb.roundToInt(),
        fat = t.fat.roundToInt(),
        activityKcal = activityKcal,
    )
}

/** R_HH: a shopping amount in grams, rounded up to [R_HH_SHOP_ROUND_G]. */
fun roundUpShopGrams(grams: Double): Double =
    if (grams <= 0.0) 0.0 else kotlin.math.ceil(grams / R_HH_SHOP_ROUND_G) * R_HH_SHOP_ROUND_G
