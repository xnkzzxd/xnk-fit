package app.xnkfit.ui.latihan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.data.program.DayView
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.ui.common.ChoiceCard
import app.xnkfit.ui.common.MinTouch

/** Display name of a generated day (Indonesian, from resources); own routines show the name the user typed. */
@Composable
fun dayTitle(day: DayView): String = when (day.dayName) {
    null -> day.name
    else -> stringResource(
        when (day.dayName) {
            app.xnkfit.domain.training.DayName.FULL_A -> R.string.lat_day_full_a
            app.xnkfit.domain.training.DayName.FULL_B -> R.string.lat_day_full_b
            app.xnkfit.domain.training.DayName.FULL_C -> R.string.lat_day_full_c
            app.xnkfit.domain.training.DayName.UPPER_A -> R.string.lat_day_upper_a
            app.xnkfit.domain.training.DayName.LOWER_A -> R.string.lat_day_lower_a
            app.xnkfit.domain.training.DayName.UPPER_B -> R.string.lat_day_upper_b
            app.xnkfit.domain.training.DayName.LOWER_B -> R.string.lat_day_lower_b
        },
    )
}

@Composable
fun settingLabel(code: String): String = stringResource(
    when (code) {
        "gym" -> R.string.lat_setting_gym // text-ok: stored code
        "home-bw" -> R.string.lat_setting_home_bw // text-ok: stored code
        "home-db" -> R.string.lat_setting_home_db // text-ok: stored code
        else -> R.string.lat_setting_custom
    },
)

/** Latihan home: the first profile questions, or the program's day list with "Mulai" (L-1, L-5). */
@Composable
fun LatihanScreen(vm: LatihanViewModel, onStart: (dayId: String?, empty: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.nav_workout), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        val program = ui.program
        if (ui.loading) return@LazyColumn
        if (program == null) {
            profileQuestions(ui, vm)
        } else {
            item { ProgramHeader(program.setting, program.source.name, program.level) }
            if (ui.missing.isNotEmpty()) item { Text(stringResource(R.string.lat_missing_note), style = MaterialTheme.typography.bodyMedium) }
            items(program.days, key = { it.id }) { day ->
                DayCard(day, next = day.id == ui.nextDayId, onStart = { onStart(day.id, false) })
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onStart(null, true) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_empty_workout)) }
                    OutlinedButton(onClick = vm::editProfile, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_change_profile)) }
                }
            }
        }
    }
}

@Composable
private fun ProgramHeader(setting: String, source: String, level: Int) {
    Column {
        Text(settingLabel(setting), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when (source) {
                    "OWN" -> R.string.lat_source_own // text-ok: enum name
                    "JELAJAHI" -> R.string.lat_source_jelajahi // text-ok: enum name
                    else -> R.string.lat_source_auto
                },
            ) + " · " + stringResource(R.string.lat_level, level),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DayCard(day: DayView, next: Boolean, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
            if (next) Text(stringResource(R.string.lat_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(dayTitle(day), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.lat_day_summary, day.items.size, day.estMinutes),
                style = MaterialTheme.typography.bodyMedium,
            )
            day.items.forEach { Text("• " + it.name + " · " + it.sets + "×" + it.repMin + "–" + it.repMax, style = MaterialTheme.typography.bodySmall) } // text-ok: numbers
            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                Button(onClick = onStart, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_start)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.profileQuestions(ui: LatihanUi, vm: LatihanViewModel) {
    val d = ui.draft
    item { Text(stringResource(R.string.lat_q_intro), style = MaterialTheme.typography.bodyMedium) }
    item { Text(stringResource(R.string.lat_q_place), style = MaterialTheme.typography.titleMedium) }
    items(listOf(EquipmentSetting.GYM, EquipmentSetting.HOME_BW, EquipmentSetting.HOME_DB)) { s ->
        ChoiceCard(d.setting == s, settingLabel(s.code)) { vm.draft { it.copy(setting = s) } }
    }
    item { Text(stringResource(R.string.lat_q_days), style = MaterialTheme.typography.titleMedium) }
    item {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4).forEach { n -> Box(d.days == n, n.toString(), Modifier.weight(1f)) { vm.draft { it.copy(days = n) } } } // text-ok: number
        }
    }
    item { Text(stringResource(R.string.lat_q_minutes), style = MaterialTheme.typography.titleMedium) }
    item {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            listOf(30, 45, 60).forEach { n -> Box(d.minutes == n, stringResource(R.string.lat_minutes_value, n), Modifier.weight(1f)) { vm.draft { it.copy(minutes = n) } } }
        }
    }
    item { Text(stringResource(R.string.lat_q_experience), style = MaterialTheme.typography.titleMedium) }
    items(Experience.entries) { e ->
        ChoiceCard(
            d.experience == e,
            stringResource(
                when (e) {
                    Experience.BARU_MULAI -> R.string.lat_exp_new
                    Experience.BEBERAPA_BULAN -> R.string.lat_exp_months
                    Experience.LEBIH_SETAHUN -> R.string.lat_exp_year
                },
            ),
        ) { vm.draft { it.copy(experience = e) } }
    }
    ui.noProgram?.let {
        item {
            Text(
                stringResource(if (it == NoProgram.SCREENING_NEEDED) R.string.lat_blocked_screening else R.string.lat_blocked_stopped),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    item {
        Button(onClick = vm::createProgram, enabled = d.complete && !ui.working, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
            Text(stringResource(R.string.lat_create))
        }
    }
    if (ui.program == null && ui.draft.complete) item { OutlinedButton(onClick = vm::cancelEdit, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lat_cancel)) } }
}

@Composable
private fun Box(selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    ChoiceCard(selected, label, modifier, onClick = onClick)
}
