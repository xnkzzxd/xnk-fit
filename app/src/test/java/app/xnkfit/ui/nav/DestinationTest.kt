package app.xnkfit.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** PRD N-1: the bottom bar is Hari ini · Latihan · ＋ · Makan · Saya, and ＋ is an action, not a screen. */
class DestinationTest {
    @Test
    fun barOrderIsFixed() {
        assertEquals(
            listOf("TODAY", "WORKOUT", "ADD", "FOOD", "ME"),
            Destination.entries.map { it.name },
        )
    }

    @Test
    fun addIsNotAScreen() {
        assertFalse(Destination.ADD.isScreen)
    }
}
