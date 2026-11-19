package app.xnkfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.ui.bantuan.WelcomeCard
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
import app.xnkfit.ui.makan.MakanHost
import app.xnkfit.ui.makan.MakanViewModel
import app.xnkfit.ui.today.TodayScreen
import app.xnkfit.ui.today.TodayViewModel
import androidx.compose.runtime.LaunchedEffect

/** App frame: five bottom destinations (PRD N-1). Hari ini, Makan and Saya exist; the rest are empty stubs. */
@Composable
fun XnkApp(
    today: TodayViewModel,
    makan: MakanViewModel,
    me: @Composable (Modifier) -> Unit = { EmptyScreen(R.string.empty_other, it) },
    workout: @Composable (Modifier) -> Unit = { EmptyScreen(R.string.empty_other, it) },
    /** Latihan first when a workout is unfinished, so "Lanjutkan latihan?" is the first thing seen (Design §4). */
    start: Destination = Destination.TODAY,
    /** Tabs whose welcome card was dismissed; null while the settings are still loading (no card flashes). */
    seenCards: Set<WelcomeTab>? = null,
    onDismissCard: (WelcomeTab) -> Unit = {},
) {
    var selected by rememberSaveable { mutableStateOf(start) }
    // Hari ini reads what the other screens wrote, so it reloads each time it is opened.
    LaunchedEffect(selected) { if (selected == Destination.TODAY) today.refresh() }
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
        val modifier = Modifier
        val tab = welcomeTabOf(selected)
        Column(Modifier.padding(padding)) {
        if (tab != null && seenCards != null && tab !in seenCards) WelcomeCard(tab, onDismiss = { onDismissCard(tab) })
        Box(Modifier.weight(1f)) {
        when (selected) {
            Destination.TODAY -> TodayScreen(
                today,
                onOpenWorkout = { selected = Destination.WORKOUT },
                onOpenFood = { selected = Destination.FOOD },
                modifier = modifier,
            )
            Destination.FOOD -> MakanHost(makan, modifier)
            Destination.ME -> me(modifier)
            // B-07: the logger until the Latihan screen (B-08) wraps it.
            Destination.WORKOUT -> workout(modifier)
            else -> EmptyScreen(R.string.empty_other, modifier)
        }
        }
        }
    }
}

/** The tab that carries a welcome card; the add action has none. */
fun welcomeTabOf(d: Destination): WelcomeTab? = when (d) {
    Destination.TODAY -> WelcomeTab.TODAY
    Destination.WORKOUT -> WelcomeTab.WORKOUT
    Destination.FOOD -> WelcomeTab.FOOD
    Destination.ME -> WelcomeTab.ME
    Destination.ADD -> null
}
