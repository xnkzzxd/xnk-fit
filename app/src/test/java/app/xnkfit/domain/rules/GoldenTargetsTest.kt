package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden profiles: whole target computed by hand (comments) and compared field by field.
 * Year 2026. Rounding: kcal to 10 (R_ROUND, halves up), grams to whole grams.
 * Macro rule: protein 1.6 g/kg; fat max(25 % kcal, 0.8 g/kg); carbs = rest / 4 (fat kcal unrounded).
 */
class GoldenTargetsTest {
    private val clear = GateFixtures.clear

    private fun ready(p: Profile, s: Screening = clear) = evaluateTargets(p, s, 2026) as GateResult.Ready

    @Test
    fun golden_R_GOAL_TURUN_man30Average() {
        // Man, 1996 → 30 y, 170 cm, 70 kg, sedang (1.70), turun.
        // BMR  = 10·70 + 6.25·170 − 5·30 + 5 = 1617.5
        // TDEE = 1617.5 × 1.70 = 2749.75 → shown 2750
        // Target = 2749.75 − 500 = 2249.75 → 2250 (floor max(1500, 1617.5) not hit)
        // Protein 1.6·70 = 112 g (448 kcal); fat 0.25·2250 = 562.5 kcal = 62.5 g → 63 (floor 56 g)
        // Carbs (2250 − 448 − 562.5) / 4 = 309.875 → 310 g. Fibre men 30–49: 36 g. BMI 24.2.
        val r = ready(Profile(Sex.LAKI_LAKI, 1996, 170.0, 70.0, ActivityLevel.SEDANG, Goal.TURUN))
        assertEquals(1617.5, r.bmrKcal, 1e-9)
        assertEquals(2750, r.maintenanceKcal)
        assertEquals(2250, r.targetKcal)
        assertEquals(Macros(112, 63, 310, 1.6), r.macros)
        assertEquals(36, r.fibreG)
        assertEquals(24.22, r.bmi, 0.01)
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun golden_R_GOAL_JAGA_woman25() {
        // Woman, 2001 → 25 y, 158 cm, 55 kg, ringan (1.55), jaga.
        // BMR  = 550 + 987.5 − 125 − 161 = 1251.5
        // TDEE = 1251.5 × 1.55 = 1939.825 → 1940 = target
        // Protein 88 g (352 kcal); fat 0.25·1940 = 485 kcal = 53.9 g → 54 (floor 44 g)
        // Carbs (1940 − 352 − 485) / 4 = 275.75 → 276 g. Fibre women 19–29: 32 g.
        val r = ready(Profile(Sex.PEREMPUAN, 2001, 158.0, 55.0, ActivityLevel.RINGAN, Goal.JAGA))
        assertEquals(1251.5, r.bmrKcal, 1e-9)
        assertEquals(1940, r.maintenanceKcal)
        assertEquals(1940, r.targetKcal)
        assertEquals(Macros(88, 54, 276, 1.6), r.macros)
        assertEquals(32, r.fibreG)
        assertTrue(r.notes.isEmpty())
    }

    @Test
    fun golden_R_GOAL_NAIK_tallMan22() {
        // Man, 2004 → 22 y, 190 cm, 85 kg, aktif (1.85), naik.
        // BMR  = 850 + 1187.5 − 110 + 5 = 1932.5
        // TDEE = 1932.5 × 1.85 = 3575.125 → shown 3580
        // Target = 3575.125 + 300 = 3875.125 → 3880
        // Protein 1.6·85 = 136 g (544 kcal); fat 0.25·3880 = 970 kcal = 107.8 g → 108 (floor 68 g)
        // Carbs (3880 − 544 − 970) / 4 = 591.5 → 592 g. Fibre men 19–29: 37 g.
        val r = ready(Profile(Sex.LAKI_LAKI, 2004, 190.0, 85.0, ActivityLevel.AKTIF, Goal.NAIK))
        assertEquals(1932.5, r.bmrKcal, 1e-9)
        assertEquals(3580, r.maintenanceKcal)
        assertEquals(3880, r.targetKcal)
        assertEquals(Macros(136, 108, 592, 1.6), r.macros)
        assertEquals(37, r.fibreG)
    }

    @Test
    fun golden_R_GOAL_TURUN_tallWomanVeryActive() {
        // Woman, 1991 → 35 y, 180 cm, 75 kg, sangat aktif (2.00), turun.
        // BMR  = 750 + 1125 − 175 − 161 = 1539
        // TDEE = 1539 × 2.00 = 3078 → shown 3080
        // Target = 3078 − 500 = 2578 → 2580
        // Protein 1.6·75 = 120 g (480 kcal); fat 0.25·2580 = 645 kcal = 71.7 g → 72 (floor 60 g)
        // Carbs (2580 − 480 − 645) / 4 = 363.75 → 364 g. Fibre women 30–49: 30 g.
        val r = ready(Profile(Sex.PEREMPUAN, 1991, 180.0, 75.0, ActivityLevel.SANGAT_AKTIF, Goal.TURUN))
        assertEquals(1539.0, r.bmrKcal, 1e-9)
        assertEquals(3080, r.maintenanceKcal)
        assertEquals(2580, r.targetKcal)
        assertEquals(Macros(120, 72, 364, 1.6), r.macros)
        assertEquals(30, r.fibreG)
    }

    @Test
    fun golden_R_GATE_FLOOR_smallWomanTurun() {
        // Woman, 1981 → 45 y, 150 cm, 48 kg, jarang (1.40), turun.
        // BMR = 480 + 937.5 − 225 − 161 = 1031.5; TDEE = 1444.1 → shown 1440
        // 1444.1 − 500 = 944.1 → 940 < 1,200 → target 1,200 (FLOOR_RAISED)
        // Protein 1.6·48 = 76.8 → 77 g (307.2 kcal); fat: 25 % = 300 kcal (33.3 g) < floor 0.8·48 = 38.4 g → 38
        // Carbs (1200 − 307.2 − 345.6) / 4 = 136.8 → 137 g. Fibre women 30–49: 30 g.
        val r = ready(Profile(Sex.PEREMPUAN, 1981, 150.0, 48.0, ActivityLevel.JARANG, Goal.TURUN))
        assertEquals(1440, r.maintenanceKcal)
        assertEquals(1200, r.targetKcal)
        assertEquals(Macros(77, 38, 137, 1.6), r.macros)
        assertEquals(30, r.fibreG)
        assertEquals(listOf(GateNote.FLOOR_RAISED), r.notes)
    }

    @Test
    fun golden_R_GATE_U18_boy15Turun() {
        // Boy, 2011 → 15 y, 172 cm, 65 kg, sedang (1.70), asks turun → jaga (R_GATE_U18).
        // BMR = 650 + 1075 − 75 + 5 = 1655; TDEE = 2813.5 → 2810 (2813.5 rounds to 2810)
        // Protein 104 g (416 kcal); fat 0.25·2810 = 702.5 kcal = 78.1 g → 78 (floor 52 g)
        // Carbs (2810 − 416 − 702.5) / 4 = 422.875 → 423 g. Fibre men 13–15: 34 g.
        val r = ready(Profile(Sex.LAKI_LAKI, 2011, 172.0, 65.0, ActivityLevel.SEDANG, Goal.TURUN))
        assertEquals(Goal.JAGA, r.goalUsed)
        assertEquals(2810, r.targetKcal)
        assertEquals(Macros(104, 78, 423, 1.6), r.macros)
        assertEquals(34, r.fibreG)
        assertTrue(r.program.lightMode)
    }
}
