package app.xnkfit.domain.rules

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules.md §8. */
class ReminderRulesTest {
    @Test
    fun R_REM_MAX_threePerDay() {
        assertEquals(3, R_REM_MAX)
        assertTrue(shouldSendReminder(actionDoneToday = false, sentToday = 2))
        assertFalse(shouldSendReminder(actionDoneToday = false, sentToday = 3))
    }

    @Test
    fun R_REM_SKIP_whenDoneToday() {
        assertFalse(shouldSendReminder(actionDoneToday = true, sentToday = 0))
    }

    @Test
    fun R_REM_HOURS_defaults() {
        assertEquals(17, ReminderKind.LATIHAN.hour)
        assertEquals(7, ReminderKind.MAKAN_PAGI.hour)
        assertEquals(12, ReminderKind.MAKAN_SIANG.hour)
        assertEquals(18, ReminderKind.MAKAN_MALAM.hour)
        assertEquals(7, ReminderKind.TIMBANG.hour)
        assertEquals(0, R_REM_HOURS_MINUTE)
        assertEquals(java.time.DayOfWeek.SUNDAY.value, R_REM_HOURS_TIMBANG_ISO_DAY)
    }

    private fun appStrings(): List<String> =
        Regex("<string[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(RepoFiles.text("app/src/main/res/values/strings.xml"))
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun R_TONE_neutralWords() {
        assertEquals(listOf("kamu"), toneProblems("Ayo, kamu bisa", isReminder = false))
        assertEquals(listOf("kak"), toneProblems("Halo Kak!", isReminder = false))
        assertTrue(toneProblems("Kakao panas", isReminder = false).isEmpty()) // whole words only
        appStrings().forEach { assertEquals(it, emptyList<String>(), toneProblems(it, isReminder = false)) }
    }

    @Test
    fun R_TONE_WORDS_noShamingNoCommandsInReminders() {
        assertEquals(listOf("gagal"), toneProblems("Target gagal", isReminder = false))
        assertEquals(listOf("harus"), toneProblems("Latihan harus hari ini", isReminder = true))
        assertTrue(toneProblems("Latihan harus hari ini", isReminder = false).isEmpty())
        assertEquals(listOf("jangan"), toneProblems("Jangan lupa makan", isReminder = true))
        // Every UI string is checked as strictly as a reminder.
        appStrings().forEach { assertEquals(it, emptyList<String>(), toneProblems(it, isReminder = true)) }
    }
}
