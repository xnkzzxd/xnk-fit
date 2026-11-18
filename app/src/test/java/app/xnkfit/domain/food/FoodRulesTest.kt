package app.xnkfit.domain.food

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/** Rules.md §6. */
class FoodRulesTest {
    private fun meal(
        id: String,
        santan: Boolean = false,
        fried: Boolean = false,
        main: String? = null,
        allergens: Set<Allergen> = emptySet(),
        halal: Boolean = true,
    ) = MealCandidate(id, santan, fried, main, allergens, halal)

    private val empty = PlanSoFar(emptyList(), 0)

    @Test
    fun R_HALAL_notHalalNeverOffered() {
        assertFalse(allowedAsAutomaticOption(meal("x", halal = false), empty, emptySet()))
    }

    @Test
    fun R_HALAL_bundledEstimatesHaveNoPorkOrAlcohol() {
        val json = RepoFiles.text("content/foods-estimates.json")
        val names = Regex("\"name_(?:id|en)\"\\s*:\\s*\"([^\"]*)\"").findAll(json).map { it.groupValues[1].lowercase() }.toList()
        assertTrue(names.isNotEmpty())
        names.forEach { name ->
            val words = name.split(Regex("[^\\p{L}]+")).toSet()
            R_HALAL_EXCLUDED.forEach { assertFalse("$name contains $it", it in words) }
        }
        assertFalse(Regex("\"halal\"\\s*:\\s*false").containsMatchIn(json))
    }

    @Test
    fun R_LIMIT_SANTAN_onePerDayThreePerWeek() {
        assertEquals(1, R_LIMIT_SANTAN_PER_DAY)
        assertEquals(3, R_LIMIT_SANTAN_PER_WEEK)
        val gulai = meal("gulai", santan = true)
        assertTrue(allowedAsAutomaticOption(gulai, empty, emptySet()))
        assertFalse(allowedAsAutomaticOption(gulai, PlanSoFar(listOf(meal("opor", santan = true)), 1), emptySet()))
        assertFalse(allowedAsAutomaticOption(gulai, PlanSoFar(emptyList(), 3), emptySet()))
        assertTrue(allowedAsAutomaticOption(gulai, PlanSoFar(emptyList(), 2), emptySet()))
    }

    @Test
    fun R_LIMIT_GORENG_onePerDay() {
        assertEquals(1, R_LIMIT_GORENG_PER_DAY)
        val ayamGoreng = meal("ayam", fried = true)
        assertFalse(allowedAsAutomaticOption(ayamGoreng, PlanSoFar(listOf(meal("tempe", fried = true)), 0), emptySet()))
    }

    @Test
    fun R_LIMIT_MAIN_sameMainOncePerDay() {
        assertEquals(1, R_LIMIT_MAIN_PER_DAY)
        val today = PlanSoFar(listOf(meal("soto", main = "ayam")), 0)
        assertFalse(allowedAsAutomaticOption(meal("sate", main = "ayam"), today, emptySet()))
        assertTrue(allowedAsAutomaticOption(meal("pepes", main = "ikan"), today, emptySet()))
    }

    @Test
    fun R_LIMIT_ALLERGY_filtersAllergens() {
        assertEquals(setOf("EGG", "SEAFOOD", "NUTS", "MILK", "LACTOSE"), Allergen.entries.map { it.name }.toSet())
        val udang = meal("udang", allergens = setOf(Allergen.SEAFOOD))
        assertFalse(allowedAsAutomaticOption(udang, empty, setOf(Allergen.SEAFOOD)))
        assertTrue(allowedAsAutomaticOption(udang, empty, setOf(Allergen.EGG)))
    }

    @Test
    fun R_BUDGET_threeLevelsNoRupiah() =
        assertEquals(listOf("HEMAT", "STANDAR", "BEBAS"), Budget.entries.map { it.name })

    @Test
    fun R_MEAL_SLOTS_four() =
        assertEquals(listOf("PAGI", "SIANG", "MALAM", "CAMILAN"), MealSlot.entries.map { it.name })

    @Test
    fun R_OPTIONS_three() = assertEquals(3, R_OPTIONS)

    /** ⚠ G7: household measure weights for cooked dishes have no single source yet (C-05, PMK 41/2014 not opened). */
    @Ignore("unverified")
    @Test
    fun R_HH_householdMeasureWeights() {
        // What can be checked now: every measure has a positive gram weight. The weights themselves wait for C-05.
        val json = RepoFiles.text("content/foods-estimates.json")
        val grams = Regex("\"grams\"\\s*:\\s*(-?[0-9.]+)").findAll(json).map { it.groupValues[1].toDouble() }.toList()
        assertTrue(grams.isNotEmpty() && grams.all { it > 0.0 })
    }

    @Test
    fun R_EST_everyEstimateRowIsFlagged() {
        val json = RepoFiles.text("content/foods-estimates.json")
        val rows = Regex("\"id\"\\s*:\\s*\"W_").findAll(json).count()
        val flagged = Regex("\"isEstimate\"\\s*:\\s*true").findAll(json).count()
        assertTrue(rows > 0)
        assertEquals(rows, flagged)
        assertFalse(Regex("\"isEstimate\"\\s*:\\s*false").containsMatchIn(json))
    }

    /** ⚠ G8: no published source for warung and bought-meal numbers (C-07, PRD open item 6). */
    @Ignore("unverified")
    @Test
    fun G8_boughtAndRegionalMealNumbers() {
        // Until C-07 finds a source these rows stay flagged estimates (R_EST); there is no sourced value to assert.
        fail("G8 open: bought and regional meal numbers have no source (Rules §10)")
    }
}
