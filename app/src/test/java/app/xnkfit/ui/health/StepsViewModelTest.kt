package app.xnkfit.ui.health

import app.xnkfit.data.health.StepsAskedStore
import app.xnkfit.data.health.StepsSource
import app.xnkfit.domain.health.DaySteps
import app.xnkfit.domain.health.HealthAvailability
import app.xnkfit.domain.health.StepsCard
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StepsViewModelTest {
    private val today = LocalDate.of(2026, 11, 19)

    private class Fake(var availability: HealthAvailability, var granted: Boolean, var days: List<DaySteps>, var fail: Boolean = false) : StepsSource {
        override fun availability() = availability
        override suspend fun hasPermission() = granted
        override suspend fun read(from: LocalDate, to: LocalDate): List<DaySteps> { if (fail) error("boom"); return days }
    }

    private class Asked(var value: Boolean = false) : StepsAskedStore {
        override suspend fun asked() = value
        override suspend fun markAsked() { value = true }
    }

    private fun vm(f: Fake, a: Asked = Asked()) = StepsViewModel(f, a) { today }

    @Test fun missingHealthConnectStaysHidden() {
        val v = vm(Fake(HealthAvailability.MISSING, false, emptyList())); v.refresh()
        assertEquals(StepsCard.Hidden, v.card.value)
    }

    @Test fun explainsFirstThenShowsAfterGrant() {
        val f = Fake(HealthAvailability.AVAILABLE, false, listOf(DaySteps(today, 3000)))
        val v = vm(f); v.refresh()
        assertEquals(StepsCard.AskPermission, v.card.value)
        f.granted = true; v.onPermissionAnswered()
        assertEquals(StepsCard.Shown(3000, 3000), v.card.value)
    }

    @Test fun refusalHidesTheCardAndIsRemembered() {
        val a = Asked(); val v = vm(Fake(HealthAvailability.AVAILABLE, false, emptyList()), a); v.onPermissionAnswered()
        assertEquals(StepsCard.Hidden, v.card.value)
        assertEquals(true, a.value)
    }

    @Test fun readErrorHidesTheCard() {
        val v = vm(Fake(HealthAvailability.AVAILABLE, true, emptyList(), fail = true), Asked(true)); v.refresh()
        assertEquals(StepsCard.Hidden, v.card.value)
    }
}
