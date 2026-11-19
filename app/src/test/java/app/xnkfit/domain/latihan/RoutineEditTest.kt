package app.xnkfit.domain.latihan

import app.xnkfit.domain.training.Pattern
import app.xnkfit.domain.training.TrainingTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor helpers (L-5): the user's numbers win; these only move and step them. */
class RoutineEditTest {
    private fun item(id: String, sets: Int = 3, min: Int = 8, max: Int = 12, rest: Int = 90) = DraftItem(id, id, sets, min, max, rest)

    @Test fun sourceAfterAnyEditIsOwn() {
        app.xnkfit.domain.training.ProgramSource.entries.forEach { assertEquals(app.xnkfit.domain.training.ProgramSource.OWN, sourceAfterEdit(it)) }
        assertTrue(generatorMayReplace(app.xnkfit.domain.training.ProgramSource.AUTO))
        assertEquals(false, generatorMayReplace(app.xnkfit.domain.training.ProgramSource.OWN))
        assertEquals(false, generatorMayReplace(app.xnkfit.domain.training.ProgramSource.JELAJAHI))
    }

    @Test fun movingKeepsOrderAtTheEnds() {
        val l = listOf(item("a"), item("b"), item("c"))
        assertEquals(listOf("b", "a", "c"), l.moved(0, 1).map { it.exerciseId })
        assertEquals(l, l.moved(0, -1))
        assertEquals(l, l.moved(2, 1))
        assertEquals(listOf("a", "c"), l.withoutAt(1).map { it.exerciseId })
    }

    @Test fun addingTheSameExerciseTwiceKeepsOne() {
        val l = listOf(item("a")).withAdded(item("a")).withAdded(item("b"))
        assertEquals(listOf("a", "b"), l.map { it.exerciseId })
    }

    @Test fun steppersStayInsideTheirGuards() {
        assertEquals(1, item("a", sets = 1).withSets(-1).sets)
        assertEquals(EDIT_SETS_MAX, item("a", sets = EDIT_SETS_MAX).withSets(1).sets)
        assertEquals(1..5, item("a", min = 1, max = 5).withReps(-1).let { it.repMin..it.repMax })
        assertEquals(9..13, item("a").withReps(1).let { it.repMin..it.repMax })
        assertEquals(0, item("a", rest = 0).withRest(-1).restSec)
        assertEquals(105, item("a", rest = 90).withRest(1).restSec)
    }

    @Test fun defaultsUseTheCompoundOrIsolationRest() {
        val lib = TrainingTestSupport.library
        val squat = lib.first { it.pattern == Pattern.SQUAT }
        val curl = lib.first { it.pattern == Pattern.CURL }
        assertTrue(defaultItem(squat, "x").restSec > defaultItem(curl, "y").restSec)
    }
}
