package app.xnkfit.ui.latihan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.xnkfit.domain.latihan.TrainingSetup
import app.xnkfit.domain.training.EquipmentSetting
import app.xnkfit.domain.training.Experience
import app.xnkfit.domain.training.GoalFocus
import kotlinx.coroutines.flow.first

/** Small flags of the Latihan tab (Design §1: DataStore for flags). Answers of the first training profile live here. */
interface LatihanPrefs {
    suspend fun setup(): TrainingSetup?
    suspend fun saveSetup(setup: TrainingSetup)

    /** L-5: automatic programming switched off; the generator then never writes anything. */
    suspend fun autoOff(): Boolean
    suspend fun setAutoOff(off: Boolean)

    /** Clock time when the review was last answered or closed; the 4-week count (R_TRN_REVIEW) starts there. */
    suspend fun reviewAnsweredAt(): Long?
    suspend fun setReviewAnsweredAt(at: Long)
}

class DataStoreLatihanPrefs(private val store: DataStore<Preferences>) : LatihanPrefs {
    override suspend fun setup(): TrainingSetup? {
        val p = store.data.first()
        val setting = EquipmentSetting.entries.firstOrNull { it.code == p[SETTING] } ?: return null
        return TrainingSetup(
            setting = setting,
            days = p[DAYS] ?: return null,
            minutes = p[MINUTES] ?: return null,
            experience = Experience.entries.firstOrNull { it.name == p[EXPERIENCE] } ?: return null,
            focus = GoalFocus.entries.firstOrNull { it.name == p[FOCUS] } ?: GoalFocus.OTOT,
            customItems = p[ITEMS].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
        )
    }

    override suspend fun saveSetup(setup: TrainingSetup) {
        store.edit {
            it[SETTING] = setup.setting.code
            it[DAYS] = setup.days
            it[MINUTES] = setup.minutes
            it[EXPERIENCE] = setup.experience.name
            it[FOCUS] = setup.focus.name
            it[ITEMS] = setup.customItems.sorted().joinToString(",")
        }
    }

    override suspend fun autoOff(): Boolean = store.data.first()[AUTO_OFF] ?: false
    override suspend fun setAutoOff(off: Boolean) { store.edit { it[AUTO_OFF] = off } }
    override suspend fun reviewAnsweredAt(): Long? = store.data.first()[REVIEW_AT]
    override suspend fun setReviewAnsweredAt(at: Long) { store.edit { it[REVIEW_AT] = at } }

    private companion object {
        val SETTING = stringPreferencesKey("setting") // text-ok: key
        val DAYS = intPreferencesKey("days") // text-ok: key
        val MINUTES = intPreferencesKey("minutes") // text-ok: key
        val EXPERIENCE = stringPreferencesKey("experience") // text-ok: key
        val FOCUS = stringPreferencesKey("focus") // text-ok: key
        val ITEMS = stringPreferencesKey("items") // text-ok: key
        val AUTO_OFF = booleanPreferencesKey("auto_off") // text-ok: key
        val REVIEW_AT = longPreferencesKey("review_at") // text-ok: key
    }
}
