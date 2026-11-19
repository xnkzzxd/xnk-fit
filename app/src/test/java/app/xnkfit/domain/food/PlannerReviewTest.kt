package app.xnkfit.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Review of the B-10 planner rules (docs/Review-Planner.md): edge cases and seeded random properties. */
class PlannerReviewTest {

    private fun dayKcal(plan: WeekPlan, pool: List<MealPackage>, day: Int): Double =
        plannedDayKcal(plan.meals, pool.associateBy { it.id }, day)

    /** Cheap low-kcal packages fill the 6-candidate shortlist; only dearer ones reach the floor. */
    private fun shortlistTrapPool(): List<MealPackage> = MealSlot.entries.flatMap { slot ->
        (1..7).map { i -> pkg("${slot.name}_H$i", slot, 100.0 + i) } +
            (1..4).map { i -> pkg("${slot.name}_B$i", slot, 600.0 + i, budget = Budget.BEBAS) }
    }

    @Test
    fun floorIsReachedWhenOnlyPackagesOutsideTheShortlistReachIt() {
        val pool = shortlistTrapPool()
        val plan = planWeek(input(pool, target = 1700, floor = 1500))
        assertTrue("below floor: ${plan.daysBelowFloor}", plan.daysBelowFloor.isEmpty())
        for (d in 1..7) assertTrue("day $d", dayKcal(plan, pool, d) >= 1500)
    }

    @Test
    fun dayWithNoEligibleOptionIsMarkedBelowFloor() {
        val pool = MealSlot.entries.map { pkg("${it.name}_E", it, 500.0, allergens = setOf(Allergen.EGG)) }
        val plan = planWeek(input(pool, avoid = setOf(Allergen.EGG)))
        assertTrue(plan.meals.isEmpty())
        assertEquals((1..7).toSet(), plan.daysBelowFloor)
    }

    @Test
    fun milkAllergyAlsoRemovesLactosePackages() {
        val lactoseOnly = pkg("PAGI_L", MealSlot.PAGI, 450.0, allergens = setOf(Allergen.LACTOSE))
        val pool = basicPool() + lactoseOnly
        val inp = input(pool, avoid = setOf(Allergen.MILK))
        assertFalse(eligible(lactoseOnly, inp))
        assertFalse(allowedAsAutomaticOption(lactoseOnly.toCandidate(), PlanSoFar(emptyList(), 0), setOf(Allergen.MILK)))
        val plan = planWeek(inp)
        assertTrue(plan.meals.none { it.packageId == "PAGI_L" })
        assertTrue(slotOptions(inp, plan.meals, 1, MealSlot.PAGI).none { it.id == "PAGI_L" })
    }

    @Test
    fun targetBelowFloorStillGivesDaysAtOrAboveFloor() {
        val pool = basicPool()
        val plan = planWeek(input(pool, target = 1000, floor = 1200))
        assertTrue(plan.daysBelowFloor.isEmpty())
        for (d in 1..7) assertTrue(dayKcal(plan, pool, d) >= 1200)
    }

    @Test
    fun veryHighTargetGivesAFullWeekWithoutCrashing() {
        val pool = basicPool()
        val plan = planWeek(input(pool, target = 4500, floor = 1500))
        assertEquals(28, plan.meals.size)
        assertTrue(plan.daysBelowFloor.isEmpty())
    }

    @Test
    fun optionsKeepingTheDayAtTheFloorComeFirst() {
        // Target equals floor: an option 10 kcal under the floor must not outrank one 20 kcal over it.
        val pool = listOf(
            pkg("P", MealSlot.PAGI, 500.0), pkg("S", MealSlot.SIANG, 500.0), pkg("C", MealSlot.CAMILAN, 100.0),
            pkg("M_LOW", MealSlot.MALAM, 390.0), pkg("M_HIGH", MealSlot.MALAM, 420.0),
        )
        val inp = input(pool, target = 1500, floor = 1500)
        val plan = listOf(PlannedMeal(1, MealSlot.PAGI, "P"), PlannedMeal(1, MealSlot.SIANG, "S"), PlannedMeal(1, MealSlot.CAMILAN, "C"))
        assertEquals("M_HIGH", slotOptions(inp, plan, 1, MealSlot.MALAM).first().id)
    }

    @Test
    fun fullyFixedDayIsLeftAsTheUserSetIt() {
        val pool = basicPool()
        val fixed = MealSlot.entries.map { PlannedMeal(3, it, "${it.name}_1", locked = true) }
        val plan = planWeek(input(pool, fixed = fixed))
        assertEquals(fixed, plan.meals.filter { it.day == 3 })
    }

    // ---- seeded random properties ----

    private fun randomPool(r: Random): List<MealPackage> = MealSlot.entries.flatMap { slot ->
        val (lo, hi) = if (slot == MealSlot.CAMILAN) 100 to 250 else 350 to 750
        (1..r.nextInt(0, 14)).map { i ->
            val allergens = Allergen.entries.filter { r.nextInt(8) == 0 }.toMutableSet()
            pkg(
                id = "${slot.name}_$i", slot = slot, kcal = r.nextInt(lo, hi).toDouble(),
                budget = Budget.entries[r.nextInt(3)], mode = PackageMode.entries[r.nextInt(3)],
                santan = r.nextInt(4) == 0, goreng = r.nextInt(3) == 0, main = "M${r.nextInt(6)}".takeIf { r.nextBoolean() },
                allergens = allergens, halal = r.nextInt(10) != 0, profile = listOf("jawa", "beli", "kos")[r.nextInt(3)],
            )
        }
    }

    @Test
    fun randomWeeksKeepTheHardRules() {
        val r = Random(20261119)
        repeat(300) { round ->
            val pool = randomPool(r)
            val avoid = Allergen.entries.filter { r.nextInt(4) == 0 }.toSet()
            val floor = if (r.nextBoolean()) 1200 else 1500
            val target = r.nextInt(900, 3500)
            val fixed = (1..r.nextInt(0, 5)).mapNotNull {
                pool.randomOrNull(r)?.let { p -> PlannedMeal(r.nextInt(1, 8), p.slot, p.id, locked = true) }
            }.distinctBy { it.day to it.slot }
            val profiles = if (r.nextInt(3) == 0) setOf("jawa", "kos") else null
            val inp = input(pool, target, floor, Budget.entries[r.nextInt(3)], BuyFrequency.entries[r.nextInt(3)], avoid, fixed, profiles, r.nextInt())
            val plan = planWeek(inp)
            val byId = pool.associateBy { it.id }
            val msg = "round $round"

            assertEquals(msg, plan, planWeek(inp)) // determinism
            assertEquals(msg, plan.meals.size, plan.meals.map { it.day to it.slot }.toSet().size)
            assertTrue(msg, plan.meals.containsAll(fixed))
            val auto = plan.meals.filterNot { it in fixed }
            for (m in auto) {
                val p = byId.getValue(m.packageId)
                assertTrue("$msg halal ${p.id}", p.halal)
                assertTrue("$msg allergen ${p.id}", p.allergens.none { it in avoid })
                if (Allergen.MILK in avoid) assertFalse("$msg lactose ${p.id}", Allergen.LACTOSE in p.allergens)
                assertTrue("$msg profile ${p.id}", profiles == null || p.profile in profiles)
                assertEquals(msg, p.slot, m.slot)
            }
            for (d in 1..7) {
                val kcal = dayKcal(plan, pool, d)
                assertEquals("$msg day $d marked", kcal < floor, d in plan.daysBelowFloor)
                if (kcal < floor) {
                    // Marked only when no eligible combination could reach the floor.
                    val fixedToday = fixed.filter { it.day == d }
                    val best = fixedToday.sumOf { byId.getValue(it.packageId).kcal } + MealSlot.entries
                        .filter { s -> fixedToday.none { it.slot == s } }
                        .sumOf { s -> pool.filter { it.slot == s && eligible(it, inp) }.maxOfOrNull { it.kcal } ?: 0.0 }
                    assertTrue("$msg day $d: $kcal < $floor but $best possible", best < floor)
                }
            }
        }
    }
}
