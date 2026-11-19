package app.xnkfit.data.food

import app.xnkfit.data.db.FoodEntity
import app.xnkfit.data.db.FoodLogEntity
import app.xnkfit.data.db.MealPackageEntity
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.FoodItem
import app.xnkfit.domain.food.HouseholdMeasure
import app.xnkfit.domain.food.LogRow
import app.xnkfit.domain.food.MealPackage
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.Nutrients
import app.xnkfit.domain.food.PackageItem
import app.xnkfit.domain.food.PackageMode
import org.json.JSONArray
import org.json.JSONObject

/** Room rows ↔ domain models for food (B-10). Parsing JSON text lives here, never in the domain. */

private fun slotOf(code: String): MealSlot = MealSlot.entries.first { it.name.equals(code, ignoreCase = true) }

fun MealSlot.code(): String = name.lowercase()

private fun allergenOf(code: String): Allergen? = when (code.lowercase()) {
    "egg" -> Allergen.EGG
    "seafood" -> Allergen.SEAFOOD
    "nuts" -> Allergen.NUTS
    "milk" -> Allergen.MILK
    "lactose" -> Allergen.LACTOSE
    else -> null
}

fun FoodEntity.toFoodItem(): FoodItem {
    val measures = ArrayList<HouseholdMeasure>()
    householdJson?.let { text ->
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val qty = o.optDouble("qty", 1.0)
            val grams = o.getDouble("grams")
            if (qty <= 0.0 || grams <= 0.0) continue
            measures += HouseholdMeasure(
                code = o.getString("code"),
                label = o.getString("label"),
                qty = qty,
                grams = grams,
                estimate = o.optString("source") != "pmk-41-2014",
            )
        }
    }
    val allergens = HashSet<Allergen>()
    allergensJson?.let { text ->
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) allergenOf(arr.getString(i))?.let(allergens::add)
    }
    return FoodItem(
        id = id, nameId = nameId, source = source,
        per100 = Nutrients(kcalPer100, protein, carb, fat),
        household = measures, isEstimate = isEstimate || source == "warung-est", halal = halal, allergens = allergens,
    )
}

fun FoodLogEntity.toLogRow() = LogRow(
    id = id,
    slot = slotOf(slot),
    nutrients = Nutrients(quickKcal ?: kcal, protein, carb, fat),
    quick = quickKcal != null,
)

fun MealPackageEntity.toMealPackage(): MealPackage {
    val o = JSONObject(bodyJson)
    val tags = o.optJSONObject("tags")
    val mains = tags?.optJSONArray("mainProtein")
    val allergens = HashSet<Allergen>()
    o.optJSONArray("allergens")?.let { a -> for (i in 0 until a.length()) allergenOf(a.getString(i))?.let(allergens::add) }
    if (o.optBoolean("lactose", false)) allergens += Allergen.LACTOSE
    val items = ArrayList<PackageItem>()
    val arr = o.getJSONArray("items")
    for (i in 0 until arr.length()) {
        val it = arr.getJSONObject(i)
        items += PackageItem(
            foodId = it.getString("foodId"),
            nameId = it.getString("name_id"),
            grams = it.getDouble("grams"),
            measureLabel = it.optJSONObject("household")?.optString("label")?.ifEmpty { null },
        )
    }
    return MealPackage(
        id = id,
        profile = profile,
        slot = slotOf(slot),
        nameId = o.getString("name_id"),
        mode = when (o.optString("mode")) {
            "beli" -> PackageMode.BELI
            "tanpa-masak" -> PackageMode.TANPA_MASAK
            else -> PackageMode.MASAK
        },
        nutrients = Nutrients(o.getDouble("kcal"), o.getDouble("protein"), o.getDouble("carb"), o.getDouble("fat")),
        budget = when (o.optString("budget")) {
            "Standar" -> Budget.STANDAR
            "Bebas" -> Budget.BEBAS
            else -> Budget.HEMAT
        },
        halal = o.optBoolean("halal", false),
        allergens = allergens,
        isEstimate = o.optBoolean("isEstimate", true),
        santan = tags?.optBoolean("santan", false) ?: false,
        goreng = tags?.optBoolean("goreng", false) ?: false,
        mainProtein = if (mains != null && mains.length() > 0) mains.getString(0) else null,
        items = items,
    )
}
