package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules.md §2. */
class TargetRulesTest {
    private val eps = 1e-9

    @Test
    fun R_GOAL_TURUN_minus500() {
        assertEquals(-500, R_GOAL_TURUN_KCAL)
        assertEquals(-500, goalAdjustmentKcal(Goal.TURUN))
    }

    @Test
    fun R_GOAL_NAIK_plus300() {
        assertEquals(300, R_GOAL_NAIK_KCAL)
        assertEquals(300, goalAdjustmentKcal(Goal.NAIK))
        // S19: +300 is within a 10–20 % surplus for maintenance 1,500–3,000 kcal.
        assertTrue(300.0 / 3000 >= 0.10 && 300.0 / 1500 <= 0.20)
    }

    @Test
    fun R_GOAL_JAGA_zero() {
        assertEquals(0, R_GOAL_JAGA_KCAL)
        assertEquals(0, goalAdjustmentKcal(Goal.JAGA))
    }

    @Test
    fun R_DEF_CAP_tenPercent() {
        assertEquals(0.10, R_DEF_CAP_FRACTION, eps)
    }

    @Test
    fun R_FLOOR_F_1200() {
        assertEquals(1200, R_FLOOR_F_KCAL)
        assertEquals(1200, sexFloorKcal(Sex.PEREMPUAN))
    }

    @Test
    fun R_FLOOR_M_1500() {
        assertEquals(1500, R_FLOOR_M_KCAL)
        assertEquals(1500, sexFloorKcal(Sex.LAKI_LAKI))
    }

    @Test
    fun R_ROUND_tenKcal() {
        assertEquals(10, R_ROUND_KCAL)
        assertEquals(2250, roundKcal(2249.75))
        assertEquals(1940, roundKcal(1939.825))
        assertEquals(3880, roundKcal(3875.125))
        assertEquals(1250, roundKcal(1245.0)) // halves go up
        assertEquals(1240, roundKcal(1244.99))
        // A floor is rounded up so the target never ends below it.
        assertEquals(1230, roundKcalUp(1229.0))
        assertEquals(1200, roundKcalUp(1200.0))
    }

    @Test
    fun R_KCAL_G_atwater() {
        assertEquals(4.0, R_KCAL_G_PROTEIN, eps)
        assertEquals(4.0, R_KCAL_G_CARB, eps)
        assertEquals(9.0, R_KCAL_G_FAT, eps)
    }

    @Test
    fun R_PROT_perKgWithFloorAndCap() {
        assertEquals(1.6, R_PROT_G_PER_KG, eps)
        assertEquals(1.4, R_PROT_MIN_G_PER_KG, eps)
        assertEquals(2.0, R_PROT_MAX_G_PER_KG, eps)
        // 1.6 × 70 = 112 g
        assertEquals(112, macros(2250, 70.0).proteinG)
        // A request outside 1.4–2.0 is held inside it: 3.0 → 2.0 → 140 g; 1.0 → 1.4 → 98 g.
        assertEquals(140, macros(2250, 70.0, proteinPerKg = 3.0).proteinG)
        assertEquals(98, macros(2250, 70.0, proteinPerKg = 1.0).proteinG)
    }

    @Test
    fun R_PROT_stepsDownToFloorWhenTargetTooSmall() {
        // 1,200 kcal, 150 kg: fat floor 0.8·150 = 120 g = 1080 kcal; protein 1.6·150 = 240 g = 960 kcal → negative carbs.
        // Protein steps to 1.4 g/kg (210 g); carbs still negative → 0, never below.
        val m = macros(1200, 150.0)
        assertEquals(1.4, m.proteinPerKg, eps)
        assertEquals(210, m.proteinG)
        assertEquals(0, m.carbG)
        assertEquals(120, m.fatG)
    }

    @Test
    fun R_FAT_quarterOfKcalWithFloor() {
        assertEquals(0.25, R_FAT_SHARE, eps)
        assertEquals(0.8, R_FAT_MIN_G_PER_KG, eps)
        // 25 % of 2,250 = 562.5 kcal / 9 = 62.5 g → 63 (floor 0.8·70 = 56 g is lower)
        assertEquals(63, macros(2250, 70.0).fatG)
        // 25 % of 1,200 = 300 kcal / 9 = 33.3 g, but floor 0.8·48 = 38.4 g → 38
        assertEquals(38, macros(1200, 48.0).fatG)
    }

    @Test
    fun R_CARB_isTheRest() {
        // 2,250 − 112·4 − 562.5 = 2250 − 448 − 562.5 = 1239.5 kcal / 4 = 309.875 → 310 g
        assertEquals(310, macros(2250, 70.0).carbG)
        // Sum of rounded grams stays within a few kcal of the target.
        val m = macros(2250, 70.0)
        val kcal = m.proteinG * R_KCAL_G_PROTEIN + m.carbG * R_KCAL_G_CARB + m.fatG * R_KCAL_G_FAT
        assertEquals(2250.0, kcal, 10.0)
    }

    @Test
    fun R_FIBRE_akgTableBySexAndAge() {
        val men = listOf(13 to 34, 15 to 34, 16 to 37, 18 to 37, 19 to 37, 29 to 37, 30 to 36, 49 to 36,
            50 to 30, 64 to 30, 65 to 25, 80 to 25, 81 to 22, 95 to 22)
        val women = listOf(13 to 29, 15 to 29, 16 to 29, 18 to 29, 19 to 32, 29 to 32, 30 to 30, 49 to 30,
            50 to 25, 64 to 25, 65 to 22, 80 to 22, 81 to 20, 95 to 20)
        men.forEach { (age, g) -> assertEquals("men $age", g, fibreG(Sex.LAKI_LAKI, age)) }
        women.forEach { (age, g) -> assertEquals("women $age", g, fibreG(Sex.PEREMPUAN, age)) }
        assertNull(fibreG(Sex.PEREMPUAN, 12))
    }

    @Test
    fun R_FIBRE_doesNotFallWithDeficit() {
        // Read from the table, not from the target: two targets, same fibre.
        val a = evaluateTargets(GateFixtures.man30.copy(goal = Goal.TURUN), GateFixtures.clear, 2026) as GateResult.Ready
        val b = evaluateTargets(GateFixtures.man30.copy(goal = Goal.NAIK), GateFixtures.clear, 2026) as GateResult.Ready
        assertEquals(a.fibreG, b.fibreG)
    }

    @Test
    fun R_RECALC_threeKg() {
        assertEquals(3.0, R_RECALC_KG, eps)
        assertTrue(recalculationSuggested(70.0, 67.0))
        assertTrue(recalculationSuggested(70.0, 73.0))
        assertFalse(recalculationSuggested(70.0, 67.5))
        assertFalse(recalculationSuggested(70.0, Double.NaN))
    }
}
