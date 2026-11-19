package app.xnkfit.ui.saya

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.xnkfit.ui.adaptive.TwoColumns
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.xnkfit.R
import app.xnkfit.data.db.PhotoEntity
import app.xnkfit.data.progress.BadgeInfo
import app.xnkfit.data.progress.BadgeState
import app.xnkfit.data.progress.MomentKind
import app.xnkfit.ui.common.MinTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDONESIA = Locale.forLanguageTag("id-ID") // text-ok: locale tag, not text
private val ONE_DECIMAL = DecimalFormat("0.0", DecimalFormatSymbols(INDONESIA)) // text-ok: number pattern
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", INDONESIA) // text-ok: date pattern
private const val PHOTO_THUMB_PX = 360
private const val BADGE_COLUMNS = 4
private const val PHOTO_COLUMNS = 3
private const val CAPTURE_AUTHORITY_SUFFIX = ".captures" // text-ok: provider authority

private fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(SHORT_DATE)

@StringRes
private fun badgeIcon(name: String): Int = when (name) {
    "star" -> R.string.badge_icon_star // text-ok: icon name from badges.json
    "medal" -> R.string.badge_icon_medal // text-ok: icon name from badges.json
    "trophy" -> R.string.badge_icon_trophy // text-ok: icon name from badges.json
    "flame" -> R.string.badge_icon_flame // text-ok: icon name from badges.json
    "run" -> R.string.badge_icon_run // text-ok: icon name from badges.json
    "food" -> R.string.badge_icon_food // text-ok: icon name from badges.json
    "check" -> R.string.badge_icon_check // text-ok: icon name from badges.json
    "ruler" -> R.string.badge_icon_ruler // text-ok: icon name from badges.json
    "camera" -> R.string.badge_icon_camera // text-ok: icon name from badges.json
    "heart" -> R.string.badge_icon_heart // text-ok: icon name from badges.json
    "cal" -> R.string.badge_icon_cal // text-ok: icon name from badges.json
    else -> R.string.badge_icon_star
}

/** Saya (PRD P-1…P-5). One column; the tablet layout is B-17. */
@Composable
fun SayaScreen(vm: SayaViewModel, modifier: Modifier = Modifier, onOpenCards: () -> Unit = {}, onOpenSettings: (() -> Unit)? = null) {
    val state by vm.state.collectAsState()
    // Opening the tab recounts points and badges, so work finished elsewhere shows up here.
    LaunchedEffect(Unit) { vm.refresh() }
    val summary = state.summary
    if (!state.loaded || summary == null) {
        Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.me_loading))
        }
        return
    }
    var showMeasure by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var openBadge by remember { mutableStateOf<BadgeState?>(null) }

    TwoColumns(modifier, padding = androidx.compose.foundation.layout.PaddingValues(16.dp), left = {
        item { ProfileCard(state, summary) }
        item { BodyCard(state, vm, onAdd = { showMeasure = true }) }
        item { BadgesCard(summary.badges, onOpen = { openBadge = it }) }
    }, right = {
        item { JourneyCard(summary, onAdd = { showNote = true }) }
        item { CardsEntry(onOpenCards) }
        if (onOpenSettings != null) item { SettingsEntry(onOpenSettings) }
        item { PhotosCard(state, vm) }
    })

    if (showMeasure) MeasureDialog(state.measureInvalid, vm, onClose = { showMeasure = false })
    if (showNote) NoteDialog(onSave = { vm.addNote(it); showNote = false }, onClose = { showNote = false })
    openBadge?.let { BadgeDialog(it, onClose = { openBadge = null }) }
    state.celebrate.firstOrNull()?.let { info ->
        AlertDialog(
            onDismissRequest = vm::celebrationShown,
            title = { Text(stringResource(R.string.me_badge_new)) },
            text = {
                Column {
                    Text(info.title, fontWeight = FontWeight.Bold)
                    Text(info.message) // text from content/badges.json
                }
            },
            confirmButton = { TextButton(onClick = vm::celebrationShown) { Text(stringResource(R.string.me_close)) } },
        )
    }
}

@Composable
private fun ProfileCard(state: SayaState, s: app.xnkfit.data.progress.ProgressSummary) {
    val name = state.nickname?.takeIf { it.isNotBlank() } ?: stringResource(R.string.me_default_name)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text(name.take(2).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                Text(name, style = MaterialTheme.typography.titleLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.me_level, s.level.level), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.me_level_points, s.level.points.toString(), s.level.next.toString()))
            }
            LinearProgressIndicator(progress = { s.level.fraction }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(s.finishedWorkouts, R.string.me_stat_workouts)
                Stat(s.streak.current, R.string.me_stat_weeks)
                Stat(s.personalRecords, R.string.me_stat_records)
            }
            Text(stringResource(R.string.me_streak_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Entry to Pengaturan (B-14, PRD P-7); Data dan cadangan (B-16) is opened from there. */
@Composable
private fun SettingsEntry(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.set_entry_hint), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpen, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.set_open)) }
        }
    }
}

/** Entry to Kartu saya (B-13, PRD P-6). */
@Composable
private fun CardsEntry(onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.me_cards_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.me_cards_hint), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpen, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.me_cards_open)) }
        }
    }
}

@Composable
private fun Stat(value: Int, @StringRes label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BodyCard(state: SayaState, vm: SayaViewModel, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.me_weight_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setWeightHidden(!state.weightHidden) }, modifier = Modifier.defaultMinSize(minHeight = MinTouch)) {
                    Text(stringResource(if (state.weightHidden) R.string.me_weight_show else R.string.me_weight_hide))
                }
            }
            val trend = state.weight
            when {
                state.weightHidden -> Text(stringResource(R.string.me_weight_hidden), style = MaterialTheme.typography.bodySmall)
                trend.latest == null -> Text(stringResource(R.string.me_weight_none), style = MaterialTheme.typography.bodySmall)
                else -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.me_weight_latest, ONE_DECIMAL.format(trend.latest)), style = MaterialTheme.typography.headlineMedium)
                        // Same colour and weight for a rise and a fall: the change is shown, never judged (P-1).
                        trend.change?.let { Text(stringResource(R.string.me_weight_change, signed(it))) }
                    }
                    val chartText = stringResource(R.string.me_weight_chart)
                    WeightChart(trend.points, Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = chartText })
                }
            }
            state.waist?.let { Text(stringResource(R.string.me_waist_latest, ONE_DECIMAL.format(it))) }
            OutlinedButton(onClick = onAdd, modifier = Modifier.defaultMinSize(minHeight = MinTouch)) { Text(stringResource(R.string.me_measure_add)) }
        }
    }
}

private fun signed(v: Double): String = (if (v > 0) "+" else "") + ONE_DECIMAL.format(v) // text-ok: sign characters

@Composable
private fun WeightChart(points: List<WeightPoint>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val lo = points.minOf { it.value }
        val hi = points.maxOf { it.value }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val t0 = points.first().takenAt
        val tSpan = (points.last().takenAt - t0).takeIf { it > 0 } ?: 1L
        fun at(p: WeightPoint) = Offset(
            x = if (points.size == 1) size.width / 2 else ((p.takenAt - t0).toFloat() / tSpan) * size.width,
            y = size.height - ((p.value - lo) / span).toFloat() * size.height * 0.8f - size.height * 0.1f,
        )
        val offsets = points.map(::at)
        offsets.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, strokeWidth = 6f) }
        offsets.forEach { drawCircle(line, radius = 8f, center = it) }
    }
}

@Composable
private fun BadgesCard(badges: List<BadgeState>, onOpen: (BadgeState) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.me_badges_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.me_badges_count, badges.count { it.earnedAt != null }, badges.size))
            }
            badges.chunked(BADGE_COLUMNS).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { b -> Box(Modifier.weight(1f)) { BadgeTile(b, onOpen) } }
                    repeat(BADGE_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(b: BadgeState, onOpen: (BadgeState) -> Unit) {
    val earned = b.earnedAt != null
    Column(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouch).clickable { onOpen(b) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(if (earned) badgeIcon(b.info.icon) else R.string.badge_icon_lock)) }
        Text(b.info.title, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

@Composable
private fun BadgeDialog(b: BadgeState, onClose: () -> Unit) {
    val earnedAt = b.earnedAt
    val body = if (earnedAt != null) {
        b.info.message + "\n\n" + stringResource(R.string.me_badge_earned, date(earnedAt)) // text-ok: joins file text and a string resource
    } else {
        stringResource(R.string.me_badge_locked)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(b.info.title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.me_close)) } },
    )
}

@Composable
private fun JourneyCard(s: app.xnkfit.data.progress.ProgressSummary, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.me_journey_title), style = MaterialTheme.typography.titleMedium)
            if (s.moments.isEmpty()) Text(stringResource(R.string.me_journey_empty), style = MaterialTheme.typography.bodySmall)
            s.moments.forEach { m ->
                val text = when (m.kind) {
                    MomentKind.BADGE -> stringResource(R.string.me_journey_badge, m.text)
                    MomentKind.LEVEL -> stringResource(R.string.me_journey_level, m.text)
                    else -> m.text
                }
                Column {
                    Text(text, fontWeight = FontWeight.Medium)
                    Text(date(m.at), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.defaultMinSize(minHeight = MinTouch)) { Text(stringResource(R.string.me_journey_add)) }
        }
    }
}

@Composable
private fun PhotosCard(state: SayaState, vm: SayaViewModel) {
    val context = LocalContext.current
    var pickKind by rememberSaveable { mutableStateOf(false) }
    // The id and side survive the camera app being open (the process can be recreated meanwhile).
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf("front") } // text-ok: stored code
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingId?.let { vm.photoCaptured(it, pendingKind, ok) }
        pendingId = null
    }
    val denied = stringResource(R.string.me_photo_denied)
    fun launchCamera() {
        val id = vm.newPhotoId()
        pendingId = id
        val uri = FileProvider.getUriForFile(context, context.packageName + CAPTURE_AUTHORITY_SUFFIX, vm.captureFile(id))
        camera.launch(uri)
    }
    // Camera permission is asked here, when a photo is wanted (PRD O-3), not at install.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, denied, Toast.LENGTH_LONG).show()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.me_photos_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.me_photos_private), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.me_photos_explain), style = MaterialTheme.typography.bodySmall)
            if (state.photos.isEmpty()) Text(stringResource(R.string.me_photos_empty))
            state.photos.chunked(PHOTO_COLUMNS).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { p -> Box(Modifier.weight(1f)) { PhotoThumb(p) { viewing = p } } }
                    repeat(PHOTO_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.photoFailed) Text(stringResource(R.string.me_photo_failed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { pickKind = true }, modifier = Modifier.defaultMinSize(minHeight = MinTouch)) {
                Text(stringResource(R.string.me_photo_add))
            }
        }
    }

    if (pickKind) {
        AlertDialog(
            onDismissRequest = { pickKind = false },
            title = { Text(stringResource(R.string.me_photo_kind_title)) },
            text = {
                Column {
                    listOf("front" to R.string.me_photo_front, "side" to R.string.me_photo_side, "back" to R.string.me_photo_back) // text-ok: stored codes
                        .forEach { (code, label) ->
                            TextButton(
                                onClick = {
                                    pendingKind = code
                                    pickKind = false
                                    val has = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (has) launchCamera() else permission.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouch),
                            ) { Text(stringResource(label)) }
                        }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickKind = false }) { Text(stringResource(R.string.me_cancel)) } },
        )
    }
    viewing?.let { p ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(date(p.takenAt)) },
            text = {
                Column {
                    PhotoThumb(p, large = true) {}
                    Text(stringResource(R.string.me_photo_delete_ask), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { vm.deletePhoto(p.id); viewing = null }) { Text(stringResource(R.string.me_photo_delete)) } },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text(stringResource(R.string.me_close)) } },
        )
    }
}

@Composable
private fun PhotoThumb(p: PhotoEntity, large: Boolean = false, onClick: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, p.path) {
        value = withContext(Dispatchers.IO) { decodeThumb(p.path, if (large) PHOTO_THUMB_PX * 2 else PHOTO_THUMB_PX) }
    }
    val kindLabel = stringResource(
        when (p.kind) {
            "side" -> R.string.me_photo_side // text-ok: stored code
            "back" -> R.string.me_photo_back // text-ok: stored code
            else -> R.string.me_photo_front
        },
    )
    val description = stringResource(R.string.me_photo_item, kindLabel, date(p.takenAt))
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
    ) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop) }
    }
}

private fun decodeThumb(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun MeasureDialog(invalid: Boolean, vm: SayaViewModel, onClose: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(MeasureType.WEIGHT) }
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.dismissMeasureError(); onClose() },
        title = { Text(stringResource(R.string.me_measure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasureType.entries.forEach { t ->
                        val label = stringResource(if (t == MeasureType.WEIGHT) R.string.me_measure_weight else R.string.me_measure_waist)
                        if (t == type) Button(onClick = { type = t }) { Text(label) } else OutlinedButton(onClick = { type = t }) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MEASURE_TEXT_MAX) },
                    label = { Text(stringResource(R.string.me_measure_value)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                if (invalid) Text(stringResource(R.string.me_measure_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.addMeasure(type, text)
                // Closed on a good number; the next state keeps the dialog open with an error otherwise.
                if (isValid(type, text)) onClose()
            }) { Text(stringResource(R.string.me_measure_save)) }
        },
        dismissButton = { TextButton(onClick = { vm.dismissMeasureError(); onClose() }) { Text(stringResource(R.string.me_cancel)) } },
    )
}

private const val MEASURE_TEXT_MAX = 7

private fun isValid(type: MeasureType, text: String): Boolean =
    text.trim().replace(',', '.').toDoubleOrNull()?.let { it >= type.min && it <= type.max } == true

@Composable
private fun NoteDialog(onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.me_journey_add)) },
        text = { OutlinedTextField(text, { text = it.take(NOTE_MAX) }, label = { Text(stringResource(R.string.me_journey_hint)) }) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.me_measure_save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.me_cancel)) } },
    )
}

private const val NOTE_MAX = 140
