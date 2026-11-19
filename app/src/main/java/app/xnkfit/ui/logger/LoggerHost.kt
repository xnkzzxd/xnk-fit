package app.xnkfit.ui.logger

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Logger tab with the system questions of the rest timer, asked only when the first timer starts (Design §1, §14).
 * Notifications first (Android 13+), then the exact-alarm page (Android 12+). A "no" anywhere leaves the in-app timer
 * with the notice from [AlarmNotice.IN_APP_ONLY].
 */
@Composable
fun LoggerHost(vm: LoggerViewModel, alarms: RestAlarms, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ask by vm.ask.collectAsState()
    val exactPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onAccessAnswered() }
    fun openExactPage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return vm.onAccessAnswered()
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, ("package:" + context.packageName).toUri()) // text-ok: uri scheme
        try { exactPage.launch(intent) } catch (e: Exception) { vm.onAccessAnswered() }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (alarms.access() == AlarmAccess.NEEDS_EXACT_ALARM) openExactPage() else vm.onAccessAnswered()
    }
    LaunchedEffect(ask) {
        when (ask) {
            AlarmAccess.NEEDS_NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.onAccessAnswered()
            AlarmAccess.NEEDS_EXACT_ALARM -> openExactPage()
            else -> Unit
        }
    }
    LoggerScreen(vm, modifier)
}
