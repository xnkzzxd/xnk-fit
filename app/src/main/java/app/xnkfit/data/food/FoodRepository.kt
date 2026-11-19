package app.xnkfit.data.food

import androidx.room.withTransaction
import app.xnkfit.data.db.FoodEntity
import app.xnkfit.data.db.FoodLogEntity
import app.xnkfit.data.db.FoodSearch
import app.xnkfit.data.db.MealPlanEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.food.FoodItem
import app.xnkfit.domain.food.LogRow
import app.xnkfit.domain.food.MealPackage
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PlannedMeal
import app.xnkfit.domain.food.nutrientsFor
import app.xnkfit.domain.food.quickNutrients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Food and grams behind a log row; both null for a quick-add. */
data class LogEntry(val foodId: String?, val grams: Double?)

/**
 * Food log, search, meal plan and own foods on Room (B-10, Design §3, §7).
 * Kcal and macros are copied into `food_log` when a row is written, so a later content update never changes the past.
 */
class FoodRepository(
    private val db: XnkDatabase,
    private val now: () -> Long,
    private val newId: () -> String,
) {
    private val dao get() = db.foodDao()

    /** Prefix search over FTS4 (Design §10). Names that start with the typed text come first, then shorter names. */
    suspend fun search(text: String): List<FoodItem> {
        val match = FoodSearch.toMatch(text) ?: return emptyList()
        val typed = text.trim().lowercase()
        return dao.search(match, SEARCH_FETCH)
            .sortedWith(compareBy({ !it.nameId.lowercase().startsWith(typed) }, { it.nameId.length }, { it.nameId }))
            .take(SEARCH_SHOWN)
            .map { it.toFoodItem() }
    }

    suspend fun recent(): List<FoodItem> = byIds(dao.recentFoodIds(LIST_SIZE))

    suspend fun favourites(): List<FoodItem> = byIds(dao.frequentFoodIds(FAVOURITE_MIN_LOGS, LIST_SIZE))

    suspend fun ownFoods(): List<FoodItem> = dao.ownFoods().map { it.toFoodItem() }

    suspend fun food(id: String): FoodItem? = dao.food(id)?.toFoodItem()

    private suspend fun byIds(ids: List<String>): List<FoodItem> {
        if (ids.isEmpty()) return emptyList()
        val found = dao.foodsByIds(ids).associateBy { it.id }
        return ids.mapNotNull { found[it]?.toFoodItem() }
    }

    // --- log ---

    /** Writes one row with kcal and macros computed now (R_LOG). Returns the new row id. */
    suspend fun logFood(day: String, slot: MealSlot, food: FoodItem, grams: Double): String {
        val n = nutrientsFor(food.per100, grams)
        val t = now()
        val id = newId()
        dao.upsertLog(
            FoodLogEntity(
                id = id, createdAt = t, updatedAt = t, day = day, slot = slot.code(), foodId = food.id, recipeId = null,
                grams = grams, kcal = n.kcal, protein = n.protein, carb = n.carb, fat = n.fat,
            ),
        )
        return id
    }

    /** Quick-add calories without a food row (M-7). */
    suspend fun logQuick(day: String, slot: MealSlot, kcal: Double): String {
        val n = quickNutrients(kcal)
        val t = now()
        val id = newId()
        dao.upsertLog(
            FoodLogEntity(
                id = id, createdAt = t, updatedAt = t, day = day, slot = slot.code(), foodId = null, recipeId = null,
                grams = null, kcal = n.kcal, protein = 0.0, carb = 0.0, fat = 0.0, quickKcal = n.kcal,
            ),
        )
        return id
    }

    /** The log row with its food and grams, for the day list. */
    suspend fun logEntry(id: String): LogEntry? = dao.logRow(id)?.let { LogEntry(it.foodId, it.grams) }

    suspend fun deleteLog(id: String) = dao.deleteLog(id)

    fun observeDay(day: String): Flow<List<LogRow>> = dao.observeDay(day).map { rows -> rows.map { it.toLogRow() } }

    suspend fun logOfDay(day: String): List<LogRow> = dao.logOfDay(day).map { it.toLogRow() }

    /** "Salin kemarin" / "Salin makan": copies stored values (not recomputed). [slot] null copies the whole day. Returns rows copied. */
    suspend fun copyLog(fromDay: String, toDay: String, slot: MealSlot? = null): Int = db.withTransaction {
        val rows = dao.logOfDay(fromDay).filter { slot == null || it.slot == slot.code() }
        val t = now()
        rows.forEach { dao.upsertLog(it.copy(id = newId(), createdAt = t, updatedAt = t, day = toDay)) }
        rows.size
    }

    /** "Buat makanan sendiri": per 100 g values; [servingGrams] becomes the one household measure "1 porsi". */
    suspend fun saveOwnFood(nameId: String, per100kcal: Double, protein: Double, carb: Double, fat: Double, servingGrams: Double?): String {
        val t = now()
        val id = OWN_PREFIX + newId()
        val household = servingGrams?.takeIf { it > 0.0 }?.let {
            """[{"code":"porsi","label":"1 porsi","qty":1,"grams":$it,"source":"own"}]"""
        }
        dao.upsertFoods(
            listOf(
                FoodEntity(
                    id = id, createdAt = t, updatedAt = t, source = "own", nameId = nameId.trim(), nameEn = null,
                    kcalPer100 = per100kcal, protein = protein, carb = carb, fat = fat, fibre = null, sodium = null,
                    householdJson = household, isEstimate = false,
                ),
            ),
        )
        return id
    }

    // --- meal packages and week plan ---

    suspend fun packages(): List<MealPackage> = db.contentDao().allMealPackages().map { it.toMealPackage() }

    suspend fun loadPlan(weekStart: String): List<PlannedMeal> = dao.plan(weekStart).map {
        PlannedMeal(it.day, MealSlot.entries.first { s -> s.code() == it.slot }, it.optionCode, it.locked, it.cookOnce, it.eatOut)
    }.sortedWith(compareBy({ it.day }, { it.slot.ordinal }))

    /** Replaces the whole week in one transaction. */
    suspend fun savePlan(weekStart: String, plan: List<PlannedMeal>) = db.withTransaction {
        dao.deletePlanWeek(weekStart)
        val t = now()
        plan.forEach {
            dao.upsertPlan(
                MealPlanEntity(
                    id = newId(), createdAt = t, updatedAt = t, weekStart = weekStart, day = it.day, slot = it.slot.code(),
                    optionCode = it.packageId, locked = it.locked, cookOnce = it.cookOnce, eatOut = it.eatOut,
                ),
            )
        }
    }

    companion object {
        const val OWN_PREFIX = "own_"
        private const val SEARCH_FETCH = 60
        private const val SEARCH_SHOWN = 40
        private const val LIST_SIZE = 20
        private const val FAVOURITE_MIN_LOGS = 3
    }
}
