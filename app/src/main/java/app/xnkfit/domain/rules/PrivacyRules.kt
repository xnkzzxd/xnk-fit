package app.xnkfit.domain.rules

// Privacy and data rules (Rules.md §9, PRD §9). Enforcement is in the data layer (B-16) and the
// build checks; the domain holds the numbers and lists.

// R_PRIV_NO_SDK: library groups that must never be a dependency (analytics, ads, crash SDKs that send data).
val R_PRIV_NO_SDK_BANNED: List<String> = listOf(
    "com.google.firebase",
    "com.google.android.gms:play-services-ads",
    "com.google.android.gms:play-services-analytics",
    "com.crashlytics",
    "io.sentry",
    "com.facebook",
    "com.appsflyer",
    "com.adjust",
    "com.mixpanel",
    "com.amplitude",
    "com.segment",
    "com.bugsnag",
)

/** R_PRIV_NET: the only reasons the app may use the network. */
enum class NetworkPurpose {
    CONTENT_UPDATE, // no user data sent (PV-2)
    DRIVE_BACKUP, // user's own Drive, only with backup on
    STORE_CRASH_REPORTS, // the store's own reports
}

// R_PRIV_PHOTO: photos stay on the phone unless photo backup is turned on.
const val R_PRIV_PHOTO_BACKUP_DEFAULT = false

// R_PRIV_BACKUP_DEFAULT: backup off until the user turns it on; automatic backup when older than 24 h, on Wi-Fi.
const val R_PRIV_BACKUP_DEFAULT_ON = false
const val R_PRIV_BACKUP_DEFAULT_AUTO_AGE_HOURS = 24
const val R_PRIV_BACKUP_DEFAULT_WIFI_ONLY = true

// R_PRIV_KEEP: Drive backups kept.
const val R_PRIV_KEEP = 3

/** R_PRIV_BACKUP_DEFAULT: automatic backup runs on open/resume when on, on Wi-Fi, and the last one is old enough. */
fun autoBackupDue(backupOn: Boolean, onWifi: Boolean, hoursSinceLast: Double?): Boolean {
    if (!backupOn) return false
    if (R_PRIV_BACKUP_DEFAULT_WIFI_ONLY && !onWifi) return false
    return hoursSinceLast == null || hoursSinceLast >= R_PRIV_BACKUP_DEFAULT_AUTO_AGE_HOURS
}

/** R_PRIV_KEEP: which backups (oldest first) to delete after a new one is written. */
fun <T> backupsToDelete(oldestFirst: List<T>): List<T> = oldestFirst.dropLast(R_PRIV_KEEP)
