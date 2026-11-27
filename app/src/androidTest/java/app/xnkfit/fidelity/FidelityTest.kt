package app.xnkfit.fidelity

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.xnkfit.MainActivity
import app.xnkfit.R
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.prefs.SettingsGraph
import app.xnkfit.data.prefs.WelcomeTab
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Fidelity measurement of the app side (tools/compare, docs/Design-Visual.md "Gerbang kesetiaan"). Run only by
 * screenshots.yml, right after ScreenshotTest of the same variant (same database, so Hari ini shows the same sample data).
 * Finds every component tagged `fid:<name>` (a `Modifier.testTag` in ui/design), and writes its box in px, the background
 * and ink colour sampled from a capture, the corner inset of the second pixel row (the radius is computed by the report)
 * and the text size/weight when the node is text, to `files/fidelity/fid_<screen>_<variant>.json`. Report only: this test
 * never fails for a missing or different component.
 */
class FidelityTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val variant = InstrumentationRegistry.getArguments().getString("variant") ?: "hp_terang_1.0"
    private val outDir = File(ctx.filesDir, "fidelity").apply { mkdirs() }

    private val tags = listOf(
        "pita_atas", "kartu_hero_hitam", "px_sec", "judul_bagian", "judul_bagian_garis", "baris_tugas", "lingkaran_centang", "tugas_judul",
        "tugas_sub", "tombol_utama", "tombol_sekunder", "tombol_garis", "bilah_bawah", "tab", "tombol_plus_tengah", "stat_tile", "sel_input",
        "tombol_centang", "chip", "kartu_putih",
    )

    private fun dist(a: Int, b: Int) = abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))

    @Test fun hariIni() {
        val db = XnkDatabase.open(ctx, "screenshots")
        runBlocking {
            db.profileDao().upsert(
                ProfileEntity(createdAt = 1L, updatedAt = 1L, sex = "laki_laki", birthYear = 1990, heightCm = 170.0, goal = "turun",
                    interest = "otot", nickname = "Contoh", screeningDone = true),
            )
            WelcomeTab.entries.forEach { SettingsGraph.store(ctx).markCardSeen(it) }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        // Same route as ScreenshotTest.fresh(): open the Hari ini tab (a clickable node with the tab label), then wait for the task card.
        val tabLabel = ctx.getString(R.string.nav_today)
        rule.waitUntil(30_000) { rule.onAllNodes(hasText(tabLabel) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText(tabLabel) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
        val label = ctx.getString(R.string.td_tasks_title)
        try {
            rule.waitUntil(60_000) { rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            runCatching { File(outDir, "_tree_$variant.txt").writeText(rule.onRoot().printToString(40).take(6000)) }
            throw e
        }
        rule.waitForIdle()
        val bmp: Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val density = ctx.resources.displayMetrics.density
        val items = StringBuilder()
        for (tag in tags) {
            val nodes = rule.onAllNodesWithTag("fid:$tag", useUnmergedTree = true).fetchSemanticsNodes()
            nodes.sortedBy { it.boundsInRoot.top }.take(3).forEachIndexed { i, n ->
                val b = n.boundsInRoot
                val x = b.left.toInt(); val y = b.top.toInt(); val w = b.width.toInt(); val h = b.height.toInt()
                fun px(xx: Int, yy: Int) = bmp.getPixel(xx.coerceIn(0, bmp.width - 1), yy.coerceIn(0, bmp.height - 1))
                val layouts = ArrayList<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val style = layouts.firstOrNull()?.layoutInput?.style
                val isText = style != null
                val bg = if (isText) px(x + 1, y + 1) else px(x + w / 2, y + minOf((4 * density).toInt(), h / 2)) // 4 dp in: below a 1 dp border, still inside any fill
                // ink: the pixel in the box farthest from bg (text colour, or the check/ring of an icon)
                var ink = bg
                if (w > 0 && h > 0) for (yy in y until minOf(y + h, bmp.height) step 1) for (xx in x until minOf(x + w, bmp.width) step 2) {
                    val p = px(xx, yy); if (dist(p, bg) > dist(ink, bg)) ink = p
                }
                // corner inset on the second pixel row: how many px from the left edge before the colour is the bg again
                var inset = -1
                if (!isText && w > 8 && h > 8) { inset = 0; while (inset < w / 2 && dist(px(x + inset, y + 1), bg) > 24) inset++ }
                fun hex(c: Int) = "[${c shr 16 and 255},${c shr 8 and 255},${c and 255}]"
                if (items.isNotEmpty()) items.append(",\n")
                items.append("""{"tag":"$tag","i":$i,"x":$x,"y":$y,"w":$w,"h":$h,"bg":${hex(bg)},"ink":${hex(ink)},"inset":$inset""")
                if (style != null) items.append(""","fontSp":${style.fontSize.value},"fontWeight":${style.fontWeight?.weight ?: 400}""")
                items.append("}")
            }
        }
        val json = """{"screen":"hari-ini","variant":"$variant","density":$density,"widthPx":${bmp.width},"heightPx":${bmp.height},"items":[
$items
]}"""
        File(outDir, "fid_hari-ini_$variant.json").writeText(json)
        scenario.close()
    }
}
