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
import app.xnkfit.domain.latihan.R_JEL_SHELF
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class OwnRoutinesTest {
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

    private val squat = "SQUAT_GOB"

    @Test fun L5_savingAnEditTurnsAnAutoProgramIntoOwnAndTheGeneratorLeavesItAlone() = runBlocking {
        ready()
        repo().createAuto(gym)
        val before = repo().active()!!
        val first = before.days.first()
        repo().saveEdit(
            before.id,
            before.days.map { d ->
                ProgramRepository.EditDay(d.name, d.items.take(if (d.id == first.id) 2 else d.items.size).map {
                    ProgramRepository.EditItem(it.exerciseId, it.sets, it.repMin, it.repMax, it.restSec)
                })
            },
        )
        val own = repo().active()!!
        assertEquals(ProgramSource.OWN, own.source)
        assertEquals(2, own.days.first().items.size)
        // The generator runs again: the own program keeps its two exercises and stays active.
        val again = repo().createAuto(gym.copy(days = 2)) as CreateResult.Created
        assertEquals(false, again.active)
        assertEquals(own.id, repo().active()!!.id)
        assertEquals(2, repo().active()!!.days.first().items.size)
        // The review never proposes anything for an own program.
        assertNull(repo().reviewOffer(gym, autoOff = false, answeredAt = null))
    }

    @Test fun reviewIsQuietWhenAutoIsOffOrNotDue() = runBlocking {
        ready()
        repo().createAuto(gym)
        assertNull(repo().reviewOffer(gym, autoOff = false, answeredAt = null)) // not due yet
        clock += 6 * WEEK_MS
        assertNull(repo().reviewOffer(gym, autoOff = true, answeredAt = null)) // L-5: automatic programming off
    }

    @Test fun anOwnRoutineIsKeptApartFromTheProgram() = runBlocking {
        ready()
        repo().createAuto(gym)
        val program = repo().active()!!
        val id = repo().createRoutine("Lengan", listOf(ProgramRepository.EditItem(program.days.first().items.first().exerciseId, 3, 8, 12, 90)))
        val routines = repo().routines()
        assertEquals(1, routines.size)
        assertEquals(ProgramSource.OWN, routines.single().source)
        // The program list and the active program do not change, and regenerating does not remove the routine.
        assertEquals(program.id, repo().active()!!.id)
        assertEquals(1, repo().all().size)
        repo().createAuto(gym.copy(days = 2))
        assertEquals(1, repo().routines().size)
        // A routine can be started like a day.
        val plan = logger().nextPlannedDay(routines.single().days.single().id)!!
        assertEquals(routines.single().days.single().id, plan.programDayId)
        repo().updateRoutine(id, "Lengan B", emptyList())
        assertEquals(0, repo().routines().single().days.single().items.size)
        repo().deleteRoutine(id)
        assertEquals(0, repo().routines().size)
    }

    @Test
    fun R_JEL_SHELF_cardBuildsAnActiveProgramAndReplacesTheOldCard() = runBlocking {
        ready()
        repo().createAuto(gym)
        R_JEL_SHELF.forEach { t ->
            val r = repo().useTemplate(t, gym) as CreateResult.Created
            assertTrue(r.active)
            assertEquals(ProgramSource.JELAJAHI, repo().active()!!.source)
            assertEquals(t.setting.code, repo().active()!!.setting)
        }
        assertEquals(1, db.programDao().allPrograms().count { it.source == "jelajahi" })
    }

    @Test fun jelajahiNeverWritesBeforeTheScreeningIsDone() = runBlocking {
        ready(done = false)
        assertTrue(repo().useTemplate(R_JEL_SHELF.first(), null) is CreateResult.Blocked)
        assertNull(db.programDao().active())
        assertNotNull(R_JEL_SHELF.firstOrNull { it.id == "home_bodyweight" })
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
