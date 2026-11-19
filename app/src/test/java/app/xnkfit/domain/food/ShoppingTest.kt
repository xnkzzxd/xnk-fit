package app.xnkfit.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Shopping list from the week plan (PRD M-8). */
class ShoppingTest {
    private fun item(id: String, name: String, grams: Double) = PackageItem(id, name, grams, null)

    private val ayam = pkg("A", MealSlot.SIANG, 500.0, items = listOf(item("TKPI_FR001", "Ayam paha", 100.0), item("TKPI_DR109", "Ketimun", 50.0)))
    private val bought = pkg("B", MealSlot.MALAM, 500.0, mode = PackageMode.BELI, items = listOf(item("W_BAKSO", "Bakso", 300.0)))
    private val packages = listOf(ayam, bought).associateBy { it.id }
    private val potong = HouseholdMeasure("potong", "1 potong", 1.0, 40.0, estimate = false)

    @Test
    fun sumsGramsAcrossDaysAndGroupsThem() {
        val plan = listOf(PlannedMeal(1, MealSlot.SIANG, "A"), PlannedMeal(2, MealSlot.SIANG, "A", cookOnce = true), PlannedMeal(3, MealSlot.SIANG, "A"))
        val list = shoppingList(plan, packages, 1) { if (it == "TKPI_FR001") listOf(potong) else emptyList() }
        val ayamLine = list.first { it.foodId == "TKPI_FR001" }
        assertEquals(300.0, ayamLine.grams, 0.0)
        assertEquals(ShopCount(8, "potong"), ayamLine.count) // 300 g / 40 g, rounded up
        assertEquals(ShopGroup.LAUK, ayamLine.group)
        val timun = list.first { it.foodId == "TKPI_DR109" }
        assertEquals(150.0, timun.grams, 0.0)
        assertNull(timun.count)
        assertEquals(ShopGroup.SAYUR_BUAH, timun.group)
        assertEquals(listOf(ShopGroup.LAUK, ShopGroup.SAYUR_BUAH), list.map { it.group })
    }

    @Test
    fun boughtMealsEatOutAndPastDaysAreLeftOut() {
        val plan = listOf(
            PlannedMeal(1, MealSlot.SIANG, "A"), // before fromDay
            PlannedMeal(4, MealSlot.MALAM, "B"), // bought
            PlannedMeal(5, MealSlot.SIANG, "A", eatOut = true),
            PlannedMeal(6, MealSlot.SIANG, "A"),
        )
        val list = shoppingList(plan, packages, fromDay = 3) { emptyList() }
        assertEquals(setOf("TKPI_FR001", "TKPI_DR109"), list.map { it.foodId }.toSet())
        assertEquals(100.0, list.first { it.foodId == "TKPI_FR001" }.grams, 0.0)
    }

    @Test
    fun unknownPackageIdIsSkippedNotACrash() {
        val list = shoppingList(listOf(PlannedMeal(1, MealSlot.PAGI, "GONE")), packages, 1) { emptyList() }
        assertEquals(emptyList<ShopLine>(), list)
    }

    @Test
    fun shopGroupFollowsTkpiLetterAndNameForEstimates() {
        assertEquals(ShopGroup.POKOK, shopGroupOf("TKPI_AR001", "Beras"))
        assertEquals(ShopGroup.LAUK, shopGroupOf("TKPI_HR002", "Telur"))
        assertEquals(ShopGroup.LAINNYA, shopGroupOf("TKPI_KR001", "Minyak"))
        assertEquals(ShopGroup.LAUK, shopGroupOf("W_AYAM_BAKAR", "Ayam bakar"))
        assertEquals(ShopGroup.POKOK, shopGroupOf("W_NASI_UDUK", "Nasi uduk"))
        assertEquals(ShopGroup.LAINNYA, shopGroupOf("W_ES_TEH", "Es teh"))
    }
}
