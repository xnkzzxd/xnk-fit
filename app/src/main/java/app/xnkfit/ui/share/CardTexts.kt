package app.xnkfit.ui.share

import android.content.res.Resources
import app.xnkfit.R
import app.xnkfit.domain.share.CardField
import app.xnkfit.domain.share.CardKind
import app.xnkfit.domain.share.CardSpec
import app.xnkfit.domain.share.FieldId
import app.xnkfit.domain.share.Headline
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Every string a card shows, already from resources (text-literal rule). The renderer only draws these. */
data class CardTexts(
    val label: String,
    val date: String?,
    val headline: String,
    /** (value, label) pairs in drawing order. */
    val fields: List<Pair<String, String>>,
    val lift: String?,
    val noKgNote: String?,
    val brand: String?,
)

private val ID = Locale.forLanguageTag("id-ID") // text-ok: locale tag
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", ID) // text-ok: date pattern

private const val MINUS = "−" // text-ok: typographic minus
private const val PLUS = "+" // text-ok: sign character

/** A number for a card: whole numbers without decimals, others with one; [signed] adds + or the minus sign. */
internal fun cardNumber(v: Double, signed: Boolean): String {
    val body = if (v % 1.0 == 0.0) "%d".format(ID, abs(v).toLong()) else "%.1f".format(ID, abs(v))
    return when {
        v < 0 -> MINUS + body
        signed && v > 0 -> PLUS + body
        else -> body
    }
}

fun cardTexts(res: Resources, spec: CardSpec): CardTexts {
    val value = spec.headlineValue
    val headline = when (spec.headline) {
        Headline.WORKOUT_TITLE -> spec.headlineText.orEmpty()
        Headline.WORKOUT_UNTITLED -> res.getString(R.string.share_h_untitled)
        Headline.WAIST_DELTA -> res.getString(R.string.share_h_waist, cardNumber(value ?: 0.0, signed = true))
        Headline.TRACKED_DAYS -> res.getString(R.string.share_h_days, cardNumber(value ?: 0.0, signed = false))
        Headline.STREAK_WEEKS -> res.getString(R.string.share_h_streak, cardNumber(value ?: 0.0, signed = false))
        Headline.LEVEL -> res.getString(R.string.share_h_level, cardNumber(value ?: 0.0, signed = false))
        Headline.FOOD_DAYS -> res.getString(R.string.share_h_food, cardNumber(value ?: 0.0, signed = false))
    }
    val kind = when (spec.kind) {
        CardKind.WORKOUT -> R.string.share_kind_workout
        CardKind.PROGRESS -> R.string.share_kind_progress
        CardKind.CONSISTENCY -> R.string.share_kind_consistency
        CardKind.FOOD -> R.string.share_kind_food
    }
    val lift = spec.lift?.let { l ->
        val reps = l.reps
        when {
            l.weightKg != null && reps != null -> res.getString(R.string.share_lift_kg, l.name, cardNumber(l.weightKg, false), reps)
            reps != null -> res.getString(R.string.share_lift, l.name, reps)
            else -> l.name
        }
    }
    return CardTexts(
        label = res.getString(kind),
        date = spec.day?.format(DAY_MONTH),
        headline = headline,
        fields = spec.fields.map { cardNumber(it.value, it.id == FieldId.WEIGHT_DELTA) to res.getString(fieldLabel(it)) },
        lift = lift,
        noKgNote = if (spec.noKg && spec.kind == CardKind.PROGRESS) res.getString(R.string.share_nokg) else null,
        brand = if (spec.brand) res.getString(R.string.app_name) else null,
    )
}

private fun fieldLabel(f: CardField): Int = when (f.id) {
    FieldId.MINUTES -> R.string.share_f_minutes
    FieldId.RECORDS -> R.string.share_f_records
    FieldId.WORKOUTS -> R.string.share_f_workouts
    FieldId.LEVEL -> R.string.share_f_level
    FieldId.DAYS -> R.string.share_f_days
    FieldId.WEIGHT_DELTA -> R.string.share_f_weight
    FieldId.FOOD_WINDOW -> R.string.share_f_window
}
