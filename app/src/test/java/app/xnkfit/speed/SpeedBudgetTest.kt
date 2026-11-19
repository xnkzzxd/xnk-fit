package app.xnkfit.speed

import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.content.ContentTestSupport
import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.WorkoutEntity
import app.xnkfit.data.db.WorkoutSetEntity
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.domain.RepoFiles
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.system.measureNanoTime

/**
 * The speed budgets of Design §10 that can be measured without a phone (B-19). CI runs this in `testDebugUnitTest`,
 * so a slower path fails the build (NF-1, PRD G6).
 *
 * Numbers here come from the JVM: Robolectric's SQLite on the CI box, not ART on the Redmi 10 or the API 26 emulator.
 * They prove the path is the right shape (one small transaction, an FTS4 index hit, no scan); they are not the
 * device numbers. Those come from the Macrobenchmark module (`benchmark/`) and are written to docs/Speed.md.
 * The tolerance is loose on purpose: the gate is the budget itself on the median, and the slow tail may reach
 * [TAIL_FACTOR] times the budget, because a shared CI runner pauses at random (GC, other jobs).
 */
@RunWith(RobolectricTestRunner::class)
class SpeedBudgetTest {

    /** Design §10 "Set tick saved to disk 50 ms": the real DAO transaction against a database file. */
    @Test fun setTickSavedToDiskWithinBudget() {
        val name = "speed-${System.nanoTime()}.db"
        val db = DbTestSupport.onDisk(name)
        try {
            runBlocking {
                val dao = db.workoutDao()
                dao.startWorkout(WorkoutEntity(id = "w", createdAt = T0, updatedAt = T0, programDayId = null, mode = "bebas", startedAt = T0))
                fun set(i: Int, done: Boolean) = WorkoutSetEntity(
                    id = "s$i", createdAt = T0, updatedAt = T0 + i, workoutId = "w", exerciseId = "SQUAT_GOB", position = i,
                    weightKg = 20.0, reps = 8, done = done, doneAt = if (done) T0 + i else null,
                )
                repeat(WARM_UP) { dao.saveSet(set(1_000 + it, false), T0 + it) }
                // A tick is an update of a row that already exists (the plan wrote it at start), so half update, half insert.
                repeat(RUNS / 2) { dao.saveSet(set(it, false), T0 + it) }
                val ms = (0 until RUNS).map { i ->
                    measureNanoTime { runBlocking { dao.saveSet(set(i, true), T0 + i, T0 + 90_000) } } / 1e6
                }
                check("set tick saved", ms, SET_TICK_MS)
            }
        } finally {
            db.close()
            DbTestSupport.context().deleteDatabase(name)
        }
    }

    /**
     * Design §10 "Food search per keystroke 100 ms on the full TKPI table": the whole bundled content is loaded
     * (TKPI, estimates, regional, packaged), then each keystroke of typed words runs the repository search
     * (FTS4 MATCH plus the sort in Kotlin), as the search box does.
     */
    @Test fun foodSearchPerKeystrokeOnTheFullTableWithinBudget() = runBlocking {
        val db = ContentTestSupport.db()
        try {
            BundledContentLoader(ContentStore(db) { T0 }, ContentTestSupport.repoSource) { T0 }.loadIfNeeded()
            val bySource = mutableMapOf<String, Int>()
            db.openHelper.readableDatabase.query("SELECT source, COUNT(*) FROM food GROUP BY source").use { c ->
                while (c.moveToNext()) bySource[c.getString(0)] = c.getInt(1)
            }
            assertEquals("every TKPI row is searched", rows("foods-tkpi.json"), bySource["tkpi"])
            // Estimates and regional rows share the source `warung-est`.
            assertTrue("every estimate row is searched", (bySource["warung-est"] ?: 0) >= rows("foods-estimates.json"))
            report("food table: $bySource")
            val repo = FoodRepository(db, now = { T0 }, newId = { "x" })
            repeat(WARM_UP) { repo.search("ayam") }
            val words = listOf("ayam goreng", "nasi putih", "tempe", "tahu", "sayur asem", "ikan", "telur", "kacang", "mie", "pisang")
            val ms = words.flatMap { w -> (1..w.length).map { w.take(it) } }
                .map { q -> measureNanoTime { repo.search(q) } / 1e6 }
            check("food search keystroke", ms, FOOD_SEARCH_MS)
        } finally {
            db.close()
        }
    }

    private fun rows(file: String) = JSONObject(RepoFiles.file("content/$file").readText()).getJSONArray("foods").length()

    private fun check(what: String, ms: List<Double>, budget: Double) {
        val s = ms.sorted()
        val median = s[s.size / 2]
        val p95 = s[(s.size * 95) / 100]
        val line = "%s: n=%d median=%.2f ms p95=%.2f ms max=%.2f ms budget=%.0f ms (JVM, not a device number)"
            .format(what, s.size, median, p95, s.last(), budget)
        println(line)
        report(line)
        assertTrue("$what median $median ms over the $budget ms budget", median <= budget)
        assertTrue("$what p95 $p95 ms over ${TAIL_FACTOR}x the $budget ms budget", p95 <= budget * TAIL_FACTOR)
    }

    /** One line per measure in app/build/speed/jvm-budgets.txt; CI uploads it with the reports. */
    private fun report(line: String) {
        val dir = File("build/speed").apply { mkdirs() }
        File(dir, "jvm-budgets.txt").appendText(line + "\n")
    }

    companion object {
        @JvmStatic @BeforeClass fun freshReport() { File("build/speed/jvm-budgets.txt").delete() }

        /** Design §10 budgets. Change them there first. */
        const val SET_TICK_MS = 50.0
        const val FOOD_SEARCH_MS = 100.0
        const val TAIL_FACTOR = 2.0
        const val WARM_UP = 20
        const val RUNS = 100
        private const val T0 = 1_700_000_000_000L
    }
}
