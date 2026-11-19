package app.xnkfit.ui.data

import app.xnkfit.R
import app.xnkfit.data.backup.BackupError
import app.xnkfit.data.backup.BackupException
import app.xnkfit.data.backup.BackupService
import app.xnkfit.data.backup.DriveAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-5 typed word and the refusal messages; the dialogs themselves need a Compose UI test (not in Design §12). */
class DataScreenLogicTest {
    @Test fun typedWord_mustMatch() {
        assertTrue(restoreWordMatches(" pulihkan ", "PULIHKAN"))
        assertFalse(restoreWordMatches("PULIH", "PULIHKAN"))
        assertFalse(restoreWordMatches("", "PULIHKAN"))
    }

    @Test fun newerBackup_saysUpdateTheApp() {
        assertEquals(R.string.data_err_newer, errorText(BackupException(BackupError.NEWER_APP)))
        assertEquals(R.string.data_err_write, errorText(BackupException(BackupError.WRITE_FAILED)))
        assertEquals(R.string.data_err_sign_in, errorText(DriveAuthException()))
        assertEquals(R.string.data_err_network, errorText(java.io.IOException()))
        BackupError.entries.forEach { errorText(BackupException(it)) }
    }

    @Test fun backupName_roundTripsTime() {
        val t = 1_700_000_000_000L
        assertEquals(t, BackupService.timeOfName(BackupService.fileName(t)))
        assertEquals(null, BackupService.timeOfName("other.zip"))
    }
}
