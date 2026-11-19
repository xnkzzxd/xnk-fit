package app.xnkfit.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class WidthClassTest {
    @Test fun phoneWidthsKeepBottomBar() {
        for (w in listOf(320, 360, 411, 430, 599)) assertEquals("$w", WidthClass.COMPACT, widthClassOf(w))
    }

    @Test fun tabletWidthsUseRail() {
        for (w in listOf(600, 601, 800, 1280)) assertEquals("$w", WidthClass.WIDE, widthClassOf(w))
    }
}

class TwoColumnsRuleTest {
    @Test fun tabletKeepsTwoColumnsAtNormalAndLargeText() {
        for (f in listOf(1.0f, 1.15f, 1.3f)) assertEquals("600 @ $f", true, useTwoColumns(600, f))
        assertEquals(true, useTwoColumns(840, 2.0f))
    }

    @Test fun veryLargeTextOnSmallTabletUsesOneColumn() {
        assertEquals(false, useTwoColumns(600, 2.0f))
    }

    @Test fun phonesNeverSplit() {
        for (w in listOf(360, 411, 430)) for (f in listOf(1.0f, 1.3f, 2.0f)) assertEquals("$w @ $f", false, useTwoColumns(w, f))
    }
}
