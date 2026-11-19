package app.xnkfit.domain.reminder

import app.xnkfit.domain.rules.R_REM_HOURS_MINUTE
import app.xnkfit.domain.rules.R_REM_HOURS_TIMBANG_ISO_DAY
import app.xnkfit.domain.rules.R_REM_MAX
import app.xnkfit.domain.rules.ReminderKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// Reminder scheduler, pure (B-15, Rules §8). The Android side only reads the facts into [ReminderFacts], asks
// [nextReminder] and posts what comes back. Nothing here knows about the clock, the database or the system.

/** R_REM_QUIET: nothing is posted from 21:00 until 07:00 (👤, Decisions.md). */
const val R_REM_QUIET_START_H = 21
const val R_REM_QUIET_END_H = 7

/** R_REM_LATE: a reminder still goes out up to this many hours after its hour (the system may run the job late); later it is dropped for the day. 👤 */
const val R_REM_LATE_HOURS = 3

/** R_REM_GAP: Latihan is offered only when the last workout is at least this many days back (rest days stay quiet). 👤 */
const val R_REM_GAP_LATIHAN_DAYS = 2

/** R_REM_BACK: "kembali" after this many days without any record; at most [R_REM_BACK_MAX] times until the next record. 👤 */
const val R_REM_BACK_DAYS = 7
const val R_REM_BACK_MAX = 2

/** R_REM_WEIGH_SKIP: Timbang is skipped when a weight was saved within this many days. */
const val R_REM_WEIGH_SKIP_DAYS = 3

/** Every fact the decision needs. Dates are the phone's local dates. */
data class ReminderFacts(
    val now: LocalDateTime,
    /** Kinds the user switched on in Pengaturan (switch per group, expanded to kinds). */
    val enabled: Set<ReminderKind>,
    /** Kinds already posted today (their count is the daily total, R_REM_MAX). */
    val sentToday: Set<ReminderKind>,
    val hasProgram: Boolean,
    val workoutFinishedToday: Boolean,
    /** Local day of the last finished workout, or null. */
    val lastWorkoutDay: LocalDate?,
    /** Meal slots with at least one log today: pagi, siang, malam. */
    val foodSlotsLoggedToday: Set<ReminderKind>,
    val lastWeightDay: LocalDate?,
    /** Last day with any record (workout, food, measure); null when the phone holds no data at all. */
    val lastActivityDay: LocalDate?,
    /** "Kembali" posts since [lastActivityDay]. */
    val backSentSinceActivity: Int,
)

fun inQuietHours(time: LocalTime): Boolean = time.hour >= R_REM_QUIET_START_H || time.hour < R_REM_QUIET_END_H

/** The one reminder to post now, or null. Called every 15 minutes; posts at most one per call. */
fun nextReminder(f: ReminderFacts): ReminderKind? {
    if (inQuietHours(f.now.toLocalTime())) return null
    if (f.sentToday.size >= R_REM_MAX) return null
    val today = f.now.toLocalDate()
    return ReminderKind.entries
        .filter { it in f.enabled && it !in f.sentToday && isDue(it, f.now) && !isDone(it, f, today) }
        .minWithOrNull(compareBy({ dueMinutes(it) }, { it.ordinal }))
}

private fun dueMinutes(kind: ReminderKind): Int = kind.hour * 60 + R_REM_HOURS_MINUTE

/** From its hour until [R_REM_LATE_HOURS] later, and only on its day (Timbang: Sunday). */
private fun isDue(kind: ReminderKind, now: LocalDateTime): Boolean {
    if (kind == ReminderKind.TIMBANG && now.dayOfWeek.value != R_REM_HOURS_TIMBANG_ISO_DAY) return false
    val minutes = now.hour * 60 + now.minute
    val start = dueMinutes(kind)
    return minutes >= start && minutes < start + R_REM_LATE_HOURS * 60
}

/** R_REM_SKIP plus the "nothing to ask" cases: no program, no data, rest day. */
private fun isDone(kind: ReminderKind, f: ReminderFacts, today: LocalDate): Boolean = when (kind) {
    ReminderKind.LATIHAN -> !f.hasProgram || f.workoutFinishedToday || backDue(f, today) ||
        (f.lastWorkoutDay != null && ChronoUnit.DAYS.between(f.lastWorkoutDay, today) < R_REM_GAP_LATIHAN_DAYS)
    ReminderKind.MAKAN_PAGI, ReminderKind.MAKAN_SIANG, ReminderKind.MAKAN_MALAM -> kind in f.foodSlotsLoggedToday
    ReminderKind.TIMBANG -> f.lastWeightDay != null && ChronoUnit.DAYS.between(f.lastWeightDay, today) < R_REM_WEIGH_SKIP_DAYS
    ReminderKind.KEMBALI -> !backDue(f, today)
}

/** A user with no data at all is never called "back"; after [R_REM_BACK_MAX] invitations the app stays quiet. */
private fun backDue(f: ReminderFacts, today: LocalDate): Boolean {
    val last = f.lastActivityDay ?: return false
    val away = ChronoUnit.DAYS.between(last, today)
    return away >= R_REM_BACK_DAYS * (f.backSentSinceActivity + 1) && f.backSentSinceActivity < R_REM_BACK_MAX
}

/**
 * Rotation index for [kind] on [day] among [count] variants. Same day, same text; the next day always a different
 * one (the index moves by one a day, offset per kind), so a text never repeats two days in a row (Design §8).
 */
fun rotationIndex(kind: ReminderKind, day: LocalDate, count: Int): Int {
    require(count >= 2) { "at least two variants" }
    return Math.floorMod(day.toEpochDay() + kind.ordinal * 3L, count.toLong()).toInt()
}
