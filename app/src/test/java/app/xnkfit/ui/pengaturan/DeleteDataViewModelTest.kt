package app.xnkfit.ui.pengaturan

import android.os.Looper
import app.xnkfit.data.privacy.DataWiper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DeleteDataViewModelTest {
    private class FakeWiper(var fail: Boolean = false) : DataWiper {
        var calls = 0
        override suspend fun wipe() { calls++; if (fail) error("disk") }
    }

    private fun settle(cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("timeout")
    }

    @Test fun wordMatchIgnoresCaseAndSpaces() {
        assertTrue(deleteWordMatches(" hapus ", "HAPUS"))
        assertFalse(deleteWordMatches("hapu", "HAPUS"))
        assertFalse(deleteWordMatches("", "HAPUS"))
    }

    @Test fun nothingIsDeletedWithoutTheTypedWord() {
        val w = FakeWiper(); var wiped = 0
        val vm = DeleteDataViewModel(w, { "HAPUS" }, { wiped++ })
        vm.open(); vm.type("hapus semua"); vm.confirm()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, w.calls); assertEquals(0, wiped)
    }

    @Test fun theTypedWordWipesOnceAndReportsDone() {
        val w = FakeWiper(); var wiped = 0
        val vm = DeleteDataViewModel(w, { "HAPUS" }, { wiped++ })
        vm.open(); vm.type("HAPUS"); vm.confirm(); vm.confirm()
        settle { wiped == 1 }
        assertEquals(1, w.calls)
    }

    @Test fun aFailedWipeKeepsTheDialogAndSaysSo() {
        val w = FakeWiper(fail = true); var wiped = 0
        val vm = DeleteDataViewModel(w, { "HAPUS" }, { wiped++ })
        vm.open(); vm.type("HAPUS"); vm.confirm()
        settle { vm.ui.value.failed }
        assertEquals(0, wiped)
        assertTrue(vm.ui.value.open)
        assertFalse(vm.ui.value.busy)
    }

    @Test fun cancelClosesAndForgetsTheTypedWord() {
        val vm = DeleteDataViewModel(FakeWiper(), { "HAPUS" }, {})
        vm.open(); vm.type("HAP"); vm.cancel()
        assertEquals(DeleteUi(), vm.ui.value)
    }
}
