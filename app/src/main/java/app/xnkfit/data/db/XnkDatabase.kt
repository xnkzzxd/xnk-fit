package app.xnkfit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The one database file `xnkfit.db` (Design §1, §3). Version 1.
 * `exportSchema = true`: the JSON for each released version is committed in `app/schemas/` and feeds
 * the migration tests (NF-5). Never edit a committed schema file; change the entities, bump the
 * version, add a Migration and a test (see README).
 */
@Database(
    entities = [
        ProfileEntity::class, MeasureEntity::class, PhotoEntity::class, EquipmentEntity::class,
        ExerciseEntity::class, ProgramEntity::class, ProgramDayEntity::class, ProgramItemEntity::class,
        WorkoutEntity::class, WorkoutSetEntity::class, ActivityEntity::class,
        FoodEntity::class, FoodFts::class, RecipeEntity::class, FoodLogEntity::class, MealPlanEntity::class,
        ReminderEntity::class, PointsEventEntity::class, BadgeEntity::class, JourneyMomentEntity::class,
        BackupMetaEntity::class, SchemaMetaEntity::class,
        ContentMetaEntity::class, ContentDocEntity::class, MealPackageEntity::class,
    ],
    version = XnkDatabase.VERSION,
    exportSchema = true,
)
abstract class XnkDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun foodDao(): FoodDao
    abstract fun progressDao(): ProgressDao
    abstract fun metaDao(): MetaDao
    abstract fun contentDao(): ContentDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "xnkfit.db"

        /** Every released migration, in order. Empty while only v1 exists. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        /** Writes the `schema_meta` row when the file is created (B-5 compares it with the app). */
        fun seedCallback(appVersion: String, now: () -> Long = System::currentTimeMillis) =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val t = now()
                    db.execSQL(
                        "INSERT INTO schema_meta (id, createdAt, updatedAt, version, appVersionCreated) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(SchemaMetaEntity.SINGLETON_ID, t, t, VERSION, appVersion),
                    )
                }
            }

        fun open(context: Context, appVersion: String): XnkDatabase =
            Room.databaseBuilder(context.applicationContext, XnkDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(seedCallback(appVersion))
                .build()
    }
}
