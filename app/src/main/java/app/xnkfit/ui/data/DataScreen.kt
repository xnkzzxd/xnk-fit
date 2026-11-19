package app.xnkfit.ui.data

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.data.backup.BackupService
import app.xnkfit.data.backup.DriveBackup
import app.xnkfit.data.backup.PreparedRestore
import app.xnkfit.ui.common.MinTouch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID") // text-ok: locale tag

private fun formatTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, ID_LOCALE).format(Date(ms))

/** Data screen (Design §9, mockup pengaturan.html "Data"): Drive backup, restore, export, import (B-1…B-6). */
@Composable
fun DataScreen(vm: DataViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r -> vm.onConsent(r.data) }
    LaunchedEffect(ui.consent) {
        ui.consent?.let { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP)) { uri ->
        if (uri != null) vm.export { context.contentResolver.openOutputStream(uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import { context.contentResolver.openInputStream(uri) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_back)) }
            Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        DriveCard(ui, vm)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.data_file_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.data_export_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(BackupService.exportName(System.currentTimeMillis())) },
                        enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.data_export)) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf(ZIP, ANY_BINARY)) },
                        enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text(stringResource(R.string.data_import)) }
                }
            }
        }
        if (ui.busy) Text(stringResource(R.string.data_busy))
        ui.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
    }

    when (ui.step) {
        RestoreStep.PICK -> if (ui.backups.isNotEmpty()) PickDialog(ui.backups, ui.deviceId, vm::pick, vm::cancelRestore)
        RestoreStep.OTHER_DEVICE -> OtherDeviceDialog(ui.pending, vm::acceptOtherDevice, vm::cancelRestore)
        RestoreStep.CONFIRM -> ui.pending?.let { ConfirmDialog(it, ui.busy, vm::confirmRestore, vm::cancelRestore) }
        RestoreStep.NONE -> Unit
    }
    ui.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text(stringResource(R.string.data_err_title)) },
            text = { Text(stringResource(err)) },
            confirmButton = { TextButton(onClick = vm::clearMessage, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_close)) } },
        )
    }
}

@Composable
private fun DriveCard(ui: DataUi, vm: DataViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.data_drive_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!ui.driveAvailable) {
                Text(stringResource(R.string.data_drive_unavailable))
                return@Column
            }
            Text(stringResource(R.string.data_drive_where), style = MaterialTheme.typography.bodySmall)
            Text(ui.lastBackupAt?.let { stringResource(R.string.data_last_backup, formatTime(it)) } ?: stringResource(R.string.data_never))
            val warn = MaterialTheme.colorScheme.error
            if (ui.stale) Text(stringResource(R.string.data_stale), color = warn)
            if (ui.settings.needsSignIn) Text(stringResource(R.string.data_sign_in_again), color = warn)
            if (ui.settings.otherDeviceWaiting) Text(stringResource(R.string.data_other_waiting), color = warn)
            SwitchRow(stringResource(R.string.data_backup_on), null, ui.settings.backupOn, vm::setBackupOn)
            SwitchRow(stringResource(R.string.data_photos_on), stringResource(R.string.data_photos_hint), ui.settings.photosOn, vm::setPhotosOn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.backupNow() }, enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.data_backup_now))
                }
                OutlinedButton(onClick = { vm.openRestore() }, enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.data_restore))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).semantics { role = Role.Switch },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PickDialog(list: List<DriveBackup>, myDevice: String, onPick: (DriveBackup) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.data_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { b ->
                    val time = BackupService.timeOfName(b.name)?.let(::formatTime) ?: b.name
                    val who = stringResource(if (b.deviceId == myDevice) R.string.data_this_phone else R.string.data_other_phone)
                    OutlinedButton(onClick = { onPick(b) }, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.data_item, time, who))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_cancel)) } },
    )
}

@Composable
private fun OtherDeviceDialog(p: PreparedRestore?, onContinue: () -> Unit, onCancel: () -> Unit) {
    val made = p?.manifest?.createdAt?.let(::formatTime).orEmpty()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.data_other_title)) },
        text = { Text(stringResource(R.string.data_other_body, made)) },
        confirmButton = { Button(onClick = onContinue, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_continue)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(p: PreparedRestore, busy: Boolean, onConfirm: (String, String) -> Unit, onCancel: () -> Unit) {
    val word = stringResource(R.string.data_restore_word)
    var typed by remember { mutableStateOf("") }
    val m = p.manifest
    val rows = m.tableRows
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.data_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.data_restore_made, formatTime(m.createdAt), m.appVersion))
                Text(stringResource(R.string.data_restore_rows, rows[T_WORKOUT] ?: 0, rows[T_FOOD_LOG] ?: 0, rows[T_MEASURE] ?: 0))
                if (m.photos) Text(stringResource(R.string.data_restore_photos, m.photoCount))
                else Text(stringResource(R.string.data_restore_photos_kept), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.data_restore_warning), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, singleLine = true,
                    label = { Text(stringResource(R.string.data_restore_type, word)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(typed, word) },
                enabled = !busy && restoreWordMatches(typed, word),
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.data_restore)) }
        },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.data_cancel)) } },
    )
}

private const val ZIP = "application/zip" // text-ok: MIME type
private const val ANY_BINARY = "application/octet-stream" // text-ok: MIME type
private const val T_WORKOUT = "workout" // text-ok: table name
private const val T_FOOD_LOG = "food_log" // text-ok: table name
private const val T_MEASURE = "measure" // text-ok: table name
