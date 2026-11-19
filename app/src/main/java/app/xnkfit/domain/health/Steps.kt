package app.xnkfit.domain.health

import java.time.LocalDate

/** Steps of one calendar day as Health Connect reports them (L-15). */
data class DaySteps(val date: LocalDate, val steps: Long)

/** Can the phone give steps at all? Only [AVAILABLE] lets the card show anything. */
enum class HealthAvailability { AVAILABLE, MISSING, NEEDS_UPDATE }

/** What the steps card shows. Steps never add calories to the target and never give level points (L-15). */
sealed interface StepsCard {
    /** No card: Health Connect missing or needing an update, permission refused, or no data. */
    data object Hidden : StepsCard

    /** Health Connect is there but the user has not allowed steps yet: explain first, then the system dialog (O-3). */
    data object AskPermission : StepsCard

    data class Shown(val today: Long, val weeklyAverage: Long) : StepsCard
}

const val R_ACT_WEEK_DAYS = 7

/** Window the data source is asked for: the 7 days ending today. */
fun weekWindow(today: LocalDate): ClosedRange<LocalDate> = today.minusDays(R_ACT_WEEK_DAYS - 1L)..today

/**
 * Today's steps and the weekly average over the 7 days ending today.
 * The average counts days that have a recording (steps above 0), so a phone that was left at home for two days does not drag it down
 * and a new user with one day of data sees that day, not a seventh of it. No recording in the window → null (card hidden).
 */
fun summarize(days: List<DaySteps>, today: LocalDate): StepsCard.Shown? {
    val window = weekWindow(today)
    val inWindow = days.filter { it.date in window && it.steps > 0 }
    if (inWindow.isEmpty()) return null
    val todaySteps = inWindow.filter { it.date == today }.sumOf { it.steps }
    val perDay = inWindow.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.steps } }
    val avg = Math.round(perDay.values.sum().toDouble() / perDay.size)
    return StepsCard.Shown(todaySteps, avg)
}

/**
 * The single decision of the card. [granted] null = not asked or unknown; the caller reads it from the source.
 * Refusing in the system dialog hides the card; [AskPermission] is shown only until the user has answered once ([asked]).
 */
fun stepsCard(availability: HealthAvailability, granted: Boolean, asked: Boolean, days: List<DaySteps>, today: LocalDate): StepsCard = when {
    availability != HealthAvailability.AVAILABLE -> StepsCard.Hidden
    !granted -> if (asked) StepsCard.Hidden else StepsCard.AskPermission
    else -> summarize(days, today) ?: StepsCard.Hidden
}
