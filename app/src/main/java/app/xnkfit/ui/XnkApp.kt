package app.xnkfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.ui.bantuan.WelcomeCard
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import app.xnkfit.ui.adaptive.WidthClass
import app.xnkfit.ui.adaptive.LocalWidthClass
import app.xnkfit.ui.adaptive.currentWidthClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.xnkfit.R
import app.xnkfit.ui.common.EmptyScreen
import app.xnkfit.ui.nav.Destination
import app.xnkfit.ui.makan.MakanHost
import app.xnkfit.ui.makan.MakanViewModel
import app.xnkfit.ui.today.TodayScreen
import app.xnkfit.ui.today.TodayViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import app.xnkfit.ui.design.PillBottomBar
import app.xnkfit.ui.design.PillRail
import app.xnkfit.ui.design.TopBand
import app.xnkfit.ui.design.Xnk
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val width = currentWidthClass()
    val wide = width == WidthClass.WIDE
    val c = Xnk.colors
    val todayState by today.state.collectAsState()
    Scaffold(
        containerColor = c.bg,
        topBar = {
            // Phone and tablet: the black band with the brand; Hari ini shows the date there, the other tabs their name.
            val dateText = if (selected == Destination.TODAY) todayState.day?.format(BandDate) else null
            TopBand(
                brand = stringResource(R.string.ob_brand),
                title = if (selected == Destination.TODAY) null else stringResource(selected.label),
                subtitle = dateText,
            )
        },
        bottomBar = {
            // The add action opens a sheet in a later task; for now it changes nothing.
            if (!wide) PillBottomBar(selected, onSelect = { if (it.isScreen) selected = it })
        },
    ) { padding ->
    CompositionLocalProvider(LocalWidthClass provides width) {
    Row(Modifier.padding(padding)) {
        // Wide windows: the same five destinations in a side rail (PRD N-4), in the band colour.
        if (wide) PillRail(selected, onSelect = { if (it.isScreen) selected = it })
        Column(Modifier.weight(1f)) {
        val modifier = Modifier
        val tab = welcomeTabOf(selected)
        if (tab != null && seenCards != null && tab !in seenCards) {
            // On the band, so Hari ini's hero still hangs from the same black surface below it.
            Box(Modifier.background(if (selected == Destination.TODAY && !wide) c.band else c.bg)) {
                WelcomeCard(tab, onDismiss = { onDismissCard(tab) })
            }
        }
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
    }
}

/** Date in the band ("Jumat, 27 November"), as on the XNK portal. */
private val BandDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id", "ID")) // text-ok: date pattern

/** The tab that carries a welcome card; the add action has none. */
fun welcomeTabOf(d: Destination): WelcomeTab? = when (d) {
    Destination.TODAY -> WelcomeTab.TODAY
    Destination.WORKOUT -> WelcomeTab.WORKOUT
    Destination.FOOD -> WelcomeTab.FOOD
    Destination.ME -> WelcomeTab.ME
    Destination.ADD -> null
}
