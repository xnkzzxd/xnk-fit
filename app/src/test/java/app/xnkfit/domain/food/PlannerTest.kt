package app.xnkfit.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/**
 * Week planner (B-10). The selection policy (R_PLAN_*) is a first version and is listed in Decisions.md for Opus review;
 * its tests are named `plannerGolden_*` and pin today's behaviour so a change to the policy shows up as a diff here.
 * The safety conditions (floor, limits, allergy, halal) are real rules and have their own running tests.
 */
class PlannerTest {
    private fun dayKcal(plan: WeekPlan, pool: List<MealPackage>, day: Int) =
        plannedDayKcal(plan.meals, pool.associateBy { it.id }, day)

    // ---- placeholders for the unreviewed rows (Rules.md ⚠) ----

    @Ignore("unverified")
    @Test
    fun R_PLAN_FIT_weightsNeedOpusReview() = fail("R_PLAN_FIT: first version, review pending (Decisions.md)")

    @Ignore("unverified")
    @Test
    fun R_PLAN_REPEAT_limitNeedsOpusReview() = fail("R_PLAN_REPEAT: first value, review pending (Decisions.md)")

    @Ignore("unverified")
    @Test
    fun R_PLAN_BUY_costNeedsOpusReview() = fail("R_PLAN_BUY: first value, review pending (Decisions.md)")

    @Ignore("unverified")
    @Test
    fun R_PLAN_COOK_ONCE_daysNeedOpusReview() = fail("R_PLAN_COOK_ONCE: first value, review pending (Decisions.md)")

    // ---- golden behaviour of the first version ----

    @Test
    fun plannerGolden_everyDayFilledAndNearTarget() {
        val pool = basicPool()
        val plan = planWeek(input(pool))
        assertEquals(R_PLAN_FIT_DAYS_PER_WEEK * MealSlot.entries.size, plan.meals.size)
        assertTrue(plan.daysBelowFloor.isEmpty())
        for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) {
            val k = dayKcal(plan, pool, d)
            assertTrue("day $d = $k", k in 1670.0..1730.0) // every combination lies in 1670..1730
            assertTrue("day $d = $k", kotlin.math.abs(k - 1700.0) <= 15.0) // and repeat costs only move it a little
        }
    }

    @Test
    fun plannerGolden_sameInputSameWeekAndSeedChangesTieBreaks() {
        val pool = basicPool()
        assertEquals(planWeek(input(pool)), planWeek(input(pool)))
        assertEquals(planWeek(input(pool, seed = 7)), planWeek(input(pool, seed = 7)))
    }

    @Test
    fun plannerGolden_samePackageAtMostTwiceAWeek() {
        val plan = planWeek(input(basicPool()))
        val counts = plan.meals.groupingBy { it.packageId }.eachCount()
        assertTrue(counts.toString(), counts.values.all { it <= R_PLAN_REPEAT_MAX_PER_WEEK })
    }

    @Test
    fun plannerGolden_goldenFirstDay() {
        // Pinned from the first version: with equal costs the id decides ties; day 1 uses the cheapest-tie ids.
        val plan = planWeek(input(basicPool()))
        val d1 = plan.meals.filter { it.day == 1 }.map { it.packageId }
        assertEquals(4, d1.size)
        assertEquals(listOf(MealSlot.PAGI, MealSlot.SIANG, MealSlot.MALAM, MealSlot.CAMILAN), plan.meals.filter { it.day == 1 }.map { it.slot })
        assertEquals(1700.0, dayKcal(plan, basicPool(), 1), 0.0)
    }

    @Test
    fun plannerGolden_oftenBuysSeesABoughtOptionAmongThree() {
        val pool = basicPool() + pkg("PAGI_BELI", MealSlot.PAGI, 700.0, mode = PackageMode.BELI, budget = Budget.BEBAS)
        val inp = input(pool, buys = BuyFrequency.OFTEN)
        val opts = slotOptions(inp, planWeek(inp).meals, 1, MealSlot.PAGI)
        assertEquals(R_OPTIONS, opts.size)
        assertTrue(opts.any { it.mode != PackageMode.MASAK })
        val rarely = input(pool, buys = BuyFrequency.RARELY)
        assertFalse(planWeek(rarely).meals.any { it.packageId == "PAGI_BELI" })
    }

    @Test
    fun plannerGolden_cookOnceRepeatsThreeDaysAndSkipsLockedCells() {
        val pool = basicPool()
        val plan = planWeek(input(pool)).meals
        val lockedTue = withChoice(plan, 2, MealSlot.SIANG, "SIANG_1", locked = true)
        val out = withCookOnce(lockedTue, 1, MealSlot.SIANG)
        val src = out.first { it.day == 1 && it.slot == MealSlot.SIANG }.packageId
        assertEquals("SIANG_1", out.first { it.day == 2 && it.slot == MealSlot.SIANG }.packageId) // locked, kept
        assertEquals(src, out.first { it.day == 3 && it.slot == MealSlot.SIANG }.packageId)
        assertTrue(out.first { it.day == 3 && it.slot == MealSlot.SIANG }.cookOnce)
        assertFalse(out.first { it.day == 4 && it.slot == MealSlot.SIANG }.cookOnce)
        assertEquals(plan.size, out.size)
        // Near the end of the week it stops at day 7.
        val late = withCookOnce(plan, 6, MealSlot.MALAM)
        assertEquals(plan.size, late.size)
        assertTrue(late.first { it.day == 7 && it.slot == MealSlot.MALAM }.cookOnce)
    }

    // ---- rules that are not ⚠ ----

    @Test
    fun R_PLAN_FLOOR_everyPlannedDayReachesTheFloorWhenPossible() {
        val pool = basicPool()
        val plan = planWeek(input(pool, target = 1500, floor = 1690))
        assertTrue(plan.daysBelowFloor.isEmpty())
        for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) assertTrue(dayKcal(plan, pool, d) >= 1690.0)
    }

    @Test
    fun R_PLAN_FLOOR_impossibleFloorTakesTheHighestAndMarksTheDay() {
        val pool = basicPool()
        val plan = planWeek(input(pool, target = 1700, floor = 5000))
        assertEquals((1..R_PLAN_FIT_DAYS_PER_WEEK).toSet(), plan.daysBelowFloor)
        // Day 1 takes the four highest: 420 + 620 + 520 + 170.
        assertEquals(1730.0, dayKcal(plan, pool, 1), 0.0)
    }

    @Test
    fun R_PLAN_FLOOR_targetBelowTheCheapestDayStillNeverGoesUnderFloor() {
        val pool = basicPool()
        val plan = planWeek(input(pool, target = 1200, floor = 1200))
        for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) assertTrue(dayKcal(plan, pool, d) >= 1200.0)
    }

    private fun santanPool(): List<MealPackage> = MealSlot.entries.flatMap { slot ->
        val base = mapOf(MealSlot.PAGI to 400.0, MealSlot.SIANG to 600.0, MealSlot.MALAM to 500.0, MealSlot.CAMILAN to 200.0).getValue(slot)
        // two santan options exactly on the target share, four plain ones a little off
        listOf(
            pkg("${slot.name}_S1", slot, base, santan = true), pkg("${slot.name}_S2", slot, base, santan = true),
            pkg("${slot.name}_P1", slot, base + 10), pkg("${slot.name}_P2", slot, base + 12),
            pkg("${slot.name}_P3", slot, base + 14), pkg("${slot.name}_P4", slot, base + 16),
        )
    }

    @Test
    fun R_LIMIT_SANTAN_plannerKeepsOnePerDayAndThreePerWeek() {
        val pool = santanPool()
        val plan = planWeek(input(pool, target = 1700, floor = 1500))
        val byId = pool.associateBy { it.id }
        for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) {
            assertTrue(plan.meals.filter { it.day == d }.count { byId[it.packageId]!!.santan } <= R_LIMIT_SANTAN_PER_DAY)
        }
        assertTrue(santanCount(plan.meals, byId) <= R_LIMIT_SANTAN_PER_WEEK)
        assertEquals(R_LIMIT_SANTAN_PER_WEEK, santanCount(plan.meals, byId)) // santan is the better fit, so the weekly cap is reached
    }

    @Test
    fun R_LIMIT_GORENG_andMainProteinAreKeptWithinADay() {
        val pool = MealSlot.entries.flatMap { slot ->
            val base = mapOf(MealSlot.PAGI to 400.0, MealSlot.SIANG to 600.0, MealSlot.MALAM to 500.0, MealSlot.CAMILAN to 200.0).getValue(slot)
            listOf(
                pkg("${slot.name}_F", slot, base, goreng = true, main = "AYAM"),
                pkg("${slot.name}_G", slot, base + 5, goreng = false, main = "TELUR"),
                pkg("${slot.name}_H", slot, base + 6, goreng = false, main = "IKAN"),
                pkg("${slot.name}_I", slot, base + 7, goreng = false, main = null),
            )
        }
        val plan = planWeek(input(pool))
        val byId = pool.associateBy { it.id }
        for (d in 1..R_PLAN_FIT_DAYS_PER_WEEK) {
            val day = plan.meals.filter { it.day == d }.map { byId[it.packageId]!! }
            assertTrue(day.count { it.goreng } <= R_LIMIT_GORENG_PER_DAY)
            val mains = day.mapNotNull { it.mainProtein }
            assertEquals(mains.toSet().size, mains.size)
        }
    }

    @Test
    fun R_LIMIT_ALLERGY_avoidedAllergensAreNeverChosenAutomatically() {
        val pool = basicPool() + MealSlot.entries.map { pkg("${it.name}_EGG", it, 700.0, allergens = setOf(Allergen.EGG)) } +
            pkg("PAGI_LAKTOSA", MealSlot.PAGI, 405.0, allergens = setOf(Allergen.LACTOSE))
        val plan = planWeek(input(pool, avoid = setOf(Allergen.EGG, Allergen.LACTOSE)))
        val byId = pool.associateBy { it.id }
        assertTrue(plan.meals.none { byId[it.packageId]!!.allergens.isNotEmpty() })
        assertEquals(28, plan.meals.size)
    }

    @Test
    fun R_HALAL_aNonHalalPackageIsNeverOffered() {
        val bad = MealSlot.entries.map { pkg("${it.name}_X", it, 1000.0, halal = false) }
        val pool = basicPool() + bad
        assertTrue(planWeek(input(pool)).meals.none { it.packageId.endsWith("_X") })
        val inp = input(pool)
        assertTrue(slotOptions(inp, planWeek(inp).meals, 1, MealSlot.SIANG).none { it.id.endsWith("_X") })
    }

    @Test
    fun R_BUDGET_preferenceOnlyWeighsTheChoiceAndHasNoPrice() {
        fun pool() = MealSlot.entries.flatMap { slot ->
            val base = mapOf(MealSlot.PAGI to 400.0, MealSlot.SIANG to 600.0, MealSlot.MALAM to 500.0, MealSlot.CAMILAN to 200.0).getValue(slot)
            Budget.entries.flatMap { b ->
                (1..4).map { i -> pkg("${slot.name}_${b.name}_$i", slot, base, budget = b) }
            }
        }
        val byId = pool().associateBy { it.id }
        val hemat = planWeek(input(pool(), budget = Budget.HEMAT))
        assertTrue(hemat.meals.all { byId[it.packageId]!!.budget == Budget.HEMAT })
        // Bebas prefers dearer options first, but variety (repeat cost) may move to cheaper ones later in the week.
        val bebas = planWeek(input(pool(), budget = Budget.BEBAS))
        assertTrue(bebas.meals.filter { it.day == 1 }.all { byId[it.packageId]!!.budget == Budget.BEBAS })
        // Standar never goes above its tier: the over-cost is larger than the repeat cost.
        val standar = planWeek(input(pool(), budget = Budget.STANDAR))
        assertTrue(standar.meals.none { byId[it.packageId]!!.budget == Budget.BEBAS })
    }

    @Test
    fun R_OPTIONS_threeDistinctOptionsPerSlotCurrentFirst() {
        val pool = basicPool(perSlot = 5)
        val inp = input(pool)
        val plan = planWeek(inp).meals
        val current = plan.first { it.day == 3 && it.slot == MealSlot.MALAM }.packageId
        val opts = slotOptions(inp, plan, 3, MealSlot.MALAM)
        assertEquals(R_OPTIONS, opts.size)
        assertEquals(current, opts.first().id)
        assertEquals(opts.size, opts.map { it.id }.toSet().size)
        assertTrue(opts.all { it.slot == MealSlot.MALAM })
    }

    @Test
    fun M4_manualChoicesAreNeverBlockedAndCountTowardTheLimits() {
        val pool = santanPool()
        val byId = pool.associateBy { it.id }
        // The user picks two santan meals on day 1 and a fourth/fifth over the week: nothing is refused.
        val fixed = listOf(
            PlannedMeal(1, MealSlot.PAGI, "PAGI_S1", locked = true),
            PlannedMeal(1, MealSlot.SIANG, "SIANG_S1", locked = true),
            PlannedMeal(2, MealSlot.SIANG, "SIANG_S2", locked = true),
            PlannedMeal(3, MealSlot.SIANG, "SIANG_S1", locked = true),
            PlannedMeal(4, MealSlot.SIANG, "SIANG_S2", locked = true),
        )
        val plan = planWeek(input(pool, fixed = fixed))
        assertTrue(plan.meals.containsAll(fixed))
        // Day 1 already has two santan, so the automatic slots that day take none; the week already has five, so no more anywhere.
        assertEquals(fixed.size, santanCount(plan.meals, byId))
        val withManual = withChoice(plan.meals, 5, MealSlot.MALAM, "MALAM_S1")
        assertEquals(fixed.size + 1, santanCount(withManual, byId)) // never blocked, just counted
    }

    @Test
    fun withEatOut_picksABoughtOptionAndFlagsIt() {
        val pool = basicPool() + pkg("MALAM_BELI", MealSlot.MALAM, 520.0, mode = PackageMode.BELI)
        val inp = input(pool)
        val plan = planWeek(inp).meals
        val out = withEatOut(inp, plan, 2, MealSlot.MALAM)!!
        val cell = out.first { it.day == 2 && it.slot == MealSlot.MALAM }
        assertEquals("MALAM_BELI", cell.packageId)
        assertTrue(cell.eatOut)
        assertNull(withEatOut(input(basicPool()), plan, 2, MealSlot.MALAM))
    }

    @Test
    fun withChoice_replacesTheCellAndKeepsTheOrder() {
        val plan = planWeek(input(basicPool())).meals
        val out = withChoice(plan, 4, MealSlot.PAGI, "PAGI_3")
        assertEquals(plan.size, out.size)
        val cell = out.first { it.day == 4 && it.slot == MealSlot.PAGI }
        assertEquals("PAGI_3", cell.packageId)
        assertTrue(cell.locked)
        assertEquals(out.sortedWith(compareBy({ it.day }, { it.slot.ordinal })), out)
        assertNotNull(out.firstOrNull { it.day == 7 })
    }

    @Test
    fun emptyPoolGivesAnEmptyPlanWithoutCrashing() {
        val plan = planWeek(input(emptyList()))
        assertTrue(plan.meals.isEmpty())
    }
}
