package app.xnkfit.ui.bantuan

import androidx.annotation.StringRes
import app.xnkfit.R
import app.xnkfit.data.prefs.WelcomeTab

/** A glossary entry: term and plain definition (PRD P-8). */
data class Term(@StringRes val term: Int, @StringRes val definition: Int)

/** A number source: a short line and the full reference shown behind the (i). */
data class Source(@StringRes val short: Int, @StringRes val full: Int)

data class WelcomeCardText(@StringRes val title: Int, @StringRes val body: Int)

/** Static Bantuan content as resource ids, so a JVM test can check that none is missing. */
object HelpContent {
    val workoutTerms = listOf(
        Term(R.string.help_term_set, R.string.help_term_set_def),
        Term(R.string.help_term_rir, R.string.help_term_rir_def),
        Term(R.string.help_term_superset, R.string.help_term_superset_def),
        Term(R.string.help_term_drop, R.string.help_term_drop_def),
        Term(R.string.help_term_failure, R.string.help_term_failure_def),
        Term(R.string.help_term_form, R.string.help_term_form_def),
        Term(R.string.help_term_pr, R.string.help_term_pr_def),
        Term(R.string.help_term_1rm, R.string.help_term_1rm_def),
    )

    val foodTerms = listOf(
        Term(R.string.help_term_macro, R.string.help_term_macro_def),
        Term(R.string.help_term_bmr, R.string.help_term_bmr_def),
        Term(R.string.help_term_met, R.string.help_term_met_def),
        Term(R.string.help_term_urt, R.string.help_term_urt_def),
    )

    val sources = listOf(
        Source(R.string.help_src_bmr, R.string.help_src_bmr_full),
        Source(R.string.help_src_act, R.string.help_src_act_full),
        Source(R.string.help_src_met, R.string.help_src_met_full),
        Source(R.string.help_src_who, R.string.help_src_who_full),
        Source(R.string.help_src_protein, R.string.help_src_protein_full),
        Source(R.string.help_src_akg, R.string.help_src_akg_full),
        Source(R.string.help_src_deficit, R.string.help_src_deficit_full),
        Source(R.string.help_src_train, R.string.help_src_train_full),
        Source(R.string.help_src_rir, R.string.help_src_rir_full),
        Source(R.string.help_src_food, R.string.help_src_food_full),
    )

    val guide = listOf(R.string.help_guide_1, R.string.help_guide_2, R.string.help_guide_3, R.string.help_guide_4)

    val calcSteps = listOf(R.string.help_calc_1, R.string.help_calc_2, R.string.help_calc_3)

    /** One card per tab (P-8). */
    val welcome: Map<WelcomeTab, WelcomeCardText> = mapOf(
        WelcomeTab.TODAY to WelcomeCardText(R.string.wc_today_title, R.string.wc_today_body),
        WelcomeTab.WORKOUT to WelcomeCardText(R.string.wc_workout_title, R.string.wc_workout_body),
        WelcomeTab.FOOD to WelcomeCardText(R.string.wc_food_title, R.string.wc_food_body),
        WelcomeTab.ME to WelcomeCardText(R.string.wc_me_title, R.string.wc_me_body),
    )
}

/** Where people send feedback (PRD P-8: a form link). Empty until the owner provides the address; never guessed. */
object FeedbackConfig {
    const val FORM_URL: String = "" // text-ok: owner-supplied URL, see docs/Decisions.md

    val available: Boolean get() = FORM_URL.startsWith("https://") // text-ok: scheme check
}
