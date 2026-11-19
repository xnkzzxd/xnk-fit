package app.xnkfit.ui.makan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import app.xnkfit.ui.adaptive.TwoColumns
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.BuyFrequency
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.ui.common.MinTouch

/** Makan home (mockup makan.html): day summary, preferences, today's plan, the day's log. */
@Composable
fun MakanScreen(state: MakanState, vm: MakanViewModel, modifier: Modifier = Modifier) {
    TwoColumns(modifier, left = {
        item { Text(stringResource(R.string.mk_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp)) }
        if (state.failed) item { Text(stringResource(R.string.mk_failed), color = MaterialTheme.colorScheme.error) }
        item { SummaryCard(state) }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ vm.open(MakanRoute.PLANNER) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_plan_week)) }
                OutlinedButton({ vm.open(MakanRoute.SHOPPING) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_shopping)) }
            }
        }
        item { TodayPlanCard(state) }
    }, right = {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(stringResource(R.string.mk_diary), style = MaterialTheme.typography.titleMedium)
                TextButton(vm::copyYesterday, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_copy_yesterday)) }
            }
        }
        if (state.loaded && state.lines.isEmpty()) item { Text(stringResource(R.string.mk_empty_day), style = MaterialTheme.typography.bodyMedium) }
        MealSlot.entries.forEach { slot -> item(key = slot.ordinal) { SlotCard(state, slot, vm) } }
        item {
            OutlinedButton({ vm.openQuick(true) }, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_quick)) }
        }
        item { PrefsCard(state, vm) }
        item { Text(stringResource(R.string.mk_disclaimer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp)) }
    })
}

@Composable
private fun SummaryCard(state: MakanState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
            when (val s = state.summary) {
                is DaySummary.Ticks -> {
                    Text(stringResource(R.string.mk_no_numbers), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.mk_meals_logged, s.mealsLogged, s.mealsTotal), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.mk_no_numbers_note), style = MaterialTheme.typography.bodyMedium)
                }
                is DaySummary.Numbers -> {
                    val t = s.targets
                    Text(stringResource(R.string.mk_today), style = MaterialTheme.typography.titleMedium)
                    if (t != null) {
                        Text(stringResource(R.string.mk_eaten_of, s.eatenKcal, t.kcal), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.mk_kcal))
                        s.remainingKcal?.let { Text(stringResource(R.string.mk_remaining, it)) }
                        s.overKcal?.let {
                            Text(stringResource(R.string.mk_over, it))
                            Text(stringResource(R.string.mk_over_note), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            stringResource(R.string.mk_macro_progress, s.protein, t.proteinG, s.carb, t.carbG, s.fat, t.fatG),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(stringResource(R.string.mk_eaten_only, s.eatenKcal), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.mk_no_target), style = MaterialTheme.typography.bodyMedium)
                    }
                    s.activityKcal?.let { Text(stringResource(R.string.mk_activity, it), style = MaterialTheme.typography.bodyMedium) }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun TodayPlanCard(state: MakanState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mk_plan_today), style = MaterialTheme.typography.titleMedium)
            val cells = state.plan.filter { it.day == state.todayIndex }
            if (cells.isEmpty()) {
                Text(stringResource(R.string.pl_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                cells.forEach { cell ->
                    val p = state.packages[cell.packageId] ?: return@forEach
                    Column(Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
                        Text(stringResource(cell.slot.label()), style = MaterialTheme.typography.labelLarge)
                        Text(p.nameId)
                        val kcal = if (state.numbersHidden) "" else stringResource(R.string.mk_approx_kcal, p.kcal.toInt()) + " · "
                        Text(kcal + stringResource(p.mode.label()), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCard(state: MakanState, slot: MealSlot, vm: MakanViewModel) {
    val lines = state.lines.filter { it.slot == slot }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(stringResource(slot.label()), style = MaterialTheme.typography.titleMedium)
                TextButton({ vm.startPick(slot) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_add)) }
            }
            lines.forEach { line ->
                Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.title ?: stringResource(R.string.mk_quick))
                        val detail = buildList {
                            line.grams?.let { add(stringResource(R.string.mk_log_line_grams, it)) }
                            if (line.estimate) add(stringResource(R.string.mk_estimate))
                        }.joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                    line.kcal?.let { Text(stringResource(if (line.estimate) R.string.mk_approx_kcal else R.string.mk_kcal_value, it)) }
                    TextButton({ vm.deleteLog(line.id) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_delete)) }
                }
            }
        }
    }
}

@Composable
private fun PrefsCard(state: MakanState, vm: MakanViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mk_prefs_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.mk_halal_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.mk_allergy), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                Allergen.entries.forEach { a ->
                    FilterChip(a in state.prefs.avoid, { vm.toggleAllergen(a) }, { Text(stringResource(a.label())) })
                }
            }
            Text(stringResource(R.string.mk_budget), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                Budget.entries.forEach { b -> FilterChip(state.prefs.budget == b, { vm.setBudget(b) }, { Text(stringResource(b.label())) }) }
            }
            Text(stringResource(R.string.mk_buys), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                BuyFrequency.entries.forEach { b -> FilterChip(state.prefs.buys == b, { vm.setBuys(b) }, { Text(stringResource(b.label())) }) }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                Button(vm::savePrefs, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_save)) }
                if (state.prefsSaved) Text(stringResource(R.string.mk_saved), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
