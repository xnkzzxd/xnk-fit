package app.xnkfit.domain.rules

// Activity calories and WHO minutes (Rules.md §4, PRD L-14, H-2).

// R_MET: MET values, 2011 Compendium (S12). Moderate effort, then vigorous effort where listed.
const val R_MET_JALAN = 3.5
const val R_MET_LARI = 8.3
const val R_MET_LARI_VIGOROUS = 11.0
const val R_MET_SEPEDA = 6.8
const val R_MET_SEPEDA_VIGOROUS = 8.0
const val R_MET_RENANG = 5.8
const val R_MET_RENANG_VIGOROUS = 9.8
const val R_MET_ANGKAT = 3.5
const val R_MET_ANGKAT_VIGOROUS = 6.0
const val R_MET_SENAM = 6.5
const val R_MET_SENAM_VIGOROUS = 7.3
const val R_MET_BULUTANGKIS = 5.5
const val R_MET_BULUTANGKIS_VIGOROUS = 7.3
const val R_MET_SEPAK = 7.0
const val R_MET_SEPAK_VIGOROUS = 10.0

// R_WHO_MIN: weekly activity minutes (S13). Vigorous minutes count double.
const val R_WHO_MIN_TARGET = 150
const val R_WHO_MIN_UPPER = 300
const val R_WHO_MIN_VIGOROUS_FACTOR = 2

private const val MINUTES_PER_HOUR = 60.0 // unit conversion, not a rule

/** Activity codes from Rules.md §4. [vigorousMet] is null where the table has "—" (moderate value is used). */
enum class ActivityType(val code: String, val moderateMet: Double, val vigorousMet: Double?) {
    JALAN("jalan", R_MET_JALAN, null),
    LARI("lari", R_MET_LARI, R_MET_LARI_VIGOROUS),
    SEPEDA("sepeda", R_MET_SEPEDA, R_MET_SEPEDA_VIGOROUS),
    RENANG("renang", R_MET_RENANG, R_MET_RENANG_VIGOROUS),
    ANGKAT("angkat", R_MET_ANGKAT, R_MET_ANGKAT_VIGOROUS),
    SENAM("senam", R_MET_SENAM, R_MET_SENAM_VIGOROUS),
    BULUTANGKIS("bulutangkis", R_MET_BULUTANGKIS, R_MET_BULUTANGKIS_VIGOROUS),
    SEPAK("sepak", R_MET_SEPAK, R_MET_SEPAK_VIGOROUS),
    ;

    fun met(vigorous: Boolean): Double = if (vigorous) vigorousMet ?: moderateMet else moderateMet
}

/**
 * R_MET: activity kcal = MET × body kg × hours. Shown only; never added to the target (M-1, R_TDEE).
 * Returns 0 for unusable input.
 */
fun activityKcal(type: ActivityType, vigorous: Boolean, weightKg: Double, minutes: Int): Double {
    if (!weightKg.isFinite() || weightKg <= 0.0 || minutes <= 0) return 0.0
    return type.met(vigorous) * weightKg * (minutes / MINUTES_PER_HOUR)
}

/** R_WHO_MIN: moderate-equivalent minutes; vigorous counts double. Steps never count (L-15). */
fun moderateEquivalentMinutes(moderateMinutes: Int, vigorousMinutes: Int): Int =
    moderateMinutes.coerceAtLeast(0) + R_WHO_MIN_VIGOROUS_FACTOR * vigorousMinutes.coerceAtLeast(0)
