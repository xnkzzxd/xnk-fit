package app.xnkfit.domain.rules

// Reminders and tone (Rules.md §8, PRD R-1, R-2, PR-4). Scheduling itself is B-15.

const val R_REM_MAX = 3

// R_REM_HOURS: default times (hour, minute). Latihan on scheduled days; Timbang on Sunday.
const val R_REM_HOURS_LATIHAN_H = 17
const val R_REM_HOURS_PAGI_H = 7
const val R_REM_HOURS_SIANG_H = 12
const val R_REM_HOURS_MALAM_H = 18
const val R_REM_HOURS_TIMBANG_H = 7
const val R_REM_HOURS_MINUTE = 0
const val R_REM_HOURS_TIMBANG_ISO_DAY = 7 // ISO-8601 day of week: 7 = Sunday

enum class ReminderKind(val hour: Int) {
    LATIHAN(R_REM_HOURS_LATIHAN_H),
    MAKAN_PAGI(R_REM_HOURS_PAGI_H),
    MAKAN_SIANG(R_REM_HOURS_SIANG_H),
    MAKAN_MALAM(R_REM_HOURS_MALAM_H),
    TIMBANG(R_REM_HOURS_TIMBANG_H),
}

/** R_REM_SKIP and R_REM_MAX: send only when the action is not done yet and fewer than 3 were sent today. */
fun shouldSendReminder(actionDoneToday: Boolean, sentToday: Int): Boolean =
    !actionDoneToday && sentToday < R_REM_MAX

// R_TONE: neutral words, no "kamu" or "Kak" (PRD PR-4). Whole words, any case.
val R_TONE_BANNED_WORDS: List<String> = listOf("kamu", "kak")

// R_TONE_WORDS: no shaming words anywhere; no commands in reminders.
val R_TONE_WORDS_SHAMING: List<String> = listOf("gagal")
val R_TONE_WORDS_REMINDER_COMMANDS: List<String> = listOf("harus", "jangan")

private val WORD_SPLIT = Regex("[^\\p{L}]+")

/** R_TONE / R_TONE_WORDS: words in [text] that break the tone rules. Empty means the text is fine. */
fun toneProblems(text: String, isReminder: Boolean): List<String> {
    val words = text.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() }.toSet()
    val banned = R_TONE_BANNED_WORDS + R_TONE_WORDS_SHAMING +
        if (isReminder) R_TONE_WORDS_REMINDER_COMMANDS else emptyList()
    return banned.filter { it in words }
}
