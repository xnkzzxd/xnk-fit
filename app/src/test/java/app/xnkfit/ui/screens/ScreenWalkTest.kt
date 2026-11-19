package app.xnkfit.ui.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.xnkfit.MainActivity
import app.xnkfit.R
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NF-5 screen tests without screenshots (Design §12/§13): the real MainActivity on Robolectric with a saved profile,
 * walked through Hari ini, Latihan, Makan, Saya, Pengaturan and Data under five window configurations (phone 360 and
 * 430 dp, tablet 600 and 840 dp; light and dark; font scale 1.0, 1.3, 2.0). Each screen must draw without a crash,
 * show its key text and keep every clickable element at 48 dp or more (touch bounds). Text that carries meaning only
 * by colour is covered by the source scan in the logic tests (no colour-only state exists in the string files).
 * Real pixels, clipping and the rail's look are in docs/Test-Manual.md (S-1..S-6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenWalkBase(private val fontScale: Float) {
    @get:Rule val rule: ComposeTestRule = createEmptyComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seed() {
        RuntimeEnvironment.setFontScale(fontScale)
        val db = XnkDatabase.open(ctx, "test")
        runBlocking {
            db.profileDao().upsert(
                ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki-laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                    interest = "otot", nickname = "Uji", screeningDone = true),
            )
        }
    }

    @After fun end() {
        scenario?.close()
        // One database per process (XnkDatabase.open); drop it so the next test seeds a fresh file.
        val f = XnkDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (f.get(null) as? XnkDatabase)?.close()
        f.set(null, null)
    }

    private fun text(id: Int) = ctx.getString(id)

    private fun waitFor(label: String) = rule.waitUntil(20_000) { rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }

        /** Lazy lists compose only what is near the window, so scroll every list until the label is there. */
    private fun scrollTo(label: String) {
        rule.waitUntil(20_000) {
            if (rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()) return@waitUntil true
            val lists = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
            for (i in 0 until lists) runCatching { rule.onAllNodes(hasScrollAction())[i].performScrollToNode(hasText(label, substring = true)) }
            rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTouchTargets(screen: String) = rule.assertTouchTargets(ctx, screen)

    private fun openTab(label: Int) {
        rule.onAllNodesWithText(text(label)).onFirst().performClick()
        rule.waitForIdle()
    }

    @Test fun everyMainScreenDrawsWithKeyTextAndBigTouchTargets() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(text(R.string.nav_today))
        rule.waitForIdle()
        assertTouchTargets("Hari ini")
        openTab(R.string.nav_workout); waitFor(text(R.string.nav_workout)); assertTouchTargets("Latihan")
        openTab(R.string.nav_food); assertTouchTargets("Makan")
        openTab(R.string.nav_me)
        scrollTo(text(R.string.set_title)); assertTouchTargets("Saya")
        // Kartu saya and Pengaturan both say "Buka"; Pengaturan comes after Kartu in the list.
        rule.onAllNodesWithText(text(R.string.set_open)).onLast().performClick()
        try { scrollTo(text(R.string.set_data_title)) } catch (e: Throwable) { throw AssertionError(rule.onRoot().printToString(60).take(2500), e) }
        assertTouchTargets("Pengaturan")
        rule.onAllNodesWithText(text(R.string.set_data_title), substring = true).onFirst().performClick()
        rule.waitForIdle(); assertTouchTargets("Data")
    }
}

@Config(qualifiers = "w360dp-h740dp-notnight") class ScreenPhone360Light : ScreenWalkBase(1.0f)
@Config(qualifiers = "w360dp-h740dp-night") class ScreenPhone360DarkFont13 : ScreenWalkBase(1.3f)
@Config(qualifiers = "w430dp-h900dp-night") class ScreenPhone430DarkFont20 : ScreenWalkBase(2.0f)
@Config(qualifiers = "w600dp-h960dp-notnight") class ScreenTablet600LightFont13 : ScreenWalkBase(1.3f)
@Config(qualifiers = "w840dp-h1200dp-night") class ScreenTablet840Dark : ScreenWalkBase(1.0f)

private fun describe(n: SemanticsNode): String {
    val own = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }
    val desc = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString()
    val kids = n.children.mapNotNull { c -> c.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } }
    return listOfNotNull(own, desc, kids.takeIf { it.isNotEmpty() }?.joinToString("|")).joinToString(" / ")
}

/** Every clickable element in the window must have a touch area of 48 dp or more (PRD NF-5, Design §15). */
internal fun ComposeTestRule.assertTouchTargets(ctx: Context, screen: String) {
    val density = ctx.resources.displayMetrics.density
    val minPx = 48f * density - 1f
    val small = onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n: SemanticsNode ->
        // A list item scrolled half out of the window is clipped by the window, not by the app: skip it.
        val clipped = n.boundsInRoot.height < n.size.height - 1f || n.boundsInRoot.width < n.size.width - 1f
        val b = n.touchBoundsInRoot
        !clipped && (b.width < minPx || b.height < minPx)
    }
    assertTrue("$screen: ${small.size} clickable element(s) under 48 dp: " +
        small.joinToString { "\"${describe(it)}\" ${it.touchBoundsInRoot.width / density}x${it.touchBoundsInRoot.height / density}dp" }, small.isEmpty())
}
