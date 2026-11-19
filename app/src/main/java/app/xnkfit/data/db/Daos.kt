package app.xnkfit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Upsert suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 'me'")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 'me'")
    suspend fun get(): ProfileEntity?

    @Upsert suspend fun upsertMeasure(measure: MeasureEntity)

    @Query("SELECT * FROM measure WHERE type = :type ORDER BY takenAt")
    fun observeMeasures(type: String): Flow<List<MeasureEntity>>

    @Query("SELECT * FROM measure WHERE type = :type ORDER BY takenAt DESC LIMIT 1")
    suspend fun latestMeasure(type: String): MeasureEntity?

    @Upsert suspend fun upsertPhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photo ORDER BY takenAt DESC")
    fun observePhotos(): Flow<List<PhotoEntity>>

    @Upsert suspend fun upsertEquipment(equipment: EquipmentEntity)

    @Query("SELECT * FROM equipment")
    suspend fun equipment(): List<EquipmentEntity>
}

/** One done set with its workout's start, for [WorkoutDao.finishedSetHistory]. */
data class HistorySetRow(
    val startedAt: Long,
    val exerciseId: String,
    val weightKg: Double?,
    val reps: Int?,
    val bandLevel: Int?,
    val form: String?,
)

@Dao
interface ProgramDao {
    @Upsert suspend fun upsertExercises(rows: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE pattern = :pattern ORDER BY nameId")
    suspend fun exercisesByPattern(pattern: String): List<ExerciseEntity>

    @Insert suspend fun insertProgram(program: ProgramEntity)

    @Insert suspend fun insertDays(rows: List<ProgramDayEntity>)

    @Insert suspend fun insertItems(rows: List<ProgramItemEntity>)

    @Query("SELECT * FROM program WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE isActive = 1 LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM exercise")
    suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT * FROM program_day WHERE programId = :programId ORDER BY position")
    suspend fun days(programId: String): List<ProgramDayEntity>

    @Query("SELECT * FROM program_item WHERE dayId = :dayId ORDER BY position")
    suspend fun items(dayId: String): List<ProgramItemEntity>

    /** `auto` programs are regenerated; `own` ones are never touched (L-5). Days and items cascade. */
    @Query("DELETE FROM program WHERE source = 'auto'")
    suspend fun deleteAutoPrograms()

    @Query("UPDATE program SET isActive = 0, updatedAt = :now WHERE isActive = 1")
    suspend fun deactivateAll(now: Long)
}

/**
 * Logger path (PR-2, NF-2). Every call is one small write; the 50 ms budget (Design §10) applies to
 * [saveSet] and [startWorkout]. No call here queues or batches anything.
 */
@Dao
interface WorkoutDao {
    @Insert suspend fun startWorkout(workout: WorkoutEntity)

    /** Update by id; returns the rows changed (0 when the set is new). Throws on a (workoutId, position) clash. */
    @Update suspend fun updateSetRow(set: WorkoutSetEntity): Int

    /** Throws on a (workoutId, position) clash. */
    @Insert suspend fun insertSetRow(set: WorkoutSetEntity)

    @Query("UPDATE workout SET updatedAt = :now, restEndsAt = :restEndsAt WHERE id = :workoutId")
    suspend fun touch(workoutId: String, now: Long, restEndsAt: Long?)

    /** Set write and the workout's `updatedAt`/rest end in one transaction (Design §4 step 2). */
    @Transaction
    suspend fun saveSet(set: WorkoutSetEntity, now: Long, restEndsAt: Long? = null) {
        // Not @Upsert: Room's upsert swallows a unique-key clash and drops the write. A set must never vanish silently.
        if (updateSetRow(set) == 0) insertSetRow(set)
        touch(set.workoutId, now, restEndsAt)
    }

    /** Several set rows (swap, superset, rest change) and the workout's clock in one transaction (B-07). */
    @Transaction
    suspend fun saveSets(sets: List<WorkoutSetEntity>, workoutId: String, now: Long, restEndsAt: Long?) {
        sets.forEach { if (updateSetRow(it) == 0) insertSetRow(it) }
        touch(workoutId, now, restEndsAt)
    }

    @Query("DELETE FROM workout_set WHERE id IN (:ids)")
    suspend fun deleteSetRows(ids: List<String>)

    /** Removing sets is a tap like any other: rows and the workout's clock together. */
    @Transaction
    suspend fun deleteSets(ids: List<String>, workoutId: String, now: Long, restEndsAt: Long?) {
        deleteSetRows(ids)
        touch(workoutId, now, restEndsAt)
    }

    /** "Batalkan latihan": the workout and (by cascade) its sets are gone. */
    @Query("DELETE FROM workout WHERE id = :workoutId")
    suspend fun deleteWorkout(workoutId: String)

    @Query("UPDATE workout SET mode = :mode, updatedAt = :now WHERE id = :workoutId")
    suspend fun setMode(workoutId: String, mode: String, now: Long)

    /** Done sets of the last finished workout that has [exerciseId] ("sebelumnya", L-7). */
    @Query(
        "SELECT s.* FROM workout_set s WHERE s.exerciseId = :exerciseId AND s.done = 1 AND s.workoutId = (" +
            "SELECT w.id FROM workout w JOIN workout_set x ON x.workoutId = w.id " +
            "WHERE x.exerciseId = :exerciseId AND x.done = 1 AND w.endedAt IS NOT NULL " +
            "ORDER BY w.startedAt DESC LIMIT 1) ORDER BY s.position",
    )
    suspend fun previousSets(exerciseId: String): List<WorkoutSetEntity>

    /** Done sets of finished workouts, with the start time, for progression (Design §6). */
    @Query(
        "SELECT w.startedAt AS startedAt, s.exerciseId AS exerciseId, s.weightKg AS weightKg, s.reps AS reps, " +
            "s.bandLevel AS bandLevel, s.form AS form FROM workout_set s JOIN workout w ON w.id = s.workoutId " +
            "WHERE s.done = 1 AND w.endedAt IS NOT NULL ORDER BY w.startedAt",
    )
    suspend fun finishedSetHistory(): List<HistorySetRow>

    /** Program day of the last finished workout, to pick the next day of the rotation. */
    @Query("SELECT programDayId FROM workout WHERE endedAt IS NOT NULL AND programDayId IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastFinishedProgramDayId(): String?

    @Query("SELECT COUNT(*) FROM workout")
    suspend fun workoutCount(): Int

    @Query("SELECT * FROM workout_set WHERE workoutId = :workoutId ORDER BY position")
    fun observeSets(workoutId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE workoutId = :workoutId ORDER BY position")
    suspend fun sets(workoutId: String): List<WorkoutSetEntity>

    @Query("UPDATE workout SET endedAt = :endedAt, feeling = :feeling, note = :note, restEndsAt = NULL, updatedAt = :endedAt WHERE id = :workoutId")
    suspend fun finish(workoutId: String, endedAt: Long, feeling: Int?, note: String?)

    @Query("SELECT * FROM workout WHERE id = :workoutId")
    suspend fun workout(workoutId: String): WorkoutEntity?

    /** Unfinished workouts started at or after [since]: "Lanjutkan latihan?" (Design §4 step 4). */
    @Query("SELECT * FROM workout WHERE endedAt IS NULL AND startedAt >= :since ORDER BY startedAt DESC")
    suspend fun unfinishedSince(since: Long): List<WorkoutEntity>

    /** Older unfinished workouts, to be closed with their last set and a neutral note. */
    @Query("SELECT * FROM workout WHERE endedAt IS NULL AND startedAt < :before")
    suspend fun staleUnfinished(before: Long): List<WorkoutEntity>

    @Query("SELECT MAX(doneAt) FROM workout_set WHERE workoutId = :workoutId AND done = 1")
    suspend fun lastDoneAt(workoutId: String): Long?

    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeFinished(limit: Int): Flow<List<WorkoutEntity>>

    @Upsert suspend fun upsertActivity(activity: ActivityEntity)

    @Query("SELECT * FROM activity WHERE at >= :from AND at < :to ORDER BY at")
    suspend fun activitiesBetween(from: Long, to: Long): List<ActivityEntity>
}

@Dao
interface FoodDao {
    @Upsert suspend fun upsertFoods(rows: List<FoodEntity>)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun food(id: String): FoodEntity?

    /**
     * Prefix search over the FTS4 index. [match] is an FTS4 MATCH expression, for example `ayam* goreng*`;
     * build it with [FoodSearch.toMatch], which also strips characters that are FTS syntax.
     */
    @Query(
        "SELECT food.* FROM food JOIN food_fts ON food.rowid = food_fts.rowid " +
            "WHERE food_fts MATCH :match ORDER BY food.nameId LIMIT :limit",
    )
    suspend fun search(match: String, limit: Int): List<FoodEntity>

    @Upsert suspend fun upsertRecipe(recipe: RecipeEntity)

    @Query("SELECT * FROM recipe ORDER BY name")
    fun observeRecipes(): Flow<List<RecipeEntity>>

    @Upsert suspend fun upsertLog(row: FoodLogEntity)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT * FROM food_log WHERE day = :day ORDER BY createdAt")
    fun observeDay(day: String): Flow<List<FoodLogEntity>>

    @Query("SELECT COALESCE(SUM(COALESCE(quickKcal, kcal)), 0) FROM food_log WHERE day = :day")
    fun observeDayKcal(day: String): Flow<Double>

    /** One option per week, day and slot: a new row for the same slot replaces the old one (M-3). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(row: MealPlanEntity)

    @Query("SELECT * FROM meal_plan WHERE weekStart = :weekStart ORDER BY day, slot")
    suspend fun plan(weekStart: String): List<MealPlanEntity>

    @Query("DELETE FROM meal_plan WHERE weekStart = :weekStart")
    suspend fun deletePlanWeek(weekStart: String)

    @Query("DELETE FROM meal_plan WHERE weekStart = :weekStart AND day = :day AND slot = :slot")
    suspend fun deletePlanCell(weekStart: String, day: Int, slot: String)

    // --- B-10 food log reads ---

    @Query("SELECT * FROM food_log WHERE day = :day ORDER BY createdAt")
    suspend fun logOfDay(day: String): List<FoodLogEntity>

    @Query("SELECT * FROM food_log WHERE id = :id")
    suspend fun logRow(id: String): FoodLogEntity?

    /** Most recently logged foods (M-7 "Terakhir"), newest first. */
    @Query("SELECT foodId FROM food_log WHERE foodId IS NOT NULL GROUP BY foodId ORDER BY MAX(createdAt) DESC LIMIT :limit")
    suspend fun recentFoodIds(limit: Int): List<String>

    /** Foods logged at least [min] times (M-7 "Favorit" is derived from the log; there is no extra table). */
    @Query(
        "SELECT foodId FROM food_log WHERE foodId IS NOT NULL GROUP BY foodId HAVING COUNT(*) >= :min " +
            "ORDER BY COUNT(*) DESC, MAX(createdAt) DESC LIMIT :limit",
    )
    suspend fun frequentFoodIds(min: Int, limit: Int): List<String>

    @Query("SELECT * FROM food WHERE id IN (:ids)")
    suspend fun foodsByIds(ids: List<String>): List<FoodEntity>

    @Query("SELECT * FROM food WHERE source = 'own' ORDER BY nameId")
    suspend fun ownFoods(): List<FoodEntity>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun recipe(id: String): RecipeEntity?
}

@Dao
interface ProgressDao {
    @Upsert suspend fun upsertReminder(row: ReminderEntity)

    @Query("SELECT * FROM reminder")
    suspend fun reminders(): List<ReminderEntity>

    @Insert suspend fun addPoints(row: PointsEventEntity)

    @Query("SELECT COALESCE(SUM(points), 0) FROM points_event")
    fun observeTotalPoints(): Flow<Int>

    /** Ignored when the badge is already earned. Returns the row id, or -1 when it was a repeat. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun earnBadge(row: BadgeEntity): Long

    @Query("SELECT * FROM badge ORDER BY earnedAt")
    fun observeBadges(): Flow<List<BadgeEntity>>

    @Upsert suspend fun upsertMoment(row: JourneyMomentEntity)

    /** Points keep a stable id (`workout:<id>`, `foodDay:<date>` ...), so a recount never doubles them (B-12). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPointsIfNew(rows: List<PointsEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMomentIfNew(row: JourneyMomentEntity)

    @Query("SELECT id FROM badge")
    suspend fun earnedBadgeCodes(): List<String>

    @Query("SELECT * FROM badge ORDER BY earnedAt")
    suspend fun badges(): List<BadgeEntity>

    @Query("SELECT * FROM journey_moment ORDER BY at DESC LIMIT :limit")
    suspend fun moments(limit: Int): List<JourneyMomentEntity>

    // Rows for the progress rules (B-12). Columns map onto `domain.progress` data classes by name.
    @Query("SELECT id, endedAt FROM workout WHERE endedAt IS NOT NULL")
    suspend fun finishedWorkouts(): List<app.xnkfit.domain.progress.FinishedWorkout>

    @Query(
        "SELECT workoutId, exerciseId, kind, weightKg, reps, bandLevel, form, doneAt FROM workout_set " +
            "WHERE done = 1 AND doneAt IS NOT NULL",
    )
    suspend fun doneSets(): List<app.xnkfit.domain.progress.DoneSet>

    @Query("SELECT at, minutes, effort FROM activity")
    suspend fun activityRows(): List<app.xnkfit.domain.progress.ActivityRow>

    @Query("SELECT day, slot FROM food_log")
    suspend fun foodLogRows(): List<app.xnkfit.domain.progress.FoodLogRow>

    @Query("SELECT type, takenAt FROM measure")
    suspend fun measureRows(): List<app.xnkfit.domain.progress.MeasureRow>

    @Query("SELECT weekStart, day FROM meal_plan")
    suspend fun planRows(): List<app.xnkfit.domain.progress.PlanRow>

    @Query("SELECT COUNT(*) FROM photo")
    suspend fun photoCount(): Int

    @Query("SELECT kind, points FROM points_event")
    suspend fun pointRows(): List<app.xnkfit.domain.progress.PointRow>

    @Query("SELECT * FROM photo ORDER BY takenAt DESC")
    suspend fun photos(): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("SELECT * FROM measure WHERE type = :type ORDER BY takenAt")
    suspend fun measures(type: String): List<MeasureEntity>

    @Upsert suspend fun upsertMeasure(row: MeasureEntity)

    @Upsert suspend fun upsertPhoto(row: PhotoEntity)

    @Query("SELECT * FROM journey_moment ORDER BY at DESC")
    fun observeMoments(): Flow<List<JourneyMomentEntity>>

    // Share cards (B-13): read-only lookups for the card facts.
    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastFinishedWorkout(): WorkoutEntity?

    @Query("SELECT name FROM program_day WHERE id = :id")
    suspend fun programDayName(id: String): String?

    @Query("SELECT nameId FROM exercise WHERE id = :id")
    suspend fun exerciseName(id: String): String?
}

@Dao
interface MetaDao {
    @Upsert suspend fun upsertBackupMeta(row: BackupMetaEntity)

    @Query("SELECT * FROM backup_meta WHERE id = 'backup'")
    suspend fun backupMeta(): BackupMetaEntity?

    @Upsert suspend fun upsertSchemaMeta(row: SchemaMetaEntity)

    @Query("SELECT * FROM schema_meta WHERE id = 'schema'")
    suspend fun schemaMeta(): SchemaMetaEntity?
}

/** Turns what the user typed into an FTS4 prefix query. Pure, so it is testable on the JVM. */
object FoodSearch {
    /** Returns null when nothing searchable was typed. Only letters and digits survive (they cannot form FTS syntax). */
    fun toMatch(input: String): String? {
        val words = input.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}

/** Content loader writes (B-04). Replace-by-kind: delete the kind, insert the new rows, all inside one transaction. */
@Dao
interface ContentDao {
    @Query("SELECT * FROM content_meta WHERE id = 'content'")
    suspend fun meta(): ContentMetaEntity?

    @Upsert suspend fun upsertMeta(row: ContentMetaEntity)

    @Query("DELETE FROM exercise")
    suspend fun deleteExercises()

    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)

    /** Deletes the foods of one content file by id prefix; user foods (`own`) never match a bundled prefix. */
    @Query("DELETE FROM food WHERE substr(id, 1, length(:prefix)) = :prefix")
    suspend fun deleteFoodsWithPrefix(prefix: String)

    @Insert suspend fun insertFoods(rows: List<FoodEntity>)

    @Query("UPDATE food SET householdJson = :json WHERE id = :id")
    suspend fun setHousehold(id: String, json: String)

    @Query("DELETE FROM meal_package")
    suspend fun deleteMealPackages()

    @Insert suspend fun insertMealPackages(rows: List<MealPackageEntity>)

    @Query("SELECT * FROM meal_package ORDER BY id")
    suspend fun allMealPackages(): List<MealPackageEntity>

    @Query("SELECT * FROM meal_package WHERE profile = :profile AND slot = :slot ORDER BY id")
    suspend fun mealPackages(profile: String, slot: String): List<MealPackageEntity>

    @Upsert suspend fun upsertDoc(row: ContentDocEntity)

    @Query("SELECT * FROM content_doc WHERE id = :id")
    suspend fun doc(id: String): ContentDocEntity?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun exerciseCount(): Int

    @Query("SELECT COUNT(*) FROM food")
    suspend fun foodCount(): Int
}
