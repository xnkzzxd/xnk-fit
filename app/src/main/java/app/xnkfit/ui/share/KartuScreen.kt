package app.xnkfit.ui.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.share.CardKind
import app.xnkfit.domain.share.CardSize
import app.xnkfit.domain.share.CardSpec
import app.xnkfit.domain.share.CardStyle
import app.xnkfit.domain.share.CardTemplate
import app.xnkfit.ui.common.MinTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_WIDTH_PX = 540
private const val GALLERY_COLUMNS = 2

/** Kartu saya: gallery of the cards the phone has data for, and the editor (PRD P-6). Back leaves through [onBack]. */
@Composable
fun KartuScreen(vm: ShareViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    var editing by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (editing) editing = false else onBack() }
    if (editing && state.spec != null) {
        Editor(vm, state, onBack = { editing = false }, modifier)
    } else {
        Gallery(vm, state, onBack, onOpen = { vm.selectKind(it); editing = true }, modifier)
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, @androidx.annotation.StringRes title: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.share_back)) }
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Gallery(vm: ShareViewModel, state: ShareState, onBack: () -> Unit, onOpen: (CardKind) -> Unit, modifier: Modifier) {
    val cards = state.galleryCards()
    Column(modifier.fillMaxWidth()) {
        TopBar(onBack, R.string.share_title)
        if (state.loaded && cards.isEmpty()) {
            Text(stringResource(R.string.share_empty), Modifier.padding(16.dp))
            return
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.share_made_here), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.share_ready, cards.size), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.share_ready_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(cards.chunked(GALLERY_COLUMNS)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { spec -> Tile(spec, { onOpen(spec.kind) }, Modifier.weight(1f)) }
                    if (row.size < GALLERY_COLUMNS) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(spec: CardSpec, onClick: () -> Unit, modifier: Modifier) {
    val res = androidx.compose.ui.platform.LocalResources.current
    val texts = remember(spec) { cardTexts(res, spec) }
    val bg = when (spec.style) {
        CardStyle.DARK -> MaterialTheme.colorScheme.inverseSurface
        CardStyle.ACCENT -> MaterialTheme.colorScheme.primary
        CardStyle.LIGHT -> MaterialTheme.colorScheme.surface
    }
    val fg = when (spec.style) {
        CardStyle.DARK -> MaterialTheme.colorScheme.inverseOnSurface
        CardStyle.ACCENT -> MaterialTheme.colorScheme.onPrimary
        CardStyle.LIGHT -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier.heightIn(min = 140.dp).background(bg, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
    ) {
        Text(texts.label.uppercase(), color = fg, style = MaterialTheme.typography.labelSmall) // text-ok: case change only
        Text(texts.headline, color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        texts.noKgNote?.let { Text(it, color = fg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun Editor(vm: ShareViewModel, state: ShareState, onBack: () -> Unit, modifier: Modifier) {
    val spec = state.spec ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val maker = remember { CardBitmaps(resources) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The preview is drawn by the same renderer as the shared picture, only smaller.
    LaunchedEffect(spec) {
        preview = withContext(Dispatchers.Default) { runCatching { maker.make(spec, PREVIEW_WIDTH_PX) }.getOrNull() }
    }
    val o = state.options
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        TopBar(onBack, R.string.share_edit_title)
        val ratio = spec.size.widthPx.toFloat() / spec.size.heightPx
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            preview?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_preview, stringResource(kindName(spec.kind))),
                    modifier = Modifier.fillMaxWidth(if (spec.size == CardSize.STORY) STORY_FRACTION else 1f).aspectRatio(ratio),
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Group(R.string.share_card) {
                state.available.forEach { k -> Chip(stringResource(kindName(k)), o.kind == k) { vm.selectKind(k) } }
            }
            Group(R.string.share_style) {
                Chip(stringResource(R.string.share_style_dark), o.style == CardStyle.DARK) { vm.setStyle(CardStyle.DARK) }
                Chip(stringResource(R.string.share_style_light), o.style == CardStyle.LIGHT) { vm.setStyle(CardStyle.LIGHT) }
                Chip(stringResource(R.string.share_style_accent), o.style == CardStyle.ACCENT) { vm.setStyle(CardStyle.ACCENT) }
            }
            Group(R.string.share_size) {
                Chip(stringResource(R.string.share_size_story), o.size == CardSize.STORY) { vm.setSize(CardSize.STORY) }
                Chip(stringResource(R.string.share_size_square), o.size == CardSize.SQUARE) { vm.setSize(CardSize.SQUARE) }
            }
            Group(R.string.share_template) {
                Chip(stringResource(R.string.share_tpl_grid), o.template == CardTemplate.GRID) { vm.setTemplate(CardTemplate.GRID) }
                Chip(stringResource(R.string.share_tpl_big), o.template == CardTemplate.BIG) { vm.setTemplate(CardTemplate.BIG) }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SwitchRow(R.string.share_sw_brand, o.brand, vm::setBrand)
                    // Saya's own "hide weight" keeps kg off the card whatever this switch says (R_SHARE_NOKG).
                    SwitchRow(R.string.share_sw_nokg, spec.noKg, vm::setHideKg, enabled = !state.sayaHidesWeight)
                    if (o.kind == CardKind.PROGRESS && state.photoPath != null) {
                        SwitchRow(R.string.share_sw_photo, o.withPhoto, vm::setWithPhoto)
                        SwitchRow(R.string.share_sw_face, o.blurFaces, vm::setBlurFaces)
                    }
                }
            }
            if (failed) Text(stringResource(R.string.share_failed), color = MaterialTheme.colorScheme.error)
            val chooserTitle = stringResource(R.string.share_chooser)
            Button(
                onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.Default) {
                            runCatching { ShareFiles.write(context.cacheDir, maker.make(spec, spec.size.widthPx)) }.getOrNull()
                        }
                        failed = file == null
                        if (file != null) context.startActivity(ShareFiles.chooser(context, file, chooserTitle))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.share_do)) }
        }
    }
}

private const val STORY_FRACTION = 0.62f

private fun kindName(k: CardKind) = when (k) {
    CardKind.WORKOUT -> R.string.share_kind_workout
    CardKind.PROGRESS -> R.string.share_kind_progress
    CardKind.CONSISTENCY -> R.string.share_kind_consistency
    CardKind.FOOD -> R.string.share_kind_food
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Group(@androidx.annotation.StringRes title: Int, content: @Composable () -> Unit) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = MinTouch))
}

@Composable
private fun SwitchRow(@androidx.annotation.StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
