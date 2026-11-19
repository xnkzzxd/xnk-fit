package app.xnkfit.ui.latihan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.data.program.ActivityWeekView
import app.xnkfit.domain.latihan.ActivityChoice
import app.xnkfit.domain.latihan.Effort
import app.xnkfit.domain.latihan.LoggedActivity
import app.xnkfit.domain.training.MuscleGroup
import app.xnkfit.ui.common.MinTouch

@Composable
fun muscleName(g: MuscleGroup): String = stringResource(
    when (g) {
        MuscleGroup.CHEST -> R.string.mus_chest
        MuscleGroup.FRONT_DELT -> R.string.mus_front_delt
        MuscleGroup.SIDE_DELT -> R.string.mus_side_delt
        MuscleGroup.REAR_DELT -> R.string.mus_rear_delt
        MuscleGroup.BICEPS -> R.string.mus_biceps
        MuscleGroup.TRICEPS -> R.string.mus_triceps
        MuscleGroup.LATS -> R.string.mus_lats
        MuscleGroup.TRAPS -> R.string.mus_traps
        MuscleGroup.QUADS -> R.string.mus_quads
        MuscleGroup.HAMS -> R.string.mus_hams
        MuscleGroup.GLUTES -> R.string.mus_glutes
        MuscleGroup.CALVES -> R.string.mus_calves
        MuscleGroup.ABS -> R.string.mus_abs
        MuscleGroup.OBLIQUES -> R.string.mus_obliques
        MuscleGroup.LOWER_BACK -> R.string.mus_lower_back
        MuscleGroup.FOREARM -> R.string.mus_forearm
    },
)

@Composable
fun activityName(code: String): String = stringResource(
    when (ActivityChoice.of(code)) {
        ActivityChoice.JALAN -> R.string.act_jalan
        ActivityChoice.LARI -> R.string.act_lari
        ActivityChoice.SEPEDA -> R.string.act_sepeda
        ActivityChoice.RENANG -> R.string.act_renang
        ActivityChoice.ANGKAT -> R.string.act_angkat
        ActivityChoice.SENAM -> R.string.act_senam
        ActivityChoice.BULUTANGKIS -> R.string.act_bulutangkis
        ActivityChoice.SEPAK -> R.string.act_sepak
        else -> R.string.act_lain
    },
)

@Composable
private fun effortName(e: Effort) = stringResource(
    when (e) { Effort.RINGAN -> R.string.act_effort_light; Effort.SEDANG -> R.string.act_effort_moderate; Effort.BERAT -> R.string.act_effort_vigorous },
)

/** "Otot minggu ini" (L-11): the map plus a text list of working sets, so the tones are never the only signal. */
fun LazyListScope.weekMapSection(ui: InsightsUi) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lat_week_map), style = MaterialTheme.typography.titleMedium)
            val body = ui.figures.body
            if (body != null) {
                MuscleMapView(body, ui.figures.regionLevels, stringResource(R.string.lat_week_map_desc))
                Text(stringResource(R.string.lat_week_legend, app.xnkfit.domain.training.R_TRN_VOL_MAX), style = MaterialTheme.typography.bodySmall)
                val trained = ui.figures.sets.filterValues { it > 0.0 }.entries.sortedByDescending { it.value }
                if (trained.isEmpty()) Text(stringResource(R.string.lat_week_empty), style = MaterialTheme.typography.bodyMedium)
                trained.forEach { (g, s) -> Text(stringResource(R.string.lat_week_row, muscleName(g), s.toInt()), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Activity card with the week's minutes and the logged rows (L-14). */
fun LazyListScope.activitySection(ui: InsightsUi, vm: InsightsViewModel) {
    item {
        val view = ui.activity.view
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stringResource(R.string.lat_activity), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = vm::openActivity, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_activity_add)) }
                }
                if (view != null) ActivitySummary(view, vm)
            }
        }
    }
}

@Composable
private fun ActivitySummary(view: ActivityWeekView, vm: InsightsViewModel) {
    val w = view.week
    Text(stringResource(R.string.lat_activity_week, w.counted, w.target), style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(progress = { w.fraction }, modifier = Modifier.fillMaxWidth())
    if (w.atUpper) Text(stringResource(R.string.lat_activity_upper, w.upper), style = MaterialTheme.typography.bodySmall)
    if (view.rows.isEmpty()) Text(stringResource(R.string.lat_activity_empty), style = MaterialTheme.typography.bodySmall)
    view.rows.asReversed().forEach { r -> ActivityRow(r) { vm.deleteActivity(r.id) } }
}

@Composable
private fun ActivityRow(r: LoggedActivity, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        val effort = Effort.of(r.effort)?.let { effortName(it) }.orEmpty()
        val kcal = if (r.kcal > 0.0) " · " + stringResource(R.string.lat_activity_kcal, r.kcal.toInt()) else ""
        Text(
            activityName(r.code) + " · " + stringResource(R.string.lat_minutes_value, r.minutes) + " · " + effort + kcal,
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDelete, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_delete)) }
    }
}

/** The sheet of the mockup: type chips, minutes stepper, effort, the kcal note. */
@Composable
fun ActivitySheet(a: ActivityUi, vm: InsightsViewModel) {
    if (!a.sheetOpen) return
    AlertDialog(
        onDismissRequest = vm::closeActivity,
        title = { Text(stringResource(R.string.lat_activity_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
                ActivityChoice.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(a.choice == c, role = Role.RadioButton, onClick = { vm.chooseActivity(c) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = a.choice == c, onClick = null)
                        Text(activityName(c.code), Modifier.padding(start = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(stringResource(R.string.lat_activity_minutes), Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.stepMinutes(-5) }, Modifier.widthIn(min = MinTouch).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_minus)) }
                    Text(a.minutes.toString(), Modifier.widthIn(min = 56.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium) // text-ok: number
                    OutlinedButton(onClick = { vm.stepMinutes(5) }, Modifier.widthIn(min = MinTouch).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_plus)) }
                }
                Text(stringResource(R.string.lat_activity_effort), style = MaterialTheme.typography.titleSmall)
                Effort.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(a.effort == e, role = Role.RadioButton, onClick = { vm.chooseEffort(e) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = a.effort == e, onClick = null)
                        Text(effortName(e), Modifier.padding(start = 8.dp))
                    }
                }
                Text(stringResource(R.string.lat_activity_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = vm::saveActivity, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_edit_save)) } },
        dismissButton = { TextButton(onClick = vm::closeActivity, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_cancel)) } },
    )
}

/** Detail of one exercise (L-11): rig only for exercises that have one; otherwise the muscle map and the cues. */
@Composable
fun ExerciseDetail(d: DetailUi?, sex: String, vm: InsightsViewModel) {
    if (d == null) return
    val mainNames = d.main.map { muscleName(it) }
    val helperNames = d.helper.map { muscleName(it) }
    AlertDialog(
        onDismissRequest = vm::closeDetail,
        title = { Text(d.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                if (d.rig != null && d.rigData != null) RigView(d.rig, d.rigData, sex, Modifier.fillMaxWidth().size(220.dp))
                MuscleMapView(d.body, d.regionLevels, stringResource(R.string.lat_detail_map_desc, d.name))
                if (mainNames.isNotEmpty()) Text(stringResource(R.string.lat_detail_main, mainNames.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                if (helperNames.isNotEmpty()) Text(stringResource(R.string.lat_detail_helper, helperNames.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                d.hint?.let { Text(stringResource(R.string.lat_detail_why), style = MaterialTheme.typography.titleSmall); Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (d.cues.isNotEmpty()) {
                    Text(stringResource(R.string.lat_detail_cues), style = MaterialTheme.typography.titleSmall)
                    d.cues.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } // text-ok: bullet
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeDetail, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_back)) } },
    )
}
