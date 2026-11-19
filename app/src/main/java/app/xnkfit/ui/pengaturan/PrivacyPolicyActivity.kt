package app.xnkfit.ui.pengaturan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.xnkfit.ui.common.XnkTheme
import app.xnkfit.data.prefs.ThemeMode

/**
 * Health Connect opens this from "privacy policy" in its permission screen and from the app's data-access page.
 * It shows the same [PrivacyScreen] as Pengaturan (B-14), so the Play listing, the app and Health Connect carry one text (Play-Health.md §1 row 4).
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { XnkTheme(ThemeMode.SYSTEM) { PrivacyScreen(onBack = { finish() }) } }
    }
}
