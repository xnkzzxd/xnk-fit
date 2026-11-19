package app.xnkfit.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Design §10 "Cold start to usable Hari ini, 2.0 s". The number checked is `timeToFullDisplayMs`: Hari ini calls
 * `ReportDrawnWhen { loaded }` once its cards have data. Budget check: tools/benchmark-budget.mjs.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    /** What a user gets after a Play install (profile applied). */
    @Test fun coldStartBaselineProfile() = start(CompilationMode.Partial(BaselineProfileMode.Require))

    /** Worst case: no profile, interpreted and JIT only (sideload before the profile is installed). */
    @Test fun coldStartNoCompilation() = start(CompilationMode.None())

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForToday()
    }
}
