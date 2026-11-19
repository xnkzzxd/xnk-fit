package app.xnkfit.ui.onboarding

import androidx.annotation.StringRes
import app.xnkfit.R
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.rules.InputProblem
import app.xnkfit.domain.rules.Screening
import app.xnkfit.domain.rules.Sex
import app.xnkfit.ui.screening.EmptyScreening
import app.xnkfit.ui.screening.ScreeningOutcome

/** Steps of the short flow (PRD O-1, mockup `onboarding.html`). */
enum class OnboardingStep { WELCOME, GOAL, BODY, INTEREST, NAME, HEALTH, SCREENING, RESULT, PERMISSIONS, UNDER_13 }

/** The four goal cards. [domain] is what the safety gates see; [code] is stored in `profile.goal`. */
enum class GoalChoice(val code: String, val domain: Goal, @StringRes val title: Int, @StringRes val hint: Int) {
    TURUN("turun", Goal.TURUN, R.string.ob_goal_turun, R.string.ob_goal_turun_hint), // text-ok: stored code, not UI text
    JAGA("jaga", Goal.JAGA, R.string.ob_goal_jaga, R.string.ob_goal_jaga_hint), // text-ok: stored code, not UI text
    BUGAR("bugar", Goal.JAGA, R.string.ob_goal_bugar, R.string.ob_goal_bugar_hint), // text-ok: stored code, not UI text
    OTOT("otot", Goal.NAIK, R.string.ob_goal_otot, R.string.ob_goal_otot_hint), // text-ok: stored code, not UI text
}

enum class Interest(val code: String, @StringRes val title: Int) {
    LATIHAN("latihan", R.string.ob_interest_workout), // text-ok: stored code, not UI text
    MAKAN("makan", R.string.ob_interest_food), // text-ok: stored code, not UI text
    KEDUANYA("keduanya", R.string.ob_interest_both), // text-ok: stored code, not UI text
}

/** The four permission rows (PRD O-3). Only the ones with [asksHere] can be asked in this task. */
enum class PermissionItem(
    @StringRes val title: Int,
    @StringRes val why: Int,
    val asksHere: Boolean,
) {
    NOTIFICATIONS(R.string.ob_perm_notifications, R.string.ob_perm_notifications_why, true),
    CAMERA(R.string.ob_perm_camera, R.string.ob_perm_camera_why, true),
    HEALTH_CONNECT(R.string.ob_perm_health, R.string.ob_perm_health_why, false),
    DRIVE(R.string.ob_perm_drive, R.string.ob_perm_drive_why, false),
}

/** Everything typed so far. Held in memory only; nothing is written before the last step (O-5, PV-1). */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val goal: GoalChoice? = null,
    val sex: Sex? = null,
    val birthYearText: String = "",
    val heightText: String = "",
    val weightText: String = "",
    val interest: Interest? = null,
    val nickname: String = "",
    /** True after "Ya / tidak yakin": the full form was opened (PRD O-2). */
    val screeningOpened: Boolean = false,
    val screening: Screening = EmptyScreening,
    /** Set when "Simpan" was pressed with unanswered questions. */
    val screeningIncomplete: Boolean = false,
    val outcome: ScreeningOutcome? = null,
    /** Fields the domain refused (R_INPUT); shown only after "Lanjut" was pressed on that step. */
    val problems: Set<InputProblem> = emptySet(),
    /** Permission rows the user already answered ("Izinkan" or "Nanti"). */
    val permissionsHandled: Set<PermissionItem> = emptySet(),
    val saving: Boolean = false,
    val finished: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val screeningDone: Boolean get() = screeningOpened && outcome != null
}
