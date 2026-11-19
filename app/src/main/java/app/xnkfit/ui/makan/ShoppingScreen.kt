package app.xnkfit.ui.makan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.ShopLine
import app.xnkfit.ui.common.MinTouch
import java.util.Locale

/** Daftar belanja (mockup belanja.html): grouped, ticked per week, from today to the end of the week (M-8). */
@Composable
fun ShoppingScreen(state: MakanState, vm: MakanViewModel, modifier: Modifier = Modifier) {
    val left = state.shopping.count { it.foodId !in state.ticks }
    val daysLeft = (DAYS_IN_WEEK - state.todayIndex + 1).coerceAtLeast(1)
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_title), style = MaterialTheme.typography.headlineSmall)
                TextButton({ vm.back() }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.mk_back)) }
            }
        }
        item {
            Text(stringResource(R.string.sh_from_plan) + " · " + stringResource(R.string.sh_items_left, left) + " · " + stringResource(R.string.sh_rest_of_week, daysLeft), style = MaterialTheme.typography.bodyMedium)
        }
        if (state.shopping.isEmpty()) item { Text(stringResource(R.string.sh_empty), style = MaterialTheme.typography.bodyLarge) }
        state.shopping.groupBy { it.group }.forEach { (group, lines) ->
            item(key = -group.ordinal - 1) {
                val done = lines.count { it.foodId in state.ticks }
                Text(
                    stringResource(group.label()) + " " + done + "/" + lines.size,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp),
                )
            }
            lines.forEach { line ->
                item(key = line.foodId) {
                    val ticked = line.foodId in state.ticks
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouch).toggleable(ticked, role = Role.Checkbox, onValueChange = { vm.tick(line.foodId, it) }),
                        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                    ) {
                        Checkbox(ticked, null)
                        Text(line.nameId, Modifier.weight(1f))
                        Text(quantityText(line))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.sh_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp)) }
    }
}

private const val DAYS_IN_WEEK = 7
private const val GRAMS_PER_KG = 1000.0
private const val LOCALE_ID = "id-ID" // text-ok: locale tag, not UI text
private const val KG_FORMAT = "%.1f" // text-ok: number format, not UI text

@Composable
private fun quantityText(line: ShopLine): String {
    val c = line.count
    return when {
        c != null -> stringResource(R.string.sh_count, c.count, c.unit)
        line.grams >= GRAMS_PER_KG -> stringResource(R.string.sh_kg, String.format(Locale.forLanguageTag(LOCALE_ID), KG_FORMAT, line.grams / GRAMS_PER_KG))
        else -> stringResource(R.string.sh_grams, line.grams.toInt())
    }
}
