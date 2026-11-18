package app.xnkfit.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules.md §1. Hand calculations are in the comments. */
class BodyRulesTest {
    private val eps = 1e-9

    @Test
    fun R_MSJ_M_bmrMen() {
        assertEquals(10.0, R_MSJ_M_PER_KG, eps)
        assertEquals(6.25, R_MSJ_M_PER_CM, eps)
        assertEquals(5.0, R_MSJ_M_PER_YEAR, eps)
        assertEquals(5.0, R_MSJ_M_CONSTANT, eps)
        // 10·70 + 6.25·170 − 5·30 + 5 = 700 + 1062.5 − 150 + 5 = 1617.5
        assertEquals(1617.5, bmrKcal(Sex.LAKI_LAKI, 70.0, 170.0, 30), eps)
    }

    @Test
    fun R_MSJ_F_bmrWomen() {
        assertEquals(10.0, R_MSJ_F_PER_KG, eps)
        assertEquals(6.25, R_MSJ_F_PER_CM, eps)
        assertEquals(5.0, R_MSJ_F_PER_YEAR, eps)
        assertEquals(-161.0, R_MSJ_F_CONSTANT, eps)
        // 10·55 + 6.25·158 − 5·25 − 161 = 550 + 987.5 − 125 − 161 = 1251.5
        assertEquals(1251.5, bmrKcal(Sex.PEREMPUAN, 55.0, 158.0, 25), eps)
    }

    @Test
    fun R_MSJ_M_menAndWomenDifferBy166() {
        // Same body: the only difference is +5 versus −161.
        val m = bmrKcal(Sex.LAKI_LAKI, 60.0, 165.0, 40)
        val f = bmrKcal(Sex.PEREMPUAN, 60.0, 165.0, 40)
        assertEquals(166.0, m - f, eps)
    }

    @Test
    fun R_ACT_palValuesFromFaoWhoUnu() {
        // S2 PAL values chosen by the owner 2026-11-18 (were 1.2 / 1.375 / 1.55 / 1.725 / 1.9).
        assertEquals(
            listOf(1.40, 1.55, 1.70, 1.85, 2.00),
            ActivityLevel.entries.map { it.pal },
        )
        // 1.40 is S2's lower limit; nothing below it.
        assertTrue(ActivityLevel.entries.all { it.pal >= 1.40 })
    }

    @Test
    fun R_TDEE_isBmrTimesPal() {
        // 1617.5 × 1.70 = 2749.75
        assertEquals(2749.75, maintenanceKcal(1617.5, ActivityLevel.SEDANG), 1e-6)
        // 1251.5 × 1.55 = 1939.825
        assertEquals(1939.825, maintenanceKcal(1251.5, ActivityLevel.RINGAN), 1e-6)
    }

    @Test
    fun R_AGE_currentYearMinusBirthYear() {
        assertEquals(30, ageFromBirthYear(1996, 2026))
        assertEquals(13, ageFromBirthYear(2013, 2026))
        assertEquals(0, ageFromBirthYear(2026, 2026))
    }

    @Test
    fun R_BMI_kgPerSquareMetre() {
        // 70 / 1.7² = 70 / 2.89 = 24.221…
        assertEquals(24.2214532, bmi(70.0, 170.0), 1e-6)
        // 48 / 1.65² = 48 / 2.7225 = 17.6309…
        assertEquals(17.6308540, bmi(48.0, 165.0), 1e-6)
    }

    @Test
    fun R_BMI_UNDER_cutOffIs18point5() {
        assertEquals(18.5, R_BMI_UNDER, eps)
        assertTrue(isUnderweight(18.49))
        assertFalse(isUnderweight(18.5))
    }

    @Test
    fun R_WHTR_boundaryHalf() {
        assertEquals(0.5, R_WHTR_FLAG, eps)
        // 85 / 170 = 0.5 → flagged ("perhatikan"); 84 / 170 = 0.494 → not
        assertEquals(true, whtrFlag(85.0, 170.0))
        assertEquals(false, whtrFlag(84.0, 170.0))
        // Fail-safe: missing or impossible values give no flag at all.
        assertNull(whtrFlag(null, 170.0))
        assertNull(whtrFlag(85.0, Double.NaN))
        assertNull(whtrFlag(0.0, 170.0))
    }

    @Test
    fun R_INPUT_plausibilityLimits() {
        assertEquals(100.0, R_INPUT_HEIGHT_MIN_CM, eps)
        assertEquals(250.0, R_INPUT_HEIGHT_MAX_CM, eps)
        assertEquals(25.0, R_INPUT_WEIGHT_MIN_KG, eps)
        assertEquals(300.0, R_INPUT_WEIGHT_MAX_KG, eps)
        assertEquals(40.0, R_INPUT_WAIST_MIN_CM, eps)
        assertEquals(250.0, R_INPUT_WAIST_MAX_CM, eps)
        assertEquals(100, R_INPUT_AGE_MAX)
    }
}
