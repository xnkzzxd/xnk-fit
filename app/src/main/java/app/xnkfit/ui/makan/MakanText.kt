package app.xnkfit.ui.makan

import androidx.annotation.StringRes
import app.xnkfit.R
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.BuyFrequency
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PackageMode
import app.xnkfit.domain.food.ShopGroup

/** Every label on the Makan screens comes from strings.xml (PRD NF-3); these tables only pick the resource. */

@StringRes fun MealSlot.label(): Int = when (this) {
    MealSlot.PAGI -> R.string.mk_slot_pagi
    MealSlot.SIANG -> R.string.mk_slot_siang
    MealSlot.MALAM -> R.string.mk_slot_malam
    MealSlot.CAMILAN -> R.string.mk_slot_camilan
}

@StringRes fun Allergen.label(): Int = when (this) {
    Allergen.EGG -> R.string.mk_allergy_egg
    Allergen.SEAFOOD -> R.string.mk_allergy_seafood
    Allergen.NUTS -> R.string.mk_allergy_nuts
    Allergen.MILK -> R.string.mk_allergy_milk
    Allergen.LACTOSE -> R.string.mk_allergy_lactose
}

@StringRes fun Budget.label(): Int = when (this) {
    Budget.HEMAT -> R.string.mk_budget_hemat
    Budget.STANDAR -> R.string.mk_budget_standar
    Budget.BEBAS -> R.string.mk_budget_bebas
}

@StringRes fun BuyFrequency.label(): Int = when (this) {
    BuyFrequency.OFTEN -> R.string.mk_buys_often
    BuyFrequency.SOMETIMES -> R.string.mk_buys_sometimes
    BuyFrequency.RARELY -> R.string.mk_buys_rarely
}

@StringRes fun PackageMode.label(): Int = when (this) {
    PackageMode.MASAK -> R.string.mk_mode_masak
    PackageMode.BELI -> R.string.mk_mode_beli
    PackageMode.TANPA_MASAK -> R.string.mk_mode_tanpa_masak
}

@StringRes fun ShopGroup.label(): Int = when (this) {
    ShopGroup.LAUK -> R.string.sh_group_lauk
    ShopGroup.SAYUR_BUAH -> R.string.sh_group_sayur_buah
    ShopGroup.POKOK -> R.string.sh_group_pokok
    ShopGroup.LAINNYA -> R.string.sh_group_lainnya
}

private const val SOURCE_PACKAGED = "packaged" // text-ok: stored source code, not UI text
private const val SOURCE_WARUNG = "warung-est" // text-ok: stored source code, not UI text
private const val SOURCE_OWN = "own" // text-ok: stored source code, not UI text

@StringRes fun sourceLabel(source: String): Int = when (source) {
    SOURCE_PACKAGED -> R.string.pk_source_packaged
    SOURCE_WARUNG -> R.string.pk_source_warung
    SOURCE_OWN -> R.string.pk_source_own
    else -> R.string.pk_source_tkpi
}

private val SHORT_DAYS = intArrayOf(
    R.string.pl_day_1, R.string.pl_day_2, R.string.pl_day_3, R.string.pl_day_4, R.string.pl_day_5, R.string.pl_day_6, R.string.pl_day_7,
)
private val LONG_DAYS = intArrayOf(
    R.string.pl_day_long_1, R.string.pl_day_long_2, R.string.pl_day_long_3, R.string.pl_day_long_4,
    R.string.pl_day_long_5, R.string.pl_day_long_6, R.string.pl_day_long_7,
)

/** [day] 1 (Monday) to 7. */
@StringRes fun shortDay(day: Int): Int = SHORT_DAYS[(day - 1).coerceIn(0, SHORT_DAYS.lastIndex)]

@StringRes fun longDay(day: Int): Int = LONG_DAYS[(day - 1).coerceIn(0, LONG_DAYS.lastIndex)]
