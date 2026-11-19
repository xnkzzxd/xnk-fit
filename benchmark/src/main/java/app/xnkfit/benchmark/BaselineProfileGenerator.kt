package app.xnkfit.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Design §10 "Baseline profiles committed for the cold-start and tab paths". Run with
 * `./gradlew :app:generateBaselineProfile` on an API 33+ emulator or a rooted API 28+ phone (benchmark/README.md);
 * the plugin writes app/src/main/generated/baselineProfiles/baseline-prof.txt, which is committed as made.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun coldStartAndTabs() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForToday()
        TABS.forEach { openTab(it) }
    }
}
