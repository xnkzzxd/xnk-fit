package app.xnkfit.ui.pengaturan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.ui.common.MinTouch

/** Typed-word confirmation for "Hapus semua data" (PRD B-7). */
@Composable
fun DeleteDialog(vm: DeleteDataViewModel) {
    val ui by vm.ui.collectAsState()
    if (!ui.open) return
    val word = stringResource(R.string.del_word)
    AlertDialog(
        onDismissRequest = vm::cancel,
        title = { Text(stringResource(R.string.del_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.del_body), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.del_drive), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = ui.typed, onValueChange = vm::type, singleLine = true, enabled = !ui.busy,
                    label = { Text(stringResource(R.string.del_type, word)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui.busy) Text(stringResource(R.string.del_busy))
                if (ui.failed) Text(stringResource(R.string.del_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = vm::confirm,
                enabled = !ui.busy && deleteWordMatches(ui.typed, word),
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.del_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = vm::cancel, enabled = !ui.busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.del_cancel)) }
        },
    )
}
