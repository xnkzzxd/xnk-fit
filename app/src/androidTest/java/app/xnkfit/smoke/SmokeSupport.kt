package app.xnkfit.smoke

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import kotlinx.coroutines.runBlocking
import java.io.File

/** Shared helpers of the emulator smoke tests (B-18). No network, no Drive, no Health Connect, no camera. */
internal val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

internal fun str(id: Int): String = ctx.getString(id)

/** A clean install: the Room file, its journal files and every DataStore file are gone, the singleton is dropped. */
internal fun wipeApp() {
    val f = XnkDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
    (f.get(null) as? XnkDatabase)?.close()
    f.set(null, null)
    ctx.deleteDatabase(XnkDatabase.FILE_NAME)
    File(ctx.filesDir, "datastore").deleteRecursively()
    File(ctx.filesDir, "photos").deleteRecursively()
}

/** Sample data: the profile onboarding would have written. */
internal fun seedProfile() {
    val db = XnkDatabase.open(ctx, "smoke")
    runBlocking {
        db.profileDao().upsert(
            ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki_laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                interest = "otot", nickname = "Uji", screeningDone = true),
        )
        // Onboarding also writes the first weight; the generator's gate needs it.
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO measure (id, createdAt, updatedAt, type, value, unit, takenAt) VALUES ('seed-w', 1, 1, 'weight', 70.0, 'kg', 1)",
        )
    }
}

/** A timeout says what was on screen instead of only "condition not satisfied". */
internal fun ComposeTestRule.waitDescribed(what: String, ms: Long = 30_000, cond: () -> Boolean) {
    try { waitUntil(ms, cond) } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("Timeout waiting for $what. Screen:\n" + screenSummary(), e)
    }
}

internal fun ComposeTestRule.waitForText(label: String, ms: Long = 30_000) =
    waitDescribed("\"$label\"", ms) { onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }

/** Bottom bar or rail item: the clickable node whose text is exactly the label (Today also has cards with the same words). */
internal fun ComposeTestRule.clickNav(label: String) {
    waitDescribed("nav \"$label\"") { onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
    waitForIdle()
}

internal fun ComposeTestRule.waitGone(label: String, ms: Long = 30_000) =
    waitUntil(ms) { onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty() }

internal fun ComposeTestRule.has(label: String) = onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeTestRule.clickText(label: String) {
    waitForText(label)
    onAllNodesWithText(label, substring = true).onFirst().performClick()
    waitForIdle()
}

/** Lazy lists compose only what is near the window: scroll every list until the label exists. */
internal fun ComposeTestRule.scrollToText(label: String) = waitDescribed("scroll to \"$label\"") {
    if (has(label)) return@waitDescribed true
    val lists = onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
    for (i in 0 until lists) runCatching { onAllNodes(hasScrollAction())[i].performScrollToNode(hasText(label, substring = true)) }
    has(label)
}

internal fun ComposeTestRule.typeInto(label: String, value: String) {
    waitForText(label)
    onAllNodes(hasSetTextAction() and hasText(label, substring = true)).onFirst().performTextInput(value)
    waitForIdle()
}

internal fun ComposeTestRule.hasDesc(label: String) = onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeTestRule.clickDesc(label: String) {
    waitUntil(30_000) { hasDesc(label) }
    onAllNodesWithContentDescription(label, substring = true).onFirst().performClick()
    waitForIdle()
}

internal fun anyClickable(rule: ComposeTestRule) = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeTestRule.clickExact(label: String) {
    scrollToText(label)
    onAllNodesWithText(label).onFirst().performClick()
    waitForIdle()
}

/**
 * On a fresh profile Latihan first asks three questions; this answers them (home, no equipment, 3 days, 30 minutes,
 * beginner) and waits for the day list, where "Latihan kosong" lives.
 */
internal fun ComposeTestRule.ensureProgram() {
    // The questions come first (the buttons are below the fold, so look for the intro line); a program has the day list.
    waitDescribed("Latihan questions or day list") { has(str(app.xnkfit.R.string.lat_q_intro)) || has(str(app.xnkfit.R.string.lat_start)) }
    if (!has(str(app.xnkfit.R.string.lat_q_intro))) return
    clickExact(str(app.xnkfit.R.string.lat_setting_home_bw))
    clickExact("3")
    clickExact(ctx.getString(app.xnkfit.R.string.lat_minutes_value, 30))
    clickExact(str(app.xnkfit.R.string.lat_exp_new))
    clickExact(str(app.xnkfit.R.string.lat_create))
    // The list stays scrolled where the button was, so the day list may be off screen: give the result a moment.
    runCatching { waitUntil(10_000) { has(str(app.xnkfit.R.string.lat_blocked_screening)) || has(str(app.xnkfit.R.string.lat_blocked_stopped)) } }
    check(!has(str(app.xnkfit.R.string.lat_blocked_screening)) && !has(str(app.xnkfit.R.string.lat_blocked_stopped))) {
        "Program creation was blocked:\n" + screenSummary()
    }
    scrollToText(str(app.xnkfit.R.string.lat_empty_workout))
}

/** One line per node that has text: enough to see what is on screen, selected or disabled. */
internal fun ComposeTestRule.screenSummary(): String = onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().firstOrNull()?.let { root ->
    val out = StringBuilder()
    fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
        val t = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }
        if (t != null) {
            val sel = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Selected) == true
            val off = n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            out.append("- ").append(t).append(if (sel) " [selected]" else "").append(if (off) " [disabled]" else "").append('\n')
        }
        n.children.forEach(::walk)
    }
    walk(root)
    out.toString().take(3500)
} ?: "(no root)"
