package app.xnkfit.ui.nav

import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.ui.common.useDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStackTest {
    @Test fun backStepsOutOfSettingsChildrenToSettingsThenHome() {
        var s = OverlayStack.open(emptyList(), Overlay.SETTINGS)
        s = OverlayStack.open(s, Overlay.DATA)
        assertEquals(listOf(Overlay.SETTINGS, Overlay.DATA), s)
        s = OverlayStack.back(s)
        assertEquals(Overlay.SETTINGS, s.last())
        assertTrue(OverlayStack.back(s).isEmpty())
    }

    @Test fun openingAScreenAlreadyOnTheStackDoesNotStackItTwice() {
        val s = OverlayStack.open(listOf(Overlay.SETTINGS, Overlay.HELP), Overlay.SETTINGS)
        assertEquals(listOf(Overlay.SETTINGS), s)
    }

    @Test fun savedTextRoundTripsAndIgnoresJunk() {
        val s = listOf(Overlay.SETTINGS, Overlay.PRIVACY)
        assertEquals(s, OverlayStack.parse(OverlayStack.save(s)))
        assertTrue(OverlayStack.parse("").isEmpty())
        assertEquals(listOf(Overlay.HELP), OverlayStack.parse("nope,HELP"))
    }

    @Test fun themeChoiceBeatsTheSystemSetting() {
        assertFalse(useDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(useDarkTheme(ThemeMode.DARK, systemDark = false))
        assertTrue(useDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(useDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }
}
