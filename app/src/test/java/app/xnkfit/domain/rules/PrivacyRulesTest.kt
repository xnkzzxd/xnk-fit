package app.xnkfit.domain.rules

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules.md §9 (PRD §9). */
class PrivacyRulesTest {
    @Test
    fun R_PRIV_NO_SDK_noBannedLibraryInTheBuild() {
        val build = RepoFiles.text("gradle/libs.versions.toml") + RepoFiles.text("app/build.gradle.kts") +
            RepoFiles.text("build.gradle.kts")
        R_PRIV_NO_SDK_BANNED.forEach { assertFalse("banned: $it", build.contains(it)) }
        // Plugins that pull those SDKs in.
        assertFalse(build.contains("com.google.gms.google-services"))
        assertFalse(build.contains("crashlytics"))
    }

    @Test
    fun R_PRIV_NET_onlyThreePurposes() {
        assertEquals(
            listOf("CONTENT_UPDATE", "DRIVE_BACKUP", "STORE_CRASH_REPORTS"),
            NetworkPurpose.entries.map { it.name },
        )
    }

    @Test
    fun R_PRIV_PHOTO_offByDefault() = assertFalse(R_PRIV_PHOTO_BACKUP_DEFAULT)

    @Test
    fun R_PRIV_BACKUP_DEFAULT_offThenDailyOnWifi() {
        assertFalse(R_PRIV_BACKUP_DEFAULT_ON)
        assertEquals(24, R_PRIV_BACKUP_DEFAULT_AUTO_AGE_HOURS)
        assertTrue(R_PRIV_BACKUP_DEFAULT_WIFI_ONLY)
        assertFalse(autoBackupDue(backupOn = false, onWifi = true, hoursSinceLast = 100.0))
        assertFalse(autoBackupDue(backupOn = true, onWifi = false, hoursSinceLast = 100.0))
        assertFalse(autoBackupDue(backupOn = true, onWifi = true, hoursSinceLast = 23.9))
        assertTrue(autoBackupDue(backupOn = true, onWifi = true, hoursSinceLast = 24.0))
        assertTrue(autoBackupDue(backupOn = true, onWifi = true, hoursSinceLast = null))
    }

    @Test
    fun R_PRIV_KEEP_lastThree() {
        assertEquals(3, R_PRIV_KEEP)
        assertEquals(listOf("a", "b"), backupsToDelete(listOf("a", "b", "c", "d", "e")))
        assertEquals(emptyList<String>(), backupsToDelete(listOf("a", "b", "c")))
    }
}
