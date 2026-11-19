package app.xnkfit.ui.pengaturan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.xnkfit.R
import app.xnkfit.data.prefs.ReminderGroup
import app.xnkfit.data.reminder.ReminderScheduler
import app.xnkfit.data.prefs.ThemeMode
import app.xnkfit.ui.common.MinTouch

/** Pengaturan (PRD P-7). Order of the sections that exist in v1; Profil, Latihan and Makan edits follow the stores they need. */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    deleteVm: DeleteDataViewModel,
    onBack: () -> Unit,
    onOpenData: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState()
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.set_back)) }
            Text(stringResource(R.string.set_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        RemindersCard(ui.settings.reminders, vm::setReminder)
        LookCard(ui.settings.theme, vm::setTheme)
        PermissionsCard()
        ContentCard(ui, vm::checkContent)
        LinkCard(R.string.set_data_title, R.string.set_data_hint, onOpenData)
        OutlinedButton(onClick = deleteVm::open, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.del_open)) }
        LinkCard(R.string.set_help_title, R.string.set_help_hint, onOpenHelp)
        LinkCard(R.string.set_privacy_title, R.string.set_privacy_hint, onOpenPrivacy)
    }
    DeleteDialog(deleteVm)
}

@Composable
private fun SectionCard(title: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val state = stringResource(if (checked) R.string.set_perm_on else R.string.set_perm_off)
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch)
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RemindersCard(on: Set<ReminderGroup>, set: (ReminderGroup, Boolean) -> Unit) {
    val context = LocalContext.current
    // O-3: the notification permission is asked when a switch is turned on. A "no" changes nothing else and is not asked again.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun change(group: ReminderGroup, enabled: Boolean) {
        set(group, enabled)
        ReminderScheduler.sync(context.applicationContext, (if (enabled) on + group else on - group).isNotEmpty())
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    SectionCard(R.string.set_remind_title) {
        Text(stringResource(R.string.set_remind_max), style = MaterialTheme.typography.bodySmall)
        SwitchRow(stringResource(R.string.set_remind_latihan), stringResource(R.string.set_remind_latihan_time), ReminderGroup.LATIHAN in on) { change(ReminderGroup.LATIHAN, it) }
        SwitchRow(stringResource(R.string.set_remind_makan), stringResource(R.string.set_remind_makan_time), ReminderGroup.MAKAN in on) { change(ReminderGroup.MAKAN, it) }
        SwitchRow(stringResource(R.string.set_remind_timbang), stringResource(R.string.set_remind_timbang_time), ReminderGroup.TIMBANG in on) { change(ReminderGroup.TIMBANG, it) }
        Text(stringResource(R.string.set_remind_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LookCard(theme: ThemeMode, set: (ThemeMode) -> Unit) {
    SectionCard(R.string.set_look_title) {
        Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.selectableGroup()) {
            ThemeRow(R.string.set_theme_system, theme == ThemeMode.SYSTEM) { set(ThemeMode.SYSTEM) }
            ThemeRow(R.string.set_theme_light, theme == ThemeMode.LIGHT) { set(ThemeMode.LIGHT) }
            ThemeRow(R.string.set_theme_dark, theme == ThemeMode.DARK) { set(ThemeMode.DARK) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.set_units), Modifier.weight(1f))
            Text(stringResource(R.string.set_units_value), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ThemeRow(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    // Bumped after every system answer so the status line is read again.
    var refresh by remember { mutableIntStateOf(0) }
    fun granted(permission: String) = refresh >= 0 && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    fun openSystem() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) // text-ok: intent data
    }
    SectionCard(R.string.set_perm_title) {
        Text(stringResource(R.string.set_perm_hint), style = MaterialTheme.typography.bodySmall)
        if (notifNeeded) {
            PermissionRow(R.string.set_perm_notif, granted(Manifest.permission.POST_NOTIFICATIONS), { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }, ::openSystem)
        } else {
            Text(stringResource(R.string.set_perm_notif) + ": " + stringResource(R.string.set_perm_notif_old))
        }
        PermissionRow(R.string.set_perm_camera, granted(Manifest.permission.CAMERA), { ask.launch(Manifest.permission.CAMERA) }, ::openSystem)
        Text(stringResource(R.string.set_perm_hc), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onAsk: () -> Unit, onSystem: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f))
            Text(stringResource(if (granted) R.string.set_perm_on else R.string.set_perm_off), fontWeight = FontWeight.Bold)
        }
        if (!granted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAsk, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.set_perm_ask)) }
                TextButton(onClick = onSystem, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.set_perm_system)) }
            }
        }
    }
}

@Composable
private fun ContentCard(ui: SettingsUi, onCheck: () -> Unit) {
    SectionCard(R.string.set_content_title) {
        ui.contentVersion?.let { Text(stringResource(R.string.set_content_version, it)) }
        Text(stringResource(R.string.set_content_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = onCheck,
            enabled = ui.update != UpdateState.CHECKING,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(stringResource(R.string.set_content_check)) }
        updateText(ui.update)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun updateText(state: UpdateState): Int? = when (state) {
    UpdateState.IDLE -> null
    UpdateState.CHECKING -> R.string.set_update_checking
    UpdateState.UPDATED -> R.string.set_update_updated
    UpdateState.UP_TO_DATE -> R.string.set_update_current
    UpdateState.UNAVAILABLE -> R.string.set_update_unavailable
    UpdateState.APP_TOO_OLD -> R.string.set_update_old_app
    UpdateState.FAILED -> R.string.set_update_failed
}

/** A card that opens another screen. */
@Composable
fun LinkCard(title: Int, hint: Int, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpen, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.set_open)) }
        }
    }
}
