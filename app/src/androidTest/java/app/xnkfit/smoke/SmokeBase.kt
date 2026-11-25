package app.xnkfit.smoke

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import app.xnkfit.MainActivity
import app.xnkfit.R
import org.junit.After
import org.junit.Rule

/** Launches the real MainActivity; `Rule` is an empty compose rule because the activity is started by hand. */
abstract class SmokeBase {
    @get:Rule val rule = createEmptyComposeRule()
    protected var scenario: ActivityScenario<MainActivity>? = null

    protected fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    /** The app should show the bottom bar or the rail with Hari ini. */
    protected fun awaitToday() { rule.waitForText(str(R.string.nav_today)) }

    protected fun openTab(label: Int) = rule.clickText(str(label))

    @After fun closeScenario() {
        runCatching { scenario?.close() }
        scenario = null
        // No ANR dialog may be left on screen (the system shows "isn't responding" over the app).
        val dev = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(dev.findObject(androidx.test.uiautomator.By.textContains("isn't responding")) == null) { "ANR dialog on screen" }
    }
}
