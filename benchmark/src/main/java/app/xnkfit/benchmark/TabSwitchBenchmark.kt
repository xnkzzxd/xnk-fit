package app.xnkfit.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Design §10 "Tab switch, 100 ms, Macrobenchmark frame timing". A tab switch is one tap and the frames that draw
 * the new screen; the checked number is `frameDurationCpuMs` P99 over all four switches of each iteration, so the
 * slowest frame of a switch must stay inside the budget. Budget check: tools/check-benchmark-budget.mjs.
 */
@RunWith(AndroidJUnit4::class)
class TabSwitchBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun tabSwitchBaselineProfile() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForToday()
        },
    ) {
        TABS.forEach { openTab(it) }
    }
}
