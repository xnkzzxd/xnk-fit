package app.xnkfit.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasClickAction
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.xnkfit.MainActivity
import app.xnkfit.R
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
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

    private fun click(label: String) {
        waitText(label)
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.waitForIdle()
    }

    /** Bottom bar and rail items are clickable and carry the label; a screen heading with the same text is not clickable. */
    private fun tab(label: String) {
        rule.waitUntil(20_000) { rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun back() {
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
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
        try { body() } catch (e: Throwable) { missing += "$screen: ${e.javaClass.simpleName} ${e.message?.take(160)}" }
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
                ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki-laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                    interest = "otot", nickname = "Contoh", screeningDone = true),
            )
            val progress = ProgressRepository(db)
            progress.addMeasure("shot-w1", "weight", 75.0, "kg") // text-ok: stored codes
            progress.addMeasure("shot-c1", "waist", 88.0, "cm") // text-ok: stored codes
        }
        launch()
        step("hari-ini") { waitText(str(R.string.nav_today)); shot("hari-ini") }
        step("latihan") { tab(str(R.string.nav_workout)); shot("latihan") }
        step("latihan-program") {
            scrollTo(str(R.string.lat_create)); click(str(R.string.lat_create))
            Thread.sleep(2_000); shot("latihan-program")
            runCatching { tab(str(R.string.nav_workout)) }
        }
        step("logger") {
            scrollTo(str(R.string.lat_empty_workout))
            click(str(R.string.lat_empty_workout))
            click(str(R.string.lg_mode_bebas))
            waitText(str(R.string.lg_finish))
            shot("logger")
            back()
            runCatching { click(str(R.string.lg_resume_finish)) }
        }
        step("makan") { tab(str(R.string.nav_food)); shot("makan") }
        step("planner") {
            click(str(R.string.mk_plan_week))
            Thread.sleep(2_500)
            shot("planner")
            back()
        }
        step("saya") { tab(str(R.string.nav_me)); shot("saya") }
        step("kartu") {
            scrollTo(str(R.string.me_cards_title))
            rule.onAllNodesWithText(str(R.string.set_open)).onFirst().performClick()
            rule.waitForIdle()
            Thread.sleep(1_500)
            shot("kartu")
            back()
        }
        step("pengaturan") {
            scrollTo(str(R.string.set_title))
            rule.onAllNodesWithText(str(R.string.set_open)).onLast().performClick()
            rule.waitForIdle()
            scrollTo(str(R.string.set_data_title))
            shot("pengaturan")
        }
        step("data") { click(str(R.string.set_data_title)); shot("data"); back() }
        step("bantuan") { scrollTo(str(R.string.set_help_title)); click(str(R.string.set_help_title)); shot("bantuan"); back() }
        step("privasi") { scrollTo(str(R.string.set_privacy_title)); click(str(R.string.set_privacy_title)); shot("privasi"); back() }
        scenario?.close()

        File(outDir, "_missing_$variant.txt").writeText(missing.joinToString("\n"))
        assertTrue("screens not captured: $missing", missing.isEmpty())
    }
}
