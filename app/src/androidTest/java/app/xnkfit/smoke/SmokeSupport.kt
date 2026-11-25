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
            ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki-laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                interest = "otot", nickname = "Uji", screeningDone = true),
        )
    }
}

internal fun ComposeTestRule.waitForText(label: String, ms: Long = 30_000) =
    waitUntil(ms) { onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }

internal fun ComposeTestRule.waitGone(label: String, ms: Long = 30_000) =
    waitUntil(ms) { onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty() }

internal fun ComposeTestRule.has(label: String) = onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeTestRule.clickText(label: String) {
    waitForText(label)
    onAllNodesWithText(label, substring = true).onFirst().performClick()
    waitForIdle()
}

/** Lazy lists compose only what is near the window: scroll every list until the label exists. */
internal fun ComposeTestRule.scrollToText(label: String) = waitUntil(30_000) {
    if (has(label)) return@waitUntil true
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
