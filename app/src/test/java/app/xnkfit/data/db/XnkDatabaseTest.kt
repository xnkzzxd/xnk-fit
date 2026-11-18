package app.xnkfit.data.db

import android.database.sqlite.SQLiteConstraintException
import app.xnkfit.data.db.DbTestSupport.T0
import app.xnkfit.data.db.DbTestSupport.food
import app.xnkfit.data.db.DbTestSupport.set
import app.xnkfit.data.db.DbTestSupport.workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
class XnkDatabaseTest {
    private lateinit var db: XnkDatabase

    @Before fun open() { db = DbTestSupport.inMemory() }
    @After fun close() { db.close() }

    // --- basic insert / query -------------------------------------------------

    @Test fun seedRowWrittenOnCreate() = runBlocking {
        val meta = db.metaDao().schemaMeta()
        assertNotNull(meta)
        assertEquals(1, meta!!.version)
        assertEquals("test", meta.appVersionCreated)
    }

    @Test fun profileRoundTrip() = runBlocking {
        val p = ProfileEntity(createdAt = T0, updatedAt = T0, sex = "f", birthYear = 2000, heightCm = 160.0,
            goal = "lose", interest = "home", nickname = "Sari", healthRisk = true, screeningDone = true)
        db.profileDao().upsert(p)
        assertEquals(p, db.profileDao().get())
        // second upsert replaces, it does not add a row
        db.profileDao().upsert(p.copy(nickname = "Sari B"))
        assertEquals("Sari B", db.profileDao().observe().first()?.nickname)
    }

    @Test fun measuresByTypeInTimeOrder() = runBlocking {
        fun m(id: String, type: String, at: Long) = MeasureEntity(id, T0, T0, type, 60.0, "kg", at)
        db.profileDao().upsertMeasure(m("a", "weight", 30))
        db.profileDao().upsertMeasure(m("b", "weight", 10))
        db.profileDao().upsertMeasure(m("c", "waist", 20))
        assertEquals(listOf("b", "a"), db.profileDao().observeMeasures("weight").first().map { it.id })
    }

    @Test fun programCascadeAndOwnProgramsSurviveRegeneration() = runBlocking {
        val d = db.programDao()
        d.insertProgram(ProgramEntity("auto1", T0, T0, "auto", "gym", 1, true))
        d.insertProgram(ProgramEntity("own1", T0, T0, "own", "gym", 1, false))
        d.insertDays(listOf(ProgramDayEntity("day1", T0, T0, "auto1", 0, "A", 45)))
        d.insertItems(listOf(ProgramItemEntity("it1", T0, T0, "day1", "SQUAT_GOB", 0, 3, 8, 12, 90)))
        assertEquals(1, d.items("day1").size)
        d.deleteAutoPrograms() // L-5: auto rows regenerate, own rows never
        assertTrue(d.days("auto1").isEmpty())
        assertTrue(d.items("day1").isEmpty())
        assertNull(d.observeActive().first())
        d.deactivateAll(T0)
        val own = db.query("SELECT COUNT(*) FROM program WHERE source = 'own'", null)
        own.moveToFirst(); assertEquals(1, own.getInt(0)); own.close()
    }

    // --- write on every tap (PR-2) --------------------------------------------

    @Test fun unfinishedWorkoutIsEndedAtNull() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout("w1", started = T0))
        assertNull(w.workout("w1")!!.endedAt)
        assertEquals(listOf("w1"), w.unfinishedSince(T0 - 1).map { it.id })
        w.finish("w1", endedAt = T0 + 1000, feeling = 4, note = null)
        assertEquals(T0 + 1000, w.workout("w1")!!.endedAt)
        assertTrue(w.unfinishedSince(T0 - 1).isEmpty())
    }

    @Test fun staleUnfinishedFoundOnlyBeforeCutoff() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout("old", started = T0 - 3 * DAY))
        w.startWorkout(workout("new", started = T0))
        assertEquals(listOf("old"), w.staleUnfinished(T0 - DAY).map { it.id })
        assertEquals(listOf("new"), w.unfinishedSince(T0 - DAY).map { it.id })
    }

    @Test fun everyTapIsOneWriteAndStaysAfterRestart() = runBlocking {
        val name = "tap-restart.db"
        var disk = DbTestSupport.onDisk(name)
        disk.workoutDao().startWorkout(workout())
        disk.workoutDao().saveSet(set("s1", position = 0, kg = 40.0, reps = 8), now = T0 + 1, restEndsAt = T0 + 91_000)
        disk.workoutDao().saveSet(set("s1", position = 0, done = true, kg = 40.0, reps = 8), now = T0 + 2, restEndsAt = T0 + 92_000)
        disk.close() // stands in for a process kill: no flush, no "save" call was made
        disk = DbTestSupport.onDisk(name)
        try {
            val sets = disk.workoutDao().sets("w1")
            assertEquals(1, sets.size)
            assertTrue(sets[0].done)
            assertEquals(40.0, sets[0].weightKg!!, 0.0)
            val w = disk.workoutDao().workout("w1")!!
            assertNull(w.endedAt)
            assertEquals(T0 + 92_000, w.restEndsAt) // rest timer stored as end time (Design §4)
            assertEquals(T0 + 2, w.updatedAt)
        } finally {
            disk.close()
            DbTestSupport.context().deleteDatabase(name)
        }
    }

    @Test fun setEditReplacesSameIdAndKeepsOrder() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout())
        w.saveSet(set("s2", position = 1, reps = 5), T0)
        w.saveSet(set("s1", position = 0, reps = 8), T0)
        w.saveSet(set("s1", position = 0, reps = 9, done = true), T0) // fix a typo
        val rows = w.observeSets("w1").first()
        assertEquals(listOf("s1", "s2"), rows.map { it.id })
        assertEquals(9, rows[0].reps)
    }

    @Test fun finishClearsRestTimer() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout())
        w.saveSet(set("s1", position = 0), T0, restEndsAt = T0 + 60_000)
        w.finish("w1", T0 + 5, 3, "ok")
        assertNull(w.workout("w1")!!.restEndsAt)
    }

    @Test fun deletingWorkoutDeletesItsSets() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout())
        w.saveSet(set("s1", position = 0), T0)
        db.openHelper.writableDatabase.execSQL("DELETE FROM workout WHERE id = 'w1'")
        assertTrue(w.sets("w1").isEmpty())
    }

    /**
     * Design §10: set tick saved to disk in 50 ms. Measured here on the JVM against a file database
     * (Robolectric's SQLite), so it proves the path is one small transaction, not the phone's speed.
     * The real number on the reference phone comes from B-19. Median, not max, to survive a busy CI box.
     */
    @Test fun setTickMedianUnderBudget() {
        val name = "timing.db"
        val disk = DbTestSupport.onDisk(name)
        try {
            runBlocking {
                disk.workoutDao().startWorkout(workout())
                repeat(20) { disk.workoutDao().saveSet(set("warm$it", position = 100 + it), T0 + it) } // warm up
                val times = (0 until 100).map { i ->
                    measureNanoTime {
                        runBlocking { disk.workoutDao().saveSet(set("t$i", position = i, done = true, reps = 8, kg = 20.0), T0 + i, T0 + 90_000) }
                    }
                }.sorted()
                val medianMs = times[times.size / 2] / 1_000_000.0
                println("saveSet median ${"%.2f".format(medianMs)} ms, max ${"%.2f".format(times.last() / 1_000_000.0)} ms")
                assertTrue("median $medianMs ms over the 50 ms budget", medianMs < 50.0)
            }
        } finally {
            disk.close()
            DbTestSupport.context().deleteDatabase(name)
        }
    }

    // --- unique keys ----------------------------------------------------------

    @Test fun setPositionIsUniquePerWorkout() = runBlocking {
        val w = db.workoutDao()
        w.startWorkout(workout())
        w.saveSet(set("s1", position = 0), T0)
        try {
            w.saveSet(set("s2", position = 0), T0)
            fail("two sets at the same position of one workout must be refused")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test fun badgeCanBeEarnedOnce() = runBlocking {
        val p = db.progressDao()
        assertTrue(p.earnBadge(BadgeEntity("FIRST_WORKOUT", T0, T0, T0)) != -1L)
        assertEquals(-1L, p.earnBadge(BadgeEntity("FIRST_WORKOUT", T0 + 1, T0 + 1, T0 + 1)))
        val rows = p.observeBadges().first()
        assertEquals(1, rows.size)
        assertEquals(T0, rows[0].earnedAt) // the first date is kept
    }

    @Test fun mealPlanHasOneOptionPerSlot() = runBlocking {
        val f = db.foodDao()
        f.upsertPlan(MealPlanEntity("p1", T0, T0, "2026-11-16", 1, "pagi", "OPT_A"))
        f.upsertPlan(MealPlanEntity("p2", T0, T0, "2026-11-16", 1, "pagi", "OPT_B")) // new choice replaces the slot
        f.upsertPlan(MealPlanEntity("p3", T0, T0, "2026-11-16", 1, "siang", "OPT_C"))
        assertEquals(listOf("OPT_B", "OPT_C"), f.plan("2026-11-16").map { it.optionCode })
    }

    @Test fun programDayPositionIsUnique() = runBlocking {
        val d = db.programDao()
        d.insertProgram(ProgramEntity("p", T0, T0, "own", "gym", 1, false))
        d.insertDays(listOf(ProgramDayEntity("d1", T0, T0, "p", 0, "A", 30)))
        try {
            d.insertDays(listOf(ProgramDayEntity("d2", T0, T0, "p", 0, "B", 30)))
            fail("duplicate position in one program")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test fun setNeedsExistingWorkout() = runBlocking {
        try {
            db.workoutDao().saveSet(set("s1", workoutId = "ghost", position = 0), T0)
            fail("foreign key must stop an orphan set")
        } catch (_: SQLiteConstraintException) {
        }
    }

    // --- food log keeps its numbers --------------------------------------------

    @Test fun foodLogKeepsKcalWhenFoodChangesOrIsDeleted() = runBlocking {
        val f = db.foodDao()
        f.upsertFoods(listOf(food("f1", "Nasi putih", kcal = 130.0)))
        f.upsertLog(FoodLogEntity("l1", T0, T0, "2026-11-18", "siang", "f1", null, 200.0, 260.0, 5.0, 56.0, 0.5))
        f.upsertFoods(listOf(food("f1", "Nasi putih", kcal = 999.0))) // content update changes the food
        db.openHelper.writableDatabase.execSQL("DELETE FROM food WHERE id = 'f1'")
        assertEquals(260.0, f.observeDayKcal("2026-11-18").first(), 0.0)
        assertEquals(1, f.observeDay("2026-11-18").first().size)
    }

    @Test fun quickKcalCountsWithoutAFood() = runBlocking {
        val f = db.foodDao()
        f.upsertLog(FoodLogEntity("q", T0, T0, "2026-11-18", "camilan", null, null, null, 0.0, 0.0, 0.0, 0.0, quickKcal = 150.0))
        f.upsertLog(FoodLogEntity("n", T0, T0, "2026-11-18", "pagi", null, null, 100.0, 200.0, 0.0, 0.0, 0.0))
        assertEquals(350.0, f.observeDayKcal("2026-11-18").first(), 0.0)
        assertEquals(0.0, f.observeDayKcal("2026-11-19").first(), 0.0)
    }

    // --- FTS4 food search (Design §10) -------------------------------------------

    @Test fun ftsPrefixSearchFindsFoods() = runBlocking {
        val f = db.foodDao()
        f.upsertFoods(listOf(
            food("1", "Ayam goreng", "Fried chicken"), food("2", "Ayam bakar", "Grilled chicken"),
            food("3", "Nasi putih", "White rice"), food("4", "Tempe goreng", "Fried tempeh"),
        ))
        assertEquals(listOf("Ayam bakar", "Ayam goreng"), f.search(FoodSearch.toMatch("aya")!!, 10).map { it.nameId })
        assertEquals(listOf("Ayam goreng", "Tempe goreng"), f.search(FoodSearch.toMatch("GORENG")!!, 10).map { it.nameId })
        assertEquals(listOf("Ayam goreng"), f.search(FoodSearch.toMatch("ayam gor")!!, 10).map { it.nameId })
        assertEquals(listOf("Nasi putih"), f.search(FoodSearch.toMatch("rice")!!, 10).map { it.nameId }) // English name too
        assertTrue(f.search(FoodSearch.toMatch("pizza")!!, 10).isEmpty())
        assertEquals(1, f.search(FoodSearch.toMatch("goreng")!!, 1).size)
    }

    @Test fun ftsFollowsUpdateAndDelete() = runBlocking {
        val f = db.foodDao()
        f.upsertFoods(listOf(food("1", "Bubur ayam")))
        assertEquals(1, f.search("bubur*", 10).size)
        f.upsertFoods(listOf(food("1", "Lontong sayur")))
        assertTrue(f.search("bubur*", 10).isEmpty())
        assertEquals(1, f.search("lontong*", 10).size)
        db.openHelper.writableDatabase.execSQL("DELETE FROM food WHERE id = '1'")
        assertTrue(f.search("lontong*", 10).isEmpty())
    }

    @Test fun ftsUsesTheIndexNotAScan() {
        val c = db.query("EXPLAIN QUERY PLAN SELECT food.* FROM food JOIN food_fts ON food.rowid = food_fts.rowid WHERE food_fts MATCH 'a*'", null)
        val plan = buildString { while (c.moveToNext()) append(c.getString(c.getColumnCount() - 1)).append('\n') }
        c.close()
        assertTrue("plan should use the virtual table: $plan", plan.contains("VIRTUAL TABLE INDEX"))
    }

    @Test fun searchInputIsSanitised() = runBlocking {
        assertNull(FoodSearch.toMatch("  "))
        assertNull(FoodSearch.toMatch("\"*-()"))
        assertEquals("nasi* goreng*", FoodSearch.toMatch("nasi  goreng!"))
        // Whatever the user types must run without an FTS syntax error.
        db.foodDao().upsertFoods(listOf(food("1", "Tahu isi")))
        for (typed in listOf("tahu OR", "\"tahu", "tahu NEAR isi", "-tahu", "tahu:isi", "(tahu", "NOT tahu")) {
            db.foodDao().search(FoodSearch.toMatch(typed)!!, 5)
        }
    }

    @Test fun noNetworkColumnsExist() {
        // Design §3, PV-1: nothing in the schema is a URL or account field.
        val c = db.query("SELECT m.name AS t, p.name AS c FROM sqlite_master m JOIN pragma_table_info(m.name) p WHERE m.type = 'table'", null)
        val names = buildList { while (c.moveToNext()) add(c.getString(1).lowercase()) }
        c.close()
        val banned = listOf("url", "email", "token", "uploadedat", "syncstatus", "remoteid")
        assertTrue(names.none { n -> banned.any { it in n } })
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
