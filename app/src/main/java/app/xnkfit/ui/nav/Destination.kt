package app.xnkfit.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.xnkfit.R

/**
 * Bottom bar, left to right (PRD N-1, Design §5). Pure data so a JVM test can check the order.
 * [ADD] is an action (it opens the sheet from PRD N-2 in a later task), not a screen.
 */
enum class Destination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val isScreen: Boolean,
) {
    TODAY(R.string.nav_today, R.drawable.ic_nav_today, isScreen = true),
    WORKOUT(R.string.nav_workout, R.drawable.ic_nav_workout, isScreen = true),
    ADD(R.string.nav_add, R.drawable.ic_nav_add, isScreen = false),
    FOOD(R.string.nav_food, R.drawable.ic_nav_food, isScreen = true),
    ME(R.string.nav_me, R.drawable.ic_nav_me, isScreen = true),
}
