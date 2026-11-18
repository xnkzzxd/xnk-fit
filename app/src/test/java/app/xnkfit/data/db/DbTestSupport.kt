package app.xnkfit.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** Shared builders for the database tests. They run on the JVM under Robolectric (Design §12). */
internal object DbTestSupport {
    const val T0 = 1_700_000_000_000L

    fun context(): Context = ApplicationProvider.getApplicationContext()

    fun inMemory(): XnkDatabase =
        Room.inMemoryDatabaseBuilder(context(), XnkDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(XnkDatabase.seedCallback("test") { T0 })
            .build()

    fun onDisk(name: String): XnkDatabase =
        Room.databaseBuilder(context(), XnkDatabase::class.java, name)
            .addCallback(XnkDatabase.seedCallback("test") { T0 })
            .build()

    fun workout(id: String = "w1", started: Long = T0) = WorkoutEntity(
        id = id, createdAt = started, updatedAt = started, programDayId = null, mode = "bebas", startedAt = started,
    )

    fun set(id: String, workoutId: String = "w1", position: Int, done: Boolean = false, reps: Int? = null, kg: Double? = null) =
        WorkoutSetEntity(
            id = id, createdAt = T0, updatedAt = T0, workoutId = workoutId, exerciseId = "SQUAT_GOB",
            position = position, weightKg = kg, reps = reps, done = done, doneAt = if (done) T0 + position else null,
        )

    fun food(id: String, nameId: String, nameEn: String? = null, kcal: Double = 100.0) = FoodEntity(
        id = id, createdAt = T0, updatedAt = T0, source = "tkpi", nameId = nameId, nameEn = nameEn,
        kcalPer100 = kcal, protein = 1.0, carb = 2.0, fat = 3.0, fibre = null, sodium = null,
    )
}
