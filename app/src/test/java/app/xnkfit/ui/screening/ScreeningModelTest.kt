package app.xnkfit.ui.screening

import app.xnkfit.domain.rules.GateNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningModelTest {
    @Test
    fun sevenQuestionsEachReadAndWriteTheirOwnAnswer() {
        assertEquals(7, ScreeningQuestion.entries.size)
        ScreeningQuestion.entries.forEach { q ->
            val set = q.with(EmptyScreening, true)
            assertEquals(true, q.answer(set))
            ScreeningQuestion.entries.filter { it != q }.forEach { other -> assertNull(other.answer(set)) }
        }
    }

    @Test
    fun everyGateNoteOnTheResultScreenHasText_andTargetCardNotesHaveNone() {
        listOf(
            GateNote.RISK_SEE_DOCTOR, GateNote.UNDER_18_LIGHT_MODE, GateNote.PREGNANT_NO_DEFICIT,
            GateNote.EATING_DISORDER_NO_NUMBERS,
        ).forEach { assertNotNull(outcomeCardFor(it)) }
        listOf(GateNote.DEFICIT_CAPPED, GateNote.BMI_UNDER_NO_TURUN, GateNote.GOAL_CHANGED_TO_JAGA, GateNote.FLOOR_RAISED)
            .forEach { assertNull(outcomeCardFor(it)) }
        assertEquals(GateNote.entries.size, 8)
    }
}
