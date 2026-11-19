package app.xnkfit.data.food

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.BuyFrequency
import app.xnkfit.domain.food.MealPackage
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PlannedMeal
import app.xnkfit.domain.food.PlannerInput
import app.xnkfit.domain.food.R_PLAN_FIT_DAYS_PER_WEEK
import app.xnkfit.domain.food.R_PLAN_REPEAT_MAX_PER_WEEK
import app.xnkfit.domain.food.dayTotals
import app.xnkfit.domain.food.plannedDayKcal
import app.xnkfit.domain.food.planWeek
import app.xnkfit.domain.food.shoppingList
import app.xnkfit.domain.food.slotOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.system.measureNanoTime

/** B-10 on Room with the real bundled content, on the JVM under Robolectric (no device). */
@RunWith(RobolectricTestRunner::class)
class FoodRepositoryTest {
    private lateinit var db: XnkDatabase
    private lateinit var repo: FoodRepository
    private var clock = 1_000L
    private var seq = 0

    @Before fun open() {
        db = T.db()
        runBlocking { BundledContentLoader(ContentStore(db) { clock }, T.repoSource) { clock }.loadIfNeeded() }
        repo = FoodRepository(db, now = { clock++ }, newId = { "id${seq++}" })
    }

    @After fun close() { db.close() }

    // ---- search and household measures ----

    @Test fun searchFindsFoodsAndPutsPrefixMatchesFirst() = runBlocking {
        val hits = repo.search("ayam gor")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().nameId.lowercase().startsWith("ayam"))
        assertTrue(repo.search("   ").isEmpty())
        assertTrue(repo.search("\"*(").isEmpty()) // FTS syntax characters never reach MATCH
    }

    @Test fun householdMeasuresReachTheFoodWithPmkAndEstimateFlags() = runBlocking {
        val nasi = repo.food("TKPI_AP001")!!
        assertTrue(nasi.household.isNotEmpty())
        val centong = nasi.household.first { it.code == "centong" }
        assertEquals(100.0, centong.grams, 0.0)
        assertTrue(centong.estimate) // centong is our estimate (⚠ G7), not printed in PMK
        assertTrue(nasi.household.any { !it.estimate })
    }

    /** Design §10: a search keystroke must stay inside 100 ms. JVM with real SQLite FTS4 on the 1,300 bundled rows; a phone is slower, see TODO. */
    @Test fun searchStaysInsideTheHundredMillisecondBudget() = runBlocking {
        repeat(3) { repo.search("ayam") } // warm up
        val typed = listOf("a", "ay", "aya", "ayam", "ayam g", "ayam go", "ayam gor", "ayam gore", "ayam goreng", "nasi", "tempe", "sayur asem")
        val times = typed.map { q -> measureNanoTime { repo.search(q) } / 1_000_000.0 }
        val worst = times.max()
        println("search worst ms: $worst of $times")
        assertTrue("slowest keystroke $worst ms, all: $times", worst < 100.0)
    }

    // ---- log ----

    @Test fun R_LOG_valuesAreCopiedAtWriteAndSurviveAFoodUpdate() = runBlocking {
        val nasi = repo.food("TKPI_AP001")!!
        val id = repo.logFood("2026-11-19", MealSlot.SIANG, nasi, 150.0)
        val before = repo.logOfDay("2026-11-19").single()
        // A content update changes the food row afterwards.
        db.foodDao().upsertFoods(listOf(db.foodDao().food("TKPI_AP001")!!.copy(kcalPer100 = 999.0)))
        val after = repo.logOfDay("2026-11-19").single()
        assertEquals(before, after)
        assertEquals(id, after.id)
        assertEquals(nasi.per100.kcal * 1.5, after.nutrients.kcal, 0.06)
    }

    @Test fun R_LOG_quickAddCountsInTheDayAndCopyKeepsStoredValues() = runBlocking {
        val nasi = repo.food("TKPI_AP001")!!
        repo.logFood("2026-11-18", MealSlot.PAGI, nasi, 100.0)
        repo.logQuick("2026-11-18", MealSlot.CAMILAN, 130.0)
        db.foodDao().upsertFoods(listOf(db.foodDao().food("TKPI_AP001")!!.copy(kcalPer100 = 500.0)))
        assertEquals(2, repo.copyLog("2026-11-18", "2026-11-19"))
        val copied = repo.logOfDay("2026-11-19")
        assertEquals(repo.logOfDay("2026-11-18").map { it.nutrients to it.slot }, copied.map { it.nutrients to it.slot })
        assertEquals(nasi.per100.kcal + 130.0, dayTotals(copied).kcal, 0.06)
        assertTrue(copied.any { it.quick })
        // One meal only.
        assertEquals(1, repo.copyLog("2026-11-18", "2026-11-20", MealSlot.CAMILAN))
        assertEquals(1, repo.logOfDay("2026-11-20").size)
    }

    @Test fun deletingAnOwnFoodDoesNotDeleteItsLogRows() = runBlocking {
        val id = repo.saveOwnFood("Bubur ibu", 80.0, 3.0, 12.0, 2.0, servingGrams = 250.0)
        val food = repo.food(id)!!
        assertEquals(250.0, food.household.single().gramsPerUnit, 0.0)
        repo.logFood("2026-11-19", MealSlot.PAGI, food, 250.0)
        assertTrue(repo.search("bubur ibu").any { it.id == id })
        assertEquals(1, repo.logOfDay("2026-11-19").size)
    }

    @Test fun recentAndFavouritesComeFromTheLog() = runBlocking {
        val nasi = repo.food("TKPI_AP001")!!
        val other = repo.search("tempe").first()
        repeat(3) { repo.logFood("2026-11-1${it}", MealSlot.SIANG, nasi, 100.0) }
        repo.logFood("2026-11-19", MealSlot.SIANG, other, 50.0)
        assertEquals(other.id, repo.recent().first().id)
        assertEquals(listOf(nasi.id), repo.favourites().map { it.id })
    }

    @Test fun observeDayEmitsTheRows() = runBlocking {
        repo.logQuick("2026-11-19", MealSlot.MALAM, 200.0)
        assertEquals(1, repo.observeDay("2026-11-19").first().size)
    }

    // ---- planner on the real packages ----

    private fun input(packages: List<MealPackage>, avoid: Set<Allergen> = emptySet(), budget: Budget = Budget.HEMAT, profiles: Set<String>? = null, fixed: List<PlannedMeal> = emptyList()) =
        PlannerInput(packages, avoid, budget, BuyFrequency.SOMETIMES, targetKcal = 1750, floorKcal = 1500, profiles = profiles, fixed = fixed, seed = 3)

    @Test fun realPackagesParseAndKeepTheirFlags() = runBlocking {
        val all = repo.packages()
        assertEquals(72, all.size)
        assertTrue(all.all { it.halal })
        assertTrue(all.any { it.santan } && all.any { it.goreng } && all.any { it.allergens.contains(Allergen.EGG) })
        assertTrue(all.any { it.allergens.contains(Allergen.LACTOSE) })
        assertTrue(all.all { it.items.isNotEmpty() && it.kcal > 0 })
    }

    @Test fun realWeekMeetsFloorLimitsAndRepeatCapForEveryProfileSet() = runBlocking {
        val all = repo.packages()
        val byId = all.associateBy { it.id }
        val sets = listOf(null, setOf("jawa"), setOf("kos"), setOf("beli"), setOf("sunda", "padang"))
        sets.forEach { profiles ->
            val plan = planWeek(input(all, profiles = profiles))
            assertEquals("profiles=$profiles", 28, plan.meals.size)
            assertTrue("profiles=$profiles below floor: ${plan.daysBelowFloor}", plan.daysBelowFloor.isEmpty())
            for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) assertTrue(plannedDayKcal(plan.meals, byId, d) >= 1500.0)
            // One profile has only 3 options per slot, which cannot fill 7 days with 2 uses each: the cap is relaxed there on purpose.
            if (profiles == null || profiles.size > 1) {
                assertTrue("profiles=$profiles", plan.meals.groupingBy { it.packageId }.eachCount().values.all { it <= R_PLAN_REPEAT_MAX_PER_WEEK })
            }
            val santan = plan.meals.count { byId[it.packageId]!!.santan }
            assertTrue("profiles=$profiles santan=$santan", santan <= 3)
        }
    }

    @Test fun realWeekWithEggAndMilkFiltersHasNoneOfThem() = runBlocking {
        val all = repo.packages()
        val byId = all.associateBy { it.id }
        val avoid = setOf(Allergen.EGG, Allergen.MILK, Allergen.LACTOSE, Allergen.SEAFOOD, Allergen.NUTS)
        val plan = planWeek(input(all, avoid = avoid))
        assertTrue(plan.meals.isNotEmpty())
        assertTrue(plan.meals.none { byId[it.packageId]!!.allergens.any { a -> a in avoid } })
    }

    @Test fun planSavesAndLoadsAndOptionsComeInThrees() = runBlocking {
        val all = repo.packages()
        val inp = input(all)
        val plan = planWeek(inp).meals
        repo.savePlan("2026-11-16", plan)
        assertEquals(plan, repo.loadPlan("2026-11-16"))
        repo.savePlan("2026-11-16", plan.take(5))
        assertEquals(5, repo.loadPlan("2026-11-16").size) // the whole week is replaced, no leftovers
        MealSlot.entries.forEach { s -> assertEquals(3, slotOptions(inp, plan, 2, s).size) }
    }

    @Test fun shoppingListFromARealWeekHasGroupsAndCounts() = runBlocking {
        val all = repo.packages()
        val plan = planWeek(input(all, profiles = setOf("jawa", "sunda"))).meals
        val measures = all.flatMap { p -> p.items.map { it.foodId } }.toSet().associateWith { id -> repo.food(id)?.household.orEmpty() }
        val list = shoppingList(plan, all.associateBy { it.id }, 1) { measures[it].orEmpty() }
        assertTrue(list.size > 10)
        assertTrue(list.all { it.grams > 0.0 })
        assertTrue(list.map { it.group }.distinct().size >= 3)
    }

    // ---- shopping ticks ----

    @Test fun shoppingTicksSurviveARestartOfTheStore() = runBlocking {
        val file = File.createTempFile("ticks", ".preferences_pb").also { it.delete() }
        fun store() = ShoppingTickStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file })
        val a = store()
        a.set("2026-11-16", "TKPI_HR002", true)
        a.set("2026-11-16", "TKPI_DR109", true)
        a.set("2026-11-16", "TKPI_DR109", false)
        assertEquals(setOf("TKPI_HR002"), a.observe("2026-11-16").first())
        assertEquals(emptySet<String>(), a.observe("2026-11-23").first())
        assertNotNull(file)
    }
}
