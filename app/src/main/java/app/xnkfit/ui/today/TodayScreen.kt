package app.xnkfit.ui.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.xnkfit.ui.adaptive.TwoColumns
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.today.ActivityWeek
import app.xnkfit.domain.today.RecalcPrompt
import app.xnkfit.domain.today.RecalcReason
import app.xnkfit.domain.today.SourceRef
import app.xnkfit.domain.today.TargetCard
import app.xnkfit.domain.today.TargetNumber
import app.xnkfit.domain.today.TaskKind
import app.xnkfit.domain.today.TodayTask
import app.xnkfit.domain.today.WorkoutCard
import app.xnkfit.domain.today.sourcesFor
import app.xnkfit.ui.common.MinTouch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gap = 12.dp

/** Text for each source behind "(i)" (strings.xml, `src_*`). */
@StringRes
fun SourceRef.text(): Int = when (this) {
    SourceRef.MIFFLIN_S1 -> R.string.src_mifflin_s1
    SourceRef.PAL_S2 -> R.string.src_pal_s2
    SourceRef.NHLBI_S5 -> R.string.src_nhlbi_s5
    SourceRef.PROTEIN_S6 -> R.string.src_protein_s6
    SourceRef.SURPLUS_S19 -> R.string.src_surplus_s19
    SourceRef.AKG_S8 -> R.string.src_akg_s8
}

/** One line per gate note on the target card. Same notes as the screening result, in gate order (R_GATE_*). */
@StringRes
fun GateNote.text(): Int = when (this) {
    GateNote.RISK_SEE_DOCTOR -> R.string.td_note_risk
    GateNote.DEFICIT_CAPPED -> R.string.td_note_deficit_capped
    GateNote.UNDER_18_LIGHT_MODE -> R.string.td_note_u18
    GateNote.PREGNANT_NO_DEFICIT -> R.string.td_note_preg
    GateNote.EATING_DISORDER_NO_NUMBERS -> R.string.td_note_ed
    GateNote.BMI_UNDER_NO_TURUN -> R.string.td_note_bmi
    GateNote.GOAL_CHANGED_TO_JAGA -> R.string.td_note_goal_changed
    GateNote.FLOOR_RAISED -> R.string.td_note_floor
}

@StringRes
private fun TargetNumber.label(): Int = when (this) {
    TargetNumber.KCAL -> R.string.td_target_kcal
    TargetNumber.PROTEIN -> R.string.td_macro_protein
    TargetNumber.CARB -> R.string.td_macro_carb
    TargetNumber.FAT -> R.string.td_macro_fat
    TargetNumber.FIBRE -> R.string.td_target_fibre
}

@StringRes
private fun Goal.label(): Int = when (this) {
    Goal.TURUN -> R.string.td_target_goal_turun
    Goal.NAIK -> R.string.td_target_goal_naik
    Goal.JAGA -> R.string.td_target_goal_jaga
}

@StringRes
private fun TaskKind.label(): Int = when (this) {
    TaskKind.WEIGH -> R.string.td_task_weigh
    TaskKind.WORKOUT -> R.string.td_task_workout
    TaskKind.FOOD -> R.string.td_task_food
}

/**
 * Hari ini (PRD H-1…H-3, M-2). Order (H-1): prompt, workout or activity card, tasks, food, target, week.
 * Navigation is passed in: the Latihan and Makan screens own what happens after the tap.
 */
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpenWorkout: () -> Unit,
    onOpenFood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by vm.state.collectAsState()
    TwoColumns(modifier, spacing = Gap, left = {
        item { Header(s) }
        if (s.failed) item { Text(stringResource(R.string.td_failed), style = MaterialTheme.typography.bodyMedium) }
        s.recalc?.let { p -> item { RecalcCard(p, vm::acceptRecalc, vm::keepTarget) } }
        if (s.loaded) {
            item {
                val w = s.workout
                if (w == null) ActivityCard(s.activity, onOpenWorkout) else WorkoutCardView(w, onOpenWorkout)
            }
            item { TasksCard(s.tasks) }
        }
    }, right = {
        if (s.loaded) {
            item { FoodCard(s.food, onOpenFood) }
            item { TargetCardView(s.target) }
            item { WeekCard(s.week) }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
    })
}

@Composable
private fun Header(s: TodayState) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.td_title), style = MaterialTheme.typography.headlineSmall)
        s.day?.let {
            val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id", "ID")) // text-ok: date pattern
            Text(it.format(fmt), style = MaterialTheme.typography.bodyMedium)
        }
        if (s.loaded) Text(stringResource(R.string.td_level_streak, s.level, s.streakWeeks), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecalcCard(p: RecalcPrompt, onAccept: () -> Unit, onKeep: () -> Unit) {
    val text = when (p.reason) {
        RecalcReason.WEIGHT_DOWN -> stringResource(R.string.td_recalc_weight_down, kgText(p.deltaKg))
        RecalcReason.WEIGHT_UP -> stringResource(R.string.td_recalc_weight_up, kgText(p.deltaKg))
        RecalcReason.GOAL_CHANGED -> stringResource(R.string.td_recalc_goal)
        RecalcReason.PACE -> stringResource(R.string.td_recalc_pace, p.raiseKcal ?: 0)
    }
    val accept = if (p.reason == RecalcReason.PACE) R.string.td_recalc_accept_pace else R.string.td_recalc_accept
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            EvenRow {
                Button(onAccept, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(accept)) }
                OutlinedButton(onKeep, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_recalc_keep)) }
            }
        }
    }
}

/** Comma as the decimal sign, one decimal, no trailing ",0". */
private fun kgText(kg: Double?): String {
    val v = Math.round((kg ?: 0.0) * 10) / 10.0
    return (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()).replace('.', ',')
}

@Composable
private fun EvenRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun WorkoutCardView(card: WorkoutCard, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (card) {
                WorkoutCard.NoProgram -> {
                    Text(stringResource(R.string.td_noprogram_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.td_noprogram_body), style = MaterialTheme.typography.bodyMedium)
                    EvenRow {
                        Button(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_noprogram_make)) }
                        OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_noprogram_food)) }
                    }
                }
                is WorkoutCard.Next -> {
                    Text(stringResource(R.string.td_workout_title), style = MaterialTheme.typography.labelLarge)
                    Text(card.day.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.td_workout_meta, card.day.exercises, card.day.minutes), style = MaterialTheme.typography.bodyMedium)
                    EvenRow {
                        Button(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_workout_start)) }
                        OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_workout_view)) }
                    }
                }
                is WorkoutCard.Resume -> {
                    Text(stringResource(R.string.td_workout_resume_title), style = MaterialTheme.typography.titleMedium)
                    card.day?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
                    Button(onOpen, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.td_workout_resume)) }
                }
                is WorkoutCard.Rest -> {
                    Text(stringResource(R.string.td_rest_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.td_rest_body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.td_rest_next, card.next.name), style = MaterialTheme.typography.bodyMedium)
                    EvenRow {
                        OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_rest_log_walk)) }
                        OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_rest_program)) }
                    }
                }
            }
        }
    }
}

/** H-2: people who do not lift get the weekly target instead of the workout card. */
@Composable
private fun ActivityCard(a: ActivityWeek, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.td_activity_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.td_activity_minutes, a.minutes, a.targetMinutes), style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(progress = { a.fraction }, Modifier.fillMaxWidth())
            if (a.minutes == 0) Text(stringResource(R.string.td_activity_empty), style = MaterialTheme.typography.bodyMedium)
            EvenRow {
                Button(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_activity_log)) }
                OutlinedButton(onOpen, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.td_activity_history)) }
            }
        }
    }
}

@Composable
private fun TasksCard(tasks: List<TodayTask>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.td_tasks_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.td_tasks_count, tasks.count { it.done }, tasks.size), style = MaterialTheme.typography.bodyMedium)
            }
            tasks.forEach { t ->
                Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(t.kind.label()), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(if (t.done) R.string.td_task_done else R.string.td_task_open), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FoodCard(summary: DaySummary?, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.td_food_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onOpen, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.td_food_open)) }
            }
            when (summary) {
                null -> Unit
                is DaySummary.Ticks -> Text(stringResource(R.string.td_food_ticks, summary.mealsLogged, summary.mealsTotal), style = MaterialTheme.typography.headlineSmall)
                is DaySummary.Numbers -> {
                    val t = summary.targets
                    if (t == null) {
                        Text(stringResource(R.string.td_food_eaten_only, summary.eatenKcal), style = MaterialTheme.typography.headlineSmall)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val fraction = (summary.eatenKcal.toFloat() / t.kcal).coerceIn(0f, 1f)
                            CircularProgressIndicator(progress = { fraction }, Modifier.size(64.dp))
                            Column {
                                val main = summary.remainingKcal?.let { stringResource(R.string.td_food_remaining, it) }
                                    ?: summary.overKcal?.let { stringResource(R.string.td_food_over, it) }
                                    ?: stringResource(R.string.td_food_of, summary.eatenKcal, t.kcal)
                                Text(main, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.td_food_of, summary.eatenKcal, t.kcal), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stringResource(R.string.td_food_macro, stringResource(R.string.td_macro_protein), summary.protein, t.proteinG))
                        Text(stringResource(R.string.td_food_macro, stringResource(R.string.td_macro_carb), summary.carb, t.carbG))
                        Text(stringResource(R.string.td_food_macro, stringResource(R.string.td_macro_fat), summary.fat, t.fatG))
                    }
                    if (summary.eatenKcal == 0) Text(stringResource(R.string.td_food_empty), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TargetCardView(card: TargetCard) {
    var source by remember { mutableStateOf<TargetNumber?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.td_target_title), style = MaterialTheme.typography.titleMedium)
            when (card) {
                TargetCard.NeedsScreening -> Text(stringResource(R.string.td_target_needs_screening), style = MaterialTheme.typography.bodyMedium)
                TargetCard.Unavailable -> Text(stringResource(R.string.td_target_unavailable), style = MaterialTheme.typography.bodyMedium)
                is TargetCard.NoNumbers -> {
                    Text(stringResource(R.string.td_target_no_numbers_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.td_target_no_numbers_body), style = MaterialTheme.typography.bodyMedium)
                    card.notes.forEach { Text(stringResource(it.text()), style = MaterialTheme.typography.bodyMedium) }
                }
                is TargetCard.Numbers -> {
                    Text(stringResource(card.goal.label()), style = MaterialTheme.typography.bodyMedium)
                    NumberRow(TargetNumber.KCAL, stringResource(R.string.td_target_kcal_value, card.kcal)) { source = it }
                    NumberRow(TargetNumber.PROTEIN, stringResource(R.string.td_target_g_value, card.proteinG)) { source = it }
                    NumberRow(TargetNumber.CARB, stringResource(R.string.td_target_g_value, card.carbG)) { source = it }
                    NumberRow(TargetNumber.FAT, stringResource(R.string.td_target_g_value, card.fatG)) { source = it }
                    NumberRow(TargetNumber.FIBRE, stringResource(R.string.td_target_g_value, card.fibreG)) { source = it }
                    if (card.raisedByPace) Text(stringResource(R.string.td_target_raised), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.td_target_estimate), style = MaterialTheme.typography.bodySmall)
                    card.notes.forEach { Text(stringResource(it.text()), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            // R_DISCLAIMER: always on the target card, whatever the state.
            Text(stringResource(R.string.td_disclaimer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
    val n = source
    if (n != null && card is TargetCard.Numbers) {
        val sources = sourcesFor(n, card.goal)
        AlertDialog(
            onDismissRequest = { source = null },
            title = { Text(stringResource(R.string.td_target_source_title, stringResource(n.label()))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sources.isEmpty()) Text(stringResource(R.string.src_none))
                    sources.forEach { Text(stringResource(it.text()), style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton({ source = null }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.td_target_source_close)) } },
        )
    }
}

@Composable
private fun NumberRow(number: TargetNumber, value: String, onSource: (TargetNumber) -> Unit) {
    val label = stringResource(number.label())
    val sourceLabel = stringResource(R.string.td_target_source_button, label)
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            TextButton(
                { onSource(number) },
                Modifier.heightIn(min = MinTouch).semantics { contentDescription = sourceLabel },
            ) { Text(stringResource(R.string.td_target_source_i)) }
        }
    }
}

@Composable
private fun WeekCard(week: List<Boolean>) {
    val names = listOf(R.string.td_day_1, R.string.td_day_2, R.string.td_day_3, R.string.td_day_4, R.string.td_day_5, R.string.td_day_6, R.string.td_day_7)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.td_week_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.td_week_active, week.count { it }), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEachIndexed { i, on ->
                    val name = stringResource(names[i])
                    val desc = stringResource(if (on) R.string.td_day_active else R.string.td_day_idle, name)
                    Column(Modifier.semantics { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(24.dp).background(
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            ),
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
