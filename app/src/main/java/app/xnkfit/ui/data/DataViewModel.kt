package app.xnkfit.ui.data

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.R
import app.xnkfit.data.backup.BackupError
import app.xnkfit.data.backup.BackupException
import app.xnkfit.data.backup.BackupService
import app.xnkfit.data.backup.BackupSettings
import app.xnkfit.data.backup.BackupSettingsStore
import app.xnkfit.data.backup.DriveAuthException
import app.xnkfit.data.backup.DriveAuthResult
import app.xnkfit.data.backup.DriveAuthorizer
import app.xnkfit.data.backup.DriveBackup
import app.xnkfit.data.backup.PreparedRestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Where a restore stands (B-5, B-6): pick → (other phone?) → typed word → done. */
enum class RestoreStep { NONE, PICK, OTHER_DEVICE, CONFIRM }

data class DataUi(
    val driveAvailable: Boolean = false,
    val settings: BackupSettings = BackupSettings(),
    val lastBackupAt: Long? = null,
    val stale: Boolean = false,
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    @StringRes val error: Int? = null,
    val backups: List<DriveBackup> = emptyList(),
    val step: RestoreStep = RestoreStep.NONE,
    val pending: PreparedRestore? = null,
    /** Google's one-time consent screen to launch (sign-in for `drive.appdata`). */
    val consent: PendingIntent? = null,
    val deviceId: String = "",
)

/** The typed confirmation (B-5): the word must match, case and surrounding spaces aside. */
fun restoreWordMatches(typed: String, word: String): Boolean = typed.trim().equals(word, ignoreCase = true)

@StringRes
fun errorText(e: Throwable): Int = when (e) {
    is BackupException -> when (e.error) {
        BackupError.NEWER_APP -> R.string.data_err_newer
        BackupError.NOT_A_BACKUP -> R.string.data_err_not_backup
        BackupError.BAD_MANIFEST -> R.string.data_err_manifest
        BackupError.CHECKSUM -> R.string.data_err_checksum
        BackupError.TOO_LARGE -> R.string.data_err_too_large
        BackupError.WRITE_FAILED -> R.string.data_err_write
    }
    is DriveAuthException -> R.string.data_err_sign_in
    is IOException -> R.string.data_err_network
    else -> R.string.data_err_generic
}

class DataViewModel(
    private val service: BackupService,
    private val store: BackupSettingsStore,
    /** Null when Drive is not set up in this build. */
    private val auth: DriveAuthorizer?,
    private val deviceId: () -> String,
) : ViewModel() {
    private val _ui = MutableStateFlow(DataUi(driveAvailable = service.driveAvailable && auth != null))
    val ui: StateFlow<DataUi> = _ui

    /** Drive action waiting for Google's consent screen. */
    private var afterConsent: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            store.settings.collect { s -> _ui.update { it.copy(settings = s) }; refreshMeta() }
        }
    }

    private suspend fun refreshMeta() {
        val meta = service.meta()
        val stale = service.isStale(_ui.value.settings)
        _ui.update { it.copy(lastBackupAt = meta?.lastBackupAt, stale = stale, deviceId = withContext(Dispatchers.IO) { deviceId() }) }
    }

    fun setBackupOn(on: Boolean) = viewModelScope.launch { store.setBackupOn(on) }
    fun setPhotosOn(on: Boolean) = viewModelScope.launch { store.setPhotosOn(on) }
    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }

    fun backupNow() = withDrive {
        service.backupToDrive(_ui.value.settings.photosOn)
        store.setNeedsSignIn(false)
        store.setOtherDeviceWaiting(false)
        refreshMeta()
        _ui.update { it.copy(message = R.string.data_done_backup) }
    }

    fun openRestore() = withDrive {
        val list = service.driveBackups()
        _ui.update { it.copy(backups = list, step = RestoreStep.PICK, message = if (list.isEmpty()) R.string.data_no_backups else null) }
    }

    fun pick(b: DriveBackup) = run { show(service.prepareFromDrive(b)) }

    /** Import (B-4): same checks and the same restore as a Drive backup. */
    fun import(open: () -> InputStream?) = run {
        val p = withContext(Dispatchers.IO) { open()?.use { service.prepareFromFile(it) } } ?: return@run
        show(p)
    }

    fun export(open: () -> OutputStream?) = run {
        withContext(Dispatchers.IO) { open()?.use { service.exportTo(it, _ui.value.settings.photosOn) } } ?: return@run
        _ui.update { it.copy(message = R.string.data_done_export) }
    }

    private fun show(p: PreparedRestore) {
        _ui.update { it.copy(pending = p, step = if (p.fromOtherDevice) RestoreStep.OTHER_DEVICE else RestoreStep.CONFIRM) }
    }

    fun acceptOtherDevice() = _ui.update { it.copy(step = RestoreStep.CONFIRM) }

    fun cancelRestore() {
        _ui.value.pending?.backup?.discard()
        _ui.update { it.copy(pending = null, step = RestoreStep.NONE) }
    }

    fun confirmRestore(typed: String, word: String) {
        val p = _ui.value.pending ?: return
        if (!restoreWordMatches(typed, word)) return
        run {
            service.restore(p)
            store.setOtherDeviceWaiting(false)
            _ui.update { it.copy(pending = null, step = RestoreStep.NONE, message = R.string.data_done_restore) }
            refreshMeta()
        }
    }

    /** Result of Google's consent screen. */
    fun onConsent(data: Intent?) {
        val next = afterConsent
        afterConsent = null
        _ui.update { it.copy(consent = null) }
        val a = auth ?: return
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { a.fromConsent(data) } }.getOrNull()
            if (r is DriveAuthResult.Token && next != null) run { next() } else _ui.update { it.copy(error = R.string.data_err_sign_in) }
        }
    }

    /** Runs a Drive action once a token is available; first asks for consent when Google needs it. */
    private fun withDrive(block: suspend () -> Unit) {
        val a = auth ?: return
        if (!service.driveAvailable) return
        run {
            when (val r = withContext(Dispatchers.IO) { a.authorize() }) {
                is DriveAuthResult.Token -> block()
                is DriveAuthResult.NeedsConsent -> { afterConsent = block; _ui.update { it.copy(consent = r.intent) } }
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null, error = null) }
            try {
                block()
            } catch (e: Exception) {
                if (e is DriveAuthException) store.setNeedsSignIn(true)
                _ui.update { it.copy(error = errorText(e), pending = null, step = RestoreStep.NONE) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }
}
