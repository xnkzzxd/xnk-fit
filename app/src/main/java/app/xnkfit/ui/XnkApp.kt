package app.xnkfit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.xnkfit.R
import app.xnkfit.ui.common.EmptyScreen
import app.xnkfit.ui.nav.Destination
import app.xnkfit.ui.today.TodayScreen

/** App frame: five bottom destinations (PRD N-1). Only Hari ini exists; the rest are empty stubs. */
@Composable
fun XnkApp(me: @Composable (Modifier) -> Unit = { EmptyScreen(R.string.empty_other, it) }) {
    var selected by rememberSaveable { mutableStateOf(Destination.TODAY) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val label = stringResource(destination.label)
                    NavigationBarItem(
                        selected = destination == selected,
                        // The add action opens a sheet in a later task; for now it changes nothing.
                        onClick = { if (destination.isScreen) selected = destination },
                        icon = { Icon(painterResource(destination.icon), contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selected) {
            Destination.TODAY -> TodayScreen(modifier)
            Destination.ME -> me(modifier)
            else -> EmptyScreen(R.string.empty_other, modifier)
        }
    }
}
