package app.xnkfit.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.nav.Destination

/**
 * Floating pill bottom bar of the XNK portal (`.tabbar`, `.tab`, `.tab-fab`): ink pill, radius 24, at most 420 dp wide with
 * 14 dp side margins (8 dp below instead of 14, so the bar takes no more height than the old Material bar); four labelled tabs and the centre ＋ (a 54 dp light square). Tabs keep their visible label and a click action,
 * which is what the smoke and screenshot tests look for (hasText(label) and hasClickAction()).
 */
@Composable
fun PillBottomBar(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val c = Xnk.colors
    val shape = RoundedCornerShape(XnkRadius.bar)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(c.ink)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { d ->
                val label = stringResource(d.label)
                if (d.isScreen) {
                    PillTab(label, d, d == selected, Modifier.weight(1f)) { onSelect(d) }
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(XnkRadius.fab))
                                .background(c.onInk)
                                .clickable(role = Role.Button) { onSelect(d) }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(d.icon), contentDescription = null, tint = c.ink, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillTab(label: String, d: Destination, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Xnk.colors
    val color = if (active) c.onInk else c.onInkMuted
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(d.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = XnkType.tab, color = color, textAlign = TextAlign.Center, maxLines = 2)
        // Active dot under the label (`.tab.active::after`).
        if (active) Canvas(Modifier.size(4.dp)) { drawCircle(color) } else Spacer(Modifier.height(4.dp))
    }
}

/** Side rail for wide windows (PRD N-4, B-17) in the same ink look: same five destinations, labelled, ＋ in the middle. */
@Composable
fun PillRail(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val c = Xnk.colors
    Column(
        modifier
            .fillMaxHeight()
            .widthIn(min = 88.dp)
            .background(c.band)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Destination.entries.forEach { d ->
            val label = stringResource(d.label)
            val active = d == selected
            if (d.isScreen) {
                val color = if (active) c.onBand else c.onBandMuted
                Column(
                    Modifier
                        .widthIn(min = 72.dp)
                        .heightIn(min = MinTouch + 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) c.bandCard else c.band)
                        .selectable(selected = active, role = Role.Tab) { onSelect(d) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(painterResource(d.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Text(label, style = XnkType.tab, color = color, textAlign = TextAlign.Center)
                }
            } else {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(XnkRadius.fab))
                        .background(c.onBand)
                        .clickable(role = Role.Button) { onSelect(d) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(d.icon), contentDescription = null, tint = c.band, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}
