package app.xnkfit.data.food

import app.xnkfit.data.db.ProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The food screens read the target through B-02's gates only (R_GATE_ED, R_FLOOR_*). */
class TargetSourceTest {
    private fun profile(
        sex: String? = "perempuan", year: Int? = 1995, goal: String? = "turun", risk: Boolean = false,
        preg: Boolean = false, ed: String? = "tidak", done: Boolean = true,
    ) = ProfileEntity(
        createdAt = 0, updatedAt = 0, sex = sex, birthYear = year, heightCm = 160.0, goal = goal, interest = null, nickname = null,
        healthRisk = risk, screeningDone = done, pregnant = preg, eatingHistory = ed,
    )

    @Test fun readyProfileGivesTargetsAndAFloor() {
        val t = foodTargetsFrom(profile(), 70.0, 2026)!!
        assertTrue(t.targets.kcal >= 1200)
        assertFalse(t.numbersHidden)
        assertTrue(t.floorKcal >= 1200)
        assertTrue(t.targets.kcal >= t.floorKcal)
    }

    @Test fun R_GATE_ED_historyHidesNumbersAndKeepsAMaintainTarget() {
        val t = foodTargetsFrom(profile(ed = "ya"), 70.0, 2026)!!
        assertTrue(t.numbersHidden)
        val plain = foodTargetsFrom(profile(goal = "jaga"), 70.0, 2026)!!
        assertEquals(plain.targets.kcal, t.targets.kcal) // "turun" became maintain
    }

    @Test fun noTargetWithoutScreeningOrWeightOrForAChild() {
        assertNull(foodTargetsFrom(profile(done = false), 70.0, 2026))
        assertNull(foodTargetsFrom(profile(), null, 2026))
        assertNull(foodTargetsFrom(profile(year = 2018), 30.0, 2026))
    }

    @Test fun riskAndPregnancyNeverGiveADeficitBelowMaintenance() {
        val maintain = foodTargetsFrom(profile(goal = "jaga"), 70.0, 2026)!!.targets.kcal
        val preg = foodTargetsFrom(profile(preg = true), 70.0, 2026)!!.targets.kcal
        assertEquals(maintain, preg)
        val risk = foodTargetsFrom(profile(risk = true), 70.0, 2026)!!.targets.kcal
        assertTrue(risk >= maintain * 0.9 - 10)
        assertNotNull(risk)
    }
}
