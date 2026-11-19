package app.xnkfit.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.xnkfit.MainActivity
import app.xnkfit.R
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.data.prefs.SettingsGraph
import app.xnkfit.data.prefs.ReminderGroup
import app.xnkfit.domain.reminder.ReminderFacts
import app.xnkfit.domain.reminder.nextReminder
import app.xnkfit.domain.reminder.rotationIndex
import app.xnkfit.domain.rules.ReminderKind
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Which switch controls which kinds (Pengaturan has one switch per group). */
fun kindsOf(groups: Set<ReminderGroup>): Set<ReminderKind> = buildSet {
    if (ReminderGroup.LATIHAN in groups) { add(ReminderKind.LATIHAN); add(ReminderKind.KEMBALI) }
    if (ReminderGroup.MAKAN in groups) addAll(listOf(ReminderKind.MAKAN_PAGI, ReminderKind.MAKAN_SIANG, ReminderKind.MAKAN_MALAM))
    if (ReminderGroup.TIMBANG in groups) add(ReminderKind.TIMBANG)
}

/** What was posted today and how many "kembali" were sent since the last record. A few small keys, phone only. */
class ReminderLog(private val store: DataStore<Preferences>) {
    private val dayKey = stringPreferencesKey("day") // text-ok: preference key
    private val sentKey = stringSetPreferencesKey("sent") // text-ok: preference key
    private val backKey = intPreferencesKey("back_count") // text-ok: preference key
    private val backForKey = stringPreferencesKey("back_for") // text-ok: preference key

    suspend fun sentToday(today: LocalDate): Set<ReminderKind> {
        val p = store.data.first()
        if (p[dayKey] != today.toString()) return emptySet()
        return (p[sentKey] ?: emptySet()).mapNotNull { n -> ReminderKind.entries.firstOrNull { it.name == n } }.toSet()
    }

    suspend fun backSent(activityDay: LocalDate?): Int {
        val p = store.data.first()
        return if (p[backForKey] == activityDay?.toString()) p[backKey] ?: 0 else 0
    }

    suspend fun record(kind: ReminderKind, today: LocalDate, activityDay: LocalDate?) {
        val before = sentToday(today)
        val back = backSent(activityDay)
        store.edit { p ->
            p[dayKey] = today.toString()
            p[sentKey] = (before + kind).map { it.name }.toSet()
            if (kind == ReminderKind.KEMBALI) { p[backKey] = back + 1; p[backForKey] = activityDay?.toString().orEmpty() }
        }
    }
}

object ReminderGraph {
    private const val FILE = "reminder_log" // text-ok: DataStore file name
    @Volatile private var log: ReminderLog? = null

    fun log(context: Context): ReminderLog = log ?: synchronized(this) {
        log ?: ReminderLog(PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile(FILE) }).also { log = it }
    }
}

/**
 * Local reminders (R-1, R-2, Design §8): one periodic WorkManager job, 15 minutes (the system minimum), no service,
 * no boot receiver. Started when the app opens and a switch is on; cancelled when every switch is off.
 */
object ReminderScheduler {
    private const val WORK_NAME = "reminders" // text-ok: unique work name

    fun sync(context: Context, anyOn: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!anyOn) { wm.cancelUniqueWork(WORK_NAME); return }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!ReminderNotifier.allowed(app)) return Result.success() // permission "no": stay silent
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val settings = SettingsGraph.store(app)
        val enabled = kindsOf(settings.settings.first().reminders)
        if (enabled.isEmpty()) return Result.success()
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
        val db = XnkDatabase.open(app, version)
        val log = ReminderGraph.log(app)
        val facts = readFacts(db, log, now, zone, enabled)
        val kind = nextReminder(facts) ?: return Result.success()
        ReminderNotifier.post(app, kind, rotationIndex(kind, today, ReminderNotifier.variantCount(app, kind)))
        log.record(kind, today, facts.lastActivityDay)
        return Result.success()
    }

    private suspend fun readFacts(db: XnkDatabase, log: ReminderLog, now: LocalDateTime, zone: ZoneId, enabled: Set<ReminderKind>): ReminderFacts {
        val today = now.toLocalDate()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val workouts = listOfNotNull(db.workoutDao().lastFinished())
        val lastWorkout = workouts.firstOrNull()?.endedAt?.let(::day)
        val foodRows = db.progressDao().foodLogRows()
        val lastFood = foodRows.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.maxOrNull()
        val weight = db.profileDao().latestMeasure("weight") // text-ok: stored code
        val lastWeight = weight?.takenAt?.let(::day)
        val slots = foodRows.filter { it.day == today.toString() }.map { it.slot }.toSet()
        val activity = listOfNotNull(lastWorkout, lastFood, lastWeight).maxOrNull()
        return ReminderFacts(
            now = now,
            enabled = enabled,
            sentToday = log.sentToday(today),
            hasProgram = db.programDao().active() != null,
            workoutFinishedToday = lastWorkout == today,
            lastWorkoutDay = lastWorkout,
            foodSlotsLoggedToday = buildSet {
                if ("pagi" in slots) add(ReminderKind.MAKAN_PAGI) // text-ok: stored slot codes
                if ("siang" in slots) add(ReminderKind.MAKAN_SIANG) // text-ok: stored slot codes
                if ("malam" in slots) add(ReminderKind.MAKAN_MALAM) // text-ok: stored slot codes
            },
            lastWeightDay = lastWeight,
            lastActivityDay = activity,
            backSentSinceActivity = log.backSent(activity),
        )
    }
}

object ReminderNotifier {
    private fun channel(kind: ReminderKind): Pair<String, Int> = when (kind) {
        ReminderKind.LATIHAN, ReminderKind.KEMBALI -> "latihan" to R.string.rem_channel_latihan // text-ok: channel id
        ReminderKind.TIMBANG -> "timbang" to R.string.rem_channel_timbang // text-ok: channel id
        else -> "makan" to R.string.rem_channel_makan // text-ok: channel id
    }

    private fun array(kind: ReminderKind): Int = when (kind) {
        ReminderKind.LATIHAN -> R.array.rem_latihan
        ReminderKind.KEMBALI -> R.array.rem_kembali
        ReminderKind.TIMBANG -> R.array.rem_timbang
        ReminderKind.MAKAN_PAGI -> R.array.rem_makan_pagi
        ReminderKind.MAKAN_SIANG -> R.array.rem_makan_siang
        ReminderKind.MAKAN_MALAM -> R.array.rem_makan_malam
    }

    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun variantCount(context: Context, kind: ReminderKind): Int = context.resources.getStringArray(array(kind)).size

    fun post(context: Context, kind: ReminderKind, variant: Int) {
        if (!allowed(context)) return
        val (id, name) = channel(kind)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.resources.getStringArray(array(kind))[variant]
        val n = NotificationCompat.Builder(context, id)
            .setSmallIcon(R.drawable.ic_nav_workout)
            .setContentTitle(context.getString(name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(8000 + kind.ordinal, n)
        } catch (e: SecurityException) {
            // Permission withdrawn after the check: nothing to show.
        }
    }
}
