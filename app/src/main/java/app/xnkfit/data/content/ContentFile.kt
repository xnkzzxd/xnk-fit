package app.xnkfit.data.content

/**
 * The content files the app knows (Content.md, Content-Update.md). Only these names are accepted from a
 * manifest: anything else, or any path with a slash, rejects the whole update.
 * [idPrefix] marks which food rows came from a file, so a replace never touches user foods (`own`).
 */
enum class ContentFile(val fileName: String, val idPrefix: String? = null, val mayBeEmpty: Boolean = false) {
    EXERCISES("exercises.json"),
    FOODS_TKPI("foods-tkpi.json", "TKPI_"),
    FOODS_ESTIMATES("foods-estimates.json", "W_"),
    FOODS_REGIONAL("foods-regional.json", "REG-"),
    // Rows with no per-100 g basis are skipped (no density is invented), so this file may load zero rows.
    FOODS_PACKAGED("foods-packaged.json", "PK_", mayBeEmpty = true),
    BADGES("badges.json"),
    MEAL_PACKAGES("meal-packages.json"),
    // Household measures by food id (C-05); joined into `food.household` after the foods are written (Content.md §4.6).
    HOUSEHOLD("household.json");

    companion object {
        /** Release marker, read separately: it is not a manifest file (the manifest carries the version itself). */
        const val VERSION_FILE = "content-version.json"

        fun byName(name: String): ContentFile? = entries.firstOrNull { it.fileName == name }
    }
}

class ContentException(message: String, cause: Throwable? = null) : Exception(message, cause)
