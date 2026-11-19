package app.xnkfit.ui.onboarding

import androidx.room.withTransaction
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase

/** Where onboarding writes its one result. Health answers go to the phone only (PV-1). */
interface ProfileStore {
    /** Saves the profile row and the first weight in one step, or neither. */
    suspend fun save(profile: ProfileEntity, firstWeight: MeasureEntity)
}

/** Room version (Design §3: `profile` is one row, the weight is a `measure` of type `weight`). */
class RoomProfileStore(private val db: XnkDatabase) : ProfileStore {
    override suspend fun save(profile: ProfileEntity, firstWeight: MeasureEntity) {
        db.withTransaction {
            db.profileDao().upsert(profile)
            db.profileDao().upsertMeasure(firstWeight)
        }
    }
}
