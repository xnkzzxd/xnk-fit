package app.xnkfit.domain.food

import kotlin.math.ceil

// Shopping list from the week plan (PRD M-8, Design §7). B-10. Pure.

/** Groups as in the approved mockup: Lauk, Sayur dan buah, Pokok. */
enum class ShopGroup { LAUK, SAYUR_BUAH, POKOK, LAINNYA }

/** How many of a countable household unit to buy, when the food has one (3 potong, 6 butir). */
data class ShopCount(val count: Int, val unit: String)

data class ShopLine(
    val foodId: String,
    val nameId: String,
    val grams: Double,
    val group: ShopGroup,
    val count: ShopCount?,
)

// R_HH: units that can be counted in a shop; others (centong, gelas, sdm) are cooking measures, not shop units.
private val R_HH_COUNTABLE_UNITS = setOf("butir", "buah", "biji", "potong", "ekor", "lembar", "papan", "bungkus")

/** TKPI group letter → shop group. Letters from Content.md §4.1; estimate rows (W_*) use their name. */
fun shopGroupOf(foodId: String, nameId: String): ShopGroup {
    if (foodId.startsWith("TKPI_") && foodId.length > "TKPI_".length) {
        return when (foodId["TKPI_".length]) {
            'A', 'B' -> ShopGroup.POKOK
            'C', 'F', 'G', 'H', 'J' -> ShopGroup.LAUK
            'D', 'E' -> ShopGroup.SAYUR_BUAH
            else -> ShopGroup.LAINNYA
        }
    }
    val n = nameId.lowercase()
    return when {
        listOf("ayam", "ikan", "telur", "tempe", "tahu", "bakso", "sate", "lele", "rendang", "perkedel").any { it in n } -> ShopGroup.LAUK
        listOf("nasi", "mie", "lontong", "bubur", "roti", "oat").any { it in n } -> ShopGroup.POKOK
        listOf("sayur", "kangkung", "capcay", "gado", "pecel", "lalap", "pisang", "buah").any { it in n } -> ShopGroup.SAYUR_BUAH
        else -> ShopGroup.LAINNYA
    }
}

/**
 * M-8: sums the foods of the cooked cells from [fromDay] on. Bought options and "makan di luar" cells are not
 * shopped for (nothing to cook). Grams are as listed in the package (±), so the list is an aid, not a recipe.
 * [measuresOf] gives a food's household measures so grams can be shown as a count when a countable unit exists.
 */
fun shoppingList(
    plan: List<PlannedMeal>,
    packages: Map<String, MealPackage>,
    fromDay: Int,
    measuresOf: (String) -> List<HouseholdMeasure>,
): List<ShopLine> {
    val grams = LinkedHashMap<String, Double>()
    val names = HashMap<String, String>()
    plan.filter { it.day >= fromDay && !it.eatOut }.forEach { cell ->
        val p = packages[cell.packageId] ?: return@forEach
        if (p.mode == PackageMode.BELI) return@forEach
        p.items.forEach { item ->
            grams.merge(item.foodId, item.grams, Double::plus)
            names.putIfAbsent(item.foodId, item.nameId)
        }
    }
    return grams.map { (id, g) ->
        val name = names.getValue(id)
        val unit = measuresOf(id).firstOrNull { it.code in R_HH_COUNTABLE_UNITS }
        val count = unit?.let { ShopCount(ceil(g / it.gramsPerUnit).toInt(), measureUnit(it.label)) }
        ShopLine(id, name, roundUpShopGrams(g), shopGroupOf(id, name), count)
    }.sortedWith(compareBy({ it.group.ordinal }, { it.nameId }))
}
