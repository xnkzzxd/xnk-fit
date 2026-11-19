package app.xnkfit.data.program

import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.ProgramEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.domain.latihan.TrainingSetup
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.domain.training.ProgramSource
import app.xnkfit.data.db.WorkoutEntity
import app.xnkfit.data.db.WorkoutSetEntity
import app.xnkfit.domain.latihan.ActivityChoice
import app.xnkfit.domain.latihan.Effort
import app.xnkfit.domain.latihan.R_MAP_TONE_NONE
import app.xnkfit.domain.training.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.util.UUID

/** B-08 piece 1 on the JVM: the B-06 generator is written to Room, read back as days, and L-5 holds. */
@RunWith(RobolectricTestRunner::class)
class ActivityAndWeekTest {
    private lateinit var db: XnkDatabase
    private var clock = NOW

    private fun logger() = LoggerRepository(db, { clock }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }
    private fun repo() = ProgramRepository(db, logger(), { clock }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }
    private val gym = TrainingSetup(EquipmentSetting.GYM, 3, 45, Experience.BARU_MULAI)

    @Before fun open() = runBlocking {
        db = T.db()
        BundledContentLoader(ContentStore(db) { clock }, T.repoSource) { clock }.loadIfNeeded()
        Unit
    }

    @After fun close() { db.close() }

    private suspend fun ready(done: Boolean = true) = db.profileDao().upsert(
        ProfileEntity(
            createdAt = NOW, updatedAt = NOW, sex = "laki_laki", birthYear = 1996, heightCm = 172.0, goal = "jaga", interest = null,
            nickname = null, screeningDone = done, eatingHistory = "tidak",
        ),
    ).also {
        db.profileDao().upsertMeasure(MeasureEntity("w", NOW, NOW, "weight", 70.0, "kg", NOW))
    }

    private suspend fun ownProgram(id: String, active: Boolean) {
        db.programDao().insertProgram(ProgramEntity(id, NOW, NOW, "own", "gym", 1, active))
    }

    private fun acts() = ActivityRepository(db, { clock }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }
    private fun weekMap() = WeekMapRepository(db, logger(), { clock }) { ZoneOffset.UTC }

    @Test fun anActivityIsStoredWithItsKcalAndCountedInTheWeek() = runBlocking {
        ready()
        assertTrue(acts().add(ActivityChoice.JALAN, Effort.SEDANG, 60))
        assertTrue(acts().add(ActivityChoice.LARI, Effort.BERAT, 30))
        assertTrue(acts().add(ActivityChoice.SENAM, Effort.RINGAN, 45))
        val w = acts().week()
        assertEquals(3, w.rows.size)
        assertEquals(120, w.week.counted) // 60 + 2 x 30; light not counted
        val walk = w.rows.first { it.code == "jalan" }
        assertEquals(3.5 * 70.0 * 1.0, walk.kcal, 1e-6) // MET x kg x hours, weight from the latest measure
        assertEquals(0.0, w.rows.first { it.effort == "light" }.kcal, 0.0)
        acts().delete(walk.id)
        assertEquals(2, acts().week().rows.size)
    }

    @Test fun aTypoInMinutesWritesNothing() = runBlocking {
        ready()
        assertEquals(false, acts().add(ActivityChoice.JALAN, Effort.SEDANG, 0))
        assertEquals(false, acts().add(ActivityChoice.JALAN, Effort.SEDANG, 100_000))
        assertEquals(0, acts().week().rows.size)
    }

    @Test fun activityOfAnotherWeekIsNotCounted() = runBlocking {
        ready()
        acts().add(ActivityChoice.JALAN, Effort.SEDANG, 40)
        clock += 8L * 24 * 60 * 60 * 1000
        assertEquals(0, acts().week().week.counted)
    }

    @Test fun theWeekMapCountsDoneSetsOfFinishedWorkoutsThisWeek() = runBlocking {
        ready()
        db.workoutDao().startWorkout(WorkoutEntity("w1", clock, clock, null, "bebas", clock, endedAt = clock + 1000))
        repeat(4) { n ->
            db.workoutDao().insertSetRow(
                WorkoutSetEntity("s$n", clock, clock, "w1", "SQUAT_BACK", n + 1, reps = 8, weightKg = 40.0, done = true, doneAt = clock),
            )
        }
        val m = weekMap().thisWeek()
        assertTrue(m.sets.getValue(MuscleGroup.QUADS) >= 4.0)
        assertTrue(m.levels.getValue(MuscleGroup.QUADS) > R_MAP_TONE_NONE)
        assertEquals(null, m.levels[MuscleGroup.BICEPS])
        // A week later nothing of it is left on the map.
        clock += 8L * 24 * 60 * 60 * 1000
        assertTrue(weekMap().thisWeek().levels.isEmpty())
    }

    @Test fun L13_feelingAndNoteAreStoredAndATypoFeelingIsDropped() = runBlocking {
        ready()
        db.workoutDao().startWorkout(WorkoutEntity("w2", clock, clock, null, "bebas", clock, endedAt = clock + 1000))
        logger().setFeelingNote("w2", 4, "  kaki pegal  ")
        db.workoutDao().observeFinished(5).first().first { it.id == "w2" }.let {
            assertEquals(4, it.feeling)
            assertEquals("kaki pegal", it.note)
        }
        logger().setFeelingNote("w2", 9, "   ")
        db.workoutDao().observeFinished(5).first().first { it.id == "w2" }.let {
            assertEquals(null, it.feeling)
            assertEquals(null, it.note)
        }
    }

    private companion object { const val NOW = 1_790_000_000_000L }
}
