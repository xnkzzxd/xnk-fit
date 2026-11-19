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
            // Schema 2 (C-08b): keys are view-qualified; obliques are split out of abs on the front view.
            val known = body.front.parts.keys.map(body.front::key) + body.back.parts.keys.map(body.back::key)
            // A bare region means every view that draws it, so a key may be missing in one view but not in both.
            regions.forEach { (g, keys) ->
                assertTrue("$g has no region drawn for $sex", keys.any { it in known })
                keys.forEach { k -> val r = k.substringAfter(':'); assertTrue("$k missing for $sex", k in known || "front:$r" in known || "back:$r" in known) }
            }
            assertEquals(4, body.front.viewBox.size)
            assertTrue(body.front.parts.getValue("obliques").isNotEmpty() && body.front.parts.getValue("abs").isNotEmpty())
        }
    }

    @Test fun onlyExercisesWithARigGetOne() {
        val withRig = RepoFiles.text("content/exercises.json").let { FigureParser.rigIds(it) }
        // C-08b: 20 XNK rigs plus local rigs (content/figures/rig-local.json); every named rig must resolve.
        assertTrue(withRig.size > 20)
        withRig.forEach { (ex, pat) -> assertEquals("$ex", pat, repo.rigFor(ex)?.id) }
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
