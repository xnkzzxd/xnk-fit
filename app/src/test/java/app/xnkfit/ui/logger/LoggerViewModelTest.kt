package app.xnkfit.ui.logger

import android.os.Looper
import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.ExerciseEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.domain.logger.LoggerMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LoggerViewModelTest {
    private val file = "lvm-${UUID.randomUUID()}.db"
    private var db: XnkDatabase = DbTestSupport.onDisk(file)
    private var clock = 1_763_510_400_000L

    @After fun close() = db.close()

    private class FakePrefs : LoggerPrefs {
        var mode: LoggerMode? = null
        var asked = false
        override suspend fun mode() = mode
        override suspend fun setMode(mode: LoggerMode) { this.mode = mode }
        override suspend fun alarmAsked() = asked
        override suspend fun setAlarmAsked() { asked = true }
    }

    private class FakeAlarms(var access: AlarmAccess) : RestAlarms {
        var scheduled: Long? = null
        override fun access() = access
        override fun schedule(atMs: Long) { scheduled = atMs }
        override fun cancel() { scheduled = null }
    }

    private val prefs = FakePrefs()

    private fun vm(alarms: RestAlarms) = LoggerViewModel(
        LoggerRepository(db, { clock }, { UUID.randomUUID().toString() }) { ZoneOffset.UTC },
        prefs, alarms, { clock }, { UUID.randomUUID().toString() }, tickClock = false,
    )

    private fun await(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("timeout: $what")
    }

    private fun LoggerViewModel.settle() = await("write") { lastWrite?.isCompleted != false }

    private fun seedExercise() = runBlocking {
        db.programDao().upsertExercises(listOf(ExerciseEntity("SQUAT_GOB", 0, 0, "Goblet Squat", "Goblet Squat", "SQUAT",
            """{"main":["QUADS"],"helper":[]}""", "1", settingsJson = """["gym"]""", equipmentJson = """["dumbbell"]""")))
    }

    @Test
    fun noModeIsPreselectedAtTheFirstWorkout() {
        val v = vm(FakeAlarms(AlarmAccess.READY))
        await("start") { v.ui.value.phase == LoggerPhase.START }
        assertNull(v.ui.value.chosenMode)
        v.start() // nothing happens without a choice
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(LoggerPhase.START, v.ui.value.phase)
    }

    @Test
    fun tapsAreOnDiskAndTheTimerAsksOnceThenFallsBack() {
        seedExercise()
        val alarms = FakeAlarms(AlarmAccess.NEEDS_EXACT_ALARM)
        val v = vm(alarms)
        await("start") { v.ui.value.phase == LoggerPhase.START }
        v.chooseMode(LoggerMode.BEBAS)
        v.start(); await("active") { v.ui.value.phase == LoggerPhase.ACTIVE }
        v.addExercise("SQUAT_GOB"); v.settle()
        await("block") { v.ui.value.blocks.isNotEmpty() }
        val set = v.ui.value.blocks.first().sets.first()
        v.edit(set.id) { it.copy(weightKg = 12.0, reps = 10) }; v.settle()
        val id = v.ui.value.workoutId!!
        val onDisk = runBlocking { db.workoutDao().sets(id) }.single()
        assertEquals(12.0, onDisk.weightKg!!, 0.0)

        v.toggleDone(set.id); v.settle()
        assertEquals(AlarmAccess.NEEDS_EXACT_ALARM, v.ask.value) // first timer: the system is asked
        assertTrue(runBlocking { db.workoutDao().sets(id) }.single().done)
        assertEquals(clock + 120_000L, runBlocking { db.workoutDao().workout(id) }!!.restEndsAt)
        v.onAccessAnswered() // user said no
        assertEquals(AlarmNotice.IN_APP_ONLY, v.ui.value.alarmNotice)
        assertNull(alarms.scheduled)

        v.extendRest(); v.settle()
        assertNull("asked only once", v.ask.value)
        assertEquals(clock + 135_000L, runBlocking { db.workoutDao().workout(id) }!!.restEndsAt)

        alarms.access = AlarmAccess.READY
        v.askAgain()
        assertEquals(clock + 135_000L, alarms.scheduled)
        assertNull(v.ui.value.alarmNotice)
    }

    @Test
    fun aNewViewModelOffersTheUnfinishedWorkout() {
        seedExercise()
        val v = vm(FakeAlarms(AlarmAccess.READY))
        await("start") { v.ui.value.phase == LoggerPhase.START }
        v.chooseMode(LoggerMode.DIPANDU); v.start()
        await("active") { v.ui.value.phase == LoggerPhase.ACTIVE }
        v.addExercise("SQUAT_GOB"); v.settle()
        val id = v.ui.value.workoutId
        db.close(); db = DbTestSupport.onDisk(file) // process gone
        val again = vm(FakeAlarms(AlarmAccess.READY))
        await("resume") { again.ui.value.phase == LoggerPhase.RESUME }
        assertEquals(id, again.ui.value.workoutId)
        again.resume(); await("active") { again.ui.value.phase == LoggerPhase.ACTIVE }
        assertEquals(LoggerMode.DIPANDU, again.ui.value.mode)
        assertEquals("SQUAT_GOB", again.ui.value.next?.exerciseId)
        again.finish(); await("done") { again.ui.value.phase == LoggerPhase.DONE }
        assertTrue(runBlocking { db.workoutDao().workout(id!!) }!!.endedAt != null)
    }
}
