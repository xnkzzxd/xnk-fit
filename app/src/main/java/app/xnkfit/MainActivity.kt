package app.xnkfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.ui.XnkApp
import app.xnkfit.ui.common.XnkTheme
import app.xnkfit.ui.onboarding.OnboardingScreen
import app.xnkfit.ui.onboarding.OnboardingViewModel
import app.xnkfit.ui.onboarding.RoomProfileStore
import java.util.Calendar
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val database by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        XnkDatabase.open(this, version)
    }

    private val onboarding: OnboardingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
                store = RoomProfileStore(database),
                currentYear = { Calendar.getInstance().get(Calendar.YEAR) },
                nowMs = System::currentTimeMillis,
                newId = { UUID.randomUUID().toString() },
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XnkTheme {
                // A saved profile means onboarding is done (it is written only at the last step).
                // Until a profile exists the first screen is onboarding (PRD O-1).
                var hasProfile by rememberSaveable { mutableStateOf<Boolean?>(null) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    hasProfile = database.profileDao().get() != null
                }
                when (hasProfile) {
                    null -> Unit
                    false -> OnboardingScreen(onboarding, onDone = { hasProfile = true })
                    true -> XnkApp()
                }
            }
        }
    }
}
