package app.xnkfit.ui.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.xnkfit.data.share.QrCode
import app.xnkfit.data.share.SHARE_URL
import app.xnkfit.domain.share.CardFacts
import app.xnkfit.domain.share.CardKind
import app.xnkfit.domain.share.CardSize
import app.xnkfit.domain.share.CardStyle
import app.xnkfit.domain.share.ConsistencyFacts
import app.xnkfit.domain.share.LiftFact
import app.xnkfit.domain.share.ProgressFacts
import app.xnkfit.domain.share.ShareOptions
import app.xnkfit.domain.share.WorkoutFacts
import app.xnkfit.domain.share.buildCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Canvas drawing under Robolectric's native graphics: real pixels, but no device fonts or GPU, so we check sizes and colours, not looks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardRendererTest {
    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val facts = CardFacts(
        workout = WorkoutFacts(LocalDate.of(2026, 11, 19), "Full Body B", 52, 2, LiftFact("Goblet Squat", 16.0, 12)),
        progress = ProgressFacts(-2.0, -1.5, 30),
        consistency = ConsistencyFacts(5, 4, 12),
    )

    private fun draw(o: ShareOptions, width: Int = 360): Bitmap {
        val spec = buildCard(facts, o, false, null)!!
        return CardRenderer.render(spec, cardTexts(res, spec), if (spec.brand) QrCode.encode(SHARE_URL) else null, null, width)
    }

    @Test
    fun sizeFollowsTheCardSize() {
        val story = draw(ShareOptions(size = CardSize.STORY))
        assertEquals(360 to 640, story.width to story.height)
        val square = draw(ShareOptions(size = CardSize.SQUARE))
        assertEquals(360 to 360, square.width to square.height)
        val full = draw(ShareOptions(), width = CardSize.STORY.widthPx)
        assertEquals(1080 to 1920, full.width to full.height)
    }

    @Test
    fun styleSetsTheBackground() {
        assertEquals(0xFF0B0B0B.toInt(), draw(ShareOptions(style = CardStyle.DARK)).getPixel(180, 320))
        assertEquals(Color.WHITE, draw(ShareOptions(style = CardStyle.LIGHT)).getPixel(180, 320))
        assertEquals(0xFF1D4ED8.toInt(), draw(ShareOptions(style = CardStyle.ACCENT)).getPixel(180, 320))
    }

    @Test
    fun qrPlateIsDrawnOnlyWhenTheBrandSwitchIsOn() {
        // Bottom-right corner area: white plate with QR modules when on, plain background when off.
        fun corner(b: Bitmap): Set<Int> = (0 until 40).flatMap { i -> (0 until 40).map { j -> b.getPixel(b.width - 70 + i, b.height - 70 + j) } }.toSet()
        val on = corner(draw(ShareOptions(brand = true)))
        val off = corner(draw(ShareOptions(brand = false)))
        assertTrue(on.contains(Color.BLACK) && on.contains(Color.WHITE))
        assertEquals(setOf(0xFF0B0B0B.toInt()), off)
    }

    @Test
    fun textIsDrawnOnTheCard() {
        // Some pixel in the headline area differs from the background.
        val b = draw(ShareOptions(kind = CardKind.CONSISTENCY, style = CardStyle.DARK))
        val colours = (0 until b.width step 3).flatMap { x -> (60 until 240 step 3).map { y -> b.getPixel(x, y) } }.toSet()
        assertTrue(colours.size > 1)
        assertNotEquals(0, colours.count { it != 0xFF0B0B0B.toInt() })
    }

    @Test
    fun textsComeFromResourcesAndHideKg() {
        val progress = buildCard(facts, ShareOptions(kind = CardKind.PROGRESS), false, null)!!
        val t = cardTexts(res, progress)
        assertEquals("Pinggang −2 cm", t.headline)
        assertEquals("Tanpa kg", t.noKgNote)
        assertTrue(t.fields.none { it.second.contains("kg") })
        val workout = cardTexts(res, buildCard(facts, ShareOptions(kind = CardKind.WORKOUT), false, null)!!)
        assertEquals("Goblet Squat × 12", workout.lift)
        val withKg = cardTexts(res, buildCard(facts, ShareOptions(kind = CardKind.WORKOUT, hideKg = false), false, null)!!)
        assertEquals("Goblet Squat 16 kg × 12", withKg.lift)
        assertEquals("19 Nov", workout.date)
    }
}
