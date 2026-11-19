package app.xnkfit.ui.logger

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.xnkfit.MainActivity
import app.xnkfit.R

/**
 * Rest alarm (L-8, Design §1, §8, §14): one exact alarm at the stored rest end, a broadcast receiver that posts one
 * local notification on the "Istirahat" channel. No foreground service, no daemon: the system alarm wakes the
 * receiver even with the screen off or the process killed (not after "Force stop", which Android treats as the
 * user's wish to stop every alarm of the app).
 */
class AndroidRestAlarms(context: Context) : RestAlarms {
    private val app = context.applicationContext
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    override fun access(): AlarmAccess = when {
        !notificationsAllowed(app) -> AlarmAccess.NEEDS_NOTIFICATIONS
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> AlarmAccess.NEEDS_EXACT_ALARM
        else -> AlarmAccess.READY
    }

    override fun schedule(atMs: Long) {
        ensureChannel(app)
        try {
            // Exact and allowed in Doze: a rest timer is useless a minute late. Not setAlarmClock: that shows an
            // alarm-clock icon and "next alarm" on the lock screen for a 90 s rest.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(app))
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the call: the in-app timer still runs.
        }
    }

    override fun cancel() {
        alarmManager.cancel(pending(app))
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val NOTIFICATION_ID = 7001
        private const val CHANNEL_ID = "istirahat" // text-ok: channel id
        private const val REQUEST = 7001

        fun notificationsAllowed(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.lg_channel_rest), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.lg_channel_rest_desc) },
            )
        }

        fun post(context: Context) {
            if (!notificationsAllowed(context)) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, REQUEST, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_workout)
                .setContentTitle(context.getString(R.string.lg_notif_title))
                .setContentText(context.getString(R.string.lg_notif_text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
            } catch (e: SecurityException) {
                // Notifications turned off after the check: nothing to show.
            }
        }
    }
}

/** Woken by the exact alarm; posts the "Istirahat selesai" notification and ends. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AndroidRestAlarms.post(context)
}
