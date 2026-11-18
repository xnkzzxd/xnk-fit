package app.xnkfit.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration harness (Design §3, §13, NF-5): every released schema in `app/schemas/` is opened and
 * migrated forward to the current version, then validated against the current schema.
 *
 * To release a new version N+1:
 *  1. change the entities, bump [XnkDatabase.VERSION]; KSP writes `schemas/.../N+1.json`; commit it;
 *  2. add `Migration(N, N+1)` to [XnkDatabase.MIGRATIONS];
 *  3. add a `migrate_N_to_N+1` test below, patterned on [exampleMigrationPattern];
 *  4. [everyReleasedVersionMigratesToLatest] picks up the new JSON by itself.
 * Never edit or delete a committed schema JSON once that version has shipped.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        XnkDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val name = "migration-test.db"

    @Test fun v1CreatesAndValidates() {
        helper.createDatabase(name, 1).use { db ->
            insertV1Rows(db)
            db.query("SELECT COUNT(*) FROM workout_set").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
        }
    }

    /** v1 → v1 harness run: reopen the v1 file through Room's own validation with the real migration list. */
    @Test fun v1ToV1KeepsDataAndPassesValidation() {
        helper.createDatabase(name, 1).use { insertV1Rows(it) }
        helper.runMigrationsAndValidate(name, 1, true, *XnkDatabase.MIGRATIONS).use { db ->
            db.query("SELECT reps FROM workout_set WHERE id = 's1'").use { c ->
                c.moveToFirst(); assertEquals(8, c.getInt(0))
            }
        }
    }

    /** Opens the v1 file with the real database class, as the app does after an update. */
    @Test fun appOpensV1FileCreatedByHarness() {
        helper.createDatabase(name, 1).use { insertV1Rows(it) }
        val db = Room_open(name)
        try {
            val sets = kotlinx.coroutines.runBlocking { db.workoutDao().sets("w1") }
            assertEquals(listOf("s1"), sets.map { it.id })
        } finally {
            db.close()
        }
    }

    /**
     * Release gate: every `N.json` under schemas must be migratable to [XnkDatabase.VERSION].
     * With only v1 this is the v1 → v1 case; from the first v2 on, it runs every older version
     * through [XnkDatabase.MIGRATIONS].
     */
    @Test fun everyReleasedVersionMigratesToLatest() {
        val versions = releasedVersions()
        assertTrue("schema JSON for version ${XnkDatabase.VERSION} missing: run a build and commit app/schemas", XnkDatabase.VERSION in versions)
        for (v in versions.filter { it < XnkDatabase.VERSION }.ifEmpty { listOf(XnkDatabase.VERSION) }) {
            helper.createDatabase("$name-$v", v).close()
            helper.runMigrationsAndValidate("$name-$v", XnkDatabase.VERSION, true, *XnkDatabase.MIGRATIONS).close()
        }
    }

    /**
     * PATTERN ONLY, not a released migration, not run. It shows what a real step looks like:
     * a column is appended with a default, old rows keep their data. Copy it when v2 exists, point it
     * at real SQL, delete this one. No v2 schema exists, so it cannot run today.
     */
    @Test
    @org.junit.Ignore("pattern for the first real migration; there is no v2 schema yet")
    fun exampleMigrationPattern() {
        helper.createDatabase(name, 1).use { insertV1Rows(it) }
        helper.runMigrationsAndValidate(name, 2, true, EXAMPLE_1_2).use { db ->
            db.query("SELECT example FROM workout WHERE id = 'w1'").use { c ->
                c.moveToFirst(); assertEquals(0, c.getInt(0)) // old row got the default
            }
        }
    }

    private fun Room_open(file: String): XnkDatabase =
        androidx.room.Room.databaseBuilder(DbTestSupport.context(), XnkDatabase::class.java, file).build()

    private fun releasedVersions(): List<Int> {
        val dir = File("schemas/${XnkDatabase::class.java.name}")
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
    }

    private fun insertV1Rows(db: SupportSQLiteDatabase) {
        val t = DbTestSupport.T0
        db.execSQL("INSERT INTO workout (id, createdAt, updatedAt, mode, startedAt) VALUES ('w1', $t, $t, 'bebas', $t)")
        db.execSQL(
            "INSERT INTO workout_set (id, createdAt, updatedAt, workoutId, exerciseId, position, kind, reps, done) " +
                "VALUES ('s1', $t, $t, 'w1', 'SQUAT_GOB', 0, 'normal', 8, 0)",
        )
    }

    private companion object {
        /** Example only (see [exampleMigrationPattern]). Not in [XnkDatabase.MIGRATIONS]. */
        val EXAMPLE_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout ADD COLUMN example INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
