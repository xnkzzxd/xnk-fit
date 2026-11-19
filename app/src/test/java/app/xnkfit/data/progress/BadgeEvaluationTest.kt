package app.xnkfit.data.progress

import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.progress.ActivityRow
import app.xnkfit.domain.progress.BadgeDefinition
import app.xnkfit.domain.progress.BadgeMetric
import app.xnkfit.domain.progress.Clock
import app.xnkfit.domain.progress.DoneSet
import app.xnkfit.domain.progress.F
import app.xnkfit.domain.progress.PlanRow
import app.xnkfit.domain.progress.PointRow
import app.xnkfit.domain.progress.ProgressData
import app.xnkfit.domain.progress.badgesToAward
import app.xnkfit.domain.progress.countPersonalRecords
import app.xnkfit.domain.progress.levelStart
import app.xnkfit.domain.progress.metricValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each of the 18 badges in the real `content/badges.json`, at its threshold and one step below. */
@RunWith(RobolectricTestRunner::class)
class BadgeEvaluationTest {
    private val infos = BadgeCatalog.parse(RepoFiles.text("content/badges.json"))

    @Test fun theFileHasEighteenBadgesAllReadable() {
        assertEquals(18, infos.size)
        assertEquals(18, infos.map { it.code }.toSet().size)
        infos.forEach { assertNotNull("${it.code} has a condition this app can evaluate", it.definition) }
        // Messages and titles come from the file, in Indonesian and neutral (R_TONE).
        infos.forEach {
            assertTrue(it.title.isNotBlank() && it.message.isNotBlank())
            assertFalse("${it.code}: kamu", Regex("(?i)\\bkamu\\b|\\bkak\\b").containsMatchIn(it.message))
        }
    }

    /** Rows where [metric] is exactly [n] (a level is reached with [levelStart] points). */
    private fun at(metric: BadgeMetric, n: Int): Pair<ProgressData, Clock> {
        val clock = F.clock()
        if (n <= 0 && metric != BadgeMetric.LEVEL) return ProgressData() to clock
        val d = F.week(0)
        return when (metric) {
            BadgeMetric.FINISHED_WORKOUTS -> ProgressData(workouts = (0 until n).map { F.workout("w$it", d.plusDays(it.toLong())) }) to clock
            BadgeMetric.PERSONAL_RECORDS -> ProgressData(sets = (0..n).map { F.set("w$it", "SQ", 20.0 + it, 8, F.ms(d.plusDays(it.toLong()))) }) to clock
            BadgeMetric.STREAK_WEEKS -> ProgressData(foodLogs = (0 until n).flatMap { F.activeWeek(F.week(it)) }) to F.clock(F.week(n - 1).plusDays(6))
            BadgeMetric.ACTIVITY_MINUTES_WEEK -> ProgressData(activities = listOf(ActivityRow(F.ms(d), n, "moderate"))) to clock
            BadgeMetric.FOOD_DAYS -> ProgressData(foodLogs = (0 until n).map { F.food(d.plusDays(it.toLong())) }) to F.clock(d.plusDays(n.toLong()))
            BadgeMetric.LOGGED_WEEKS -> ProgressData(foodLogs = (0 until n).flatMap { w -> (0 until 5).map { F.food(F.week(w).plusDays(it.toLong())) } }) to F.clock(F.week(n))
            BadgeMetric.SCHEDULED_MEASURES -> ProgressData(points = List(n) { PointRow("measure", 2) }) to clock
            BadgeMetric.PHOTOS -> ProgressData(photoCount = n) to clock
            BadgeMetric.RETURN_AFTER_BREAK -> ProgressData(workouts = listOf(F.workout("a", d), F.workout("b", d.plusDays(n.toLong())))) to F.clock(d.plusDays(n.toLong()))
            BadgeMetric.LEVEL -> ProgressData(points = listOf(PointRow("workout", n))) to clock // n is points here
            BadgeMetric.PLANNED_DAYS -> ProgressData(plans = (1..n).map { PlanRow(d.toString(), it) }) to clock
        }
    }

    @Test fun everyBadgeIsEarnedAtItsThresholdAndNotBelow() {
        infos.forEach { info ->
            val def = info.definition!!
            val (reach, below) = if (def.metric == BadgeMetric.LEVEL) levelStart(def.atLeast) to levelStart(def.atLeast) - 1
            else def.atLeast to def.atLeast - 1
            val (dataAt, clockAt) = at(def.metric, reach)
            val (dataBelow, clockBelow) = at(def.metric, below)
            assertTrue("${info.code} at $reach", badgesToAward(listOf(def), dataAt, clockAt, emptySet()).isNotEmpty())
            assertTrue("${info.code} below ($below)", badgesToAward(listOf(def), dataBelow, clockBelow, emptySet()).isEmpty())
            assertEquals("${info.code} metric", def.atLeast, if (def.metric == BadgeMetric.LEVEL) metricValue(def.metric, dataAt, clockAt) else metricValue(def.metric, dataAt, clockAt))
        }
    }

    @Test fun anEarnedBadgeIsNeverAwardedTwiceAndNeverRemoved() {
        val def = infos.first { it.code == "WO_FIRST" }.definition!!
        val (data, clock) = at(BadgeMetric.FINISHED_WORKOUTS, 1)
        assertEquals(listOf(def), badgesToAward(listOf(def), data, clock, emptySet()))
        assertTrue(badgesToAward(listOf(def), data, clock, setOf("WO_FIRST")).isEmpty())
        // Data gone later (workout deleted): the rule still never awards again, and nothing here can remove a badge.
        assertTrue(badgesToAward(listOf(def), ProgressData(), clock, setOf("WO_FIRST")).isEmpty())
    }

    @Test fun badgesComeBackInFileOrder() {
        val defs = infos.map { it.definition!! }.shuffled()
        val data = ProgressData(workouts = (0 until 5).map { F.workout("w$it", F.week(0).plusDays(it.toLong())) })
        assertEquals(listOf("WO_FIRST", "WO_5"), badgesToAward(defs, data, F.clock(), emptySet()).map { it.code })
    }

    @Test fun anUnknownMetricIsReadAsNotEvaluableNeverEarned() {
        val json = """{"badges":[{"code":"X","order":1,"title_id":"t","message_id":"m","icon":"star",
            "condition":{"metric":"weightLost","op":">=","value":1}}]}"""
        assertEquals(null, BadgeCatalog.parse(json).single().definition)
    }

    // ---- metric definitions in badges.json ----

    @Test fun personalRecordsFollowTheWrittenDefinition() {
        val t = F.ms(F.week(0))
        fun s(w: String, kg: Double?, reps: Int, day: Int, form: String? = "rapi", kind: String = "normal", band: Int? = null) =
            F.set(w, "SQ", kg, reps, t + day * 86_400_000L, form, kind, band)
        // The first set is the baseline, not a record.
        assertEquals(0, countPersonalRecords(listOf(s("a", 20.0, 8, 0))))
        // More weight is a record; the same weight with more reps is a record; the same again is not.
        assertEquals(2, countPersonalRecords(listOf(s("a", 20.0, 8, 0), s("b", 22.5, 6, 1), s("c", 22.5, 7, 2), s("d", 22.5, 7, 3))))
        // At most one record per exercise per workout.
        assertEquals(1, countPersonalRecords(listOf(s("a", 20.0, 8, 0), s("b", 25.0, 8, 1), s("b", 30.0, 8, 1))))
        // A heavier set done unsure (form is neither rapi nor ragu) and drop sets do not count.
        assertEquals(0, countPersonalRecords(listOf(s("a", 20.0, 8, 0), s("b", 30.0, 8, 1, form = null), s("c", 30.0, 8, 2, kind = "drop"))))
        assertEquals(1, countPersonalRecords(listOf(s("a", 20.0, 8, 0), s("b", 30.0, 8, 1, form = "ragu"))))
        // Bodyweight: more reps than every earlier bodyweight set. Band sets never count.
        assertEquals(1, countPersonalRecords(listOf(s("a", null, 10, 0), s("b", null, 12, 1), s("c", null, 11, 2))))
        assertEquals(0, countPersonalRecords(listOf(s("a", null, 10, 0, band = 1), s("b", null, 20, 1, band = 2))))
        // Different exercises are independent.
        val other = DoneSet("z", "BP", "normal", 40.0, 5, null, "rapi", t)
        assertEquals(0, countPersonalRecords(listOf(s("a", 20.0, 8, 0), other)))
    }

    @Test fun activityWeekCountsVigorousDoubleAndUsesTheBestWeek() {
        val d = F.week(0)
        val a = listOf(ActivityRow(F.ms(d), 50, "vigorous"), ActivityRow(F.ms(d.plusDays(2)), 50, "light"), ActivityRow(F.ms(F.week(1)), 120, "moderate"))
        assertEquals(150, metricValue(BadgeMetric.ACTIVITY_MINUTES_WEEK, ProgressData(activities = a), F.clock()))
    }

    @Test fun loggedWeeksCountDaysNotAmounts() {
        val four = (0 until 4).map { F.food(F.week(0).plusDays(it.toLong())) }
        val fiveInTwoWeeks = four.dropLast(0) + listOf(F.food(F.week(0).plusDays(5)))
        assertEquals(0, metricValue(BadgeMetric.LOGGED_WEEKS, ProgressData(foodLogs = four), F.clock()))
        assertEquals(1, metricValue(BadgeMetric.LOGGED_WEEKS, ProgressData(foodLogs = fiveInTwoWeeks), F.clock()))
    }

    @Test fun plannedDaysUseTheFullestWeek() {
        val rows = (1..4).map { PlanRow("2026-11-16", it) } + (1..7).map { PlanRow("2026-11-23", it) } + PlanRow("2026-11-23", 7)
        assertEquals(7, metricValue(BadgeMetric.PLANNED_DAYS, ProgressData(plans = rows), F.clock()))
    }

    @Test fun returnAfterBreakNeedsFourteenWholeDays() {
        fun gap(days: Long) = ProgressData(workouts = listOf(F.workout("a", F.week(0)), F.workout("b", F.week(0).plusDays(days))))
        assertEquals(13, metricValue(BadgeMetric.RETURN_AFTER_BREAK, gap(13), F.clock()))
        assertEquals(14, metricValue(BadgeMetric.RETURN_AFTER_BREAK, gap(14), F.clock()))
    }

    @Test fun noBadgeConditionReadsWeight() {
        val text = RepoFiles.text("content/badges.json")
        assertFalse(Regex("\"metric\"\\s*:\\s*\"[^\"]*(?i:weight)[^\"]*\"").containsMatchIn(text.replace("\"scheduledMeasures\"", "")))
    }
}
