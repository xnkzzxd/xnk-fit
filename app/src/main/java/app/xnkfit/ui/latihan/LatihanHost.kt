package app.xnkfit.ui.latihan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.xnkfit.R
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.logger.LoggerHost
import app.xnkfit.ui.logger.LoggerPhase
import app.xnkfit.ui.logger.LoggerViewModel
import app.xnkfit.ui.logger.RestAlarms

/**
 * The Latihan tab: its home list, and the logger once "Mulai" is pressed. An unfinished workout opens the logger at once
 * so "Lanjutkan latihan?" is the first thing seen (Design §4); a workout in progress is never hidden behind the list.
 */
@Composable
fun LatihanHost(vm: LatihanViewModel, insights: InsightsViewModel, logger: LoggerViewModel, alarms: RestAlarms, modifier: Modifier = Modifier) {
    val loggerUi by logger.ui.collectAsState()
    var inLogger by rememberSaveable { mutableStateOf(false) }
    val busy = loggerUi.phase == LoggerPhase.RESUME || loggerUi.phase == LoggerPhase.ACTIVE || loggerUi.phase == LoggerPhase.DONE
    LaunchedEffect(inLogger, loggerUi.phase) { if (!inLogger && loggerUi.phase == LoggerPhase.START) vm.refresh() }
    // The figures are read after the first frame, so the list is on screen without any image first (Design §10).
    LaunchedEffect(Unit) { insights.load() }
    if (inLogger || busy) {
        BackHandler(enabled = !busy) { inLogger = false }
        Column(modifier) {
            if (!busy) TextButton(onClick = { inLogger = false }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_back)) }
            LoggerHost(logger, alarms)
        }
    } else {
        LatihanScreen(vm, insights, onStart = { dayId, empty -> logger.useDay(dayId, empty); inLogger = true }, modifier = modifier)
    }
}
