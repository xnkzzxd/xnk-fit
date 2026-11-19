package app.xnkfit.ui.latihan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.data.program.ProgramView
import app.xnkfit.domain.latihan.R_JEL_SHELF
import app.xnkfit.domain.training.ChangeKind
import app.xnkfit.domain.training.ProgramChange
import app.xnkfit.domain.training.ReasonCode
import app.xnkfit.ui.common.MinTouch

/** One-line reason of a proposed change (L-4, NF-3): what was seen, in neutral words. */
@Composable
fun reasonText(code: ReasonCode): String = stringResource(
    when (code) {
        ReasonCode.HARDER_VARIANT -> R.string.lat_reason_harder
        ReasonCode.LEVEL_UP -> R.string.lat_reason_level
        else -> R.string.lat_reason_updated
    },
)

@Composable
private fun changeTitle(c: ProgramChange, review: ReviewUi): String {
    fun name(id: String?) = review.names[id] ?: id.orEmpty()
    val current = review.offer.current.days.getOrNull(c.dayPosition)?.items?.getOrNull(c.itemPosition)?.exerciseId
    return when (c.kind) {
        ChangeKind.SWAP_EXERCISE -> stringResource(R.string.lat_change_swap, name(c.fromExerciseId), name(c.toExerciseId))
        ChangeKind.SETS -> stringResource(R.string.lat_change_sets, name(current), c.fromSets ?: 0, c.toSets ?: 0)
        ChangeKind.REPS -> stringResource(
            R.string.lat_change_reps, name(current), c.fromReps?.first ?: 0, c.fromReps?.last ?: 0, c.toReps?.first ?: 0, c.toReps?.last ?: 0,
        )
        ChangeKind.REPLACE_PROGRAM -> stringResource(R.string.lat_change_replace)
    }
}

/** L-4: the proposals with their reasons; each one is ticked or not, then applied or put off. */
@Composable
fun ReviewCard(review: ReviewUi, vm: LatihanViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lat_review_title), style = MaterialTheme.typography.titleMedium)
            review.offer.changes.forEachIndexed { i, c ->
                val on = i in review.ticked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MinTouch).toggleable(on, role = Role.Checkbox, onValueChange = { vm.toggleChange(i) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(changeTitle(c, review), style = MaterialTheme.typography.bodyMedium)
                        Text(reasonText(c.reason.code), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::declineReview, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_review_later)) }
                Button(onClick = vm::applyReview, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_review_apply)) }
            }
        }
    }
}

/** L-9: own routines with "Buat". */
@Composable
fun RoutinesSection(routines: List<ProgramView>, vm: LatihanViewModel, onStart: (dayId: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.lat_routines), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = vm::newRoutine, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_create_short)) }
        }
        if (routines.isEmpty()) Text(stringResource(R.string.lat_routines_empty), style = MaterialTheme.typography.bodyMedium)
        routines.forEach { r ->
            val day = r.days.firstOrNull() ?: return@forEach
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                    Text(day.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.lat_day_summary, day.items.size, day.estMinutes), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { vm.deleteRoutine(r.id) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_delete)) }
                        OutlinedButton(onClick = { vm.editRoutine(r.id) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit)) }
                        Button(onClick = { onStart(day.id) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_start)) }
                    }
                }
            }
        }
    }
}

/** L-9: the four cards of the Jelajahi shelf. */
@Composable
fun JelajahiSection(vm: LatihanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lat_jelajahi), style = MaterialTheme.typography.titleMedium)
        R_JEL_SHELF.forEach { t ->
            val title = stringResource(
                when (t.id) {
                    "full_body_gym" -> R.string.lat_jel_full_body // text-ok: stored code
                    "upper_lower_gym" -> R.string.lat_jel_upper_lower // text-ok: stored code
                    "home_bodyweight" -> R.string.lat_jel_home_bw // text-ok: stored code
                    else -> R.string.lat_jel_home_db
                },
            )
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.lat_jel_meta, t.days, settingLabel(t.setting.code)), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.useTemplate(t.id) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_use)) }
                }
            }
        }
    }
}

@Composable
fun AutoSwitch(autoOff: Boolean, vm: LatihanViewModel) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).toggleable(!autoOff, role = Role.Switch, onValueChange = { vm.setAutoOff(!it) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.lat_auto_switch), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = !autoOff, onCheckedChange = null)
    }
}
