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
import app.xnkfit.data.progress.PhotoStore
import app.xnkfit.data.progress.ProgressRepository
import app.xnkfit.data.progress.RoomPhotoGateway
import app.xnkfit.ui.saya.DataStoreSayaPrefs
import app.xnkfit.ui.saya.SayaScreen
import app.xnkfit.ui.saya.SayaViewModel
import app.xnkfit.data.share.ShareFactsSource
import app.xnkfit.ui.share.KartuScreen
import app.xnkfit.ui.share.ShareViewModel
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
import app.xnkfit.data.logger.LoggerRepository
import app.xnkfit.ui.logger.AndroidRestAlarms
import app.xnkfit.ui.logger.DataStoreLoggerPrefs
import app.xnkfit.ui.latihan.DataStoreLatihanPrefs
import app.xnkfit.ui.latihan.LatihanHost
import app.xnkfit.ui.latihan.LatihanViewModel
import app.xnkfit.data.program.ProgramRepository
import app.xnkfit.ui.logger.LoggerViewModel
import app.xnkfit.ui.nav.Destination
import app.xnkfit.data.food.TargetBasisStore
import app.xnkfit.data.today.TodayRepository
import app.xnkfit.ui.today.TodayViewModel
import java.time.LocalDate
import java.util.Calendar
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import app.xnkfit.data.backup.BackupScheduler
import app.xnkfit.data.backup.BackupGraph
import app.xnkfit.data.backup.DeviceId
import app.xnkfit.data.backup.DriveConfig
import app.xnkfit.data.backup.PlayDriveAuth
import app.xnkfit.ui.data.DataScreen
import app.xnkfit.ui.data.DataViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        XnkDatabase.open(this, version)
    }

    // One DataStore per file: Makan and Hari ini must share this one (what the target was calculated with, M-2).
    private val basisStore by lazy {
        TargetBasisStore(PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile(TARGET_BASIS_FILE) })
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
                    targets = RoomTargetSource(database, basisStore) { Calendar.getInstance().get(Calendar.YEAR) },
                    prefsStore = FoodPrefsStore(store(FOOD_PREFS_FILE)),
                    ticksStore = ShoppingTickStore(store(SHOPPING_TICKS_FILE)),
                    today = { LocalDate.now() },
                ) as T
            }
        }
    }

    private val restAlarms by lazy { AndroidRestAlarms(applicationContext) }

    private val loggerRepo by lazy { LoggerRepository(database, System::currentTimeMillis, { UUID.randomUUID().toString() }) }

    private val latihan: LatihanViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LatihanViewModel(
                programs = ProgramRepository(database, loggerRepo, System::currentTimeMillis, { UUID.randomUUID().toString() }),
                logger = loggerRepo,
                prefs = DataStoreLatihanPrefs(PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile(LATIHAN_PREFS_FILE) }),
            ) as T
        }
    }

    private val logger: LoggerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoggerViewModel(
                repo = LoggerRepository(database, System::currentTimeMillis, { UUID.randomUUID().toString() }),
                prefs = DataStoreLoggerPrefs(PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile(LOGGER_PREFS_FILE) }),
                alarms = restAlarms,
                now = System::currentTimeMillis,
                newId = { UUID.randomUUID().toString() },
            ) as T
        }
    }

    private val today: TodayViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(
                repo = TodayRepository(database),
                food = FoodRepository(database, System::currentTimeMillis) { UUID.randomUUID().toString() },
                progress = ProgressRepository(database),
                basisStore = basisStore,
                today = { LocalDate.now() },
                currentYear = { Calendar.getInstance().get(Calendar.YEAR) },
            ) as T
        }
    }

    private val saya: SayaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SayaViewModel(
                repo = ProgressRepository(database),
                photos = RoomPhotoGateway(database, PhotoStore(applicationContext)),
                prefs = DataStoreSayaPrefs(applicationContext),
                loadNickname = { database.profileDao().get()?.nickname },
                newId = { UUID.randomUUID().toString() },
            ) as T
        }
    }

    private val share: ShareViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val source = ShareFactsSource(database, ProgressRepository(database))
                val photos = RoomPhotoGateway(database, PhotoStore(applicationContext))
                val prefs = DataStoreSayaPrefs(applicationContext)
                return ShareViewModel(
                    loadFacts = source::load,
                    loadPhotoPath = { photos.list().firstOrNull()?.path },
                    loadSayaHidesWeight = prefs::weightHidden,
                ) as T
            }
        }
    }

    // B-16: Data dan cadangan. Drive parts are null when this build has no OAuth client id ("belum tersedia").
    private val data: DataViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DataViewModel(
                service = BackupGraph.service(applicationContext),
                store = BackupGraph.settings(applicationContext),
                auth = if (DriveConfig.configured) PlayDriveAuth(applicationContext) else null,
                deviceId = DeviceId(applicationContext.noBackupFilesDir)::get,
            ) as T
        }
    }

    override fun onResume() {
        super.onResume()
        // B-2: automatic backup is checked when the app is opened or resumed, never in the background.
        lifecycleScope.launch { runCatching { BackupScheduler.onAppOpen(applicationContext) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XnkTheme {
                // A saved profile means onboarding is done (it is written only at the last step).
                // Until a profile exists the first screen is onboarding (PRD O-1).
                var hasProfile by rememberSaveable { mutableStateOf<Boolean?>(null) }
                var startTab by rememberSaveable { mutableStateOf(Destination.TODAY) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // Bundled foods and meal packages must be in Room before any screen reads them (B-04, B-10).
                    try { ContentBootstrap.loadBundled(this@MainActivity, database) } catch (e: Exception) { /* a broken bundle is a build bug; the app still opens */ }
                    // Design §4 step 4: an unfinished workout from today or yesterday opens on Latihan.
                    val yesterday = LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (database.workoutDao().unfinishedSince(yesterday).isNotEmpty()) startTab = Destination.WORKOUT
                    hasProfile = database.profileDao().get() != null
                }
                when (hasProfile) {
                    null -> Unit
                    false -> OnboardingScreen(onboarding, onDone = { hasProfile = true })
                    true -> {
                        var showCards by rememberSaveable { mutableStateOf(false) }
                        var showData by rememberSaveable { mutableStateOf(false) }
                        if (showCards) {
                            KartuScreen(share, onBack = { showCards = false })
                        } else if (showData) {
                            DataScreen(data, onBack = { showData = false })
                        } else {
                            XnkApp(today, makan, me = { SayaScreen(saya, it, onOpenCards = { showCards = true }, onOpenData = { showData = true }) }, workout = { LatihanHost(latihan, logger, restAlarms, it) }, start = startTab)
                        }
                    }
                }
            }
        }
    }
}

private const val FOOD_PREFS_FILE = "food_prefs" // text-ok: file name, not UI text
private const val TARGET_BASIS_FILE = "target_basis" // text-ok: file name, not UI text
private const val SHOPPING_TICKS_FILE = "shopping_ticks" // text-ok: file name, not UI text
private const val LATIHAN_PREFS_FILE = "latihan_prefs" // text-ok: file name, not UI text
private const val LOGGER_PREFS_FILE = "logger_prefs" // text-ok: file name, not UI text
