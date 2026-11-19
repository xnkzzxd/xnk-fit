package app.xnkfit.ui.makan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.MealPackage
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PlannedMeal
import app.xnkfit.ui.common.MinTouch
import kotlinx.coroutines.launch

private const val DAYS = 7
private const val TABLET_MIN_DP = 600

/**
 * Rencana minggu (mockup planner.html, PRD M-3, M-4, M-10). Phone: one day per page, swipe between days.
 * Wide screen: seven columns. Choosing a cell opens its three options with swap, lock, masak sekali, makan di luar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannerScreen(state: MakanState, vm: MakanViewModel, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) } // day * 10 + slot ordinal
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.pl_title), style = MaterialTheme.typography.headlineSmall)
            TextButton({ vm.back() }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_back)) }
        }
        Button({ vm.autoFill() }, Modifier.fillMaxWidth().heightIn(min = MinTouch)) { Text(stringResource(R.string.pl_autofill)) }
        if (state.plan.isEmpty()) {
            Text(stringResource(R.string.pl_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            if (state.santan >= app.xnkfit.domain.food.R_LIMIT_SANTAN_PER_WEEK) {
                Text(stringResource(R.string.pl_santan_note, state.santan), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.daysBelowFloor.isNotEmpty()) Text(stringResource(R.string.pl_limited), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pl_never_blocked), style = MaterialTheme.typography.bodySmall)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth.value >= TABLET_MIN_DP) {
                    WeekTable(state, vm, selected) { selected = it }
                } else {
                    DayPager(state, vm, selected) { selected = it }
                }
            }
        }
    }
}


private fun key(day: Int, slot: MealSlot) = day * KEY_BASE + slot.ordinal
private const val KEY_BASE = 10

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayPager(state: MakanState, vm: MakanViewModel, selected: Int?, onSelect: (Int?) -> Unit) {
    val pager = rememberPagerState(initialPage = (state.todayIndex - 1).coerceIn(0, DAYS - 1)) { DAYS }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            for (d in 1..DAYS) {
                FilterChip(pager.currentPage == d - 1, { scope.launch { pager.animateScrollToPage(d - 1) } }, { Text(stringResource(shortDay(d))) })
            }
        }
        Text(stringResource(R.string.pl_swipe), style = MaterialTheme.typography.bodySmall)
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val day = page + 1
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(stringResource(longDay(day)), style = MaterialTheme.typography.titleMedium)
                    if (!state.numbersHidden) Text(stringResource(R.string.pl_day_kcal, state.dayKcal[day] ?: 0))
                }
                val range = state.proteinRange
                if (!state.numbersHidden && range != null) {
                    Text(stringResource(R.string.pl_day_protein, state.dayProtein[day] ?: 0, range.first, range.last), style = MaterialTheme.typography.bodySmall)
                }
                MealSlot.entries.forEach { slot ->
                    val cell = state.plan.firstOrNull { it.day == day && it.slot == slot }
                    CellCard(state, vm, day, slot, cell, selected == key(day, slot)) { onSelect(if (selected == key(day, slot)) null else key(day, slot)) }
                }
            }
        }
    }
}

@Composable
private fun WeekTable(state: MakanState, vm: MakanViewModel, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
            for (d in 1..DAYS) {
                Text(stringResource(shortDay(d)) + if (state.numbersHidden) "" else " " + stringResource(R.string.pl_day_kcal, state.dayKcal[d] ?: 0), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
        }
        MealSlot.entries.forEach { slot ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                for (d in 1..DAYS) {
                    val cell = state.plan.firstOrNull { it.day == d && it.slot == slot }
                    val p = cell?.let { state.packages[it.packageId] }
                    OutlinedCard(
                        Modifier.weight(1f).heightIn(min = MinTouch).selectable(selected == key(d, slot), role = Role.Button) { onSelect(if (selected == key(d, slot)) null else key(d, slot)) },
                        border = BorderStroke(if (selected == key(d, slot)) 2.dp else 1.dp, if (selected == key(d, slot)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    ) {
                        Text((p?.nameId).orEmpty(), Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            val day = sel / KEY_BASE
            val slot = MealSlot.entries[sel % KEY_BASE]
            Editor(state, vm, day, slot, state.plan.firstOrNull { it.day == day && it.slot == slot })
        }
    }
}

@Composable
private fun CellCard(state: MakanState, vm: MakanViewModel, day: Int, slot: MealSlot, cell: PlannedMeal?, open: Boolean, onToggle: () -> Unit) {
    val p = cell?.let { state.packages[it.packageId] }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(open, role = Role.Button, onClick = onToggle), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(slot.label()), style = MaterialTheme.typography.labelLarge)
                    Text(p?.nameId ?: "", style = MaterialTheme.typography.titleSmall)
                    if (p != null) {
                        val tags = buildList {
                            if (!state.numbersHidden) add(stringResource(R.string.mk_approx_kcal, p.kcal.toInt()))
                            add(stringResource(p.mode.label()))
                            if (p.santan) add(stringResource(R.string.pl_santan_tag))
                            if (cell.locked) add(stringResource(R.string.pl_locked))
                            if (cell.cookOnce) add(stringResource(R.string.pl_cook_once))
                            if (cell.eatOut) add(stringResource(R.string.pl_eat_out_on))
                        }
                        Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.pl_swap))
            }
            if (open) Editor(state, vm, day, slot, cell)
        }
    }
}

/** Three options for one cell and the four actions. Choosing never fails: manual choices are not blocked (M-4). */
@Composable
private fun Editor(state: MakanState, vm: MakanViewModel, day: Int, slot: MealSlot, cell: PlannedMeal?) {
    val options: List<MealPackage> = state.options[day to slot].orEmpty()
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.pl_options_title, stringResource(slot.label()), stringResource(longDay(day))), style = MaterialTheme.typography.labelLarge)
        options.forEach { o ->
            val chosen = cell?.packageId == o.id
            OutlinedCard(
                Modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(chosen, role = Role.RadioButton) { vm.choose(day, slot, o.id) },
                border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(o.nameId, style = MaterialTheme.typography.titleSmall)
                    val tags = buildList {
                        if (!state.numbersHidden) add(stringResource(R.string.mk_approx_kcal, o.kcal.toInt()))
                        add(stringResource(o.mode.label()))
                        if (o.santan) add(stringResource(R.string.pl_santan_tag))
                        if (chosen) add(stringResource(R.string.pl_chosen))
                    }
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (cell != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.toggleLock(day, slot) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(if (cell.locked) R.string.pl_locked else R.string.pl_lock)) }
                OutlinedButton({ vm.cookOnce(day, slot) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pl_cook_once)) }
                OutlinedButton({ vm.eatOut(day, slot) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pl_eat_out)) }
            }
        }
    }
}
