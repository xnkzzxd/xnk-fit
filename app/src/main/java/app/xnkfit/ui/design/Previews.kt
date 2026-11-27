package app.xnkfit.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.xnkfit.ui.nav.Destination

// Compose previews of every XNK component, light and dark, at 1.0 and 1.3 font scale (docs/Design-Visual.md §2).
// Sample words are preview-only placeholders, never shipped as UI text.

@Composable
private fun Sheet(dark: Boolean, content: @Composable () -> Unit) = XnkDesignTheme(dark) {
    Column(Modifier.background(Xnk.colors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Catalogue(dark: Boolean) = Sheet(dark) {
    TopBand(brand = "XNK FIT", subtitle = "Jumat, 27 November") // text-ok: preview sample
    HeroCard(eyebrow = "Latihan hari ini") { // text-ok: preview sample
        Text("Full Body B", style = XnkType.heroTitle) // text-ok: preview sample
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Mulai", {}, Modifier.weight(1f), onInk = true) // text-ok: preview sample
            TonalButton("Lihat", {}, Modifier.weight(1f), onInk = true) // text-ok: preview sample
        }
    }
    SectionHeader("Tugas hari ini", trailing = "1 dari 3") // text-ok: preview sample
    XnkCard {
        CheckRow("Timbang", done = true, stateText = "Selesai") // text-ok: preview sample
        CheckRow("Latihan", done = false, stateText = "Belum", sub = "Full Body B", action = { RowAction("Mulai", {}, strong = true) }) // text-ok: preview sample
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Simpan", {}) // text-ok: preview sample
        SecondaryButton("Batal", {}) // text-ok: preview sample
        TonalButton("Catat", {}) // text-ok: preview sample
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("12", "sesi", Modifier.weight(1f)) // text-ok: preview sample
        StatTile("5", "minggu", Modifier.weight(1f), emphasis = true) // text-ok: preview sample
        ProgressRing(0.64f) { Text("630", style = XnkType.kpi) } // text-ok: preview sample
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Semua", selected = true, onClick = {}) // text-ok: preview sample
        Chip("Dada", selected = false, onClick = {}) // text-ok: preview sample
        KgRepCell("40", "KG") // text-ok: preview sample
        KgRepCell("10", "REP") // text-ok: preview sample
        CheckButton(true, "Set selesai", {}) // text-ok: preview sample
        CheckButton(false, "Set selesai", {}) // text-ok: preview sample
    }
    CardFlat { LineChart(listOf(72f, 71.6f, 71.8f, 71.1f, 70.9f, 70.4f), "Berat 6 minggu") } // text-ok: preview sample
    MeterBar(0.4f)
    PillBottomBar(Destination.TODAY, {})
}

@Preview(name = "Komponen terang", heightDp = 1400) // text-ok: preview label
@Composable
private fun CatalogueLight() = Catalogue(dark = false)

@Preview(name = "Komponen gelap", heightDp = 1400) // text-ok: preview label
@Composable
private fun CatalogueDark() = Catalogue(dark = true)

@Preview(name = "Komponen font 1,3", heightDp = 1600, fontScale = 1.3f) // text-ok: preview label
@Composable
private fun CatalogueBigFont() = Catalogue(dark = false)

@Preview(name = "Rail tablet", heightDp = 600) // text-ok: preview label
@Composable
private fun RailPreview() = XnkDesignTheme(false) { PillRail(Destination.WORKOUT, {}) }
