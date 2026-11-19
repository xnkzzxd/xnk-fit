package app.xnkfit.data.progress

import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.db.ActivityEntity
import app.xnkfit.data.db.FoodLogEntity
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.WorkoutEntity
import app.xnkfit.data.db.XnkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {
    private lateinit var db: XnkDatabase
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 11, 16)
    private var today = monday.plusDays(6)
    private var clock = 5_000L

    private fun ms(d: LocalDate, h: Int = 10) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun repo() = ProgressRepository(db, { zone }, { today }, { clock })

    @Before fun open() {
        db = T.db()
        runBlocking { BundledContentLoader(ContentStore(db) { clock }, T.repoSource) { clock }.loadIfNeeded() }
    }

    @After fun close() { db.close() }

    private suspend fun finish(id: String, d: LocalDate) = db.workoutDao().startWorkout(
        WorkoutEntity(id, 1, 1, null, "bebas", ms(d, 9), endedAt = ms(d, 10)),
    )

    @Test fun firstWorkoutGivesPointsABadgeAndAJourneyMomentOnce() = runBlocking {
        finish("w1", monday)
        val r = repo()
        val earned = r.refresh()
        assertEquals(listOf("WO_FIRST"), earned.map { it.code })
        assertEquals("Latihan pertama selesai. Awal yang baik. 🎉", earned.single().message) // message comes from the file
        val s = r.summary()
        assertEquals(10, s.points)
        assertEquals(1, s.finishedWorkouts)
        assertEquals(1, s.badges.count { it.earnedAt != null })
        assertEquals(18, s.badges.size)
        assertEquals(listOf("badge"), s.moments.map { it.kind })
        // A second refresh changes nothing: no double points, no second celebration.
        assertTrue(r.refresh().isEmpty())
        assertEquals(10, r.summary().points)
    }

    @Test fun anEarnedBadgeAndItsPointsSurviveDeletingTheSourceRows() = runBlocking {
        finish("w1", monday)
        val r = repo()
        r.refresh()
        db.openHelper.writableDatabase.execSQL("DELETE FROM workout")
        r.refresh()
        val s = r.summary()
        assertEquals(1, s.badges.count { it.earnedAt != null })
        assertEquals(10, s.points) // points only add up (P-3)
    }

    @Test fun weightNumberNeverChangesPointsOrBadges() = runBlocking {
        val sunday = monday.plusDays(6)
        suspend fun weigh(id: String, kg: Double) = db.progressDao().upsertMeasure(MeasureEntity(id, 1, 1, "weight", kg, "kg", ms(sunday)))
        weigh("m1", 80.0)
        val r = repo()
        r.refresh()
        val withHigh = r.summary().points
        db.openHelper.writableDatabase.execSQL("UPDATE measure SET value = 55.0")
        r.refresh()
        assertEquals(withHigh, r.summary().points)
        assertEquals(2, withHigh) // R_PTS_MEASURE, whatever the number is
    }

    @Test fun measureOnAnotherDayGivesNothing() = runBlocking {
        db.progressDao().upsertMeasure(MeasureEntity("m1", 1, 1, "weight", 70.0, "kg", ms(monday)))
        val r = repo(); r.refresh()
        assertEquals(0, r.summary().points)
    }

    @Test fun activityAndFoodRowsFeedPointsAndStreak() = runBlocking {
        val dao = db.progressDao()
        db.workoutDao().upsertActivity(ActivityEntity("a1", 1, 1, "walk", 45, "moderate", 100.0, ms(monday)))
        db.foodDao().upsertLog(FoodLogEntity("f1", 1, 1, monday.plusDays(1).toString(), "pagi", null, null, null, 100.0, 1.0, 1.0, 1.0))
        val r = repo(); r.refresh()
        val s = r.summary()
        assertEquals(1, s.streak.current) // activity day + food day = 2 active days
        assertEquals(3 + 5, s.points) // activity 3 + streak week 5
        assertTrue(dao.pointRows().none { it.kind == "foodDay" })
    }

    @Test fun reachingLevelThreeEarnsItsBadgeAndLevelMoments() = runBlocking {
        for (i in 0 until 25) finish("w$i", monday.plusDays((i % 7).toLong()).minusWeeks((i / 7).toLong()))
        val r = repo()
        val earned = r.refresh().map { it.code }
        assertTrue(earned.toString(), "LEVEL_3" in earned)
        val s = r.summary()
        assertTrue(s.level.level >= 3)
        assertTrue(s.moments.any { it.kind == "level" })
    }

    @Test fun photosCountForTheFirstPhotoBadge() = runBlocking {
        db.progressDao().upsertPhoto(app.xnkfit.data.db.PhotoEntity("p1", 1, 1, "/data/x/photos/p1.jpg", 1, "front"))
        assertTrue("PHOTO_FIRST" in repo().refresh().map { it.code })
    }

    @Test fun withoutBadgeContentNothingIsAwardedAndNothingCrashes() = runBlocking {
        db.close(); db = T.db()
        finish("w1", monday)
        val r = repo()
        assertTrue(r.refresh().isEmpty())
        assertEquals(10, r.summary().points)
        assertFalse(r.summary().badges.any { it.earnedAt != null })
    }
}
