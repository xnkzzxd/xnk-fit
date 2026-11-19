package app.xnkfit.ui.latihan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.latihan.DraftItem
import app.xnkfit.ui.common.MinTouch

/** Editor of an own routine, or of one day of the program (L-5, L-9). Every button is at least 48 dp. */
@Composable
fun RoutineEditor(e: EditorUi, vm: LatihanViewModel, modifier: Modifier = Modifier) {
    val picker = e.picker
    if (picker != null) return ExercisePicker(picker, onPick = vm::pick, onBack = vm::closePicker, modifier)
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(if (e.programId == null) R.string.lat_edit_new_title else R.string.lat_edit_title),
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (e.fromProgram) item { Text(stringResource(R.string.lat_edit_becomes_own), style = MaterialTheme.typography.bodyMedium) }
        if (!e.fromProgram) item {
            OutlinedTextField(
                value = e.name, onValueChange = vm::rename, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lat_edit_name)) },
            )
        }
        itemsIndexed(e.items, key = { _, it -> it.exerciseId }) { i, item -> EditRow(i, item, e.items.size, vm) }
        item {
            OutlinedButton(onClick = vm::openPicker, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_add)) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::closeEditor, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_cancel)) }
                Button(onClick = vm::saveEditor, enabled = e.canSave, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_save)) }
            }
        }
    }
}

@Composable
private fun EditRow(i: Int, item: DraftItem, count: Int, vm: LatihanViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Stepper(stringResource(R.string.lat_edit_sets), item.sets.toString(), { vm.changeSets(i, -1) }, { vm.changeSets(i, 1) }) // text-ok: number
            Stepper(stringResource(R.string.lat_edit_reps), "${item.repMin}–${item.repMax}", { vm.changeReps(i, -1) }, { vm.changeReps(i, 1) }) // text-ok: numbers
            Stepper(stringResource(R.string.lat_edit_rest), stringResource(R.string.lat_edit_seconds, item.restSec), { vm.changeRest(i, -1) }, { vm.changeRest(i, 1) })
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.moveItem(i, -1) }, enabled = i > 0, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_up)) }
                TextButton(onClick = { vm.moveItem(i, 1) }, enabled = i < count - 1, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_down)) }
                TextButton(onClick = { vm.removeItem(i) }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_remove)) }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onMinus, Modifier.widthIn(min = MinTouch).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_minus)) }
        Text(value, Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onPlus, Modifier.widthIn(min = MinTouch).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_plus)) }
    }
}

@Composable
private fun ExercisePicker(options: List<PickOption>, onPick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    val shown = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_back)) }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lat_pick_search)) },
                )
            }
        }
        itemsIndexed(shown, key = { _, o -> o.id }) { _, o ->
            TextButton(onClick = { onPick(o.id) }, Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                Text(o.name, Modifier.fillMaxWidth())
            }
        }
    }
}
