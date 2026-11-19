package app.xnkfit.data.food

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * What the current target was calculated with, and what the user last answered to the recalculation prompt (M-2).
 * [weightKg] and [goal] feed the calculation; [askedKg] and [askedGoal] are the references the prompt compares with
 * (they move on "Hitung ulang" and on "Pertahankan"). [raiseKcal] is the sum of accepted R_PACE raises and
 * [paceHandledWeek] the last week index whose pace suggestion was answered.
 */
data class TargetBasis(
    val weightKg: Double? = null,
    val goal: String? = null,
    val askedKg: Double? = null,
    val askedGoal: String? = null,
    val raiseKcal: Int = 0,
    val paceHandledWeek: Int? = null,
)

/** DataStore on the phone only (PV-1); no schema change. Not in the backup zip until B-16 adds it. */
class TargetBasisStore(private val store: DataStore<Preferences>) {
    private val weightKey = doublePreferencesKey("basis_weight")
    private val goalKey = stringPreferencesKey("basis_goal")
    private val askedKgKey = doublePreferencesKey("basis_asked_kg")
    private val askedGoalKey = stringPreferencesKey("basis_asked_goal")
    private val raiseKey = intPreferencesKey("basis_raise")
    private val paceKey = intPreferencesKey("basis_pace_week")

    suspend fun load(): TargetBasis {
        val p = store.data.first()
        return TargetBasis(p[weightKey], p[goalKey], p[askedKgKey], p[askedGoalKey], p[raiseKey] ?: 0, p[paceKey])
    }

    suspend fun save(b: TargetBasis) {
        store.edit {
            fun <T> set(key: Preferences.Key<T>, v: T?) { if (v == null) it.remove(key) else it[key] = v }
            set(weightKey, b.weightKg); set(goalKey, b.goal); set(askedKgKey, b.askedKg); set(askedGoalKey, b.askedGoal)
            it[raiseKey] = b.raiseKcal; set(paceKey, b.paceHandledWeek)
        }
    }
}
