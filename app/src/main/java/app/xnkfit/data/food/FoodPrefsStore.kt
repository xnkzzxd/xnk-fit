package app.xnkfit.data.food

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.BuyFrequency
import kotlinx.coroutines.flow.first

/** The three food preferences on the Makan screen (PRD M-5, M-9, mockup makan.html). Halal is always on (R_HALAL). */
data class FoodPrefs(
    val avoid: Set<Allergen> = emptySet(),
    val budget: Budget = Budget.HEMAT,
    val buys: BuyFrequency = BuyFrequency.SOMETIMES,
)

/** DataStore, because it is one small record the user edits and Room's schema stays untouched. Nothing leaves the phone (PV-1). */
class FoodPrefsStore(private val store: DataStore<Preferences>) {
    private val avoidKey = stringSetPreferencesKey("food_avoid")
    private val budgetKey = stringPreferencesKey("food_budget")
    private val buysKey = stringPreferencesKey("food_buys")

    suspend fun load(): FoodPrefs {
        val p = store.data.first()
        return FoodPrefs(
            avoid = p[avoidKey].orEmpty().mapNotNull { n -> Allergen.entries.firstOrNull { it.name == n } }.toSet(),
            budget = Budget.entries.firstOrNull { it.name == p[budgetKey] } ?: FoodPrefs().budget,
            buys = BuyFrequency.entries.firstOrNull { it.name == p[buysKey] } ?: FoodPrefs().buys,
        )
    }

    suspend fun save(prefs: FoodPrefs) {
        store.edit {
            it[avoidKey] = prefs.avoid.map { a -> a.name }.toSet()
            it[budgetKey] = prefs.budget.name
            it[buysKey] = prefs.buys.name
        }
    }
}
