package app.xnkfit.ui.today

import androidx.annotation.StringRes
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xnkfit.R
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.rules.GateNote
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.today.ActivityWeek
import app.xnkfit.domain.today.RecalcPrompt
import app.xnkfit.domain.today.RecalcReason
import app.xnkfit.domain.today.SourceRef
import app.xnkfit.domain.today.TargetCard
import app.xnkfit.domain.today.TargetNumber
import app.xnkfit.domain.today.TaskKind
import app.xnkfit.domain.today.TodayTask
import app.xnkfit.domain.today.WorkoutCard
import app.xnkfit.domain.today.sourcesFor
import app.xnkfit.ui.adaptive.TwoColumns
import app.xnkfit.ui.adaptive.currentTwoColumns
import app.xnkfit.ui.common.MinTouch
import app.xnkfit.ui.design.BandHang
import app.xnkfit.ui.design.CheckRow
import app.xnkfit.ui.design.DayDot
import app.xnkfit.ui.design.HeroCard
import app.xnkfit.ui.design.HomeTopBand
import app.xnkfit.ui.design.MeterBar
import app.xnkfit.ui.design.PrimaryButton
import app.xnkfit.ui.design.ProgressRing
import app.xnkfit.ui.design.RowAction
import app.xnkfit.ui.design.SecondaryButton
import app.xnkfit.ui.design.SectionHeader
import app.xnkfit.ui.design.TonalButton
import app.xnkfit.ui.design.TopBand
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.xnkfit.ui.design.Xnk
import app.xnkfit.ui.design.XnkCard
import app.xnkfit.ui.design.XnkSpace
import app.xnkfit.ui.design.XnkType

private val Gap = 16.dp

/** Text for each source behind "(i)" (strings.xml, `src_*`). */
@StringRes
fun SourceRef.text(): Int = when (this) {
    SourceRef.MIFFLIN_S1 -> R.string.src_mifflin_s1
    SourceRef.PAL_S2 -> R.string.src_pal_s2
    SourceRef.NHLBI_S5 -> R.string.src_nhlbi_s5
    SourceRef.PROTEIN_S6 -> R.string.src_protein_s6
    SourceRef.SURPLUS_S19 -> R.string.src_surplus_s19
    SourceRef.AKG_S8 -> R.string.src_akg_s8
}

/** One line per gate note on the target card. Same notes as the screening result, in gate order (R_GATE_*). */
@StringRes
fun GateNote.text(): Int = when (this) {
    GateNote.RISK_SEE_DOCTOR -> R.string.td_note_risk
    GateNote.DEFICIT_CAPPED -> R.string.td_note_deficit_capped
    GateNote.UNDER_18_LIGHT_MODE -> R.string.td_note_u18
    GateNote.PREGNANT_NO_DEFICIT -> R.string.td_note_preg
    GateNote.EATING_DISORDER_NO_NUMBERS -> R.string.td_note_ed
    GateNote.BMI_UNDER_NO_TURUN -> R.string.td_note_bmi
    GateNote.GOAL_CHANGED_TO_JAGA -> R.string.td_note_goal_changed
    GateNote.FLOOR_RAISED -> R.string.td_note_floor
}

@StringRes
private fun TargetNumber.label(): Int = when (this) {
    TargetNumber.KCAL -> R.string.td_target_kcal
    TargetNumber.PROTEIN -> R.string.td_macro_protein
    TargetNumber.CARB -> R.string.td_macro_carb
    TargetNumber.FAT -> R.string.td_macro_fat
    TargetNumber.FIBRE -> R.string.td_target_fibre
}

@StringRes
private fun Goal.label(): Int = when (this) {
    Goal.TURUN -> R.string.td_target_goal_turun
    Goal.NAIK -> R.string.td_target_goal_naik
    Goal.JAGA -> R.string.td_target_goal_jaga
}

@StringRes
private fun TaskKind.label(): Int = when (this) {
    TaskKind.WEIGH -> R.string.td_task_weigh
    TaskKind.WORKOUT -> R.string.td_task_workout
    TaskKind.FOOD -> R.string.td_task_food
}

/**
 * Hari ini (PRD H-1…H-3, M-2), in the XNK look (docs/Design-Visual.md): the band continues from the top bar and the hero card
 * (next workout, rest day, activity or first steps) hangs over its edge; then the to-do list, food ring, target and week.
 * Order (H-1): prompt, workout or activity card, tasks, food, target, week. Navigation is passed in.
 */
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpenWorkout: () -> Unit,
    onOpenFood: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bell: the reminders live in Pengaturan. */
    onOpenSettings: () -> Unit = {},
    /** Avatar: the Saya tab. */
    onOpenMe: () -> Unit = {},
) {
    val s by vm.state.collectAsState()
    // Design §10: "cold start to a usable Hari ini" ends here; Macrobenchmark reads it as timeToFullDisplay (B-19).
    ReportDrawnWhen { s.loaded }
    val split = currentTwoColumns()
    val pad = Modifier.padding(horizontal = if (split) 0.dp else XnkSpace.page)
    Column(modifier) {
    if (split) BandTop(s, onOpenSettings, onOpenMe)
    TwoColumns(
        Modifier.weight(1f),
        split = split,
        spacing = Gap,
        padding = PaddingValues(horizontal = if (split) XnkSpace.pageWide else 0.dp),
        left = {
            // One item from the band to the hero, so no page-coloured gap opens between them.
            item {
                Column {
                    if (!split) BandTop(s, onOpenSettings, onOpenMe)
                    s.recalc?.let { p ->
                        Box(Modifier.background(if (split) Xnk.colors.bg else Xnk.colors.band).padding(bottom = 12.dp)) {
                            RecalcCard(p, vm::acceptRecalc, vm::keepTarget, pad)
                        }
                    }
                    if (s.loaded) BandHang(Modifier.padding(top = if (split) XnkSpace.gap else 0.dp), bandBelow = if (split) 0.dp else 36.dp, sidePadding = !split) {
                        val w = s.workout
                        if (w == null) ActivityHero(s.activity, onOpenWorkout) else WorkoutHero(w, onOpenWorkout)
                    }
                }
            }
            if (s.failed) item { Text(stringResource(R.string.td_failed), style = XnkType.md, color = Xnk.colors.fgMuted, modifier = pad) }
            if (s.loaded) {
                item { TasksSection(s.tasks, s.food, onOpenWorkout, onOpenFood, pad) }
            }
        },
        right = {
            if (s.loaded) {
                item { FoodSection(s.food, onOpenFood, pad.padding(top = if (split) XnkSpace.gap else 0.dp)) }
                item { TargetCardView(s.target, pad) }
                item { WeekCard(s.week, s.day?.dayOfWeek?.value, pad) }
                item { Box(Modifier.padding(bottom = XnkSpace.gap)) }
            }
        },
    )
    }
}

/** Date in the band ("Jumat, 27 November"), as on the XNK portal. */
private val BandDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id", "ID")) // text-ok: date pattern

/** The band at the top of the page (phone: inside the list, so it scrolls away as on the portal): logo, bell, avatar, date and level. */
@Composable
private fun BandTop(s: TodayState, onBell: () -> Unit, onAvatar: () -> Unit) {
    val level = if (s.loaded) stringResource(R.string.td_level_streak, s.level, s.streakWeeks) else null
    HomeTopBand(
        date = s.day?.format(BandDate),
        initials = initialsOf(s.nickname) ?: stringResource(R.string.td_avatar_neutral),
        bellLabel = stringResource(R.string.td_bell),
        avatarLabel = stringResource(R.string.td_avatar),
        onBell = onBell,
        onAvatar = onAvatar,
        trailing = level,
    )
}

/** Up to two initials from the nickname's first two words; null when there is no nickname. */
private fun initialsOf(nickname: String?): String? =
    nickname?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(2)?.map { it.first().uppercaseChar() }?.joinToString("")?.ifEmpty { null } // text-ok: regex

@Composable
private fun RecalcCard(p: RecalcPrompt, onAccept: () -> Unit, onKeep: () -> Unit, modifier: Modifier) {
    val text = when (p.reason) {
        RecalcReason.WEIGHT_DOWN -> stringResource(R.string.td_recalc_weight_down, kgText(p.deltaKg))
        RecalcReason.WEIGHT_UP -> stringResource(R.string.td_recalc_weight_up, kgText(p.deltaKg))
        RecalcReason.GOAL_CHANGED -> stringResource(R.string.td_recalc_goal)
        RecalcReason.PACE -> stringResource(R.string.td_recalc_pace, p.raiseKcal ?: 0)
    }
    val accept = if (p.reason == RecalcReason.PACE) R.string.td_recalc_accept_pace else R.string.td_recalc_accept
    XnkCard(modifier) {
        Text(text, style = XnkType.lg)
        EvenRow {
            PrimaryButton(stringResource(accept), onAccept, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.td_recalc_keep), onKeep, Modifier.weight(1f))
        }
    }
}

/** Comma as the decimal sign, one decimal, no trailing ",0". */
private fun kgText(kg: Double?): String {
    val v = Math.round((kg ?: 0.0) * 10) / 10.0
    return (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()).replace('.', ',')
}

@Composable
private fun EvenRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

/** Hero buttons sit at the bottom of the card, under the rings (`.nx-actions`). */
@Composable
private fun ColumnScope.HeroButtons(content: @Composable RowScope.() -> Unit) {
    Spacer(Modifier.weight(1f))
    EvenRow(content)
}

@Composable
private fun HeroTitle(text: String) =
    Text(text, style = XnkType.heroTitle, color = Xnk.colors.onBand, modifier = Modifier.padding(end = 72.dp))

/** `.nx-when`: the line above the big number (16 sp bold). */
@Composable
private fun HeroWhen(text: String) =
    Text(text, style = XnkType.lg.copy(fontWeight = FontWeight.Bold), color = Xnk.colors.onBand, modifier = Modifier.padding(top = 6.dp, end = 72.dp))

/** `.nx-count`: the big number (4xl, 800); it may wrap on large fonts. */
@Composable
private fun HeroBig(text: String) = Text(text, style = XnkType.bigNumber, color = Xnk.colors.onBand, modifier = Modifier.padding(end = 72.dp))

@Composable
private fun HeroMeta(text: String) = Text(text, style = XnkType.sm, color = Xnk.colors.onBandMuted, modifier = Modifier.padding(end = 72.dp))

@Composable
private fun WorkoutHero(card: WorkoutCard, onOpen: () -> Unit) {
    when (card) {
        WorkoutCard.NoProgram -> HeroCard(eyebrow = stringResource(R.string.td_noprogram_title)) {
            HeroTitle(stringResource(R.string.td_noprogram_body))
            HeroButtons {
                TonalButton(stringResource(R.string.td_noprogram_food), onOpen, Modifier.weight(1f), onInk = true)
                PrimaryButton(stringResource(R.string.td_noprogram_make), onOpen, Modifier.weight(1f), onInk = true)
            }
        }
        is WorkoutCard.Next -> HeroCard(eyebrow = stringResource(R.string.td_workout_title)) {
            HeroWhen(stringResource(R.string.td_hero_when, card.day.name, card.day.exercises))
            HeroBig(stringResource(R.string.td_hero_minutes, card.day.minutes))
            HeroButtons {
                TonalButton(stringResource(R.string.td_workout_view), onOpen, Modifier.weight(1f), onInk = true)
                PrimaryButton(stringResource(R.string.td_workout_start), onOpen, Modifier.weight(1f), onInk = true, icon = painterResource(R.drawable.ic_play))
            }
        }
        is WorkoutCard.Resume -> HeroCard(eyebrow = stringResource(R.string.td_workout_resume_title)) {
            HeroBig(card.day?.name ?: stringResource(R.string.td_workout_resume))
            HeroButtons { PrimaryButton(stringResource(R.string.td_workout_resume), onOpen, Modifier.weight(1f), onInk = true, icon = painterResource(R.drawable.ic_play)) }
        }
        is WorkoutCard.Rest -> HeroCard(eyebrow = stringResource(R.string.td_rest_title)) {
            HeroWhen(stringResource(R.string.td_rest_next, card.next.name))
            HeroBig(stringResource(R.string.td_rest_body))
            HeroButtons {
                TonalButton(stringResource(R.string.td_rest_program), onOpen, Modifier.weight(1f), onInk = true)
                PrimaryButton(stringResource(R.string.td_rest_log_walk), onOpen, Modifier.weight(1f), onInk = true)
            }
        }
    }
}

/** H-2: people who do not lift get the weekly target instead of the workout card. */
@Composable
private fun ActivityHero(a: ActivityWeek, onOpen: () -> Unit) {
    HeroCard(eyebrow = stringResource(R.string.td_activity_title)) {
        HeroTitle(stringResource(R.string.td_activity_minutes, a.minutes, a.targetMinutes))
        MeterBar(a.fraction, Modifier.padding(top = 8.dp, end = 72.dp), onInk = true)
        if (a.minutes == 0) HeroMeta(stringResource(R.string.td_activity_empty))
        HeroButtons {
            TonalButton(stringResource(R.string.td_activity_history), onOpen, Modifier.weight(1f), onInk = true)
            PrimaryButton(stringResource(R.string.td_activity_log), onOpen, Modifier.weight(1f), onInk = true)
        }
    }
}

/** "Tugas hari ini" (`.todo`): header with count and bar, then one check row per task; Latihan and Makan rows have a shortcut. */
@Composable
private fun TasksSection(tasks: List<TodayTask>, food: DaySummary?, onOpenWorkout: () -> Unit, onOpenFood: () -> Unit, modifier: Modifier) {
    val done = tasks.count { it.done }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.td_tasks_title), trailing = stringResource(R.string.td_tasks_count, done, tasks.size))
        MeterBar(if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size)
        XnkCard(padding = 16.dp) {
            if (tasks.isEmpty()) Text(stringResource(R.string.td_tasks_empty), style = XnkType.sm, color = Xnk.colors.fgMuted)
            tasks.forEachIndexed { i, t ->
                val sub = if (t.kind == TaskKind.FOOD) foodLine(food) else null
                CheckRow(
                    title = stringResource(t.kind.label()),
                    done = t.done,
                    stateText = stringResource(if (t.done) R.string.td_task_done else R.string.td_task_open),
                    sub = sub,
                    divider = i < tasks.lastIndex,
                    action = when {
                        t.done -> null
                        t.kind == TaskKind.WORKOUT -> { { RowAction(stringResource(R.string.td_workout_start), onOpenWorkout, strong = true) } }
                        t.kind == TaskKind.FOOD -> { { RowAction(stringResource(R.string.td_activity_log), onOpenFood) } }
                        else -> null
                    },
                )
            }
        }
    }
}

/** Sub-line of the food task: the same text the food card shows (no number in ticks mode or without a target). */
@Composable
private fun foodLine(food: DaySummary?): String? = when (food) {
    is DaySummary.Numbers -> food.targets?.let { stringResource(R.string.td_food_of, food.eatenKcal, it.kcal) }
    is DaySummary.Ticks -> stringResource(R.string.td_food_ticks, food.mealsLogged, food.mealsTotal)
    null -> null
}

@Composable
private fun FoodSection(summary: DaySummary?, onOpen: () -> Unit, modifier: Modifier) {
    val c = Xnk.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.td_food_title), action = {
            TextButton(onOpen, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.td_food_open), style = XnkType.sm, color = c.fg) }
        })
        XnkCard {
            val ringSize = 112.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
            when (summary) {
                null -> Text(stringResource(R.string.td_food_empty), style = XnkType.md, color = c.fgMuted)
                is DaySummary.Ticks -> Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val f = if (summary.mealsTotal == 0) 0f else summary.mealsLogged.toFloat() / summary.mealsTotal
                    ProgressRing(f, size = ringSize) { Text(stringResource(R.string.td_ring_ticks, summary.mealsLogged, summary.mealsTotal), style = XnkType.kpi, color = c.fg) }
                    Text(stringResource(R.string.td_food_ticks, summary.mealsLogged, summary.mealsTotal), style = XnkType.md, modifier = Modifier.weight(1f))
                }
                is DaySummary.Numbers -> {
                    val t = summary.targets
                    if (t == null) {
                        Text(stringResource(R.string.td_food_eaten_only, summary.eatenKcal), style = XnkType.heroTitle, color = c.fg)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            val fraction = (summary.eatenKcal.toFloat() / t.kcal).coerceIn(0f, 1f)
                            ProgressRing(fraction, size = ringSize) {
                                val left = summary.remainingKcal
                                val over = summary.overKcal
                                Text((left ?: over ?: summary.eatenKcal).toString(), style = XnkType.kpi, color = c.fg)
                                Text(stringResource(if (left != null) R.string.td_ring_left else if (over != null) R.string.td_ring_over else R.string.td_ring_eaten), style = XnkType.xs, color = c.fgMuted)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Macro(R.string.td_macro_protein, summary.protein, t.proteinG)
                                Macro(R.string.td_macro_carb, summary.carb, t.carbG)
                                Macro(R.string.td_macro_fat, summary.fat, t.fatG)
                            }
                        }
                        Text(stringResource(R.string.td_food_of, summary.eatenKcal, t.kcal), style = XnkType.xs, color = c.fgMuted)
                    }
                    if (summary.eatenKcal == 0) Text(stringResource(R.string.td_food_empty), style = XnkType.md, color = c.fgMuted)
                }
            }
        }
    }
}

@Composable
private fun Macro(@StringRes name: Int, eaten: Int, target: Int) {
    Text(stringResource(R.string.td_food_macro, stringResource(name), eaten, target), style = XnkType.sm.copy(fontWeight = FontWeight.Bold), color = Xnk.colors.fg)
    MeterBar(if (target <= 0) 0f else eaten.toFloat() / target)
}

@Composable
private fun TargetCardView(card: TargetCard, modifier: Modifier) {
    var source by remember { mutableStateOf<TargetNumber?>(null) }
    val c = Xnk.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.td_target_title))
        XnkCard {
            when (card) {
                TargetCard.NeedsScreening -> Text(stringResource(R.string.td_target_needs_screening), style = XnkType.md)
                TargetCard.Unavailable -> Text(stringResource(R.string.td_target_unavailable), style = XnkType.md)
                is TargetCard.NoNumbers -> {
                    Text(stringResource(R.string.td_target_no_numbers_title), style = XnkType.heroTitle, color = c.fg)
                    Text(stringResource(R.string.td_target_no_numbers_body), style = XnkType.md)
                    card.notes.forEach { Text(stringResource(it.text()), style = XnkType.md) }
                }
                is TargetCard.Numbers -> {
                    Text(stringResource(card.goal.label()), style = XnkType.md, color = c.fgMuted)
                    NumberRow(TargetNumber.KCAL, stringResource(R.string.td_target_kcal_value, card.kcal)) { source = it }
                    NumberRow(TargetNumber.PROTEIN, stringResource(R.string.td_target_g_value, card.proteinG)) { source = it }
                    NumberRow(TargetNumber.CARB, stringResource(R.string.td_target_g_value, card.carbG)) { source = it }
                    NumberRow(TargetNumber.FAT, stringResource(R.string.td_target_g_value, card.fatG)) { source = it }
                    NumberRow(TargetNumber.FIBRE, stringResource(R.string.td_target_g_value, card.fibreG)) { source = it }
                    if (card.raisedByPace) Text(stringResource(R.string.td_target_raised), style = XnkType.xs, color = c.fgMuted)
                    Text(stringResource(R.string.td_target_estimate), style = XnkType.xs, color = c.fgMuted)
                    card.notes.forEach { Text(stringResource(it.text()), style = XnkType.md) }
                }
            }
            // R_DISCLAIMER: always on the target card, whatever the state.
            Text(stringResource(R.string.td_disclaimer), style = XnkType.xs, color = c.fgMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
    val n = source
    if (n != null && card is TargetCard.Numbers) {
        val sources = sourcesFor(n, card.goal)
        AlertDialog(
            onDismissRequest = { source = null },
            title = { Text(stringResource(R.string.td_target_source_title, stringResource(n.label()))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sources.isEmpty()) Text(stringResource(R.string.src_none))
                    sources.forEach { Text(stringResource(it.text()), style = XnkType.md) }
                }
            },
            confirmButton = { TextButton({ source = null }, Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.td_target_source_close)) } },
        )
    }
}

@Composable
private fun NumberRow(number: TargetNumber, value: String, onSource: (TargetNumber) -> Unit) {
    val label = stringResource(number.label())
    val sourceLabel = stringResource(R.string.td_target_source_button, label)
    val c = Xnk.colors
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = XnkType.lg, color = c.fg2, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = XnkType.lg.copy(fontWeight = FontWeight.ExtraBold), color = c.fg)
            TextButton(
                { onSource(number) },
                Modifier.heightIn(min = MinTouch).semantics { contentDescription = sourceLabel },
            ) { Text(stringResource(R.string.td_target_source_i), color = c.fgMuted) }
        }
    }
}

/** "Minggu ini": seven dots, Monday first; [todayIso] (1 = Monday) gets a ring. */
@Composable
private fun WeekCard(week: List<Boolean>, todayIso: Int?, modifier: Modifier) {
    val names = listOf(R.string.td_day_1, R.string.td_day_2, R.string.td_day_3, R.string.td_day_4, R.string.td_day_5, R.string.td_day_6, R.string.td_day_7)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.td_week_title), trailing = stringResource(R.string.td_week_active, week.count { it }))
        XnkCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEachIndexed { i, on ->
                    val name = stringResource(names[i])
                    val desc = stringResource(if (on) R.string.td_day_active else R.string.td_day_idle, name)
                    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayDot(active = on, today = todayIso == i + 1)
                        Text(name, style = XnkType.xs2.copy(fontWeight = FontWeight.Bold), color = Xnk.colors.fgMuted)
                    }
                }
            }
        }
    }
}
