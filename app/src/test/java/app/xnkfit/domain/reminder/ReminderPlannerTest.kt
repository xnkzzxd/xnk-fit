package app.xnkfit.domain.reminder

import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.rules.ReminderKind
import app.xnkfit.domain.rules.toneProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** B-15: scheduler logic with a fake clock (a plain LocalDateTime) and rotation text tone. */
class ReminderPlannerTest {
    private val wed = LocalDate.of(2026, 11, 18) // Wednesday
    private val all = ReminderKind.entries.toSet()

    private fun facts(
        at: String = "17:00",
        date: LocalDate = wed,
        enabled: Set<ReminderKind> = all,
        sent: Set<ReminderKind> = emptySet(),
        program: Boolean = true,
        doneToday: Boolean = false,
        lastWorkout: LocalDate? = date.minusDays(3),
        slots: Set<ReminderKind> = emptySet(),
        lastWeight: LocalDate? = null,
        lastActivity: LocalDate? = date.minusDays(1),
        back: Int = 0,
    ) = ReminderFacts(
        now = LocalDateTime.of(date, java.time.LocalTime.parse(at)), enabled = enabled, sentToday = sent, hasProgram = program,
        workoutFinishedToday = doneToday, lastWorkoutDay = lastWorkout, foodSlotsLoggedToday = slots, lastWeightDay = lastWeight,
        lastActivityDay = lastActivity, backSentSinceActivity = back,
    )

    @Test fun picksKindAtItsHour() {
        assertEquals(ReminderKind.MAKAN_PAGI, nextReminder(facts(at = "07:15")))
        assertEquals(ReminderKind.MAKAN_SIANG, nextReminder(facts(at = "12:00", sent = setOf(ReminderKind.MAKAN_PAGI))))
        assertEquals(ReminderKind.LATIHAN, nextReminder(facts(at = "17:30")))
        assertNull(nextReminder(facts(at = "10:00"))) // nothing due
    }

    @Test fun R_REM_MAX_threeTotalPerDay() {
        val three = setOf(ReminderKind.MAKAN_PAGI, ReminderKind.MAKAN_SIANG, ReminderKind.LATIHAN)
        assertNull(nextReminder(facts(at = "18:00", sent = three)))
        assertEquals(ReminderKind.MAKAN_MALAM, nextReminder(facts(at = "18:00", sent = three - ReminderKind.LATIHAN, enabled = all - ReminderKind.LATIHAN)))
    }

    @Test fun neverTwoOfTheSameKindInADay() {
        assertNull(nextReminder(facts(at = "17:10", enabled = setOf(ReminderKind.LATIHAN), sent = setOf(ReminderKind.LATIHAN))))
    }

    @Test fun R_REM_LATE_dropsAfterThreeHours() {
        assertNull(nextReminder(facts(at = "10:01", enabled = setOf(ReminderKind.MAKAN_PAGI))))
        assertEquals(ReminderKind.MAKAN_PAGI, nextReminder(facts(at = "09:59", enabled = setOf(ReminderKind.MAKAN_PAGI))))
    }

    @Test fun R_REM_QUIET_hours() {
        assertTrue(inQuietHours(java.time.LocalTime.of(21, 0)))
        assertTrue(inQuietHours(java.time.LocalTime.of(6, 59)))
        assertNull(nextReminder(facts(at = "21:30", enabled = setOf(ReminderKind.MAKAN_MALAM))))
        assertEquals(ReminderKind.MAKAN_MALAM, nextReminder(facts(at = "20:30", enabled = setOf(ReminderKind.MAKAN_MALAM))))
    }

    @Test fun switchesAreHonoured() {
        assertNull(nextReminder(facts(at = "17:00", enabled = setOf(ReminderKind.MAKAN_PAGI))))
        assertNull(nextReminder(facts(at = "17:00", enabled = emptySet())))
    }

    @Test fun R_REM_SKIP_whenAlreadyDone() {
        assertNull(nextReminder(facts(at = "17:00", enabled = setOf(ReminderKind.LATIHAN), doneToday = true)))
        assertNull(nextReminder(facts(at = "12:00", enabled = setOf(ReminderKind.MAKAN_SIANG), slots = setOf(ReminderKind.MAKAN_SIANG))))
    }

    @Test fun R_REM_GAP_latihanNeedsProgramAndNotARestDay() {
        val only = setOf(ReminderKind.LATIHAN)
        assertNull(nextReminder(facts(enabled = only, program = false)))
        assertNull(nextReminder(facts(enabled = only, lastWorkout = wed.minusDays(1)))) // rest day
        assertEquals(ReminderKind.LATIHAN, nextReminder(facts(enabled = only, lastWorkout = wed.minusDays(2))))
        assertEquals(ReminderKind.LATIHAN, nextReminder(facts(enabled = only, lastWorkout = null))) // never trained
    }

    @Test fun R_REM_WEIGH_SKIP_onlySundayAndNotRightAfterWeighing() {
        val sun = LocalDate.of(2026, 11, 22)
        val only = setOf(ReminderKind.TIMBANG)
        assertEquals(ReminderKind.TIMBANG, nextReminder(facts(at = "07:00", date = sun, enabled = only)))
        assertNull(nextReminder(facts(at = "07:00", date = wed, enabled = only)))
        assertNull(nextReminder(facts(at = "07:00", date = sun, enabled = only, lastWeight = sun.minusDays(1))))
    }

    @Test fun comeBackAfterAbsenceReplacesLatihan() {
        val gone = facts(lastWorkout = wed.minusDays(9), lastActivity = wed.minusDays(8))
        assertEquals(ReminderKind.KEMBALI, nextReminder(gone))
        assertNull(nextReminder(gone.copy(sentToday = setOf(ReminderKind.KEMBALI))))
    }

    @Test fun R_REM_BACK_isRareAndNeedsData() {
        val gone = facts(lastWorkout = wed.minusDays(30), lastActivity = wed.minusDays(30))
        assertEquals(ReminderKind.KEMBALI, nextReminder(gone.copy(enabled = setOf(ReminderKind.KEMBALI), backSentSinceActivity = 1)))
        assertNull(nextReminder(gone.copy(enabled = setOf(ReminderKind.KEMBALI), backSentSinceActivity = 2))) // at most two
        assertNull(nextReminder(gone.copy(enabled = setOf(ReminderKind.KEMBALI), lastActivityDay = null))) // no data at all
        assertNull(nextReminder(facts(enabled = setOf(ReminderKind.KEMBALI), lastActivity = wed.minusDays(6))))
        // second invitation waits another week
        assertNull(nextReminder(facts(enabled = setOf(ReminderKind.KEMBALI), lastActivity = wed.minusDays(10), back = 1)))
    }

    @Test fun R_REM_ROTATE_neverRepeatsTwoDaysInARow() {
        for (kind in ReminderKind.entries) for (n in 2..8) {
            var day = LocalDate.of(2026, 1, 1)
            repeat(400) {
                assertNotEquals(rotationIndex(kind, day, n), rotationIndex(kind, day.plusDays(1), n))
                day = day.plusDays(1)
            }
        }
        assertEquals(rotationIndex(ReminderKind.LATIHAN, wed, 5), rotationIndex(ReminderKind.LATIHAN, wed, 5)) // deterministic
    }

    // ---- tone of the texts (R_TONE, R_TONE_WORDS, Content §10) ----

    private val arrays = Regex("<string-array name=\"(rem_[a-z_]+)\">(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL)
        .findAll(RepoFiles.text("app/src/main/res/values/strings_reminder.xml"))
        .associate { m -> m.groupValues[1] to Regex("<item>(.*?)</item>").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList() }

    @Test fun R_REM_ROTATE_everyKindHasAtLeastFiveVariants() {
        assertEquals(setOf("rem_latihan", "rem_kembali", "rem_timbang", "rem_makan_pagi", "rem_makan_siang", "rem_makan_malam"), arrays.keys)
        arrays.forEach { (name, v) -> assertTrue("$name has ${v.size}", v.size >= 5 && v.toSet().size == v.size) }
    }

    private val blameWords = listOf(
        "berat", "kalori", "kkal", "kg", "gemuk", "kurus", "lemak", "diet", "malas", "terlambat", "bolos", "ketinggalan", "sehat",
        "menurunkan", "membakar", "wajib", "harus", "jangan", "gagal", "kamu", "kak", "anda", "bapak", "ibu", "kantor", "anak", "keluarga",
    )

    @Test fun textsKeepTheToneRules() {
        arrays.forEach { (name, v) ->
            v.forEach { t ->
                assertEquals("$name: $t", emptyList<String>(), toneProblems(t, isReminder = true))
                val words = t.lowercase().split(Regex("[^\\p{L}]+")).toSet()
                assertEquals("$name: $t", emptySet<String>(), blameWords.toSet().intersect(words))
                assertTrue("$name too long: $t", t.length <= 110)
                val emoji = t.codePoints().filter { it > 0x2000 && !Character.isLetterOrDigit(it) }.count()
                assertTrue("$name: $t", emoji <= 1)
                assertTrue("$name: no exclamation pile-up: $t", !t.contains("!!"))
            }
        }
    }
}
