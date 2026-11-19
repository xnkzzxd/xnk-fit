package app.xnkfit.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import app.xnkfit.R
import app.xnkfit.data.health.HealthConnectSteps
import app.xnkfit.domain.health.StepsCard
import app.xnkfit.ui.common.MinTouch

/** Steps (L-15). Nothing is drawn while hidden. The explanation is on the card; the system dialog opens only from its button. */
@Composable
fun StepsCardView(vm: StepsViewModel, modifier: Modifier = Modifier) {
    val card by vm.card.collectAsState()
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.onPermissionAnswered() }
    LaunchedEffect(Unit) { vm.refresh() }
    when (val c = card) {
        StepsCard.Hidden -> Unit
        StepsCard.AskPermission -> Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.steps_why), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { launcher.launch(HealthConnectSteps.PERMISSIONS) }, Modifier.heightIn(min = MinTouch)) {
                    Text(stringResource(R.string.steps_allow))
                }
            }
        }
        is StepsCard.Shown -> Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.steps_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.steps_today, c.today), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.steps_average, c.weeklyAverage), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.steps_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
