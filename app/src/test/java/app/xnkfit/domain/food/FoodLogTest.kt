package app.xnkfit.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Food log, household measures and the day summary (B-10; Rules.md R_LOG, R_HH, R_GATE_ED). */
class FoodLogTest {
    private val nasi = Nutrients(130.0, 2.7, 28.0, 0.3)

    private fun centong() = HouseholdMeasure("centong", "1 centong", 1.0, 100.0, estimate = true)
    private fun gelas() = HouseholdMeasure("gelas", "¾ gelas", 0.75, 100.0, estimate = false)

    @Test
    fun R_LOG_kcalAndMacrosAreComputedPerGramsAndRoundedToOneDecimal() {
        val n = nutrientsFor(nasi, 150.0)
        assertEquals(195.0, n.kcal, 0.0)
        assertEquals(4.1, n.protein, 0.0)
        assertEquals(42.0, n.carb, 0.0)
        assertEquals(0.5, n.fat, 0.0)
    }

    @Test
    fun R_LOG_badGramsGiveZeroNotNaN() {
        assertEquals(Nutrients.Empty, nutrientsFor(nasi, 0.0))
        assertEquals(Nutrients.Empty, nutrientsFor(nasi, -5.0))
        assertEquals(Nutrients.Empty, nutrientsFor(nasi, Double.NaN))
        assertEquals(Nutrients.Empty, nutrientsFor(nasi, Double.POSITIVE_INFINITY))
    }

    @Test
    fun R_LOG_quickAddStoresKcalOnly() {
        val q = quickNutrients(130.0)
        assertEquals(130.0, q.kcal, 0.0)
        assertEquals(0.0, q.protein + q.carb + q.fat, 0.0)
        assertEquals(Nutrients.Empty, quickNutrients(-1.0))
    }

    @Test
    fun householdMeasure_gramsFollowTheCountAndStepIsHalf() {
        assertEquals(150.0, gramsOf(centong(), 1.5), 0.0)
        // ¾ gelas = 100 g, so one gelas = 133.3 g
        assertEquals(133.3, gelas().gramsPerUnit, 0.05)
        assertEquals(0.0, gramsOf(centong(), 0.0), 0.0)
        assertEquals(1.5, stepCount(1.0, up = true), 0.0)
        assertEquals(0.5, stepCount(1.0, up = false), 0.0)
        assertEquals(0.5, stepCount(0.5, up = false), 0.0) // never below one step
    }

    @Test
    fun householdMeasure_displayShowsQuantityUnitAndGramsBeside() {
        val d = portionDisplay(centong(), 1.5)
        assertEquals(PortionDisplay("1½", "centong", 150), d)
        assertEquals(PortionDisplay("¾", "gelas", 100), portionDisplay(gelas(), 0.75))
        assertEquals("2", quantityText(2.0))
        assertEquals("½", quantityText(0.5))
        assertEquals("1.2", quantityText(1.2))
        assertEquals("potong sedang", measureUnit("2 potong sedang"))
    }

    @Test
    fun householdMeasure_householdFirstThenGrams() {
        val food = FoodItem("F", "Nasi", "tkpi", nasi, listOf(centong(), gelas()), false, null, emptySet())
        val choices = portionChoices(food)
        assertTrue(choices.first() is PortionChoice.Household)
        assertEquals(PortionChoice.Grams, choices.last())
        assertEquals(PortionChoice.Household(centong()) to 1.0, defaultPortion(food))
        val bare = food.copy(household = emptyList())
        assertEquals(PortionChoice.Grams, defaultPortion(bare).first)
    }

    private fun row(id: String, slot: MealSlot, kcal: Double, p: Double = 0.0, quick: Boolean = false) =
        LogRow(id, slot, Nutrients(kcal, p, 0.0, 0.0), quick)

    @Test
    fun R_LOG_dayTotalsSumStoredRowsIncludingQuickAdd() {
        val t = dayTotals(listOf(row("a", MealSlot.PAGI, 420.0, 20.0), row("b", MealSlot.SIANG, 130.0, quick = true)))
        assertEquals(550.0, t.kcal, 0.0)
        assertEquals(20.0, t.protein, 0.0)
    }

    @Test
    fun daySummary_remainingOverAndActivityIsNotAdded() {
        val targets = DayTargets(1750, 110, 205, 49)
        val under = daySummary(listOf(row("a", MealSlot.PAGI, 1120.0, 62.0)), targets, numbersHidden = false, activityKcal = 160) as DaySummary.Numbers
        assertEquals(1120, under.eatenKcal)
        assertEquals(630, under.remainingKcal)
        assertNull(under.overKcal)
        assertEquals(160, under.activityKcal) // shown beside; the target is still 1750 (M-1)
        assertEquals(1750, under.targets!!.kcal)
        val over = daySummary(listOf(row("a", MealSlot.PAGI, 1870.0)), targets, false, null) as DaySummary.Numbers
        assertEquals(120, over.overKcal)
        assertNull(over.remainingKcal)
        val noTarget = daySummary(listOf(row("a", MealSlot.PAGI, 300.0)), null, false, null) as DaySummary.Numbers
        assertNull(noTarget.remainingKcal)
        assertNull(noTarget.overKcal)
    }

    @Test
    fun R_GATE_ED_summaryHasNoNumbersOnlyMealsLogged() {
        val s = daySummary(
            listOf(row("a", MealSlot.PAGI, 9999.0), row("b", MealSlot.PAGI, 1.0), row("c", MealSlot.MALAM, 500.0)),
            DayTargets(1750, 110, 205, 49), numbersHidden = true, activityKcal = 300,
        )
        assertEquals(DaySummary.Ticks(mealsLogged = 2, mealsTotal = 4), s)
    }

    @Test
    fun shopRounding_roundsUpToFiftyGrams() {
        assertEquals(0.0, roundUpShopGrams(0.0), 0.0)
        assertEquals(50.0, roundUpShopGrams(1.0), 0.0)
        assertEquals(250.0, roundUpShopGrams(201.0), 0.0)
        assertEquals(200.0, roundUpShopGrams(200.0), 0.0)
    }
}
