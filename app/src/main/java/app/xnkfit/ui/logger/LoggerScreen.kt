package app.xnkfit.ui.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.logger.LoggerMode
import app.xnkfit.domain.logger.LoggerSet
import app.xnkfit.domain.logger.R_TRN_LOGGER_HOLD_STEP_S
import app.xnkfit.domain.logger.R_TRN_LOGGER_REPS_STEP
import app.xnkfit.domain.logger.SetKind
import app.xnkfit.domain.logger.SetMeasure
import app.xnkfit.domain.logger.clampBand
import app.xnkfit.domain.logger.clampHold
import app.xnkfit.domain.logger.clampKg
import app.xnkfit.domain.logger.clampReps
import app.xnkfit.domain.training.FormRating
import app.xnkfit.domain.training.R_TRN_PROG_STEP_DUMBBELL_KG
import app.xnkfit.domain.training.ReasonCode
import app.xnkfit.ui.common.ChoiceCard
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.common.StepButtons

/** Logger screen (mockup logger.html). Stateless apart from the sheet search text; every action goes to the ViewModel. */
@Composable
fun LoggerScreen(vm: LoggerViewModel, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    when (ui.phase) {
        LoggerPhase.LOADING -> Unit
        LoggerPhase.START -> StartPane(ui, vm, modifier)
        LoggerPhase.RESUME -> Column(modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.lg_resume_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.lg_resume_sub))
            ButtonPair(stringResource(R.string.lg_resume_finish), vm::finishResumed, stringResource(R.string.lg_resume), vm::resume)
        }
        LoggerPhase.ACTIVE -> ActivePane(ui, vm, modifier)
        LoggerPhase.DONE -> Column(modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.lg_done_title), style = MaterialTheme.typography.headlineSmall)
            ui.doneTotals?.let { Stats(ui.copy(blocks = emptyList()), it.doneSets, it.volumeKg, showDuration = false) }
            Text(stringResource(R.string.lg_feeling_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                (1..FEELING_MAX).forEach { n ->
                    ChoiceCard(ui.doneFeeling == n, n.toString(), Modifier.weight(1f)) { vm.setFeeling(n) } // text-ok: number
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = ui.doneNote, onValueChange = vm::setNote, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.lg_note)) },
            )
            Button(vm::closeDone, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_done_close)) }
        }
    }
    ui.sheet?.let { LoggerSheets(it, ui, vm) }
}

@Composable
private fun StartPane(ui: LoggerUi, vm: LoggerViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text(
            ui.plannedDay?.let { stringResource(dayLabel(it.name)) } ?: stringResource(R.string.lg_free_workout),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!ui.hasProgram) Text(stringResource(R.string.lg_no_program))
        if (ui.chosenMode == null) Text(stringResource(R.string.lg_first_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.lg_how), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lg_how_sub))
        // L-6 / D-06: neither card is selected until the user picks; the start button waits for that.
        ChoiceCard(ui.chosenMode == LoggerMode.DIPANDU, stringResource(R.string.lg_mode_dipandu), hint = stringResource(R.string.lg_mode_dipandu_sub)) {
            vm.chooseMode(LoggerMode.DIPANDU)
        }
        ChoiceCard(ui.chosenMode == LoggerMode.BEBAS, stringResource(R.string.lg_mode_bebas), hint = stringResource(R.string.lg_mode_bebas_sub)) {
            vm.chooseMode(LoggerMode.BEBAS)
        }
        StepButtons(stringResource(R.string.lg_start), vm::start, nextEnabled = ui.chosenMode != null)
    }
}

@Composable
private fun ActivePane(ui: LoggerUi, vm: LoggerViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.dayName?.let { stringResource(dayLabel(it.name)) } ?: stringResource(R.string.lg_free_workout),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                )
                TextButton({ vm.openSheet(LoggerSheet.Menu) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_menu)) }
                Button({ vm.openSheet(LoggerSheet.Finish) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_finish)) }
            }
            Stats(ui, ui.totals.doneSets, ui.totals.volumeKg, showDuration = true)
        }
        RestBar(ui, vm)
        Column(Modifier.weight(1f)) {
            if (ui.mode == LoggerMode.DIPANDU) GuidedPane(ui, vm) else TablePane(ui, vm)
        }
    }
}

@Composable
private fun Stats(ui: LoggerUi, sets: Int, volume: Double, showDuration: Boolean) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        if (showDuration) {
            val sec = ((ui.nowMs - ui.startedAt).coerceAtLeast(0) / MS_PER_S).toInt()
            Stat(minSec(sec), stringResource(R.string.lg_duration), Modifier.weight(1f))
        }
        Stat(formatKg(volume), stringResource(R.string.lg_volume), Modifier.weight(1f))
        Stat(sets.toString(), stringResource(R.string.lg_sets), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) = Column(modifier) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    Text(label, style = MaterialTheme.typography.labelMedium)
}

/** Rest timer bar (L-8). Shown while a rest runs; the alarm notice stays until the user allows or the workout ends. */
@Composable
private fun RestBar(ui: LoggerUi, vm: LoggerViewModel) {
    val left = ui.restLeftSec
    if (ui.restEndsAt != null) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (left > 0) R.string.lg_rest else R.string.lg_rest_over), style = MaterialTheme.typography.labelLarge)
                    if (left > 0) Text(minSec(left), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(vm::extendRest, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_rest_plus)) }
                OutlinedButton(vm::skipRest, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_rest_skip)) }
            }
        }
    }
    if (ui.alarmNotice == AlarmNotice.IN_APP_ONLY) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lg_alarm_off), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(vm::askAgain, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_alarm_allow)) }
        }
    }
}

/** Dipandu: one exercise, one set at a time (L-6). */
@Composable
private fun GuidedPane(ui: LoggerUi, vm: LoggerViewModel) {
    val next = ui.next
    val block = next?.let { ui.blockOf(it) }
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(12.dp)) {
        if (next == null || block == null) {
            Text(stringResource(if (ui.blocks.isEmpty()) R.string.lg_no_program else R.string.lg_all_done))
            Button(vm::openAdd, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_add_exercise)) }
            return@Column
        }
        val exercises = ui.blocks.map { it.slot to it.exerciseId }.distinct()
        val position = exercises.indexOf(block.slot to block.exerciseId) + 1
        Text(stringResource(R.string.lg_exercise_of, position, exercises.size), style = MaterialTheme.typography.labelLarge)
        Text(block.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.lg_set_of, block.sets.indexOf(next) + 1, block.sets.size))
        ButtonPair(
            stringResource(R.string.lg_swap), { vm.openSwap(block.slot, block.exerciseId) },
            stringResource(R.string.lg_list), { vm.openSheet(LoggerSheet.ExerciseList) }, outlinedBoth = true,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lg_set_n, block.sets.indexOf(next) + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton({ vm.openSheet(LoggerSheet.Kind(next.id)) }, Modifier.heightIn(min = MinTouch)) { Text(kindLabel(next.kind)) }
                }
                Suggestion(block)
                Fields(next, block, vm)
                FormRow(next, vm)
                Button({ vm.toggleDone(next.id) }, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.lg_done_set)) }
            }
        }
    }
}

@Composable
private fun Suggestion(block: ExerciseBlock) {
    val t = block.target
    val lo = t?.repMin ?: block.repMin
    val hi = t?.repMax ?: block.repMax
    val line = when {
        block.measure == SetMeasure.HOLD_SECONDS -> stringResource(R.string.lg_hold_hint)
        t?.loadKg != null && lo != null && hi != null -> stringResource(R.string.lg_suggest_kg, formatKg(t.loadKg), lo, hi)
        t?.bandLevel != null && lo != null && hi != null -> stringResource(R.string.lg_suggest_band, t.bandLevel, lo, hi)
        block.measure == SetMeasure.KG_REPS && t != null -> stringResource(R.string.lg_suggest_first)
        lo != null && hi != null -> stringResource(R.string.lg_suggest_reps, lo, hi)
        else -> null
    }
    if (line != null) Text(stringResource(R.string.lg_suggest) + " · " + line, style = MaterialTheme.typography.bodyMedium) // text-ok: separator
    if (t != null && block.measure != SetMeasure.HOLD_SECONDS) {
        Text(reasonText(t.reason.code, t.reason.stepKg, t.reason.reps), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun reasonText(code: ReasonCode, stepKg: Double?, reps: Int?): String = when (code) {
    ReasonCode.FIRST_TIME -> stringResource(R.string.lg_reason_FIRST_TIME)
    ReasonCode.MORE_REPS -> stringResource(R.string.lg_reason_MORE_REPS)
    ReasonCode.ADD_LOAD -> stringResource(R.string.lg_reason_ADD_LOAD, formatKg(stepKg ?: 0.0))
    ReasonCode.NEED_EXTRA_REPS -> stringResource(R.string.lg_reason_NEED_EXTRA_REPS, formatKg(stepKg ?: 0.0), reps ?: 0)
    ReasonCode.FORM_HOLD -> stringResource(R.string.lg_reason_FORM_HOLD)
    ReasonCode.ADD_SET -> stringResource(R.string.lg_reason_ADD_SET)
    ReasonCode.HARDER_VARIANT -> stringResource(R.string.lg_reason_HARDER_VARIANT)
    ReasonCode.BAND_UP -> stringResource(R.string.lg_reason_BAND_UP)
    ReasonCode.RETURN_AFTER_BREAK -> stringResource(R.string.lg_reason_RETURN_AFTER_BREAK)
    ReasonCode.LEVEL_UP -> stringResource(R.string.lg_reason_LEVEL_UP)
    ReasonCode.PROGRAM_UPDATED -> stringResource(R.string.lg_reason_PROGRAM_UPDATED)
}

/** Number fields with − / + steppers (Design §11); each change is one write. */
@Composable
private fun Fields(set: LoggerSet, block: ExerciseBlock, vm: LoggerViewModel) {
    val step = block.stepKg ?: R_TRN_PROG_STEP_DUMBBELL_KG
    when (block.measure) {
        SetMeasure.KG_REPS -> {
            Stepper(stringResource(R.string.lg_weight), set.weightKg?.let(::formatKg), decimal = true,
                onMinus = { vm.edit(set.id) { it.copy(weightKg = clampKg((it.weightKg ?: 0.0) - step)) } },
                onPlus = { vm.edit(set.id) { it.copy(weightKg = clampKg((it.weightKg ?: 0.0) + step)) } },
                onText = { v -> vm.edit(set.id) { it.copy(weightKg = clampKg(v.replace(',', '.').toDoubleOrNull())) } })
            RepsStepper(set, vm)
        }
        SetMeasure.REPS -> RepsStepper(set, vm)
        SetMeasure.BAND_REPS -> {
            Stepper(stringResource(R.string.lg_band), set.bandLevel?.toString(), decimal = false,
                onMinus = { vm.edit(set.id) { it.copy(bandLevel = clampBand((it.bandLevel ?: 1) - 1)) } },
                onPlus = { vm.edit(set.id) { it.copy(bandLevel = clampBand((it.bandLevel ?: 0) + 1)) } },
                onText = { v -> vm.edit(set.id) { it.copy(bandLevel = clampBand(v.toIntOrNull())) } })
            RepsStepper(set, vm)
        }
        SetMeasure.HOLD_SECONDS -> Stepper(stringResource(R.string.lg_hold), set.holdSec?.toString(), decimal = false,
            onMinus = { vm.edit(set.id) { it.copy(holdSec = clampHold((it.holdSec ?: 0) - R_TRN_LOGGER_HOLD_STEP_S)) } },
            onPlus = { vm.edit(set.id) { it.copy(holdSec = clampHold((it.holdSec ?: 0) + R_TRN_LOGGER_HOLD_STEP_S)) } },
            onText = { v -> vm.edit(set.id) { it.copy(holdSec = clampHold(v.toIntOrNull())) } })
    }
}

@Composable
private fun RepsStepper(set: LoggerSet, vm: LoggerViewModel) =
    Stepper(stringResource(R.string.lg_reps), set.reps?.toString(), decimal = false,
        onMinus = { vm.edit(set.id) { it.copy(reps = clampReps((it.reps ?: 0) - R_TRN_LOGGER_REPS_STEP)) } },
        onPlus = { vm.edit(set.id) { it.copy(reps = clampReps((it.reps ?: 0) + R_TRN_LOGGER_REPS_STEP)) } },
        onText = { v -> vm.edit(set.id) { it.copy(reps = clampReps(v.toIntOrNull())) } })

@Composable
private fun Stepper(label: String, value: String?, decimal: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, onText: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        val minus = stringResource(R.string.lg_minus)
        val plus = stringResource(R.string.lg_plus)
        OutlinedButton(onMinus, Modifier.size(MinTouch).semantics { contentDescription = "$minus $label" }) { Text("−") } // text-ok: symbol
        OutlinedTextField(
            value = value.orEmpty(), onValueChange = onText, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onPlus, Modifier.size(MinTouch).semantics { contentDescription = "$plus $label" }) { Text("+") } // text-ok: symbol
    }
}

@Composable
private fun FormRow(set: LoggerSet, vm: LoggerViewModel) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        FilterChip(set.form == FormRating.RAPI, { vm.setForm(set.id, FormRating.RAPI) }, { Text(stringResource(R.string.lg_form_rapi)) }, Modifier.weight(1f).heightIn(min = MinTouch))
        FilterChip(set.form == FormRating.RAGU, { vm.setForm(set.id, FormRating.RAGU) }, { Text(stringResource(R.string.lg_form_ragu)) }, Modifier.weight(1f).heightIn(min = MinTouch))
    }
}

/** Bebas: a table of every set (L-6). */
@Composable
private fun TablePane(ui: LoggerUi, vm: LoggerViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ui.blocks, key = { "${it.slot}-${it.exerciseId}" }) { block -> // text-ok: list key
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                    if (block.supersetGroup != null) Text(stringResource(R.string.lg_superset), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(block.name, style = MaterialTheme.typography.titleMedium)
                            Suggestion(block)
                        }
                        TextButton({ vm.openSheet(LoggerSheet.ExerciseMenu(block.slot, block.exerciseId)) }, Modifier.heightIn(min = MinTouch)) {
                            Text(stringResource(R.string.lg_ex_menu))
                        }
                    }
                    Text(stringResource(R.string.lg_prev), style = MaterialTheme.typography.labelSmall)
                    block.sets.forEachIndexed { i, set -> TableRow(i, set, block, vm) }
                    TextButton({ vm.addSet(block.slot, block.exerciseId) }, Modifier.heightIn(min = MinTouch)) { Text("+ " + stringResource(R.string.lg_add_set)) } // text-ok: symbol
                }
            }
        }
        item {
            Button(vm::openAdd, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text("+ " + stringResource(R.string.lg_add_exercise)) } // text-ok: symbol
            Spacer(Modifier.heightIn(min = MinTouch))
        }
    }
}

@Composable
private fun TableRow(i: Int, set: LoggerSet, block: ExerciseBlock, vm: LoggerViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val tickLabel = stringResource(R.string.lg_tick)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { expanded = !expanded }, Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            TextButton({ vm.openSheet(LoggerSheet.Kind(set.id)) }, Modifier.widthIn(min = MinTouch).heightIn(min = MinTouch)) {
                Text(when (set.kind) {
                    SetKind.NORMAL -> (i + 1).toString()
                    SetKind.DROP -> stringResource(R.string.lg_kind_short_drop)
                    SetKind.FAILURE -> stringResource(R.string.lg_kind_short_failure)
                })
            }
            Text(previousText(block.previous.getOrNull(i), block.measure), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(currentText(set, block.measure), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Checkbox(set.done, { vm.toggleDone(set.id) }, Modifier.size(MinTouch).semantics { contentDescription = tickLabel })
        }
        if (expanded && !set.done) {
            Fields(set, block, vm)
            FormRow(set, vm)
        }
        HorizontalDivider()
    }
}

@Composable
private fun previousText(p: LoggerSet?, m: SetMeasure): String = when {
    p == null -> stringResource(R.string.lg_prev_none)
    else -> currentText(p, m)
}

@Composable
private fun currentText(s: LoggerSet, m: SetMeasure): String = when (m) {
    SetMeasure.KG_REPS -> if (s.weightKg != null && s.reps != null) stringResource(R.string.lg_prev_kg, formatKg(s.weightKg), s.reps) else stringResource(R.string.lg_prev_none)
    SetMeasure.REPS -> s.reps?.let { stringResource(R.string.lg_prev_reps, it) } ?: stringResource(R.string.lg_prev_none)
    SetMeasure.BAND_REPS -> if (s.bandLevel != null && s.reps != null) stringResource(R.string.lg_prev_band, s.bandLevel, s.reps) else stringResource(R.string.lg_prev_none)
    SetMeasure.HOLD_SECONDS -> s.holdSec?.let { stringResource(R.string.lg_prev_hold, it) } ?: stringResource(R.string.lg_prev_none)
}

@Composable
private fun kindLabel(kind: SetKind): String = stringResource(
    when (kind) {
        SetKind.NORMAL -> R.string.lg_kind_normal
        SetKind.DROP -> R.string.lg_kind_drop
        SetKind.FAILURE -> R.string.lg_kind_failure
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoggerSheets(sheet: LoggerSheet, ui: LoggerUi, vm: LoggerViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeSheet) {
        Column(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(8.dp)) {
            when (sheet) {
                is LoggerSheet.Swap -> {
                    Title(stringResource(R.string.lg_swap_title))
                    Text(stringResource(R.string.lg_swap_sub))
                    if (sheet.candidates.isEmpty()) Text(stringResource(R.string.lg_swap_none))
                    Text(stringResource(R.string.lg_swap_today), style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.heightIn(max = SHEET_LIST_MAX)) {
                        items(sheet.candidates, key = { it.id }) { o -> SheetRow(o.name) { vm.swap(sheet.slot, sheet.exerciseId, o.id) } }
                    }
                }
                is LoggerSheet.Add -> {
                    Title(stringResource(R.string.lg_add_title))
                    var q by remember { mutableStateOf("") }
                    OutlinedTextField(q, { q = it }, label = { Text(stringResource(R.string.lg_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = SHEET_LIST_MAX)) {
                        items(sheet.options.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }, key = { it.id }) { o ->
                            SheetRow(o.name) { vm.addExercise(o.id) }
                        }
                    }
                }
                is LoggerSheet.Kind -> {
                    Title(stringResource(R.string.lg_kind_title))
                    SheetRow(stringResource(R.string.lg_kind_normal)) { vm.setKind(sheet.setId, SetKind.NORMAL) }
                    SheetRow(stringResource(R.string.lg_kind_drop), stringResource(R.string.lg_kind_drop_sub)) { vm.setKind(sheet.setId, SetKind.DROP) }
                    SheetRow(stringResource(R.string.lg_kind_failure), stringResource(R.string.lg_kind_failure_sub)) { vm.setKind(sheet.setId, SetKind.FAILURE) }
                    SheetRow(stringResource(R.string.lg_delete_set)) { vm.deleteSet(sheet.setId) }
                }
                is LoggerSheet.ExerciseMenu -> {
                    val block = ui.blocks.firstOrNull { it.slot == sheet.slot && it.exerciseId == sheet.exerciseId }
                    Title(block?.name.orEmpty())
                    SheetRow(stringResource(R.string.lg_ex_swap)) { vm.openSwap(sheet.slot, sheet.exerciseId) }
                    SheetRow(stringResource(if (block?.supersetGroup != null) R.string.lg_ex_superset_off else R.string.lg_ex_superset)) { vm.toggleSuperset(sheet.slot) }
                    SheetRow(stringResource(R.string.lg_ex_rest)) { vm.openSheet(LoggerSheet.Rest(sheet.slot, sheet.exerciseId)) }
                    SheetRow(stringResource(R.string.lg_ex_remove)) { vm.removeExercise(sheet.slot, sheet.exerciseId) }
                }
                is LoggerSheet.Rest -> {
                    Title(stringResource(R.string.lg_ex_rest))
                    REST_CHOICES_S.forEach { s -> SheetRow(stringResource(R.string.lg_rest_value, s)) { vm.setRest(sheet.slot, sheet.exerciseId, s) } }
                }
                LoggerSheet.ExerciseList -> {
                    Title(stringResource(R.string.lg_today))
                    ui.blocks.forEachIndexed { i, b ->
                        SheetRow("${i + 1}  ${b.name} · " + stringResource(R.string.lg_list_row, b.sets.count { it.done }, b.sets.size)) { // text-ok: separator
                            vm.openSheet(LoggerSheet.ExerciseMenu(b.slot, b.exerciseId))
                        }
                    }
                }
                LoggerSheet.Menu -> {
                    Title(stringResource(R.string.lg_menu))
                    SheetRow(stringResource(R.string.lg_mode_dipandu)) { vm.switchMode(LoggerMode.DIPANDU) }
                    SheetRow(stringResource(R.string.lg_mode_bebas)) { vm.switchMode(LoggerMode.BEBAS) }
                    SheetRow(stringResource(R.string.lg_cancel)) { vm.openSheet(LoggerSheet.Cancel) }
                }
                LoggerSheet.Cancel -> {
                    Title(stringResource(R.string.lg_cancel))
                    Text(stringResource(R.string.lg_cancel_sub))
                    ButtonPair(stringResource(R.string.lg_back), vm::closeSheet, stringResource(R.string.lg_cancel_yes), vm::cancelWorkout)
                }
                LoggerSheet.Finish -> {
                    Title(stringResource(R.string.lg_finish_title))
                    Text(stringResource(R.string.lg_finish_count, ui.totals.doneSets, ui.totals.plannedSets))
                    ButtonPair(stringResource(R.string.lg_keep_going), vm::closeSheet, stringResource(R.string.lg_finish), vm::finish)
                }
            }
            Spacer(Modifier.heightIn(min = MinTouch))
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun SheetRow(title: String, hint: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(onClick = onClick).padding(vertical = 8.dp), Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}

/** Two buttons share the row evenly (Design §11). */
@Composable
private fun ButtonPair(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit, outlinedBoth: Boolean = false) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onLeft, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(left) }
        if (outlinedBoth) OutlinedButton(onRight, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(right) }
        else Button(onRight, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(right) }
    }
}

private fun dayLabel(name: String): Int = when (name) {
    "FULL_A" -> R.string.lg_day_FULL_A // text-ok: enum code
    "FULL_B" -> R.string.lg_day_FULL_B // text-ok: enum code
    "FULL_C" -> R.string.lg_day_FULL_C // text-ok: enum code
    "UPPER_A" -> R.string.lg_day_UPPER_A // text-ok: enum code
    "LOWER_A" -> R.string.lg_day_LOWER_A // text-ok: enum code
    "UPPER_B" -> R.string.lg_day_UPPER_B // text-ok: enum code
    else -> R.string.lg_day_LOWER_B
}

internal fun minSec(totalSec: Int): String = "%d:%02d".format(totalSec / SEC_PER_MIN, totalSec % SEC_PER_MIN) // text-ok: number format

internal fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) "%,d".format(kg.toLong()).replace(',', '.') else "%.1f".format(kg).replace('.', ',') // text-ok: number format

private const val MS_PER_S = 1000L
private const val SEC_PER_MIN = 60
private val SHEET_LIST_MAX = 360.dp
private val REST_CHOICES_S = listOf(60, 90, 120, 180)

private const val FEELING_MAX = 5
