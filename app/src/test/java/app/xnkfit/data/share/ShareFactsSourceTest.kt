package app.xnkfit.data.share

import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.db.FoodLogEntity
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.WorkoutEntity
import app.xnkfit.data.db.WorkoutSetEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.progress.ProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ShareFactsSourceTest {
    private lateinit var db: XnkDatabase
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 19)
    private fun ms(d: LocalDate, h: Int = 10) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun source() = ShareFactsSource(db, ProgressRepository(db, { zone }, { today }, { 5_000L }), { zone }, { today })

    @Before
    fun open() {
        db = T.db()
        runBlocking { BundledContentLoader(ContentStore(db) { 5_000L }, T.repoSource) { 5_000L }.loadIfNeeded() }
    }

    @After
    fun close() { db.close() }

    private suspend fun workout(id: String, d: LocalDate, weight: Double, reps: Int) {
        db.workoutDao().startWorkout(WorkoutEntity(id, 1, 1, null, "bebas", ms(d, 9), endedAt = ms(d, 10)))
        db.workoutDao().insertSetRow(WorkoutSetEntity("$id-s", 1, 1, id, "squat-goblet", 0, "normal", weight, reps, null, null, "rapi", true, ms(d, 9) + 1000))
    }

    @Test
    fun emptyPhoneHasNoFactsToShare() = runBlocking {
        val f = source().load()
        assertNull(f.workout)
        assertNull(f.progress)
        assertEquals(0, f.food?.daysLogged)
    }

    @Test
    fun workoutFactsCountMinutesTopLiftAndOnlyThisWorkoutsRecords() = runBlocking {
        workout("w0", today.minusDays(5), 10.0, 10)
        workout("w1", today, 12.0, 10)
        val w = source().load().workout!!
        assertEquals(today, w.day)
        assertEquals(60, w.minutes)
        assertEquals(1, w.records)
        assertEquals(12.0, w.topLift!!.weightKg!!, 0.0)
        assertEquals(10, w.topLift!!.reps)
    }

    @Test
    fun progressFactsAreChangesAndSpan() = runBlocking {
        db.progressDao().upsertMeasure(MeasureEntity("m1", 1, 1, "waist", 82.0, "cm", ms(today.minusDays(30))))
        db.progressDao().upsertMeasure(MeasureEntity("m2", 1, 1, "waist", 80.0, "cm", ms(today)))
        val p = source().load().progress!!
        assertEquals(-2.0, p.waistDeltaCm!!, 0.0)
        assertNull(p.weightDeltaKg)
        assertEquals(30, p.trackedDays)
    }

    @Test
    fun foodDaysCountOnlyTheLastSevenDays() = runBlocking {
        fun log(i: Int, d: LocalDate) = FoodLogEntity("f$i", 1, 1, d.toString(), "pagi", null, null, null, 100.0, 1.0, 1.0, 1.0)
        listOf(today, today.minusDays(1), today.minusDays(1), today.minusDays(6), today.minusDays(7)).forEachIndexed { i, d ->
            db.foodDao().upsertLog(log(i, d))
        }
        assertEquals(3, source().load().food!!.daysLogged)
    }
}
