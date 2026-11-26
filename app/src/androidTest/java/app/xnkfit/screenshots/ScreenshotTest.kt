package app.xnkfit.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.xnkfit.MainActivity
import app.xnkfit.R
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.prefs.SettingsGraph
import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.data.progress.ProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Screenshot run for the owner to judge the look without a phone (Design §12, B-17/B-18 follow-up).
 * One run = one display variant: the workflow sets the window size, density, night mode and font scale with
 * `adb shell` and passes the variant name as the instrumentation argument `variant`
 * (`<hp|tablet>_<terang|gelap>_<1.0|1.3>`). The real MainActivity is driven by clicks over sample data made here
 * (a made-up profile and weights, no personal data). PNGs go to the app's own `files/screenshots/`, pulled with `run-as`.
 * A screen that cannot be reached is written to `_missing.txt` and fails the run at the end, after all others were tried.
 */
class ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val variant = InstrumentationRegistry.getArguments().getString("variant") ?: "hp_terang_1.0"
    private val outDir = File(ctx.filesDir, "screenshots").apply { mkdirs() }
    private val missing = mutableListOf<String>()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun str(id: Int) = ctx.getString(id)

    private fun waitText(label: String, ms: Long = 30_000) =
        rule.waitUntil(ms) { rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** A loading placeholder must be gone before a shot: the shot waits for the screen's own text, never a fixed pause. */
    private fun waitGone(label: String, ms: Long = 60_000) =
        rule.waitUntil(ms) { rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty() }

    private fun click(label: String) {
        waitText(label)
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.waitForIdle()
    }

    /** Scrolls to a clickable element whose whole text is [label] (choice cards, number boxes) and clicks it. */
    private fun clickExact(label: String) {
        scrollTo(label)
        rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    /** Fresh activity (overlay state is not kept), then the given tab. */
    private fun fresh(tabLabel: Int) { launch(); tab(str(tabLabel)) }

    /** Bottom bar and rail items are clickable and carry the label; a screen heading with the same text is not clickable. */
    private fun tab(label: String) {
        rule.waitUntil(20_000) { rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    /** The on-screen "Kembali" when there is one (the system key did not leave Kartu saya on the emulator), else the key. */
    private fun back() {
        val label = str(R.string.lat_back)
        val nodes = rule.onAllNodes(hasText(label) and hasClickAction())
        if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes.onFirst().performClick()
        else inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    /** Lazy lists compose only what is near the window, so scroll every list until the label is there. */
    private fun scrollTo(label: String) = rule.waitUntil(20_000) {
        if (rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()) return@waitUntil true
        val lists = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until lists) runCatching { rule.onAllNodes(hasScrollAction())[i].performScrollToNode(hasText(label, substring = true)) }
        rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shot(screen: String) {
        rule.waitForIdle()
        val bmp: Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "${screen}_$variant.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun step(screen: String, body: () -> Unit) {
        try { body() } catch (e: Throwable) {
            missing += "$screen: ${e.javaClass.simpleName} ${e.message?.take(160)}"
            // What was on screen, to find out why the label was never there.
            runCatching { File(outDir, "_tree_${screen}_$variant.txt").writeText(rule.onRoot().printToString(40).take(6000)) }
            runCatching { shot("fail-$screen") }
        }
    }

    /** A screen that is extra to the required set: a miss is written to `_note_*` and does not fail the run. */
    private fun extra(screen: String, body: () -> Unit) {
        try { body() } catch (e: Throwable) {
            runCatching { File(outDir, "_note_${screen}_$variant.txt").writeText("${e.javaClass.simpleName} ${e.message?.take(200)}") }
        }
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun typeInto(label: String, text: String) {
        rule.onAllNodes(hasSetTextAction() and hasText(label)).onFirst().performTextInput(text)
    }

    @Test fun everyMainScreen() {
        outDir.listFiles()?.filter { it.name.endsWith("_$variant.png") }?.forEach { it.delete() }
        val db = XnkDatabase.open(ctx, "screenshots")

        // 1. First run: onboarding and the screening form, on an empty database.
        launch()
        step("onboarding") {
            waitText(str(R.string.ob_start))
            shot("onboarding")
            click(str(R.string.ob_start))
            click(str(R.string.ob_goal_turun))
            click(str(R.string.ob_next))
            click(str(R.string.ob_sex_male))
            typeInto(str(R.string.ob_birth_year), "1990")
            typeInto(str(R.string.ob_height), "170")
            typeInto(str(R.string.ob_weight), "75")
            click(str(R.string.ob_next))
            click(str(R.string.ob_interest_both))
            click(str(R.string.ob_next))
            click(str(R.string.ob_next))
            waitText(str(R.string.ob_health_yes))
        }
        step("skrining") {
            click(str(R.string.ob_health_yes))
            waitText(str(R.string.sc_title))
            shot("skrining")
        }
        scenario?.close()

        // 2. Sample data (made up), then the app with a saved profile.
        runBlocking {
            db.profileDao().upsert(
                ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki_laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                    interest = "otot", nickname = "Contoh", screeningDone = true),
            )
            val progress = ProgressRepository(db)
            progress.addMeasure("shot-w1", "weight", 75.0, "kg") // text-ok: stored codes
            progress.addMeasure("shot-c1", "waist", 88.0, "cm") // text-ok: stored codes
        }
        launch()
        // First visit to each tab shows the welcome card ("Mengerti"): one series with it, then all marked seen for the rest.
        step("sambutan") {
            waitText(str(R.string.td_tasks_title))
            shot("sambutan-hari-ini")
            tab(str(R.string.nav_workout)); shot("sambutan-latihan")
            tab(str(R.string.nav_food)); shot("sambutan-makan")
            tab(str(R.string.nav_me)); waitGone(str(R.string.me_loading)); shot("sambutan-saya")
        }
        runBlocking { WelcomeTab.entries.forEach { SettingsGraph.store(ctx).markCardSeen(it) } }
        launch()
        step("latihan") { tab(str(R.string.nav_workout)); shot("latihan") }
        step("latihan-program") {
            clickExact(str(R.string.lat_setting_home_bw))
            clickExact("3")
            clickExact(str(R.string.lat_minutes_value).format(30))
            clickExact(str(R.string.lat_exp_new))
            clickExact(str(R.string.lat_create))
            scrollTo(str(R.string.lat_empty_workout))
            shot("latihan-program")
        }
        step("logger") {
            scrollTo(str(R.string.lat_empty_workout))
            click(str(R.string.lat_empty_workout))
            waitText(str(R.string.lg_how))
            shot("logger-mode")
            // The choice card's text is "Bebas, Tabel semua set" (title and hint merged).
            rule.onAllNodes(hasText(str(R.string.lg_mode_bebas), substring = true) and hasClickAction()).onFirst().performClick()
            rule.waitForIdle()
            Thread.sleep(1_500)
            // After the choice the table of the empty workout (or a start button) shows; take what is there.
            shot("logger")
        }
        extra("logger-berjalan") {
            // A running workout: one exercise, one set typed and ticked, so the SET/KG/REP table and the rest timer show.
            // The start pane (mode chosen) still shows: "Mulai latihan" opens the running logger.
            click(str(R.string.lg_start))
            waitText(str(R.string.lg_finish))
            click("+ " + str(R.string.lg_add_exercise))
            waitText(str(R.string.lg_add_title))
            typeInto(str(R.string.lg_search), "Push-Up (Bodyweight)")
            rule.waitForIdle()
            rule.onAllNodes(hasText("Push-Up (Bodyweight)") and hasClickAction()).onFirst().performClick()
            rule.waitForIdle()
            waitText("+ " + str(R.string.lg_add_set))
            shot("logger-berjalan-gerakan")
            // Best effort from here: the set row is opened, reps raised once, ticked. A miss is noted, the shot is taken anyway.
            runCatching {
                rule.onAllNodes(hasText("\u2014", substring = true) and hasClickAction()).onFirst().performClick()
                rule.waitForIdle()
                rule.onAllNodes(hasContentDescription(str(R.string.lg_plus))).onFirst().performClick()
                rule.waitForIdle()
                rule.onAllNodes(hasContentDescription(str(R.string.lg_tick))).onFirst().performClick()
                rule.waitForIdle()
                waitText(str(R.string.lg_rest))
            }.onFailure { File(outDir, "_note_logger-berjalan_$variant.txt").writeText("tick not done: ${it.message?.take(200)}") }
            shot("logger-berjalan")
        }
        step("hari-ini") {
            fresh(R.string.nav_today)
            // Loaded: the task card is there only after the first read, so the shot never shows the bare header.
            waitText(str(R.string.td_tasks_title))
            shot("hari-ini")
        }
        step("makan") { fresh(R.string.nav_food); shot("makan") }
        step("planner") {
            click(str(R.string.mk_plan_week))
            Thread.sleep(2_500)
            shot("planner")
            back()
        }
        step("saya") { tab(str(R.string.nav_me)); waitGone(str(R.string.me_loading)); waitText("Contoh"); shot("saya") }
        step("kartu") {
            fresh(R.string.nav_me)
            scrollTo(str(R.string.me_cards_title))
            rule.onAllNodesWithText(str(R.string.set_open)).onFirst().performClick()
            rule.waitForIdle()
            Thread.sleep(1_500)
            shot("kartu")
        }
        fun settings() {
            fresh(R.string.nav_me)
            scrollTo(str(R.string.set_title))
            rule.onAllNodesWithText(str(R.string.set_open)).onLast().performClick()
            rule.waitForIdle()
            scrollTo(str(R.string.set_data_title))
        }
        step("pengaturan") { settings(); shot("pengaturan") }
        // The cards in Pengaturan are not clickable themselves: the "Buka" button is. Data, Bantuan and Privasi are the last three.
        fun openFromSettings(fromEnd: Int, marker: Int) {
            settings()
            val buttons = rule.onAllNodes(hasText(str(R.string.set_open)) and hasClickAction())
            val n = buttons.fetchSemanticsNodes().size
            check(n >= 3) { "Buka buttons in Pengaturan: $n" }
            buttons[n - 1 - fromEnd].performScrollTo().performClick() // off-screen in the scrolling column until scrolled to
            rule.waitForIdle()
            // Fail (not save a wrong screen) when this screen's own text never shows.
            waitText(str(marker))
        }
        step("data") { openFromSettings(2, R.string.data_file_title); shot("data") }
        step("bantuan") { openFromSettings(1, R.string.help_disclaimer_title); shot("bantuan") }
        step("privasi") { openFromSettings(0, R.string.privacy_review_note); shot("privasi") }
        scenario?.close()

        File(outDir, "_missing_$variant.txt").writeText(missing.joinToString("\n"))
        assertTrue("screens not captured: $missing", missing.isEmpty())
    }
}
