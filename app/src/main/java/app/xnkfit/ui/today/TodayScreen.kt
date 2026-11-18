package app.xnkfit.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.xnkfit.R
import app.xnkfit.ui.common.EmptyScreen

/** Hari ini (PRD H-1). Skeleton only: the empty state, nothing else. */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    EmptyScreen(message = R.string.empty_today, modifier = modifier)
}
