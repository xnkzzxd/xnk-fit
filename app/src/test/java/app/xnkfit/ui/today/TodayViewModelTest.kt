package app.xnkfit.ui.today

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.xnkfit.data.db.ActivityEntity
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.ProgramDayEntity
import app.xnkfit.data.db.ProgramEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.data.food.RoomTargetSource
import app.xnkfit.data.food.TargetBasisStore
import app.xnkfit.data.progress.ProgressRepository
import app.xnkfit.data.today.TodayRepository
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.quickNutrients
import app.xnkfit.domain.today.RecalcReason
import app.xnkfit.domain.today.TargetCard
import app.xnkfit.domain.today.TaskKind
import app.xnkfit.domain.today.WorkoutCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Hari ini on the JVM with a real (Robolectric) Room: gates, no-numbers mode, recalculation, empty states. */
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    private lateinit var db: XnkDatabase
    private lateinit var store: TargetBasisStore
    private var seq = 0
    private val wed = LocalDate.of(2026, 11, 18)
    private val zone = ZoneOffset.UTC

    @Before fun open() {
        db = app.xnkfit.data.content.ContentTestSupport.db()
        store = basis()
    }

    @After fun close() { db.close() }

    private fun basis() = TargetBasisStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { File.createTempFile("basis", ".preferences_pb").also { it.delete() } },
    )

    private fun ms(d: LocalDate, hour: Int = 8) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun vm(day: LocalDate = wed, store: TargetBasisStore = this.store) = TodayViewModel(
        repo = TodayRepository(db, { zone }, { ms(day, 12) }),
        food = FoodRepository(db, { 1_000L + seq }, { "id${seq++}" }),
        progress = ProgressRepository(db, { zone }, { day }, { ms(day, 12) }),
        basisStore = store, today = { day }, currentYear = { 2026 }, zone = { zone },
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

    private fun profile(
        goal: String = "turun", ed: String = "tidak", risk: Boolean = false, preg: Boolean = false,
        interest: String = "keduanya", done: Boolean = true, year: Int = 1995, sex: String = "perempuan",
    ) = runBlocking {
        db.profileDao().upsert(
            ProfileEntity(createdAt = 0, updatedAt = 0, sex = sex, birthYear = year, heightCm = 165.0, goal = goal, interest = interest,
                nickname = "Dita", healthRisk = risk, screeningDone = done, pregnant = preg, eatingHistory = ed),
        )
    }

    private fun weight(kg: Double, day: LocalDate, id: String = "w$day") = runBlocking {
        db.profileDao().upsertMeasure(MeasureEntity(id, 0, 0, "weight", kg, "kg", ms(day)))
    }

    private fun load(v: TodayViewModel): TodayState { await("load") { v.state.value.loaded }; return v.state.value }

    @Test fun noProfileShowsTheEmptyTargetAndNoNumber() {
        val s = load(vm())
        assertEquals(TargetCard.NeedsScreening, s.target)
        assertNull(s.recalc)
    }

    @Test fun screeningNotDoneGivesNoTarget() {
        profile(done = false); weight(70.0, wed.minusDays(10))
        assertEquals(TargetCard.NeedsScreening, load(vm()).target)
    }

    @Test fun readyProfileShowsNumbersAndMatchesTheFoodScreensTarget() {
        profile(); weight(70.0, wed.minusDays(10))
        val s = load(vm())
        val card = s.target as TargetCard.Numbers
        val foodSide = runBlocking { RoomTargetSource(db, store) { 2026 }.current() }!!
        assertEquals(foodSide.targets.kcal, card.kcal) // one calculation for both screens
        assertEquals(foodSide.targets.proteinG, card.proteinG)
        val sum = s.food as DaySummary.Numbers
        assertEquals(card.kcal, sum.targets!!.kcal)
        assertEquals(0, sum.eatenKcal)
        assertEquals("Dita", s.nickname)
    }

    @Test fun R_GATE_ED_showsNoCaloriesAnywhere() {
        profile(ed = "ya"); weight(70.0, wed.minusDays(10))
        runBlocking { FoodRepository(db, { 5L }, { "x1" }).logQuick(wed.toString(), MealSlot.PAGI, 400.0) }
        val s = load(vm())
        assertTrue(s.target is TargetCard.NoNumbers)
        assertEquals(DaySummary.Ticks(1, MealSlot.entries.size), s.food)
        assertNull(s.recalc)
    }

    @Test fun gatesThatForbidADeficitNeverShowOne() {
        profile(goal = "turun", preg = true); weight(70.0, wed.minusDays(10))
        val c = load(vm()).target as TargetCard.Numbers
        assertTrue(c.goal != app.xnkfit.domain.rules.Goal.TURUN)
    }

    @Test fun recalcAsksAtThreeKiloAndAcceptMovesTheTarget() {
        profile(goal = "jaga"); weight(70.0, wed.minusDays(20))
        val v = vm()
        val before = (load(v).target as TargetCard.Numbers).kcal
        assertNull(v.state.value.recalc)

        weight(67.0, wed.minusDays(1))
        v.refresh()
        await("prompt") { v.state.value.recalc != null }
        assertEquals(RecalcReason.WEIGHT_DOWN, v.state.value.recalc!!.reason)
        assertEquals((v.state.value.target as TargetCard.Numbers).kcal, before) // nothing changes by itself

        v.acceptRecalc()
        await("accepted") { v.state.value.recalc == null }
        val after = (v.state.value.target as TargetCard.Numbers).kcal
        assertTrue(after < before)
        val food = runBlocking { RoomTargetSource(db, store) { 2026 }.current() }!!
        assertEquals(after, food.targets.kcal)
    }

    @Test fun keepingTheTargetDoesNotAskAgainUntilItChangesAgain() {
        profile(goal = "jaga"); weight(70.0, wed.minusDays(20))
        val v = vm(); val before = (load(v).target as TargetCard.Numbers).kcal
        weight(66.0, wed.minusDays(1)); v.refresh()
        await("prompt") { v.state.value.recalc != null }
        v.keepTarget()
        await("kept") { v.state.value.recalc == null }
        assertEquals(before, (v.state.value.target as TargetCard.Numbers).kcal)
        v.refresh(); Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle()
        assertNull(v.state.value.recalc)
        weight(62.5, wed); v.refresh() // 3.5 kg more
        await("again") { v.state.value.recalc != null }
    }

    @Test fun goalChangeAsksToRecalculate() {
        profile(goal = "jaga"); weight(70.0, wed.minusDays(10))
        val v = vm(); load(v)
        profile(goal = "otot"); v.refresh()
        await("prompt") { v.state.value.recalc?.reason == RecalcReason.GOAL_CHANGED }
        v.acceptRecalc()
        await("accepted") { v.state.value.recalc == null }
        assertEquals(app.xnkfit.domain.rules.Goal.NAIK, (v.state.value.target as TargetCard.Numbers).goal)
    }

    @Test fun fastLossSuggestsAPaceRaiseAndTheUserDecides() {
        profile(goal = "turun"); val d0 = wed.minusDays(14)
        weight(80.0, d0); weight(79.0, d0.plusDays(7)); weight(78.0, wed)
        val v = vm(); val before = (load(v).target as TargetCard.Numbers).kcal
        assertEquals(RecalcReason.PACE, v.state.value.recalc!!.reason)
        v.acceptRecalc()
        await("raised") { v.state.value.recalc == null }
        val c = v.state.value.target as TargetCard.Numbers
        assertEquals(before + 100, c.kcal)
        assertTrue(c.raisedByPace)
    }

    @Test fun noProgramAndTheFirstDayAreHandled() {
        profile(); weight(70.0, wed.minusDays(10))
        assertEquals(WorkoutCard.NoProgram, load(vm()).workout)
        runBlocking {
            db.programDao().insertProgram(ProgramEntity("p", 0, 0, "auto", "gym", 1, true))
            db.programDao().insertDays(listOf(ProgramDayEntity("d1", 0, 0, "p", 0, "Full Body A", 45), ProgramDayEntity("d2", 0, 0, "p", 1, "Full Body B", 45)))
        }
        val v = vm()
        val w = load(v).workout as WorkoutCard.Next
        assertEquals("Full Body A", w.day.name)
        assertTrue(v.state.value.tasks.any { it.kind == TaskKind.WORKOUT && !it.done })
    }

    @Test fun peopleWhoDoNotLiftSeeTheWeeklyActivityInstead() {
        profile(interest = "makan"); weight(70.0, wed.minusDays(10))
        runBlocking {
            db.workoutDao().upsertActivity(ActivityEntity("a1", 0, 0, "jalan", 45, "moderate", 100.0, ms(wed.minusDays(1))))
            db.workoutDao().upsertActivity(ActivityEntity("a2", 0, 0, "lari", 20, "vigorous", 200.0, ms(wed)))
            db.workoutDao().upsertActivity(ActivityEntity("a3", 0, 0, "jalan", 30, "moderate", 80.0, ms(wed.minusDays(9)))) // last week
        }
        val s = load(vm())
        assertNull(s.workout)
        assertEquals(85, s.activity.minutes)
        assertTrue(s.tasks.none { it.kind == TaskKind.WORKOUT })
    }

    @Test fun foodAndWeekStripShowWhatWasLogged() {
        profile(goal = "jaga"); weight(70.0, wed.minusDays(10))
        runBlocking { FoodRepository(db, { 5L }, { "x2" }).logQuick(wed.toString(), MealSlot.PAGI, 400.0) }
        val s = load(vm())
        assertEquals(400, (s.food as DaySummary.Numbers).eatenKcal)
        assertTrue(s.tasks.single { it.kind == TaskKind.FOOD }.done)
        assertTrue(s.week[2]) // Wednesday
        assertFalse(s.week[0])
        assertNotNull(s.day)
    }
}
