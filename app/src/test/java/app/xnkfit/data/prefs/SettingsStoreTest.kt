package app.xnkfit.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(): DataStoreSettingsStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s${System.nanoTime()}.preferences_pb") })
    }

    @Test fun defaultsFollowTheMockup() = runBlocking {
        val s = store().settings.first()
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(setOf(ReminderGroup.LATIHAN, ReminderGroup.MAKAN), s.reminders)
        assertTrue(s.seenCards.isEmpty())
    }

    @Test fun themeAndRemindersAreKept() = runBlocking {
        val st = store()
        st.setTheme(ThemeMode.DARK)
        st.setReminder(ReminderGroup.TIMBANG, true)
        st.setReminder(ReminderGroup.LATIHAN, false)
        val s = st.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(setOf(ReminderGroup.MAKAN, ReminderGroup.TIMBANG), s.reminders)
    }

    @Test fun allRemindersCanBeOff() = runBlocking {
        val st = store()
        st.setReminder(ReminderGroup.LATIHAN, false)
        st.setReminder(ReminderGroup.MAKAN, false)
        assertTrue(st.settings.first().reminders.isEmpty())
    }

    @Test fun welcomeCardsAreShownOnceAndCanBeReset() = runBlocking {
        val st = store()
        st.markCardSeen(WelcomeTab.FOOD)
        st.markCardSeen(WelcomeTab.FOOD)
        assertEquals(setOf(WelcomeTab.FOOD), st.settings.first().seenCards)
        st.resetCards()
        assertTrue(st.settings.first().seenCards.isEmpty())
    }
}
