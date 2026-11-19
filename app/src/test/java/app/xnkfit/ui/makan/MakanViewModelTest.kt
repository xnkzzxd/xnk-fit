package app.xnkfit.ui.makan

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.xnkfit.data.content.ContentTestSupport as T
import app.xnkfit.data.content.BundledContentLoader
import app.xnkfit.data.content.ContentStore
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.food.FoodPrefsStore
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.data.food.ShoppingTickStore
import app.xnkfit.data.food.RoomTargetSource
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.food.MealSlot
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

/** Makan screens' logic on the JVM with real content and a real (Robolectric) Room. No Compose UI test: see Decisions.md. */
@RunWith(RobolectricTestRunner::class)
class MakanViewModelTest {
    private lateinit var db: XnkDatabase
    private var seq = 0
    private val monday = LocalDate.of(2026, 11, 16)

    @Before fun open() {
        db = T.db()
        runBlocking { BundledContentLoader(ContentStore(db) { 1L }, T.repoSource) { 1L }.loadIfNeeded() }
    }

    @After fun close() { db.close() }

    private fun store() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { File.createTempFile("makan", ".preferences_pb").also { it.delete() } }

    private fun vm(day: LocalDate = monday.plusDays(2)) = MakanViewModel(
        repo = FoodRepository(db, { 1_000L + seq }, { "id${seq++}" }),
        targets = RoomTargetSource(db) { 2026 },
        prefsStore = FoodPrefsStore(store()),
        ticksStore = ShoppingTickStore(store()),
        today = { day },
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

    private fun profile(ed: String = "tidak") = runBlocking {
        db.profileDao().upsert(
            ProfileEntity(createdAt = 0, updatedAt = 0, sex = "perempuan", birthYear = 1995, heightCm = 160.0, goal = "jaga", interest = null,
                nickname = null, healthRisk = false, screeningDone = true, pregnant = false, eatingHistory = ed),
        )
        db.profileDao().upsertMeasure(MeasureEntity("w", 0, 0, "weight", 65.0, "kg", 0))
    }

    @Test fun startsWithTheWeekOfTodayAndContentLoaded() {
        val v = vm()
        await("load") { v.state.value.loaded }
        val s = v.state.value
        assertEquals("2026-11-18", s.day)
        assertEquals("2026-11-16", s.weekStart)
        assertEquals(3, s.todayIndex)
        assertEquals(72, s.packages.size)
        assertFalse(s.hasTarget)
        assertTrue(s.summary is DaySummary.Numbers)
    }

    @Test fun searchPickPortionAndLogUpdateTheSummary() {
        profile()
        val v = vm()
        await("load") { v.state.value.hasTarget }
        v.startPick(MealSlot.SIANG)
        v.setQuery("nasi putih")
        await("results") { v.state.value.pick.results.isNotEmpty() }
        val food = v.state.value.pick.results.first()
        v.chooseFood(food)
        val p1 = v.state.value.pick.portion!!
        v.stepPortion(true)
        assertTrue(v.state.value.pick.portion!!.grams > p1.grams) // half a unit more, grams follow
        v.logPortion()
        await("logged") { v.state.value.lines.size == 1 && v.state.value.route == MakanRoute.HOME }
        val s = v.state.value
        assertEquals(MealSlot.SIANG, s.lines.single().slot)
        val sum = s.summary as DaySummary.Numbers
        assertTrue(sum.eatenKcal > 0)
        assertEquals(s.lines.single().kcal, sum.eatenKcal)
        v.deleteLog(s.lines.single().id)
        await("deleted") { v.state.value.lines.isEmpty() }
    }

    @Test fun quickAddNeedsAPositiveNumber() {
        val v = vm()
        await("load") { v.state.value.loaded }
        v.logQuick("abc")
        await("error") { v.state.value.pick.error }
        v.logQuick("250")
        await("quick") { v.state.value.lines.size == 1 }
        assertTrue(v.state.value.lines.single().quick)
    }

    @Test fun R_GATE_ED_noNumbersAnywhereInTheState() {
        profile(ed = "ya")
        val v = vm()
        await("load") { v.state.value.numbersHidden }
        v.logQuick("400")
        await("quick") { v.state.value.lines.size == 1 }
        val s = v.state.value
        assertTrue(s.summary is DaySummary.Ticks)
        assertNull(s.lines.single().kcal)
        v.autoFill()
        await("plan") { v.state.value.plan.isNotEmpty() }
        assertTrue(v.state.value.numbersHidden) // the screens hide dayKcal and package kcal on this flag
        assertNull(v.state.value.proteinRange)
        assertTrue(v.state.value.dayProtein.isEmpty())
    }

    @Test fun plannerAimsAtTheProteinRangeAndShowsTheDayProtein() {
        profile()
        val v = vm()
        await("load") { v.state.value.hasTarget }
        assertEquals(91..130, v.state.value.proteinRange) // R_PROT 1.4–2.0 g/kg of 65 kg, from domain.rules
        v.autoFill()
        await("plan") { v.state.value.plan.size == 28 }
        val s = v.state.value
        assertEquals(7, s.dayProtein.size)
        assertTrue(s.dayProtein.values.all { it > 0 })
    }

    @Test fun autoFillBuildsAWeekAndManualChoiceAndLockSurviveARefill() {
        profile()
        val v = vm()
        await("load") { v.state.value.hasTarget }
        v.autoFill()
        await("plan") { v.state.value.plan.size == 28 }
        val s = v.state.value
        assertEquals(3, s.options[3 to MealSlot.SIANG]!!.size)
        val other = s.options[3 to MealSlot.SIANG]!!.first { it.id != s.plan.first { c -> c.day == 3 && c.slot == MealSlot.SIANG }.packageId }
        v.choose(3, MealSlot.SIANG, other.id)
        await("chosen") { v.state.value.plan.first { it.day == 3 && it.slot == MealSlot.SIANG }.packageId == other.id }
        v.toggleLock(3, MealSlot.SIANG)
        await("locked") { v.state.value.plan.first { it.day == 3 && it.slot == MealSlot.SIANG }.locked }
        v.autoFill()
        await("refilled") { v.state.value.plan.size == 28 }
        assertEquals(other.id, v.state.value.plan.first { it.day == 3 && it.slot == MealSlot.SIANG }.packageId)
        v.cookOnce(1, MealSlot.MALAM)
        await("cook once") { v.state.value.plan.first { it.day == 2 && it.slot == MealSlot.MALAM }.cookOnce }
    }

    @Test fun shoppingListStartsToday_andTicksStick() {
        profile()
        val v = vm()
        await("load") { v.state.value.hasTarget }
        v.autoFill()
        await("shopping") { v.state.value.shopping.isNotEmpty() }
        val first = v.state.value.shopping.first()
        v.tick(first.foodId, true)
        await("tick") { first.foodId in v.state.value.ticks }
        v.tick(first.foodId, false)
        await("untick") { first.foodId !in v.state.value.ticks }
    }

    @Test fun preferencesSaveAndFeedThePlanner() {
        profile()
        val v = vm()
        await("load") { v.state.value.hasTarget }
        v.toggleAllergen(Allergen.EGG)
        v.toggleAllergen(Allergen.MILK)
        v.savePrefs()
        await("saved") { v.state.value.prefsSaved }
        v.autoFill()
        await("plan") { v.state.value.plan.isNotEmpty() }
        val s = v.state.value
        assertTrue(s.plan.none { c -> s.packages[c.packageId]!!.allergens.any { it == Allergen.EGG || it == Allergen.MILK } })
    }

    @Test fun backWalksUpOneLevelAtATime() {
        val v = vm()
        await("load") { v.state.value.loaded }
        assertFalse(v.back())
        v.startPick(MealSlot.PAGI)
        v.openQuick(true)
        assertTrue(v.back()) // closes the quick form
        assertEquals(MakanRoute.PICK, v.state.value.route)
        assertTrue(v.back())
        assertEquals(MakanRoute.HOME, v.state.value.route)
        assertNotNull(v.state.value.pick)
    }
}
