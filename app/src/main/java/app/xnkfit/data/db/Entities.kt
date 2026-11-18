package app.xnkfit.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room schema v1 (Design §3). Rules the file follows:
 * - every table has `id` (UUID text unless noted), `createdAt`, `updatedAt` (epoch ms);
 * - lists (muscles, items, household measures, ingredients, days, allergens) are JSON text,
 *   written and read by the code that owns the feature; the database never needs to query inside them;
 * - nothing here needs the network, and nothing leaves the phone (PV-1);
 * - v1.x only appends columns or tables; never drops a column (Design §3).
 * Enum-like fields are plain text codes from Content.md so the schema JSON stays stable.
 */

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val createdAt: Long,
    val updatedAt: Long,
    val sex: String?,
    val birthYear: Int?,
    val heightCm: Double?,
    val goal: String?,
    val interest: String?,
    val nickname: String?,
    val healthRisk: Boolean = false,
    val screeningDone: Boolean = false,
    /** Pregnant or breastfeeding: one answer (Design §3). */
    val pregnant: Boolean = false,
    val eatingHistory: String? = null,
) {
    companion object {
        const val SINGLETON_ID = "me"
    }
}

@Entity(tableName = "measure", indices = [Index(value = ["type", "takenAt"])])
data class MeasureEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** weight, waist, plus six opt-in types. */
    val type: String,
    val value: Double,
    /** Always metric. */
    val unit: String,
    val takenAt: Long,
)

@Entity(tableName = "photo")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** File in app-private storage (PV-3); never in the media store. */
    val path: String,
    val takenAt: Long,
    /** front, side, back. */
    val kind: String,
    /** Photos stay out of backup unless B-3 is on (Design §14). */
    val hiddenFromBackup: Boolean = true,
)

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** gym, home-bw, home-db, custom. */
    val setting: String,
    /** JSON array of equipment codes. */
    val itemsJson: String,
)

/** Read-only from bundled content; `id` is the stable code, e.g. SQUAT_GOB. */
@Entity(tableName = "exercise", indices = [Index("pattern")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val nameId: String,
    val nameEn: String,
    val pattern: String,
    /** JSON: main and helper muscles. */
    val musclesJson: String,
    val level: String,
    val variantOf: String? = null,
    val cuesEn: String? = null,
    val hintId: String? = null,
)

@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** auto, own, jelajahi. `auto` rows are regenerated, `own` never (L-5). */
    val source: String,
    val setting: String,
    val rulesVersion: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "program_day",
    foreignKeys = [ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["programId", "position"], unique = true)],
)
data class ProgramDayEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val programId: String,
    val position: Int,
    val name: String,
    val estMinutes: Int,
)

@Entity(
    tableName = "program_item",
    foreignKeys = [ForeignKey(ProgramDayEntity::class, ["id"], ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["dayId", "position"], unique = true), Index("exerciseId")],
)
data class ProgramItemEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dayId: String,
    /** Stable exercise code; no foreign key so a content update cannot break a program. */
    val exerciseId: String,
    val position: Int,
    val setsTarget: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int,
    val supersetGroup: Int? = null,
    val setKindDefault: String = "normal",
)

/** Written on every tap (PR-2). `endedAt == null` means the workout is not finished. */
@Entity(tableName = "workout", indices = [Index("startedAt"), Index("endedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** No foreign key: the program may be regenerated while the history stays. */
    val programDayId: String?,
    /** dipandu, bebas. */
    val mode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** 1 to 5, set at "Selesai". */
    val feeling: Int? = null,
    val note: String? = null,
    /** Rest timer end time, stored as a clock time so a killed app resumes (Design §4). */
    val restEndsAt: Long? = null,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [ForeignKey(WorkoutEntity::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workoutId", "position"], unique = true)],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    /** normal, drop, failure, ... */
    val kind: String = "normal",
    val weightKg: Double? = null,
    val reps: Int? = null,
    /** Replaces kg for bands (L-2). */
    val bandLevel: Int? = null,
    val rpe: Double? = null,
    /** rapi, ragu. */
    val form: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
)

@Entity(tableName = "activity", indices = [Index("at")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: String,
    val minutes: Int,
    /** light, moderate, vigorous. */
    val effort: String,
    /** Computed from MET when written (Rules.md), then stored. */
    val kcal: Double,
    val at: Long,
)

@Entity(tableName = "food", indices = [Index("source")])
data class FoodEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** tkpi, packaged, warung-est, own. */
    val source: String,
    val nameId: String,
    val nameEn: String?,
    val kcalPer100: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val fibre: Double?,
    val sodium: Double?,
    /** JSON: household measure codes with gram weight. */
    val householdJson: String? = null,
    val isEstimate: Boolean = false,
    val halal: Boolean? = null,
    /** JSON array of allergen codes. */
    val allergensJson: String? = null,
)

/**
 * Prebuilt FTS4 index over [FoodEntity] names (Design §10: FTS4, not LIKE).
 * Room keeps it in step with `food` through triggers, so a content load needs no extra step.
 */
@Fts4(contentEntity = FoodEntity::class)
@Entity(tableName = "food_fts")
data class FoodFts(
    val nameId: String,
    val nameEn: String?,
)

@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val name: String,
    val servings: Double,
    /** JSON: list of {foodId, gram}. */
    val ingredientsJson: String,
    /** own, bundled. */
    val origin: String,
)

/**
 * Kcal and macros are stored at write time so the log never changes when a food row updates.
 * No foreign key on foodId/recipeId: deleting an own food must not delete past logs.
 */
@Entity(tableName = "food_log", indices = [Index("day")])
data class FoodLogEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Local date, ISO yyyy-MM-dd. */
    val day: String,
    /** pagi, siang, malam, camilan. */
    val slot: String,
    val foodId: String?,
    val recipeId: String?,
    val grams: Double?,
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    /** Quick-add calories without a food row. */
    val quickKcal: Double? = null,
)

@Entity(
    tableName = "meal_plan",
    indices = [Index(value = ["weekStart", "day", "slot"], unique = true)],
)
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Local date of the week's first day, ISO. */
    val weekStart: String,
    /** Day of week, 1 (Monday) to 7. */
    val day: Int,
    val slot: String,
    val optionCode: String,
    val locked: Boolean = false,
    val cookOnce: Boolean = false,
    val eatOut: Boolean = false,
)

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: String,
    val hour: Int,
    val minute: Int,
    /** JSON array of weekday numbers. */
    val daysJson: String,
    val enabled: Boolean,
)

@Entity(tableName = "points_event", indices = [Index("at")])
data class PointsEventEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val at: Long,
    /** workout, activity, foodDay, measure, streakWeek, loggedWeek (R_PTS_TARGET: week with food logged on 5+ days). Never from weight (P-3). */
    val kind: String,
    val points: Int,
)

/** `id` is the badge code (18 codes in Content.md), so a badge can be earned once. */
@Entity(tableName = "badge")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val earnedAt: Long,
)

@Entity(tableName = "journey_moment", indices = [Index("at")])
data class JourneyMomentEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val at: Long,
    val text: String,
    val kind: String,
    val photoId: String? = null,
)

/** One row, `id` = "backup". B-6 compares [lastDeviceId] with this phone's id. */
@Entity(tableName = "backup_meta")
data class BackupMetaEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val createdAt: Long,
    val updatedAt: Long,
    val lastBackupAt: Long?,
    val lastDeviceId: String?,
    /** JSON: Drive file ids of the last three backups. */
    val lastDriveFileIdsJson: String? = null,
) {
    companion object {
        const val SINGLETON_ID = "backup"
    }
}

/** One row, `id` = "schema". B-5 newer-version check. */
@Entity(tableName = "schema_meta")
data class SchemaMetaEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int,
    val appVersionCreated: String,
) {
    companion object {
        const val SINGLETON_ID = "schema"
    }
}
