package app.xnkfit.ui.screening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.rules.Screening
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.common.StepButtons
import androidx.compose.foundation.layout.heightIn

/**
 * Full health form (PRD S-1, O-2; mockup `screening.html`). Stateless: the caller owns [screening].
 * Reusable from the first program later, not only from onboarding.
 */
@Composable
fun ScreeningScreen(
    screening: Screening,
    incomplete: Boolean,
    onAnswer: (ScreeningQuestion, Boolean?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sc_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.sc_subtitle), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sc_private), style = MaterialTheme.typography.labelLarge)
        ScreeningSection.entries.forEach { section ->
            Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
            ScreeningQuestion.entries.filter { it.section == section }.forEach { q ->
                QuestionRow(q, q.answer(screening), onAnswer)
            }
        }
        if (incomplete) {
            Text(
                stringResource(R.string.sc_incomplete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(stringResource(R.string.sc_footnote), style = MaterialTheme.typography.bodySmall)
        StepButtons(
            nextLabel = stringResource(R.string.sc_save), onNext = onSave,
            backLabel = stringResource(R.string.ob_back), onBack = onBack,
        )
    }
}

@Composable
private fun QuestionRow(q: ScreeningQuestion, answer: Boolean?, onAnswer: (ScreeningQuestion, Boolean?) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(q.label), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.selectableGroup().fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Option(stringResource(R.string.sc_yes), answer == true) { onAnswer(q, true) }
                Option(stringResource(R.string.sc_no), answer == false) { onAnswer(q, false) }
                // "—" exists for the pregnancy question only: a man may leave it (R_GATE_SCREEN).
                if (q == ScreeningQuestion.PREGNANT) {
                    Option(stringResource(R.string.sc_skip), answer == null) { onAnswer(q, null) }
                }
            }
        }
    }
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = MinTouch).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
    }
}

/** Result after "Simpan" (mockup "Hasil"). Always carries the R_DISCLAIMER text. */
@Composable
fun ScreeningResultScreen(
    outcome: ScreeningOutcome,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sc_result), style = MaterialTheme.typography.labelLarge)
        when (outcome) {
            ScreeningOutcome.Clear -> {
                Text(stringResource(R.string.sc_out_clear_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.sc_out_clear_body), style = MaterialTheme.typography.bodyLarge)
            }
            is ScreeningOutcome.Notes -> outcome.cards.forEach { card ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(card.title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(card.body), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(card.hint), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        StepButtons(
            nextLabel = stringResource(R.string.ob_next), onNext = onContinue,
            backLabel = stringResource(R.string.ob_back), onBack = onBack,
        )
    }
}
