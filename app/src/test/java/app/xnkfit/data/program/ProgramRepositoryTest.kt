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
class ProgramRepositoryTest {
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

    @Test fun generatedProgramIsStoredAsDaysAndItemsAndIsActive() = runBlocking {
        ready()
        val created = repo().createAuto(gym) as CreateResult.Created
        assertTrue(created.active)
        val view = repo().active()!!
        assertEquals(ProgramSource.AUTO, view.source)
        assertEquals("gym", view.setting)
        assertEquals(3, view.days.size)
        assertTrue(view.days.all { it.items.size >= 4 && it.estMinutes > 0 })
        // The name comes from the library, not the code.
        assertTrue(view.days.first().items.none { it.name == it.exerciseId })
        assertEquals("gym", db.profileDao().equipment().single().setting)
    }

    @Test fun noProgramBeforeTheScreeningIsDone() = runBlocking {
        ready(done = false)
        assertTrue(repo().createAuto(gym) is CreateResult.Blocked)
        assertEquals(null, db.programDao().active())
        assertTrue(db.programDao().allPrograms().isEmpty())
    }

    @Test fun regeneratingReplacesTheAutoProgramOnly() = runBlocking {
        ready()
        repo().createAuto(gym)
        repo().createAuto(gym.copy(days = 2))
        val all = db.programDao().allPrograms()
        assertEquals(1, all.size)
        assertEquals(2, repo().active()!!.days.size)
    }

    @Test fun L5_anOwnProgramIsNeverOverwrittenByTheGenerator() = runBlocking {
        ready()
        ownProgram("mine", active = true)
        val created = repo().createAuto(gym) as CreateResult.Created
        assertEquals(false, created.active)
        assertEquals("mine", db.programDao().active()!!.id)
        // Running it again still leaves the own row alone, and keeps one auto row.
        repo().createAuto(gym)
        val rows = db.programDao().allPrograms()
        assertEquals(1, rows.count { it.source == "own" })
        assertEquals(1, rows.count { it.source == "auto" })
        repo().activate(rows.first { it.source == "auto" }.id)
        assertEquals("auto", db.programDao().active()!!.source)
        assertEquals(1, db.programDao().allPrograms().count { it.source == "own" })
    }

    @Test fun startingAChosenDayUsesThatDayNotTheRotation() = runBlocking {
        ready()
        repo().createAuto(gym)
        val days = repo().active()!!.days
        val plan = logger().nextPlannedDay(days.last().id)!!
        assertEquals(days.last().id, plan.programDayId)
        assertEquals(days.first().id, logger().nextPlannedDay()!!.programDayId)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
