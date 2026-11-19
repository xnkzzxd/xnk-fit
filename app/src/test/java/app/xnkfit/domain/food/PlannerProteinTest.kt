package app.xnkfit.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.fail
import kotlin.random.Random

/** R_PLAN_PROT (owner answer 2026-11-19): the planner aims at the R_PROT range, after the floor, filters and limits. */
class PlannerProteinTest {
    private val range = 90..130

    private fun day1(plan: WeekPlan) = plan.meals.filter { it.day == 1 }

    /** Days 2–7 fully fixed, so only day 1 is planned and runs with and without protein can be compared. */
    private fun fixedRest(pool: List<MealPackage>, rnd: Random): List<PlannedMeal> =
        (2..7).flatMap { d -> MealSlot.entries.mapNotNull { s -> pool.filter { it.slot == s }.randomOrNull(rnd)?.let { PlannedMeal(d, s, it.id, locked = true) } } }

    @Ignore("unverified")
    @Test
    fun R_PLAN_PROT_weightsNeedASource() = fail("R_PLAN_PROT: cost 10 per gram is a first value without a source (Decisions.md)")

    @Test
    fun plannerProtein_goldenPicksTheHigherProteinPackageAtEqualKcal() {
        val pool = basicPool(1).filter { it.slot != MealSlot.SIANG } +
            pkg("SIANG_lo", MealSlot.SIANG, 600.0, protein = 10.0) + pkg("SIANG_hi", MealSlot.SIANG, 600.0, protein = 60.0)
        val plan = planWeek(input(pool, protein = range))
        assertTrue(plan.meals.filter { it.slot == MealSlot.SIANG }.count { it.packageId == "SIANG_hi" } >= 2)
        assertEquals("SIANG_hi", day1(plan).first { it.slot == MealSlot.SIANG }.packageId)
    }

    @Test
    fun R_PLAN_FLOOR_neverTradedForProtein() {
        // The high-protein lunch leaves the day under the floor; the low-protein one reaches it.
        val pool = basicPool(1).filter { it.slot != MealSlot.SIANG } +
            pkg("SIANG_lo", MealSlot.SIANG, 600.0, protein = 5.0) + pkg("SIANG_hi", MealSlot.SIANG, 300.0, protein = 90.0)
        val plan = planWeek(input(pool, target = 1600, floor = 1600, protein = range))
        assertTrue(plan.daysBelowFloor.isEmpty())
        assertEquals("SIANG_lo", day1(plan).first { it.slot == MealSlot.SIANG }.packageId)
    }

    @Test
    fun R_LIMIT_ALLERGY_neverLiftedForProtein() {
        val pool = basicPool(2) + pkg("SIANG_egg", MealSlot.SIANG, 605.0, protein = 80.0, allergens = setOf(Allergen.EGG)) +
            pkg("PAGI_lac", MealSlot.PAGI, 405.0, protein = 80.0, allergens = setOf(Allergen.LACTOSE))
        val plan = planWeek(input(pool, avoid = setOf(Allergen.EGG, Allergen.MILK), protein = range))
        assertTrue(plan.meals.none { it.packageId == "SIANG_egg" || it.packageId == "PAGI_lac" })
    }

    @Test
    fun R_HALAL_neverLiftedForProtein() {
        val pool = basicPool(2) + pkg("MALAM_x", MealSlot.MALAM, 505.0, protein = 90.0, halal = false)
        assertTrue(planWeek(input(pool, protein = range)).meals.none { it.packageId == "MALAM_x" })
    }

    @Test
    fun plannerProtein_doesNotRaiseTheDayAboveTheTarget() {
        // A dinner of 900 kcal has much more protein but takes the day above the target: protein must not pick it.
        val pool = basicPool(1).filter { it.slot != MealSlot.MALAM } +
            pkg("MALAM_lo", MealSlot.MALAM, 500.0, protein = 10.0) + pkg("MALAM_hi", MealSlot.MALAM, 900.0, protein = 100.0)
        val plan = planWeek(input(pool, target = 1700, floor = 1500, protein = range))
        assertEquals("MALAM_lo", day1(plan).first { it.slot == MealSlot.MALAM }.packageId)
    }

    @Test
    fun plannerProtein_propertySafeAndNeverWorseOnProtein() {
        val rnd = Random(20261119)
        repeat(300) { n ->
            val slots = MealSlot.entries
            val pool = (0 until rnd.nextInt(4, 24)).map { i ->
                val slot = slots[rnd.nextInt(slots.size)]
                pkg(
                    "p$i", slot, rnd.nextInt(80, 900).toDouble(), budget = Budget.entries[rnd.nextInt(3)],
                    santan = rnd.nextInt(5) == 0, goreng = rnd.nextInt(5) == 0, main = listOf("ayam", "ikan", "telur", null)[rnd.nextInt(4)],
                    allergens = if (rnd.nextInt(6) == 0) setOf(Allergen.entries[rnd.nextInt(Allergen.entries.size)]) else emptySet(),
                    halal = rnd.nextInt(10) != 0, protein = rnd.nextInt(0, 60).toDouble(),
                )
            }
            val avoid = if (rnd.nextBoolean()) setOf(Allergen.entries[rnd.nextInt(Allergen.entries.size)]) else emptySet()
            val target = rnd.nextInt(1300, 2600)
            val floor = rnd.nextInt(1200, target + 1)
            val fixed = fixedRest(pool, rnd)
            val base = input(pool, target = target, floor = floor, avoid = avoid, fixed = fixed, seed = n)
            val prot = base.copy(proteinRangeG = rnd.nextInt(50, 120).let { it..(it + 40) })
            val byId = pool.associateBy { it.id }
            val a = planWeek(base)
            val b = planWeek(prot)
            val ka = plannedDayKcal(a.meals, byId, 1)
            val kb = plannedDayKcal(b.meals, byId, 1)
            val msg = "case $n"
            // Floor first: protein never loses a floor the plan without protein reached.
            if (ka >= floor) assertTrue(msg, kb >= floor)
            assertEquals(msg, 1 in a.daysBelowFloor, 1 in b.daysBelowFloor)
            // Filters hold for every automatic pick.
            day1(b).forEach { m ->
                val p = byId.getValue(m.packageId)
                assertTrue(msg, p.halal)
                assertFalse(msg, p.allergens.any { it in avoid.withLactose() })
            }
            // Protein only changes the day when it stays at or under max(target, floor), and never makes protein worse.
            if (day1(a) != day1(b)) assertTrue(msg, kb <= maxOf(target, floor) || ka == kb)
            val pa = proteinCost(prot, ka, plannedDayProtein(a.meals, byId, 1))
            val pb = proteinCost(prot, kb, plannedDayProtein(b.meals, byId, 1))
            assertTrue("$msg $pa $pb", pb <= pa + 1e-6)
            // Deterministic.
            assertEquals(msg, b, planWeek(prot))
        }
    }
}

/** Owner answer 2026-11-19: with no target the planner still plans, with no kcal aim and no floor; filters still hold. */
class PlannerNoTargetTest {
    private fun noTarget(pool: List<MealPackage>, avoid: Set<Allergen> = emptySet()) =
        input(pool, avoid = avoid).copy(targetKcal = null, floorKcal = null, proteinRangeG = range())

    private fun range(): IntRange? = 90..130 // ignored without a target

    @Test
    fun plannerNoTarget_fillsTheWeekAndMarksNoDay() {
        val plan = planWeek(noTarget(basicPool()))
        assertEquals(28, plan.meals.size)
        assertTrue(plan.daysBelowFloor.isEmpty())
        assertEquals(plan, planWeek(noTarget(basicPool())))
    }

    @Test
    fun R_LIMIT_ALLERGY_keptWithoutATarget() {
        val pool = basicPool(2) + pkg("SIANG_egg", MealSlot.SIANG, 10.0, allergens = setOf(Allergen.EGG)) +
            pkg("PAGI_lac", MealSlot.PAGI, 10.0, allergens = setOf(Allergen.LACTOSE)) + pkg("MALAM_x", MealSlot.MALAM, 10.0, halal = false)
        val plan = planWeek(noTarget(pool, setOf(Allergen.EGG, Allergen.MILK)))
        assertTrue(plan.meals.none { it.packageId in setOf("SIANG_egg", "PAGI_lac", "MALAM_x") })
        val inp = noTarget(pool, setOf(Allergen.EGG, Allergen.MILK))
        for (d in 1..7) for (s in MealSlot.entries) {
            assertTrue(slotOptions(inp, plan.meals, d, s).none { it.id in setOf("SIANG_egg", "PAGI_lac", "MALAM_x") })
        }
    }

    @Test
    fun R_LIMIT_SANTAN_keptWithoutATarget() {
        val pool = basicPool(1) + (1..6).map { pkg("MALAM_s$it", MealSlot.MALAM, 300.0, santan = true) } +
            (1..6).map { pkg("SIANG_s$it", MealSlot.SIANG, 300.0, santan = true) }
        val plan = planWeek(noTarget(pool))
        val byId = pool.associateBy { it.id }
        assertTrue(santanCount(plan.meals, byId) <= 3)
        for (d in 1..7) assertTrue(plan.meals.count { it.day == d && byId.getValue(it.packageId).santan } <= 1)
    }

    @Test
    fun plannerNoTarget_eatOutStillWorks() {
        val pool = basicPool(1) + pkg("SIANG_beli", MealSlot.SIANG, 700.0, mode = PackageMode.BELI)
        val inp = noTarget(pool)
        val next = withEatOut(inp, planWeek(inp).meals, 1, MealSlot.SIANG)
        assertEquals("SIANG_beli", next!!.first { it.day == 1 && it.slot == MealSlot.SIANG }.packageId)
    }
}
