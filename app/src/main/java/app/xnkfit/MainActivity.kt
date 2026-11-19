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
import app.xnkfit.data.content.ContentBootstrap
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.ui.XnkApp
import app.xnkfit.ui.common.XnkTheme
import app.xnkfit.ui.onboarding.OnboardingScreen
import app.xnkfit.ui.onboarding.OnboardingViewModel
import app.xnkfit.ui.onboarding.RoomProfileStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.xnkfit.data.food.FoodPrefsStore
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.data.food.RoomTargetSource
import app.xnkfit.data.food.ShoppingTickStore
import app.xnkfit.ui.makan.MakanViewModel
import java.time.LocalDate
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

    private val makan: MakanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = applicationContext
                fun store(name: String) = PreferenceDataStoreFactory.create { app.preferencesDataStoreFile(name) }
                return MakanViewModel(
                    repo = FoodRepository(database, System::currentTimeMillis) { UUID.randomUUID().toString() },
                    targets = RoomTargetSource(database) { Calendar.getInstance().get(Calendar.YEAR) },
                    prefsStore = FoodPrefsStore(store(FOOD_PREFS_FILE)),
                    ticksStore = ShoppingTickStore(store(SHOPPING_TICKS_FILE)),
                    today = { LocalDate.now() },
                ) as T
            }
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
                    // Bundled foods and meal packages must be in Room before any screen reads them (B-04, B-10).
                    try { ContentBootstrap.loadBundled(this@MainActivity, database) } catch (e: Exception) { /* a broken bundle is a build bug; the app still opens */ }
                    hasProfile = database.profileDao().get() != null
                }
                when (hasProfile) {
                    null -> Unit
                    false -> OnboardingScreen(onboarding, onDone = { hasProfile = true })
                    true -> XnkApp(makan)
                }
            }
        }
    }
}

private const val FOOD_PREFS_FILE = "food_prefs" // text-ok: file name, not UI text
private const val SHOPPING_TICKS_FILE = "shopping_ticks" // text-ok: file name, not UI text
