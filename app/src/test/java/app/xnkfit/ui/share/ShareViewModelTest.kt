package app.xnkfit.ui.share

import app.xnkfit.domain.share.CardFacts
import app.xnkfit.domain.share.CardKind
import app.xnkfit.domain.share.CardStyle
import app.xnkfit.domain.share.ConsistencyFacts
import app.xnkfit.domain.share.FoodFacts
import app.xnkfit.domain.share.ProgressFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ShareViewModelTest {
    private fun advanceUntilIdle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    private val facts = CardFacts(
        progress = ProgressFacts(-2.0, -1.0, 10),
        consistency = ConsistencyFacts(3, 2, 4),
        food = FoodFacts(5),
    )

    private fun vm(f: CardFacts = facts, hides: Boolean = false, photo: String? = "/p.jpg") = ShareViewModel({ f }, { photo }, { hides })

    @Test
    fun galleryOffersOnlyCardsWithData() {
        val m = vm()
        m.refresh(); advanceUntilIdle()
        assertEquals(listOf(CardKind.PROGRESS, CardKind.CONSISTENCY, CardKind.FOOD), m.state.value.available)
        assertEquals(3, m.state.value.galleryCards().size)
    }

    @Test
    fun emptyPhoneShowsNoCardsAndLoadFailureDoesNotCrash() {
        val m = vm(CardFacts())
        m.refresh(); advanceUntilIdle()
        assertTrue(m.state.value.loaded)
        assertTrue(m.state.value.galleryCards().isEmpty())
        val broken = ShareViewModel({ error("db") }, { null }, { false })
        broken.refresh(); advanceUntilIdle()
        assertTrue(broken.state.value.loaded)
    }

    @Test
    fun defaultsAreNoKgAndBrandOnAndTheSwitchesEditTheCard() {
        val m = vm()
        m.refresh(); advanceUntilIdle()
        m.selectKind(CardKind.PROGRESS)
        val s = m.state.value
        assertTrue(s.options.hideKg && s.options.brand && s.options.blurFaces)
        assertFalse(s.spec!!.fields.any { it.id.name == "WEIGHT_DELTA" })
        m.setHideKg(false); m.setStyle(CardStyle.LIGHT)
        assertTrue(m.state.value.spec!!.fields.any { it.id.name == "WEIGHT_DELTA" })
        assertEquals(CardStyle.LIGHT, m.state.value.spec!!.style)
        m.setWithPhoto(true)
        assertEquals("/p.jpg", m.state.value.spec!!.photoPath)
    }

    @Test
    fun sayaHidingWeightKeepsKgOffEvenWhenTheSwitchIsOff() {
        val m = vm(hides = true)
        m.refresh(); advanceUntilIdle()
        m.selectKind(CardKind.PROGRESS); m.setHideKg(false)
        assertTrue(m.state.value.spec!!.noKg)
        assertFalse(m.state.value.spec!!.fields.any { it.id.name == "WEIGHT_DELTA" })
        assertNull(vm().state.value.spec)
    }
}
