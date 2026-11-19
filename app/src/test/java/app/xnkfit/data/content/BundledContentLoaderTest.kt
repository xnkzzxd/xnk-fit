package app.xnkfit.data.content

import app.xnkfit.data.db.FoodSearch
import app.xnkfit.data.content.ContentTestSupport as T
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BundledContentLoaderTest {
    private lateinit var db: app.xnkfit.data.db.XnkDatabase
    private var clock = 1_000L
    private fun loader(src: ContentSource) = BundledContentLoader(ContentStore(db) { clock }, src) { clock }

    @Before fun open() { db = T.db() }
    @After fun close() { db.close() }

    @Test fun realContentLoadsInOneGo() = runBlocking {
        val r = loader(T.repoSource).loadIfNeeded() as BundledContentLoader.Result.Loaded
        val dao = db.contentDao()
        assertEquals(1, r.contentVersion)
        assertEquals(149, dao.exerciseCount())
        // 1143 TKPI + 66 estimates + 108 regional + 1 packaged; the ml-only packaged row has no per-100 g basis and is skipped.
        assertEquals(1143 + 66 + 108 + 1, dao.foodCount())
        assertTrue(r.skipped.any { it.startsWith("PK_ULTRA") })
        val profiles = listOf("jawa", "sunda", "padang", "umum", "beli", "kos")
        val slots = listOf("pagi", "siang", "malam", "camilan")
        assertEquals(72, profiles.sumOf { p -> slots.sumOf { sl -> dao.mealPackages(p, sl).size } })
        assertNotNull(dao.doc("badges"))
        assertNotNull(dao.doc("meal-packages-meta"))
        // FTS4 works right after the load, with no extra step.
        val hits = db.foodDao().search(FoodSearch.toMatch("beras giling")!!, 10)
        assertTrue(hits.any { it.id == "TKPI_AR001" })
        val meta = dao.meta()!!
        assertEquals("bundled", meta.source)
        assertEquals(7, ContentStore.parseHashes(meta.filesJson).size)
    }

    @Test fun loadingTwiceChangesNothing() = runBlocking {
        val files = T.miniFiles("a")
        assertTrue(loader(T.source(files, 1)).loadIfNeeded() is BundledContentLoader.Result.Loaded)
        clock = 2_000
        assertEquals(BundledContentLoader.Result.AlreadyCurrent, loader(T.source(files, 1)).loadIfNeeded())
        assertEquals(1_000L, db.contentDao().meta()!!.updatedAt)
        assertEquals(1, db.contentDao().exerciseCount())
        assertEquals(3, db.contentDao().foodCount())
    }

    @Test fun olderAppDoesNotOverwriteNewerContent() = runBlocking {
        loader(T.source(T.miniFiles("new"), 5)).loadIfNeeded()
        assertEquals(BundledContentLoader.Result.AlreadyCurrent, loader(T.source(T.miniFiles("old"), 3)).loadIfNeeded())
        assertEquals("Squat new", db.programDao().exercisesByPattern("SQUAT").single().nameEn)
    }

    @Test fun newVersionReplacesOldAndKeepsUserFoods() = runBlocking {
        loader(T.source(T.miniFiles("old"), 1)).loadIfNeeded()
        db.foodDao().upsertFoods(listOf(app.xnkfit.data.db.DbTestSupport.food("OWN_1", "Sambal ibu").copy(source = "own")))
        loader(T.source(T.miniFiles("new"), 2)).loadIfNeeded()
        val ids = db.foodDao().search("nasi*", 50).map { it.id }.toSet()
        assertEquals(setOf("TKPI_new", "W_new", "REG-new"), ids)
        assertEquals("Sambal ibu", db.foodDao().food("OWN_1")!!.nameId)
        assertEquals(2, db.contentDao().meta()!!.contentVersion)
        assertEquals(listOf("MP_new"), db.contentDao().mealPackages("jawa", "pagi").map { it.id })
    }

    @Test fun brokenBundledFileWritesNothing() = runBlocking {
        val files = T.miniFiles("a").toMutableMap()
        files["foods-regional.json"] = "{ not json".toByteArray()
        try { loader(T.source(files, 1)).loadIfNeeded(); fail() } catch (e: ContentException) { /* expected */ }
        assertEquals(0, db.contentDao().exerciseCount())
        assertEquals(null, db.contentDao().meta())
    }

    @Test fun failedWriteRollsBackEverything() = runBlocking {
        loader(T.source(T.miniFiles("old"), 1)).loadIfNeeded()
        // Same id twice in one table passes the parser per file but not the database: the transaction must undo it all.
        val dup = T.miniFiles("new").toMutableMap()
        val badFood = org.json.JSONObject(String(dup["foods-tkpi.json"]!!)).getJSONArray("foods")
        badFood.put(badFood.getJSONObject(0))
        // parser rejects duplicates first; both layers keep the old data
        dup["foods-tkpi.json"] = org.json.JSONObject().put("foods", badFood).toString().toByteArray()
        try { loader(T.source(dup, 2)).loadIfNeeded(); fail() } catch (e: ContentException) { /* expected */ }
        assertEquals(1, db.contentDao().meta()!!.contentVersion)
        assertEquals("TKPI_old", db.foodDao().search("nasi*", 50).first { it.source == "tkpi" }.id)
    }

    @Test fun inactiveExerciseIsNotLoaded() = runBlocking {
        val files = T.miniFiles("a").toMutableMap()
        val ex = org.json.JSONObject(String(files["exercises.json"]!!))
        ex.getJSONArray("exercises").put(org.json.JSONObject(ex.getJSONArray("exercises").getJSONObject(0).toString()).put("id", "OLD_ONE").put("active", false))
        files["exercises.json"] = ex.toString().toByteArray()
        val r = loader(T.source(files, 1)).loadIfNeeded() as BundledContentLoader.Result.Loaded
        assertEquals(1, db.contentDao().exerciseCount())
        assertTrue(r.skipped.single().startsWith("OLD_ONE"))
    }
}
