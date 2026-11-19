package app.xnkfit.ui.pengaturan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.ui.common.MinTouch

/** Privacy policy text inside the app (PRD PV-4); the same text as docs/Privacy-Policy.md. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.privacy_back)) }
            Text(
                stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(stringResource(R.string.privacy_review_note), style = MaterialTheme.typography.bodySmall)
        PrivacyText.sections.forEach { (title, body) ->
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(stringResource(body))
        }
    }
}

/** The nine policy sections as resource ids. */
object PrivacyText {
    val sections: List<Pair<Int, Int>> = listOf(
        R.string.privacy_1_title to R.string.privacy_1_body,
        R.string.privacy_2_title to R.string.privacy_2_body,
        R.string.privacy_3_title to R.string.privacy_3_body,
        R.string.privacy_4_title to R.string.privacy_4_body,
        R.string.privacy_5_title to R.string.privacy_5_body,
        R.string.privacy_6_title to R.string.privacy_6_body,
        R.string.privacy_7_title to R.string.privacy_7_body,
        R.string.privacy_8_title to R.string.privacy_8_body,
        R.string.privacy_9_title to R.string.privacy_9_body,
    )
}
