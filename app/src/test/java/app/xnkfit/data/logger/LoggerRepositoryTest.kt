package app.xnkfit.data.logger

import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.ExerciseEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.logger.LoggerMode
import app.xnkfit.domain.logger.restAfter
import app.xnkfit.domain.logger.restEndsAt
import app.xnkfit.domain.training.DayName
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.FormRating
import app.xnkfit.domain.training.GoalFocus
import app.xnkfit.domain.training.ItemRole
import app.xnkfit.domain.training.LoadKind
import app.xnkfit.domain.training.Pattern
import app.xnkfit.domain.training.Program
import app.xnkfit.domain.training.ProgramDay
import app.xnkfit.domain.training.ProgramItem
import app.xnkfit.domain.training.ProgramSource
import app.xnkfit.domain.training.ReasonCode
import app.xnkfit.domain.training.TrainingLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.util.UUID

/**
 * PR-2 / NF-2 on the JVM: every tap is on disk when the call returns, and a "kill" (closing the database without
 * finishing, then opening a new instance on the same file) finds the workout exactly as it was. A real process kill
 * on a phone is B-18's instrumented test; this cannot replace it.
 */
@RunWith(RobolectricTestRunner::class)
class LoggerRepositoryTest {
    private val file = "logger-${UUID.randomUUID()}.db"
    private var clock = DAY0
    private var db: XnkDatabase = DbTestSupport.onDisk(file)

    @After fun close() = db.close()

    private fun repo() = LoggerRepository(db, { clock }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC }

    /** Simulated kill: the open handle is dropped without any finish or flush call, a new one opens the file. */
    private fun kill(): LoggerRepository {
        db.close()
        db = DbTestSupport.onDisk(file)
        return repo()
    }

    private suspend fun seed(r: LoggerRepository) {
        fun ex(id: String, pattern: String, eq: String) = ExerciseEntity(
            id, DAY0, DAY0, id, id, pattern, """{"main":["QUADS"],"helper":[]}""", "1",
            settingsJson = """["gym"]""", equipmentJson = eq,
        )
        db.programDao().upsertExercises(listOf(ex("SQUAT_GOB", "SQUAT", """["dumbbell"]"""), ex("CORE_PLANK", "CORE", "[]")))
        val items = listOf(
            ProgramItem("SQUAT_GOB", Pattern.SQUAT, ItemRole.MAIN, 3, 8, 12, 120, LoadKind.WEIGHTED, false),
            ProgramItem("CORE_PLANK", Pattern.CORE, ItemRole.ACCESSORY, 2, 10, 15, 60, LoadKind.BODYWEIGHT_OR_BAND, false),
        )
        r.saveProgram(
            Program(ProgramSource.AUTO, EquipmentSetting.GYM, TrainingLevel.L1, GoalFocus.OTOT, 1,
                listOf(ProgramDay(0, DayName.FULL_A, 30, items), ProgramDay(1, DayName.FULL_B, 30, items)), emptyList(), emptyMap(), emptySet()),
        )
    }

    @Test
    fun startWritesTheWholePlanAndSurvivesAKill() = runBlocking {
        var r = repo()
        seed(r)
        val plan = r.nextPlannedDay()!!
        assertEquals(DayName.FULL_A, plan.dayName)
        val id = r.start(LoggerMode.DIPANDU, plan)
        r = kill()
        val open = r.recoverUnfinished()!!
        assertEquals(id, open.id)
        assertNull(open.endedAt)
        val sets = r.load(id)!!.sets
        assertEquals(5, sets.size)
        assertEquals(listOf(20, 20), sets.filter { it.exerciseId == "CORE_PLANK" }.map { it.holdSec })
        assertNull("no kg invented without a log", sets.first().weightKg)
        assertEquals(8, sets.first().reps)
    }

    @Test
    fun everyTapIsOnDiskAtOnce() = runBlocking {
        var r = repo()
        seed(r)
        val id = r.start(LoggerMode.DIPANDU, r.nextPlannedDay())
        var sets = r.load(id)!!.sets
        // tap 1: weight, tap 2: form, tap 3: tick with rest; a kill after each tap keeps it.
        r.saveSet(id, sets[0].copy(weightKg = 16.0), null)
        r = kill(); assertEquals(16.0, r.load(id)!!.sets[0].weightKg!!, 0.0)
        r.saveSet(id, r.load(id)!!.sets[0].copy(form = FormRating.RAGU), null)
        r = kill(); assertEquals(FormRating.RAGU, r.load(id)!!.sets[0].form)
        sets = r.load(id)!!.sets
        val done = sets[0].copy(done = true, doneAt = clock)
        val after = sets.map { if (it.id == done.id) done else it }
        val rest = restEndsAt(clock, restAfter(done, after))
        r.saveSet(id, done, rest)
        r = kill()
        val back = r.load(id)!!
        assertTrue(back.sets[0].done)
        assertEquals(clock + 120_000L, back.workout.restEndsAt)
        r.deleteSets(id, listOf(back.sets.last().id), back.workout.restEndsAt)
        r = kill(); assertEquals(4, r.load(id)!!.sets.size)
    }

    @Test
    fun unfinishedFromTwoDaysAgoIsClosedAtItsLastSet() = runBlocking {
        var r = repo()
        seed(r)
        val id = r.start(LoggerMode.BEBAS, r.nextPlannedDay())
        val s = r.load(id)!!.sets[0]
        r.saveSet(id, s.copy(weightKg = 10.0, done = true, doneAt = DAY0 + 600_000L), null)
        clock = DAY0 + 2 * DAY_MS
        r = kill()
        assertNull(r.recoverUnfinished())
        assertEquals(DAY0 + 600_000L, db.workoutDao().workout(id)!!.endedAt)
    }

    @Test
    fun yesterdayIsStillOffered() = runBlocking {
        var r = repo()
        val id = r.start(LoggerMode.BEBAS, null)
        clock = DAY0 + DAY_MS
        r = kill()
        assertEquals(id, r.recoverUnfinished()?.id)
    }

    @Test
    fun finishedWorkoutGivesPreviousNumbersAndNextDay() = runBlocking {
        val r = repo()
        seed(r)
        val id = r.start(LoggerMode.DIPANDU, r.nextPlannedDay())
        r.load(id)!!.sets.filter { it.exerciseId == "SQUAT_GOB" }.forEach {
            r.saveSet(id, it.copy(weightKg = 14.0, reps = 10, form = FormRating.RAPI, done = true, doneAt = clock), null)
        }
        r.finish(id)
        assertEquals(3, r.previous("SQUAT_GOB").size)
        clock += DAY_MS
        val next = r.nextPlannedDay()!!
        assertEquals(DayName.FULL_B, next.dayName)
        val t = next.targets.first { it.exerciseId == "SQUAT_GOB" }
        assertEquals(ReasonCode.MORE_REPS, t.reason.code)
        val id2 = r.start(LoggerMode.DIPANDU, next)
        val first = r.load(id2)!!.sets.first()
        assertEquals(14.0, first.weightKg!!, 0.0)
        assertEquals(10, first.reps)
        assertNotNull(r.history().lastEpochDay)
    }

    /** Design §10: a set tick saved to disk within 50 ms. JVM figure only; the phone figure is B-19. */
    @Test
    fun setTickStaysUnderFiftyMs() = runBlocking {
        val r = repo()
        seed(r)
        val id = r.start(LoggerMode.BEBAS, r.nextPlannedDay())
        val s = r.load(id)!!.sets[0]
        val times = (1..200).map { n ->
            val t0 = System.nanoTime()
            r.saveSet(id, s.copy(reps = n % 20, done = n % 2 == 0), clock + n)
            (System.nanoTime() - t0) / 1_000_000.0
        }.drop(20).sorted()
        val p95 = times[(times.size * 0.95).toInt()]
        println("saveSet ms: median=${times[times.size / 2]} p95=$p95 max=${times.last()}")
        assertTrue("p95 $p95 ms", p95 < 50.0)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val DAY0 = 1_763_510_400_000L // 2025-11-19 00:00 UTC
    }
}
