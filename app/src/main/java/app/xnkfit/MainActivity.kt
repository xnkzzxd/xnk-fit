package app.xnkfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
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
import app.xnkfit.ui.latihan.InsightsViewModel
import app.xnkfit.data.figures.FigureRepository
import app.xnkfit.data.program.ActivityRepository
import app.xnkfit.data.program.WeekMapRepository
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
import app.xnkfit.R
import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.ui.bantuan.HelpScreen
import app.xnkfit.data.prefs.SettingsGraph
import app.xnkfit.ui.nav.Overlay
import app.xnkfit.ui.nav.OverlayStack
import app.xnkfit.data.privacy.AndroidDataWiper
import app.xnkfit.ui.pengaturan.DeleteDataViewModel
import app.xnkfit.ui.pengaturan.PrivacyScreen
import app.xnkfit.ui.pengaturan.SettingsScreen
import app.xnkfit.ui.pengaturan.SettingsViewModel
import app.xnkfit.ui.data.DataScreen
import app.xnkfit.ui.data.DataViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.xnkfit.data.reminder.ReminderScheduler

class MainActivity : ComponentActivity() {
    private val database by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        XnkDatabase.open(this, version)
    }

    // One DataStore per file: Makan and Hari ini must share this one (what the target was calculated with, M-2).
    private val basisStore by lazy {
        TargetBasisStore(PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile(TARGET_BASIS_FILE) })
    }

    private val settingsStore by lazy {
        // Shared with the reminder worker: one DataStore per file in a process.
        SettingsGraph.store(applicationContext)
    }

    // B-14: Pengaturan. The content check here is the manual one: it skips the 24 h wait.
    private val settingsVm: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                store = settingsStore,
                installedContentVersion = { database.contentDao().meta()?.contentVersion },
                checkUpdate = {
                    val code = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)).toInt()
                    ContentBootstrap.checkForUpdate(database, code, force = true)
                },
            ) as T
        }
    }

    // B-14: "Hapus semua data". After a full wipe the app restarts in a fresh process, so no open store writes old data back.
    private val deleteVm: DeleteDataViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DeleteDataViewModel(
                wiper = AndroidDataWiper(applicationContext, database),
                word = { getString(R.string.del_word) },
                onWiped = ::restartAfterWipe,
            ) as T
        }
    }

    private fun restartAfterWipe() {
        val intent = android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
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

    // B-09: steps from Health Connect (L-15). One DataStore file remembers that the user answered the system dialog.
    private val steps: app.xnkfit.ui.health.StepsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = app.xnkfit.ui.health.StepsViewModel(
                source = app.xnkfit.data.health.HealthConnectSteps(applicationContext),
                asked = app.xnkfit.data.health.DataStoreStepsAsked(PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile(STEPS_FILE) }),
            ) as T
        }
    }

    private val insights: InsightsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = InsightsViewModel(
                logger = loggerRepo,
                figures = FigureRepository { path -> applicationContext.assets.open(path).bufferedReader().use { it.readText() } },
                week = WeekMapRepository(database, loggerRepo, System::currentTimeMillis),
                activity = ActivityRepository(database, System::currentTimeMillis, { UUID.randomUUID().toString() }),
                sexOf = { if (database.profileDao().get()?.sex == "perempuan") "f" else "m" }, // text-ok: stored code and figure key
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
        // B-15: keep the 15-minute reminder check running while any switch is on (WorkManager keeps it across restarts).
        lifecycleScope.launch {
            runCatching { ReminderScheduler.sync(applicationContext, settingsStore.settings.first().reminders.isNotEmpty()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettings by settingsStore.settings.collectAsState(initial = null)
            XnkTheme(appSettings?.theme ?: ThemeMode.SYSTEM) {
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
                        var saved by rememberSaveable { mutableStateOf("") }
                        val stack = OverlayStack.parse(saved)
                        fun open(o: Overlay) { saved = OverlayStack.save(OverlayStack.open(OverlayStack.parse(saved), o)) }
                        val back = { saved = OverlayStack.save(OverlayStack.back(OverlayStack.parse(saved))) }
                        when (stack.lastOrNull()) {
                            Overlay.CARDS -> KartuScreen(share, onBack = back)
                            Overlay.DATA -> DataScreen(data, onBack = back)
                            Overlay.SETTINGS -> SettingsScreen(
                                settingsVm, deleteVm, onBack = back,
                                onOpenData = { open(Overlay.DATA) }, onOpenHelp = { open(Overlay.HELP) }, onOpenPrivacy = { open(Overlay.PRIVACY) },
                            )
                            Overlay.HELP -> HelpScreen(
                onBack = back, onOpenPrivacy = { open(Overlay.PRIVACY) },
                onShowWelcomeAgain = { lifecycleScope.launch { settingsStore.resetCards() } },
            )
                            Overlay.PRIVACY -> PrivacyScreen(onBack = back)
                            null -> XnkApp(today, makan, me = { SayaScreen(saya, it, onOpenCards = { open(Overlay.CARDS) }, onOpenSettings = { open(Overlay.SETTINGS) }) }, workout = { LatihanHost(latihan, insights, logger, restAlarms, steps, it) }, start = startTab, seenCards = appSettings?.seenCards, onDismissCard = { tab -> lifecycleScope.launch { settingsStore.markCardSeen(tab) } })
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
private const val STEPS_FILE = "steps_prefs" // text-ok: file name, not UI text
private const val LATIHAN_PREFS_FILE = "latihan_prefs" // text-ok: file name, not UI text
private const val LOGGER_PREFS_FILE = "logger_prefs" // text-ok: file name, not UI text
