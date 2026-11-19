package app.xnkfit.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A single-choice card (radio semantics), at least [MinTouch] high. Colour is not the only signal: a text mark too. */
@Composable
fun ChoiceCard(
    selected: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    OutlinedCard(
        border = BorderStroke(if (selected) 2.dp else 1.dp, color),
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouch).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.heightIn(min = MinTouch).padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Kembali" and "Lanjut" row used by every step. [onBack] null hides the back button. */
@Composable
fun StepButtons(
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    nextEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        if (onBack != null && backLabel != null) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(backLabel) }
        }
        Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f).heightIn(min = MinTouch)) {
            Text(nextLabel)
        }
    }
}
