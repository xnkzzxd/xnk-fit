package app.xnkfit.data.db

import app.xnkfit.data.backup.BACKUP_TABLES
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * NF-2 on the JVM. A process kill in the middle of a write is simulated by closing the database while a transaction is
 * open and never committed, then opening the same file again. Restore, content update, logger and food log all write in
 * one transaction, so the file must show exactly the old state. A real kill of the app process is a manual test
 * (docs/Test-Manual.md, K-1..K-5).
 */
@RunWith(RobolectricTestRunner::class)
class KillMidTransactionTest {
    private val file = "kill-${UUID.randomUUID()}.db"
    private var db: XnkDatabase = DbTestSupport.onDisk(file)

    @After fun close() = db.close()

    private fun reopen() {
        db.close()
        db = DbTestSupport.onDisk(file)
    }

    /** Runs [writes] inside a transaction, then dies: no commit, handle dropped. */
    private fun dieInside(writes: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        writes(sql)
        // no setTransactionSuccessful, no endTransaction: the process is gone
        reopen()
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun killInTheMiddleOfAWorkoutSetKeepsTheTickedSets() = runBlocking {
        val dao = db.workoutDao()
        dao.startWorkout(DbTestSupport.workout())
        dao.saveSets(listOf(DbTestSupport.set("s1", position = 0, done = true, reps = 8, kg = 20.0), DbTestSupport.set("s2", position = 1)), "w1", DbTestSupport.T0, null)
        dieInside { it.execSQL("UPDATE workout_set SET done = 0, reps = NULL"); it.execSQL("DELETE FROM workout_set WHERE id = 's2'") }
        val sets = db.workoutDao().sets("w1")
        assertEquals(2, sets.size)
        assertTrue(sets.first { it.id == "s1" }.done)
        assertEquals(8, sets.first { it.id == "s1" }.reps)
        assertNull("the workout is still open", db.workoutDao().workout("w1")!!.endedAt)
        assertEquals("w1", db.workoutDao().unfinishedSince(DbTestSupport.T0 - 1).single().id)
    }

    @Test fun killInTheMiddleOfRestoreKeepsTheOldData() = runBlocking {
        db.workoutDao().startWorkout(DbTestSupport.workout())
        db.foodDao().upsertLog(log("l1"))
        val before = BACKUP_TABLES.associate { it.name to count(it.name) }
        dieInside { sql -> BACKUP_TABLES.asReversed().forEach { sql.execSQL("DELETE FROM ${it.name}") } }
        BACKUP_TABLES.forEach { assertEquals(it.name, before.getValue(it.name), count(it.name)) }
        assertTrue(count("workout") == 1 && count("food_log") == 1)
    }

    @Test fun killInTheMiddleOfAContentUpdateKeepsTheOldContent() = runBlocking {
        db.foodDao().upsertFoods(listOf(DbTestSupport.food("tkpi_1", "Nasi"), DbTestSupport.food("tkpi_2", "Tempe")))
        dieInside { it.execSQL("DELETE FROM food") }
        assertEquals(2, count("food"))
        assertEquals("Nasi", db.foodDao().food("tkpi_1")!!.nameId)
    }

    @Test fun killAfterEachMealLogKeepsEveryEarlierEntry() = runBlocking {
        for (i in 1..3) {
            db.foodDao().upsertLog(log("l$i"))
            reopen()
            assertEquals(i, count("food_log"))
        }
        dieInside { it.execSQL("DELETE FROM food_log") }
        assertEquals(3, count("food_log"))
    }

    private fun log(id: String) = FoodLogEntity(id, DbTestSupport.T0, DbTestSupport.T0, "2026-11-19", "pagi", null, null, null, 300.0, 10.0, 40.0, 8.0, 300.0)
}
