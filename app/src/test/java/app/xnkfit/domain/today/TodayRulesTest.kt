package app.xnkfit.domain.today

import app.xnkfit.domain.rules.ActivityLevel
import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.rules.PaceResult
import app.xnkfit.domain.rules.Profile
import app.xnkfit.domain.rules.Screening
import app.xnkfit.domain.rules.Sex
import app.xnkfit.domain.rules.evaluateTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The Hari ini cards and the recalculation prompt (B-11) against every gate of B-02. Pure, no Android. */
class TodayRulesTest {
    private fun screening(
        risk: Boolean = false, preg: Boolean = false, ed: Boolean = false,
    ) = Screening(risk, false, false, false, false, preg, ed)

    private fun result(
        goal: Goal = Goal.TURUN, birthYear: Int = 1995, weight: Double = 70.0, height: Double = 160.0,
        sex: Sex = Sex.PEREMPUAN, s: Screening? = screening(),
    ) = evaluateTargets(Profile(sex, birthYear, height, weight, ActivityLevel.JARANG, goal), s, 2026)

    private fun numbers(r: GateResult) = targetCardOf(r) as TargetCard.Numbers

    // ---- target card, every gate ----

    @Test fun plainAdultGetsNumbersAndADeficitForTurun() {
        val c = numbers(result())
        assertEquals(Goal.TURUN, c.goal)
        val maintain = numbers(result(goal = Goal.JAGA))
        assertTrue(c.kcal < maintain.kcal)
        assertTrue(c.proteinG > 0 && c.carbG >= 0 && c.fatG > 0 && c.fibreG > 0)
    }

    @Test fun R_GATE_ED_hasNoNumbersAtAll() {
        val c = targetCardOf(result(s = screening(ed = true)))
        assertTrue(c is TargetCard.NoNumbers)
        assertTrue(GateNote.EATING_DISORDER_NO_NUMBERS in (c as TargetCard.NoNumbers).notes)
    }

    @Test fun deficitIsNeverOfferedWhereAGateForbidsIt() {
        val maintain = numbers(result(goal = Goal.JAGA)).kcal
        val u18 = numbers(result(birthYear = 2010)) // youngest possible age 15
        assertTrue(u18.goal != Goal.TURUN && GateNote.UNDER_18_LIGHT_MODE in u18.notes)
        val preg = numbers(result(s = screening(preg = true)))
        assertEquals(Goal.JAGA, preg.goal)
        assertEquals(maintain, preg.kcal)
        assertTrue(GateNote.PREGNANT_NO_DEFICIT in preg.notes)
        val thin = numbers(result(weight = 42.0, height = 170.0)) // BMI under 18.5
        assertEquals(Goal.JAGA, thin.goal)
        assertTrue(GateNote.BMI_UNDER_NO_TURUN in thin.notes)
    }

    @Test fun riskAnswerKeepsTheDoctorNoteAndAtMostASmallDeficit() {
        val risk = numbers(result(s = screening(risk = true)))
        val maintain = numbers(result(goal = Goal.JAGA)).kcal
        assertTrue(GateNote.RISK_SEE_DOCTOR in risk.notes)
        assertTrue(risk.kcal >= maintain * 0.9 - 10)
    }

    @Test fun noScreeningOrNoProfileGivesNoNumber() {
        assertEquals(TargetCard.NeedsScreening, targetCardOf(null))
        assertEquals(TargetCard.NeedsScreening, targetCardOf(result(s = null)))
    }

    @Test fun aChildOrBadInputGivesNoNumber() {
        assertEquals(TargetCard.Unavailable, targetCardOf(result(birthYear = 2018)))
        assertEquals(TargetCard.Unavailable, targetCardOf(result(weight = 5.0)))
    }

    @Test fun floorGateRaisesTheTargetAndSaysSo() {
        val c = numbers(result(sex = Sex.PEREMPUAN, weight = 45.0, height = 150.0, birthYear = 1950))
        assertTrue(c.kcal >= 1200)
    }

    // ---- sources (i) ----

    @Test fun everyNumberExceptCarbHasASource() {
        TargetNumber.entries.filter { it != TargetNumber.CARB }.forEach {
            assertTrue("$it", sourcesFor(it, Goal.JAGA).isNotEmpty())
        }
        assertTrue(sourcesFor(TargetNumber.CARB, Goal.JAGA).isEmpty())
        assertTrue(SourceRef.NHLBI_S5 in sourcesFor(TargetNumber.KCAL, Goal.TURUN))
        assertTrue(SourceRef.SURPLUS_S19 in sourcesFor(TargetNumber.KCAL, Goal.NAIK))
        assertFalse(SourceRef.NHLBI_S5 in sourcesFor(TargetNumber.KCAL, Goal.NAIK))
    }

    // ---- pace raise ----

    @Test fun paceRaiseAddsToADeficitOnlyAndRebuildsMacros() {
        val r = result() as GateResult.Ready
        val up = withPaceRaise(r, 70.0, 100)
        assertEquals(r.targetKcal + 100, up.targetKcal)
        assertTrue(up.macros.carbG >= r.macros.carbG)
        val keep = result(goal = Goal.JAGA) as GateResult.Ready
        assertEquals(keep.targetKcal, withPaceRaise(keep, 70.0, 100).targetKcal)
        val ed = result(s = screening(ed = true)) as GateResult.Ready
        assertEquals(ed.targetKcal, withPaceRaise(ed, 70.0, 100).targetKcal)
        assertEquals(r.targetKcal, withPaceRaise(r, 70.0, 0).targetKcal)
    }

    // ---- recalculation prompt (M-2, R_RECALC) ----

    private val noPace = PaceResult.NoSuggestion

    @Test fun R_RECALC_weightMovesThreeKiloAsks() {
        assertNull(recalcPrompt(70.0, 67.1, Goal.TURUN, Goal.TURUN, false, noPace))
        assertNull(recalcPrompt(70.0, 72.9, Goal.TURUN, Goal.TURUN, false, noPace))
        val down = recalcPrompt(70.0, 67.0, Goal.TURUN, Goal.TURUN, false, noPace)!!
        assertEquals(RecalcReason.WEIGHT_DOWN, down.reason)
        assertEquals(3.0, down.deltaKg!!, 1e-9)
        assertEquals(RecalcReason.WEIGHT_UP, recalcPrompt(70.0, 74.0, Goal.JAGA, Goal.JAGA, false, noPace)!!.reason)
    }

    @Test fun goalChangeAsksFirst() {
        val p = recalcPrompt(70.0, 60.0, Goal.TURUN, Goal.NAIK, false, noPace)!!
        assertEquals(RecalcReason.GOAL_CHANGED, p.reason)
    }

    @Test fun paceSuggestionAsksOnlyWithNumbers() {
        val pace = PaceResult.SuggestRaise(100, 0.015)
        val p = recalcPrompt(70.0, 69.0, Goal.TURUN, Goal.TURUN, false, pace)!!
        assertEquals(RecalcReason.PACE, p.reason)
        assertEquals(100, p.raiseKcal)
        assertNull(recalcPrompt(70.0, 69.0, Goal.JAGA, Goal.JAGA, true, pace)) // R_GATE_ED
    }

    @Test fun missingValuesNeverAsk() {
        assertNull(recalcPrompt(null, 60.0, null, null, false, noPace))
        assertNull(recalcPrompt(70.0, null, Goal.TURUN, Goal.TURUN, false, noPace))
        assertNull(recalcPrompt(Double.NaN, 60.0, Goal.TURUN, Goal.TURUN, false, noPace))
    }

    @Test fun weeklyWeightsKeepTheLastOfEachWeekAndPaceReadsThem() {
        val z = ZoneOffset.UTC
        fun at(d: LocalDate) = d.atStartOfDay(z).toInstant().toEpochMilli()
        val mon = LocalDate.of(2026, 11, 2)
        val rows = listOf(
            at(mon) to 80.0, at(mon.plusDays(3)) to 79.9, // week 1: last is 79.9
            at(mon.plusDays(7)) to 78.0, at(mon.plusDays(14)) to 76.0,
        )
        val w = weeklyWeights(rows, z)
        assertEquals(3, w.size)
        assertEquals(79.9, w[0].kg, 1e-9)
        assertEquals(1, w[1].weekIndex - w[0].weekIndex)
        assertTrue(paceOf(rows, z) is PaceResult.SuggestRaise) // 79.9 → 78 → 76: both weeks down, > 1 % a week
        assertEquals(PaceResult.NoSuggestion, paceOf(rows.take(3), z))
    }

    // ---- workout card (H-1, H-3) ----

    private val days = listOf(ProgramDayInfo("a", "Full Body A", 5, 45), ProgramDayInfo("b", "Full Body B", 5, 45), ProgramDayInfo("c", "Full Body C", 6, 50))

    @Test fun noProgramSaysSo() = assertEquals(WorkoutCard.NoProgram, workoutCard(emptyList(), null, false, null, false))

    @Test fun nextDayFollowsTheLastFinishedAndWraps() {
        assertEquals(WorkoutCard.Next(days[0]), workoutCard(days, null, false, null, false))
        assertEquals(WorkoutCard.Next(days[1]), workoutCard(days, "a", false, null, false))
        assertEquals(WorkoutCard.Next(days[0]), workoutCard(days, "c", false, null, false))
        assertEquals(WorkoutCard.Next(days[0]), workoutCard(days, "gone", false, null, false)) // regenerated program
    }

    @Test fun finishedTodayIsARestDayAndOpenSessionOffersResume() {
        assertEquals(WorkoutCard.Rest(days[2]), workoutCard(days, "b", true, null, false))
        assertEquals(WorkoutCard.Resume(days[1]), workoutCard(days, "a", false, "b", true))
        assertEquals(WorkoutCard.Resume(null), workoutCard(days, "a", false, null, true))
    }

    // ---- weekly activity (H-2, R_WHO_MIN) ----

    @Test fun vigorousCountsDoubleAndLightDoesNot() {
        val a = activityWeek(listOf("moderate" to 60, "vigorous" to 20, "light" to 90, "moderate" to 10))
        assertEquals(110, a.minutes)
        assertEquals(150, a.targetMinutes)
        assertEquals(1f, activityWeek(listOf("moderate" to 400)).fraction, 0f)
        assertEquals(0, activityWeek(emptyList()).minutes)
    }

    // ---- tasks and week strip ----

    @Test fun tasksFollowTheDay() {
        assertEquals(listOf(TaskKind.FOOD), todayTasks(false, false, false, false, 0).map { it.kind })
        val all = todayTasks(true, false, true, false, 1)
        assertEquals(listOf(TaskKind.WEIGH, TaskKind.WORKOUT, TaskKind.FOOD), all.map { it.kind })
        assertEquals(listOf(false, false, true), all.map { it.done })
        assertTrue(todayTasks(false, true, false, true, 2).all { it.done || it.kind == TaskKind.FOOD }) // weighed earlier in the week
    }

    @Test fun weekStripIsMondayFirst() {
        val wed = LocalDate.of(2026, 11, 18)
        val strip = weekStrip(setOf(LocalDate.of(2026, 11, 16), wed, LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 23)), wed)
        assertEquals(listOf(true, false, true, false, false, false, true), strip)
    }
}
