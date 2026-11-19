package app.xnkfit.domain.share

import java.time.LocalDate

// Share cards (PRD P-6, Rules §9 R_SHARE_*). Pure: this file decides which fields a card shows, in which order, and what
// privacy switches remove. It holds no text; the UI maps ids to strings and the renderer draws them.

// R_SHARE_NOKG, R_SHARE_BRAND, R_SHARE_FACE: the defaults of the three switches (PRD P-6).
const val R_SHARE_NOKG_DEFAULT = true
const val R_SHARE_BRAND_DEFAULT = true
const val R_SHARE_FACE_DEFAULT = true

// R_SHARE_PX: pixel sizes.
const val R_SHARE_PX_WIDTH = 1080
const val R_SHARE_PX_STORY_HEIGHT = 1920
const val R_SHARE_PX_SQUARE_HEIGHT = 1080

// R_SHARE_DATA: the food card counts days with food logged in this many days.
const val R_SHARE_DATA_FOOD_WINDOW_DAYS = 7

enum class CardKind { WORKOUT, PROGRESS, CONSISTENCY, FOOD }

enum class CardStyle { DARK, LIGHT, ACCENT }

enum class CardSize(val widthPx: Int, val heightPx: Int) {
    STORY(R_SHARE_PX_WIDTH, R_SHARE_PX_STORY_HEIGHT),
    SQUARE(R_SHARE_PX_WIDTH, R_SHARE_PX_SQUARE_HEIGHT),
}

/** GRID: title and a grid of numbers (the mockup). BIG: one large number with a short line under it. */
enum class CardTemplate { GRID, BIG }

data class ShareOptions(
    val kind: CardKind = CardKind.WORKOUT,
    val style: CardStyle = CardStyle.DARK,
    val size: CardSize = CardSize.STORY,
    val template: CardTemplate = CardTemplate.GRID,
    /** App name and QR (R_SHARE_BRAND). */
    val brand: Boolean = R_SHARE_BRAND_DEFAULT,
    /** No kg anywhere on the card (R_SHARE_NOKG). */
    val hideKg: Boolean = R_SHARE_NOKG_DEFAULT,
    /** Mosaic over faces on a photo (R_SHARE_FACE). */
    val blurFaces: Boolean = R_SHARE_FACE_DEFAULT,
    /** Progress card only: put the latest photo on the card. Off by default; photos stay private (PV-3). */
    val withPhoto: Boolean = false,
)

/** The heaviest (or highest-rep) set of the workout. [weightKg] is null for bodyweight. */
data class LiftFact(val name: String, val weightKg: Double?, val reps: Int?)

data class WorkoutFacts(
    val day: LocalDate,
    val title: String?,
    val minutes: Int?,
    val records: Int,
    val topLift: LiftFact?,
)

/** [waistDeltaCm] and [weightDeltaKg] are last minus first; [trackedDays] spans first to last measure. */
data class ProgressFacts(val waistDeltaCm: Double?, val weightDeltaKg: Double?, val trackedDays: Int?)

data class ConsistencyFacts(val streakWeeks: Int, val level: Int, val workouts: Int)

/** Days with at least one food entry in the last [R_SHARE_DATA_FOOD_WINDOW_DAYS] days. */
data class FoodFacts(val daysLogged: Int)

data class CardFacts(
    val workout: WorkoutFacts? = null,
    val progress: ProgressFacts? = null,
    val consistency: ConsistencyFacts? = null,
    val food: FoodFacts? = null,
)

/** The big line of a card. The UI turns it into text from string resources. */
enum class Headline { WORKOUT_TITLE, WORKOUT_UNTITLED, WAIST_DELTA, TRACKED_DAYS, STREAK_WEEKS, LEVEL, FOOD_DAYS }

/** A labelled number on the card, in the order drawn. */
enum class FieldId { MINUTES, RECORDS, WORKOUTS, LEVEL, DAYS, WEIGHT_DELTA, FOOD_WINDOW }

data class CardField(val id: FieldId, val value: Double)

data class CardSpec(
    val kind: CardKind,
    val style: CardStyle,
    val size: CardSize,
    val template: CardTemplate,
    val headline: Headline,
    /** Set for [Headline.WORKOUT_TITLE]. */
    val headlineText: String?,
    /** Set for the numeric headlines. */
    val headlineValue: Double?,
    val fields: List<CardField>,
    val lift: LiftFact?,
    val day: LocalDate?,
    val brand: Boolean,
    /** True when the no-kg switch is on: the card says so ("Tanpa kg"). */
    val noKg: Boolean,
    /** A progress photo path, only when asked for and available. */
    val photoPath: String?,
    val blurFaces: Boolean,
)

/** R_SHARE_NOKG: the switch in the editor, or Saya's own "hide weight", keeps kg off the card. */
fun effectiveHideKg(optionHideKg: Boolean, sayaHidesWeight: Boolean): Boolean = optionHideKg || sayaHidesWeight

/** R_SHARE_DATA: kinds that have data on the phone, in gallery order. */
fun availableKinds(facts: CardFacts): List<CardKind> = CardKind.entries.filter { buildCard(facts, ShareOptions(kind = it), false, null) != null }

/**
 * Builds one card, or null when the phone has no data for [ShareOptions.kind] (R_SHARE_DATA).
 * [sayaHidesWeight] is Saya's own switch; [photoPath] is the latest private photo, used only on the progress card.
 */
fun buildCard(facts: CardFacts, options: ShareOptions, sayaHidesWeight: Boolean, photoPath: String?): CardSpec? {
    val hideKg = effectiveHideKg(options.hideKg, sayaHidesWeight)
    val parts = when (options.kind) {
        CardKind.WORKOUT -> workoutParts(facts.workout, hideKg)
        CardKind.PROGRESS -> progressParts(facts.progress, hideKg)
        CardKind.CONSISTENCY -> consistencyParts(facts.consistency)
        CardKind.FOOD -> foodParts(facts.food)
    } ?: return null
    return CardSpec(
        kind = options.kind,
        style = options.style,
        size = options.size,
        template = options.template,
        headline = parts.headline,
        headlineText = parts.text,
        headlineValue = parts.value,
        fields = parts.fields,
        lift = parts.lift,
        day = parts.day,
        brand = options.brand,
        noKg = hideKg,
        photoPath = if (options.kind == CardKind.PROGRESS && options.withPhoto) photoPath else null,
        blurFaces = options.blurFaces,
    )
}

private class Parts(
    val headline: Headline,
    val text: String? = null,
    val value: Double? = null,
    val fields: List<CardField> = emptyList(),
    val lift: LiftFact? = null,
    val day: LocalDate? = null,
)

private fun workoutParts(w: WorkoutFacts?, hideKg: Boolean): Parts? {
    w ?: return null
    val title = w.title?.takeIf { it.isNotBlank() }
    val fields = buildList {
        w.minutes?.takeIf { it > 0 }?.let { add(CardField(FieldId.MINUTES, it.toDouble())) }
        // R_SHARE_DATA: "0 rekor" is not printed.
        if (w.records > 0) add(CardField(FieldId.RECORDS, w.records.toDouble()))
    }
    val lift = w.topLift?.let { if (hideKg) it.copy(weightKg = null) else it }
    return Parts(
        headline = if (title != null) Headline.WORKOUT_TITLE else Headline.WORKOUT_UNTITLED,
        text = title,
        fields = fields,
        lift = lift,
        day = w.day,
    )
}

private fun progressParts(p: ProgressFacts?, hideKg: Boolean): Parts? {
    p ?: return null
    val headline: Headline
    val value: Double
    when {
        p.waistDeltaCm != null -> { headline = Headline.WAIST_DELTA; value = p.waistDeltaCm }
        p.trackedDays != null && p.trackedDays > 0 -> { headline = Headline.TRACKED_DAYS; value = p.trackedDays.toDouble() }
        else -> return null
    }
    val fields = buildList {
        if (headline == Headline.WAIST_DELTA && p.trackedDays != null && p.trackedDays > 0) {
            add(CardField(FieldId.DAYS, p.trackedDays.toDouble()))
        }
        // R_SHARE_NOKG: only ever the change, never the weight itself, and only when kg is allowed.
        if (!hideKg && p.weightDeltaKg != null) add(CardField(FieldId.WEIGHT_DELTA, p.weightDeltaKg))
    }
    return Parts(headline = headline, value = value, fields = fields)
}

private fun consistencyParts(c: ConsistencyFacts?): Parts? {
    c ?: return null
    val fields = buildList {
        if (c.streakWeeks > 0) add(CardField(FieldId.LEVEL, c.level.toDouble()))
        if (c.workouts > 0) add(CardField(FieldId.WORKOUTS, c.workouts.toDouble()))
    }
    return if (c.streakWeeks > 0) {
        Parts(Headline.STREAK_WEEKS, value = c.streakWeeks.toDouble(), fields = fields)
    } else {
        Parts(Headline.LEVEL, value = c.level.toDouble(), fields = fields)
    }
}

private fun foodParts(f: FoodFacts?): Parts? {
    f ?: return null
    if (f.daysLogged <= 0) return null
    return Parts(
        Headline.FOOD_DAYS,
        value = f.daysLogged.toDouble(),
        fields = listOf(CardField(FieldId.FOOD_WINDOW, R_SHARE_DATA_FOOD_WINDOW_DAYS.toDouble())),
    )
}

/** Mosaic boxes (R_SHARE_FACE). [faces] are detected face rectangles; each grows by [marginPercent] and is clipped to the picture. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

const val R_SHARE_FACE_MARGIN_PERCENT = 40

/** Face boxes enlarged so hair and chin are covered, clipped to the picture. */
fun mosaicBoxes(faces: List<Box>, width: Int, height: Int): List<Box> = faces.map { f ->
    val mx = (f.right - f.left) * R_SHARE_FACE_MARGIN_PERCENT / R_SHARE_FACE_PERCENT_BASE
    val my = (f.bottom - f.top) * R_SHARE_FACE_MARGIN_PERCENT / R_SHARE_FACE_PERCENT_BASE
    Box((f.left - mx).coerceAtLeast(0), (f.top - my).coerceAtLeast(0), (f.right + mx).coerceAtMost(width), (f.bottom + my).coerceAtMost(height))
}

private const val R_SHARE_FACE_PERCENT_BASE = 100
