package app.xnkfit.ui.bantuan

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.xnkfit.R
import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.domain.rules.R_FAT_SHARE
import app.xnkfit.domain.rules.R_FLOOR_F_KCAL
import app.xnkfit.domain.rules.R_FLOOR_M_KCAL
import app.xnkfit.domain.rules.R_PROT_G_PER_KG
import app.xnkfit.domain.rules.R_WHO_MIN_TARGET
import app.xnkfit.ui.common.MinTouch
import java.util.Locale

/** Bantuan (PRD P-8): how targets are worked out, glossary, sources, welcome cards, feedback, privacy. */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onShowWelcomeAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var welcomeReset by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.help_back)) }
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        // R_DISCLAIMER: shown once in Bantuan (and on the target card).
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.help_hero_label), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.help_disclaimer_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.help_disclaimer_body))
            }
        }
        Section(R.string.help_guide_title) {
            HelpContent.guide.forEachIndexed { i, step -> Text(stringResource(R.string.help_numbered, i + 1, stringResource(step))) }
        }
        Section(R.string.help_calc_title) {
            HelpContent.calcSteps.forEachIndexed { i, step -> Text(stringResource(R.string.help_numbered, i + 1, stringResource(step))) }
            Text(stringResource(R.string.help_numbered, 4, stringResource(R.string.help_calc_4, R_FLOOR_F_KCAL, R_FLOOR_M_KCAL)))
            Text(stringResource(R.string.help_calc_protein, String.format(ID, "%.1f", R_PROT_G_PER_KG)))
            Text(stringResource(R.string.help_calc_fat, Math.round(R_FAT_SHARE * 100).toInt()))
            Text(stringResource(R.string.help_calc_activity))
            Text(stringResource(R.string.help_calc_weekly, R_WHO_MIN_TARGET))
            Text(stringResource(R.string.help_calc_safety), style = MaterialTheme.typography.bodySmall)
        }
        Section(R.string.help_terms_title) {
            Text(stringResource(R.string.help_terms_workout), style = MaterialTheme.typography.labelLarge)
            HelpContent.workoutTerms.forEach { TermRow(it) }
            Text(stringResource(R.string.help_terms_food), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            HelpContent.foodTerms.forEach { TermRow(it) }
        }
        Section(R.string.help_sources_title) {
            Text(stringResource(R.string.help_sources_hint), style = MaterialTheme.typography.bodySmall)
            HelpContent.sources.forEach { SourceRow(it) }
            Text(stringResource(R.string.help_src_note), style = MaterialTheme.typography.bodySmall)
        }
        Section(R.string.help_welcome_title) {
            Text(stringResource(R.string.help_welcome_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { onShowWelcomeAgain(); welcomeReset = true },
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.help_welcome_again)) }
            if (welcomeReset) Text(stringResource(R.string.help_welcome_done), style = MaterialTheme.typography.bodySmall)
        }
        Section(R.string.help_feedback_title) {
            Text(stringResource(R.string.help_feedback_hint), style = MaterialTheme.typography.bodySmall)
            FeedbackButton()
        }
        Button(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)) {
            Text(stringResource(R.string.help_privacy_open))
        }
    }
}

private val ID: Locale = Locale.forLanguageTag("id-ID") // text-ok: locale tag

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TermRow(t: Term) {
    Column {
        Text(stringResource(t.term), fontWeight = FontWeight.Bold)
        Text(stringResource(t.definition), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One source line with an (i) button that opens the full reference under it. */
@Composable
private fun SourceRow(s: Source) {
    var open by rememberSaveable(s.short) { mutableStateOf(false) }
    val short = stringResource(s.short)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
            Text(short, Modifier.weight(1f))
            val desc = stringResource(R.string.help_info_desc, short)
            TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = MinTouch).semantics { contentDescription = desc }) {
                Text(stringResource(R.string.help_info_mark))
            }
        }
        if (open) Text(stringResource(s.full), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FeedbackButton() {
    val context = LocalContext.current
    if (FeedbackConfig.available) {
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, FeedbackConfig.FORM_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(stringResource(R.string.help_feedback_open)) }
    } else {
        Text(stringResource(R.string.help_feedback_soon), style = MaterialTheme.typography.bodySmall)
    }
}

/** The one-time card at the top of a tab (PRD P-8). Dismissing it stores that the tab was seen. */
@Composable
fun WelcomeCard(tab: WelcomeTab, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val text = HelpContent.welcome.getValue(tab)
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(text.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(text.body))
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.wc_dismiss)) }
        }
    }
}
