package app.xnkfit.data.figures

import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.training.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Content §5.1 on the JVM: the figure files parse, every map region exists, and a rig exists only where `rig` says so. */
@RunWith(RobolectricTestRunner::class)
class FigureRepositoryTest {
    private val repo = FigureRepository { path ->
        val file = when {
            path.startsWith("content/") -> path
            else -> "content/" + path
        }
        RepoFiles.text(file)
    }

    @Test fun everyMuscleCodeMapsToRegionsThatExistInTheFigure() {
        val regions = MuscleGroup.entries.associateWith { repo.regionsOf[it].orEmpty() }
        assertTrue(regions.values.all { it.isNotEmpty() })
        for (sex in listOf("m", "f")) {
            val body = repo.body(sex)
            val known = body.front.parts.keys + body.back.parts.keys
            regions.values.flatten().forEach { assertTrue("$it missing for $sex", it in known) }
            assertEquals(4, body.front.viewBox.size)
        }
    }

    @Test fun onlyExercisesWithARigGetOne() {
        val withRig = RepoFiles.text("content/exercises.json").let { FigureParser.rigIds(it) }
        assertEquals(20, withRig.size)
        val (id, pattern) = withRig.entries.first()
        val rig = repo.rigFor(id)
        assertNotNull(rig)
        assertEquals(pattern, rig!!.id)
        assertEquals(13, rig.frames.size)
        assertTrue(rig.main.isNotEmpty())
        // SQUAT_BACK has rig = null in exercises.json: map and cues only.
        assertNull(repo.rigFor("SQUAT_BACK"))
        assertNull(repo.rigFor("NOT_AN_EXERCISE"))
    }

    @Test fun everyRigPatternHasTheSegmentsItOrders() {
        val data = repo.rigData()
        data.patterns.values.forEach { p ->
            assertEquals(13, p.frames.size)
            p.order.forEach { sg -> assertTrue("${p.id} lacks $sg", p.frames.all { sg in it.segments }) }
            val base = p.order.map { it.removeSuffix("L").removeSuffix("R") }
            assertTrue(base.all { it in data.lengths || it == "head" })
        }
    }
}
