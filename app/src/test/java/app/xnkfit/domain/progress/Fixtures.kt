package app.xnkfit.domain.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Fixture helpers: UTC, so a date is the same everywhere. 2026-11-16 is a Monday. */
internal object F {
    val zone: ZoneId = ZoneId.of("UTC")
    val monday: LocalDate = LocalDate.of(2026, 11, 16)
    fun clock(today: LocalDate = monday.plusDays(6)) = Clock(zone, today)

    fun ms(d: LocalDate, hour: Int = 10): Long = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** The Monday [n] weeks after [monday] (n may be negative). */
    fun week(n: Int): LocalDate = monday.plusWeeks(n.toLong())

    fun workout(id: String, d: LocalDate) = FinishedWorkout(id, ms(d))
    fun food(d: LocalDate, slot: String = "pagi") = FoodLogRow(d.toString(), slot)
    fun act(d: LocalDate, minutes: Int, effort: String = "moderate") = ActivityRow(ms(d), minutes, effort)
    fun set(w: String, ex: String, kg: Double?, reps: Int, t: Long, form: String? = "rapi", kind: String = "normal", band: Int? = null) =
        DoneSet(w, ex, kind, kg, reps, band, form, t)

    /** Two active days in the week starting [week]: it counts toward the streak. */
    fun activeWeek(week: LocalDate): List<FoodLogRow> = listOf(food(week), food(week.plusDays(1)))

    fun dataWithWeeks(vararg weeks: LocalDate) = ProgressData(foodLogs = weeks.flatMap { activeWeek(it) })

    val sunday: DayOfWeek = DayOfWeek.SUNDAY
}
