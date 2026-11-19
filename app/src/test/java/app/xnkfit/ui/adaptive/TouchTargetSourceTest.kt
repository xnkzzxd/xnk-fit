package app.xnkfit.ui.adaptive

import app.xnkfit.ui.common.MinTouch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-2 on the JVM: no Compose UI test exists (Design §12), so the 48 dp rule is checked in the source. */
class TouchTargetSourceTest {
    private fun uiFiles(): List<File> {
        val root = listOf("src/main/java/app/xnkfit/ui", "app/src/main/java/app/xnkfit/ui").map(::File).first { it.isDirectory }
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test fun minTouchIs48dp() = assertEquals(48f, MinTouch.value, 0f)

    @Test fun noMinimumHeightBelow48dp() {
        val small = Regex("""(heightIn|sizeIn|defaultMinSize)\([^)]*min(Height)?\s*=\s*(\d+)\.dp""")
        val bad = uiFiles().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                small.find(line)?.takeIf { it.groupValues[3].toInt() < 48 }?.let { "${f.name}:${i + 1}" }
            }
        }
        assertTrue("touch targets under 48 dp: $bad", bad.isEmpty())
    }

    @Test fun readingWidthIsBounded() = assertTrue(MaxReadingWidth.value in 480f..720f)
}
