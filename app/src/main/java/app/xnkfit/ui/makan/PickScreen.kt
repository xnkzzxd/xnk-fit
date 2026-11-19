package app.xnkfit.ui.makan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.FoodItem
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PortionChoice
import app.xnkfit.domain.food.nutrientsFor
import app.xnkfit.domain.food.portionDisplay
import app.xnkfit.ui.common.MinTouch
import kotlin.math.roundToInt

/** Pilih makanan (mockup pilih-makanan.html): search, recent, favourites, own foods; household measure first (M-6). */
@Composable
fun PickScreen(state: MakanState, vm: MakanViewModel, modifier: Modifier = Modifier) {
    val pick = state.pick
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(stringResource(R.string.pk_title), style = MaterialTheme.typography.headlineSmall)
                TextButton({ vm.back() }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_back)) }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { s -> FilterChip(pick.slot == s, { vm.selectSlot(s) }, { Text(stringResource(s.label())) }) }
            }
        }
        when {
            pick.portion != null -> item { PortionPanel(state, vm) }
            pick.quickOpen -> item { QuickForm(state, vm) }
            pick.ownOpen -> item { OwnFoodForm(state, vm) }
            else -> browse(state, vm)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.browse(state: MakanState, vm: MakanViewModel) {
    val pick = state.pick
    item {
        OutlinedTextField(
            value = pick.query, onValueChange = vm::setQuery, singleLine = true,
            label = { Text(stringResource(R.string.pk_search_hint)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        )
    }
    val searching = pick.query.isNotBlank()
    if (!searching) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                PickTab.entries.forEach { t ->
                    val label = when (t) {
                        PickTab.RECENT -> R.string.pk_tab_recent
                        PickTab.FAVOURITE -> R.string.pk_tab_favourite
                        PickTab.OWN -> R.string.pk_tab_own
                    }
                    FilterChip(pick.tab == t, { vm.selectTab(t) }, { Text(stringResource(label)) })
                }
            }
        }
    }
    val rows = if (searching) pick.results else pick.list
    if (searching) item { Text(stringResource(R.string.pk_results_count, rows.size), style = MaterialTheme.typography.labelLarge) }
    if (rows.isEmpty()) item { Text(stringResource(if (searching) R.string.pk_no_results else R.string.pk_empty_list), style = MaterialTheme.typography.bodyMedium) }
    items(rows, key = { it.id }) { food -> FoodRow(food, state.numbersHidden) { vm.chooseFood(food) } }
    item {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ vm.openOwn(true) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_own_title)) }
            OutlinedButton({ vm.openQuick(true) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_quick_title)) }
        }
    }
}

/** One result row: name, source, the first household measure with grams beside it (M-6), kcal for that portion. */
@Composable
private fun FoodRow(food: FoodItem, hideNumbers: Boolean, onClick: () -> Unit) {
    val measure = food.household.firstOrNull()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch).padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food.nameId, style = MaterialTheme.typography.titleSmall)
                val tail = measure?.let {
                    val d = portionDisplay(it, it.qty)
                    " · " + stringResource(R.string.pk_measure_line, d.quantity + " " + d.unit, d.grams)
                }.orEmpty()
                val estimate = if (food.isEstimate) " · " + stringResource(R.string.mk_estimate) else ""
                Text(stringResource(sourceLabel(food.source)) + tail + estimate, style = MaterialTheme.typography.bodySmall)
            }
            if (!hideNumbers) {
                val kcal = nutrientsFor(food.per100, measure?.grams ?: PER_100).kcal.roundToInt()
                Text(stringResource(if (food.isEstimate) R.string.mk_approx_kcal else R.string.mk_kcal_value, kcal))
            }
        }
    }
}

private const val PER_100 = 100.0

@Composable
private fun PortionPanel(state: MakanState, vm: MakanViewModel) {
    val p = state.pick.portion ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(p.food.nameId, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pk_portion), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                p.food.household.forEach { m ->
                    val sel = (p.choice as? PortionChoice.Household)?.measure == m
                    FilterChip(sel, { vm.choosePortion(PortionChoice.Household(m)) }, { Text(portionDisplay(m, m.qty).let { it.unit }) })
                }
                FilterChip(p.choice == PortionChoice.Grams, { vm.choosePortion(PortionChoice.Grams) }, { Text(stringResource(R.string.pk_grams)) })
            }
            when (val c = p.choice) {
                is PortionChoice.Household -> {
                    val d = portionDisplay(c.measure, p.count)
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                        OutlinedButton({ vm.stepPortion(false) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_minus)) }
                        Text(
                            stringResource(R.string.pk_measure_line, d.quantity + " " + d.unit, d.grams),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        OutlinedButton({ vm.stepPortion(true) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_plus)) }
                    }
                }
                PortionChoice.Grams -> OutlinedTextField(
                    value = p.gramsText, onValueChange = vm::setGramsText, singleLine = true,
                    label = { Text(stringResource(R.string.pk_grams_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                )
            }
            if (!state.numbersHidden) {
                val n = nutrientsFor(p.food.per100, p.grams)
                Text(stringResource(if (p.food.isEstimate) R.string.mk_approx_kcal else R.string.mk_kcal_value, n.kcal.roundToInt()), style = MaterialTheme.typography.headlineSmall)
            }
            Button({ vm.logPortion() }, Modifier.fillMaxWidth().heightIn(min = MinTouch), enabled = p.grams > 0.0) { Text(stringResource(R.string.pk_log)) }
        }
    }
}

@Composable
private fun QuickForm(state: MakanState, vm: MakanViewModel) {
    var kcal by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pk_quick_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = kcal, onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(MAX_DIGITS) }, singleLine = true,
                label = { Text(stringResource(R.string.pk_quick_kcal)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            )
            if (state.pick.error) Text(stringResource(R.string.pk_input_error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ vm.openQuick(false) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_cancel)) }
                Button({ vm.logQuick(kcal) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_save)) }
            }
        }
    }
}

private const val MAX_DIGITS = 7

@Composable
private fun OwnFoodForm(state: MakanState, vm: MakanViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carb by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var serving by rememberSaveable { mutableStateOf("") }
    fun num(v: String) = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(MAX_DIGITS)
    @Composable
    fun field(value: String, label: Int, number: Boolean, onChange: (String) -> Unit) = OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pk_own_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pk_own_saved_note), style = MaterialTheme.typography.bodySmall)
            field(name, R.string.pk_own_name, false) { name = it.take(NAME_MAX) }
            field(kcal, R.string.pk_own_kcal, true) { kcal = num(it) }
            field(protein, R.string.pk_own_protein, true) { protein = num(it) }
            field(carb, R.string.pk_own_carb, true) { carb = num(it) }
            field(fat, R.string.pk_own_fat, true) { fat = num(it) }
            field(serving, R.string.pk_own_serving, true) { serving = num(it) }
            if (state.pick.error) Text(stringResource(R.string.pk_input_error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ vm.openOwn(false) }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.pk_cancel)) }
                Button({ vm.saveOwn(name, kcal, protein, carb, fat, serving) }, Modifier.weight(1f).heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_save)) }
            }
        }
    }
}

private const val NAME_MAX = 60
