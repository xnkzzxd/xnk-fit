package app.xnkfit.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R_LEVEL is still a tuning default (Rules §12 #3, ⚠): these tests use the written values and are named without the rule ID. */
class LevelTest {
    @Test fun writtenThresholdsGiveTheMockupLevel() {
        val p = levelProgress(520)
        assertEquals(4, p.level); assertEquals(450, p.floor); assertEquals(700, p.next)
        assertEquals((520 - 450) / (700f - 450), p.fraction, 0.0001f)
    }

    @Test fun startOfEachLevel() {
        listOf(0, 100, 250, 450, 700, 1000, 1400, 1900, 2500).forEachIndexed { i, start ->
            assertEquals(i + 1, levelProgress(start).level)
            assertEquals(start, levelStart(i + 1))
        }
    }

    @Test fun pastTheTableEachLevelAddsSixHundred() {
        assertEquals(3100, levelStart(10))
        assertEquals(10, levelProgress(3100).level)
        assertEquals(9, levelProgress(3099).level)
        assertEquals(3700, levelProgress(3100).next)
    }

    @Test fun levelNeverDropsAsPointsOnlyAdd() {
        var last = 0
        for (p in 0..6000 step 13) { val l = levelProgress(p).level; assertTrue(l >= last); last = l }
    }
}
