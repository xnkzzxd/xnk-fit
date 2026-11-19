package app.xnkfit.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The app under test (release-like `benchmarkRelease` build made by the baseline profile plugin). */
const val TARGET = "app.xnkfit"

/** Bottom bar labels (res/values/strings.xml `nav_*`); each icon carries its label as content description. */
val TABS = listOf("Latihan", "Makan", "Saya", "Hari ini")

private const val TIMEOUT_MS = 10_000L

/**
 * Waits until Hari ini shows its bottom bar. The device must have finished onboarding once by hand
 * (benchmark/README.md): a fresh install opens onboarding, which is not the path the budget is about.
 */
fun MacrobenchmarkScope.waitForToday() {
    check(device.wait(Until.hasObject(By.desc("Hari ini")), TIMEOUT_MS)) {
        "Hari ini did not appear. Finish onboarding once on this device, then run again (benchmark/README.md)."
    }
    device.waitForIdle()
}

/** Taps one bottom bar tab and waits for the screen to settle. */
fun MacrobenchmarkScope.openTab(label: String) {
    val tab = device.findObject(By.desc(label)) ?: error("No tab \"$label\" on screen")
    tab.click()
    device.waitForIdle()
}
