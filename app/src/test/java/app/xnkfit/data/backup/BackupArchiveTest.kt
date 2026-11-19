package app.xnkfit.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import app.xnkfit.data.db.DbTestSupport
import app.xnkfit.data.db.XnkDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Design §13 "Backup round trip": export → import equals source, and every refusal happens before any write. */
@RunWith(RobolectricTestRunner::class)
class BackupArchiveTest {
    private lateinit var source: XnkDatabase
    private lateinit var target: XnkDatabase
    private lateinit var tmp: File

    @Before fun setUp() {
        source = DbTestSupport.inMemory()
        target = DbTestSupport.inMemory()
        tmp = File(DbTestSupport.context().cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
        fill(source, "a", rows = 2)
        fill(target, "b", rows = 1)
    }

    @After fun tearDown() {
        source.close(); target.close(); tmp.deleteRecursively()
    }

    // --- round trip ---

    @Test fun roundTrip_everyTableEqualsSource() {
        val zip = backup(source)
        restore(zip)
        for (t in BACKUP_TABLES) {
            assertEquals("table ${t.name}", dump(source, t), dump(target, t))
            assertEquals("table ${t.name} has rows", 2, dumpArr(target, t).length())
        }
    }

    @Test fun roundTrip_bundledContentAndPhotosOnPhoneStay() {
        val zip = backup(source)
        restore(zip)
        // Bundled food row of the target is not user data: it stays; the own rows come from the backup.
        assertEquals(1, count(target, "SELECT COUNT(*) FROM food WHERE source = 'tkpi'"))
        assertEquals(2, count(target, "SELECT COUNT(*) FROM food WHERE source = 'own'"))
        // FTS rebuilt by Room's triggers.
        assertEquals(1, count(target, "SELECT COUNT(*) FROM food_fts WHERE food_fts MATCH 'own_a_food_1'"))
        // Backup without photos: the phone's photo rows stay (PV-3).
        assertEquals(1, count(target, "SELECT COUNT(*) FROM photo"))
    }

    @Test fun roundTrip_secondBackupHasSameDataBytes() {
        restore(backup(source))
        val a = entries(backup(source)).filterKeys { it.startsWith(DATA_DIR) }
        val b = entries(backup(target)).filterKeys { it.startsWith(DATA_DIR) }
        assertEquals(a.keys, b.keys)
        a.forEach { (k, v) -> assertEquals(k, String(v), String(b.getValue(k))) }
    }

    @Test fun manifest_listsSchemaAppDeviceTimeShaAndRows() {
        val m = Manifest.parse(String(entries(backup(source)).getValue(MANIFEST_NAME)))
        assertEquals(XnkDatabase.VERSION, m.schemaVersion)
        assertEquals("1.2.3", m.appVersion)
        assertEquals("dev-a", m.deviceId)
        assertEquals(T_NOW, m.createdAt)
        assertEquals(2, m.tableRows["workout_set"])
        assertEquals(BACKUP_TABLES.size, m.tableRows.size)
        m.files.forEach { assertEquals(64, it.sha256.length) }
    }

    @Test fun export_addsReadableCsvThatRestoreIgnores() {
        val zip = backup(source, csv = true)
        val csv = String(entries(zip).getValue("${CSV_DIR}workout.csv"))
        assertTrue(csv.startsWith("id,"))
        assertEquals(3, csv.trimEnd().lines().size)
        restore(zip) // import uses the same reader and restorer
        assertEquals(dump(source, BACKUP_TABLES.first()), dump(target, BACKUP_TABLES.first()))
    }

    @Test fun photos_roundTripWhenSwitchedOn() {
        val photoDirA = File(tmp, "photosA").apply { mkdirs() }
        File(photoDirA, "photo_a_1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(photoDirA, "photo_a_2.jpg").writeBytes(byteArrayOf(4, 5))
        val zip = backup(source, photos = true) { File(photoDirA, "$it.jpg") }
        val photoDirB = File(tmp, "photosB").apply { mkdirs() }
        File(photoDirB, "photo_b_1.jpg").writeBytes(byteArrayOf(9))
        restore(zip, photoDirB)
        assertEquals(2, count(target, "SELECT COUNT(*) FROM photo"))
        assertEquals(listOf("photo_a_1.jpg", "photo_a_2.jpg"), photoDirB.list()!!.sorted())
        assertEquals(listOf<Byte>(1, 2, 3), File(photoDirB, "photo_a_1.jpg").readBytes().toList())
        val path = target.openHelper.readableDatabase.query("SELECT path FROM photo WHERE id = 'photo_a_1'")
            .use { it.moveToFirst(); it.getString(0) }
        assertEquals(File(photoDirB, "photo_a_1.jpg").path, path)
    }

    @Test fun photos_offMeansNoPhotoInZip() {
        val zip = backup(source, photos = false) { File(tmp, "x.jpg").apply { writeBytes(byteArrayOf(1)) } }
        assertTrue(entries(zip).keys.none { it.startsWith(PHOTO_DIR) || it == "${DATA_DIR}photo.json" })
    }

    // --- refusals: nothing written ---

    @Test fun refuses_garbageManifest() = assertRefused(BackupError.BAD_MANIFEST) { it[MANIFEST_NAME] = "{not json".toByteArray() }

    @Test fun refuses_missingManifest() = assertRefused(BackupError.BAD_MANIFEST) { it.remove(MANIFEST_NAME) }

    @Test fun refuses_wrongChecksum() = assertRefused(BackupError.CHECKSUM) {
        it["${DATA_DIR}profile.json"] = "[]".toByteArray()
    }

    @Test fun refuses_wrongRowCount() = assertRefused(BackupError.CHECKSUM) { e ->
        e[MANIFEST_NAME] = editManifest(e) { m -> m.copy(files = m.files.map { if (it.path.endsWith("workout.json")) it.copy(rows = 5) else it }) }
    }

    @Test fun refuses_missingFile() = assertRefused(BackupError.CHECKSUM) { it.remove("${DATA_DIR}badge.json") }

    @Test fun refuses_unlistedFile() = assertRefused(BackupError.CHECKSUM) { it["${DATA_DIR}exercise.json"] = "[]".toByteArray() }

    @Test fun refuses_unsafeEntryName() = assertRefused(BackupError.NOT_A_BACKUP) { it["../evil.json"] = "[]".toByteArray() }

    @Test fun refuses_notAZip() {
        try {
            BackupReader.read(ByteArrayInputStream("hello".toByteArray()), File(tmp, "stage"))
            fail("expected refusal")
        } catch (e: BackupException) {
            assertTrue(e.error == BackupError.BAD_MANIFEST || e.error == BackupError.NOT_A_BACKUP)
        }
    }

    @Test fun refuses_newerSchema_perbaruiAplikasiDulu() = assertRefused(BackupError.NEWER_APP) { e ->
        e[MANIFEST_NAME] = editManifest(e) { it.copy(schemaVersion = XnkDatabase.VERSION + 1) }
    }

    @Test fun refuses_newerLayout() = assertRefused(BackupError.NEWER_APP) { e ->
        e[MANIFEST_NAME] = editManifest(e) { it.copy(format = BACKUP_FORMAT_VERSION + 1) }
    }

    @Test fun writeFailure_rollsBackAndOldDataStays() {
        // Valid checksums, but the last table carries a column this schema does not know.
        val e = entries(backup(source)).toMutableMap()
        val path = "${DATA_DIR}journey_moment.json"
        val rows = org.json.JSONArray(String(e.getValue(path)))
        rows.getJSONObject(0).put("noSuchColumn", 1)
        e[path] = rows.toString().toByteArray()
        e[MANIFEST_NAME] = editManifest(e) { m ->
            m.copy(files = m.files.map { if (it.path == path) it.copy(sha256 = sha256Hex(e.getValue(path))) else it })
        }
        val before = BACKUP_TABLES.associateWith { dump(target, it) }
        val checked = BackupReader.read(ByteArrayInputStream(zip(e)), File(tmp, "stage"))
        try {
            BackupRestorer(target, File(tmp, "photos")).restore(checked)
            fail("expected WRITE_FAILED")
        } catch (ex: BackupException) {
            assertEquals(BackupError.WRITE_FAILED, ex.error)
        }
        BACKUP_TABLES.forEach { assertEquals(it.name, before.getValue(it), dump(target, it)) }
    }

    @Test fun deviceId_isStableAndRandom() {
        val dir = File(tmp, "dev")
        val a = DeviceId(dir).get()
        assertEquals(a, DeviceId(dir).get())
        assertNotEquals(a, DeviceId(File(tmp, "dev2")).get())
    }

    // --- helpers ---

    private fun assertRefused(expected: BackupError, edit: (MutableMap<String, ByteArray>) -> Unit) {
        val e = entries(backup(source)).toMutableMap()
        edit(e)
        val before = BACKUP_TABLES.associateWith { dump(target, it) }
        val stage = File(tmp, "stage")
        try {
            BackupReader.read(ByteArrayInputStream(zip(e)), stage)
            fail("expected $expected")
        } catch (ex: BackupException) {
            assertEquals(expected, ex.error)
        }
        assertTrue("staging cleaned", !stage.exists())
        BACKUP_TABLES.forEach { assertEquals(it.name, before.getValue(it), dump(target, it)) }
    }

    private fun editManifest(e: Map<String, ByteArray>, f: (Manifest) -> Manifest): ByteArray =
        f(Manifest.parse(String(e.getValue(MANIFEST_NAME)))).toJson().toByteArray()

    private fun backup(db: XnkDatabase, photos: Boolean = false, csv: Boolean = false, photoFile: (String) -> File? = { null }): ByteArray {
        val out = ByteArrayOutputStream()
        BackupWriter(db, "dev-a", "1.2.3", { T_NOW }, photoFile).write(out, photos, csv)
        return out.toByteArray()
    }

    private fun restore(zip: ByteArray, photoDir: File = File(tmp, "photos")) {
        val checked = BackupReader.read(ByteArrayInputStream(zip), File(tmp, "stage"))
        BackupRestorer(target, photoDir).restore(checked)
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                m[e.name] = z.readBytes()
            }
        }
        return m
    }

    private fun zip(e: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> e.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun dumpArr(db: XnkDatabase, t: BackupTable) = dumpTable(db.openHelper.readableDatabase, t)
    private fun dump(db: XnkDatabase, t: BackupTable) = dumpArr(db, t).toString()

    private fun count(db: XnkDatabase, sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    companion object {
        const val T_NOW = 1_700_000_123_000L

        /**
         * Fills every backup table (and `photo`, plus one bundled food row) with [rows] rows, columns typed from
         * PRAGMA table_info and foreign keys pointing at the parent's first row, so the test follows the schema.
         */
        fun fill(db: XnkDatabase, tag: String, rows: Int) {
            val sql = db.openHelper.writableDatabase
            for (t in BACKUP_TABLES + PHOTO_TABLE) {
                val fks = foreignKeys(sql, t.name)
                for (n in 1..rows) {
                    val cols = columns(sql, t.name)
                    val o = JSONObject()
                    cols.forEach { (name, type) ->
                        o.put(name, when {
                            name == "id" -> "${t.name}_${tag}_$n"
                            name in fks -> "${fks.getValue(name)}_${tag}_1"
                            t.name == "food" && name == "source" -> "own"
                            t.name == "food" && name == "nameId" -> "own_${tag}_food_$n"
                            t.name == "recipe" && name == "origin" -> "own"
                            name == "position" || name == "day" && type == "INTEGER" -> n
                            type == "INTEGER" -> 1_000L * n
                            type == "REAL" -> 1.5 * n
                            else -> "${name}_${tag}_$n"
                        })
                    }
                    insertRows(sql, t.name, org.json.JSONArray().put(o), cols.keys)
                }
                if (rows > 0 && t.name == "food") {
                    sql.execSQL(
                        "INSERT INTO food (id, createdAt, updatedAt, source, nameId, kcalPer100, protein, carb, fat, isEstimate) " +
                            "VALUES ('tkpi_$tag', 0, 0, 'tkpi', 'nasi_$tag', 1, 1, 1, 1, 0)",
                    )
                }
                if (t.name == "photo" && tag == "b") {
                    // Target keeps one photo; trim the rest so the test sees exactly one.
                    sql.execSQL("DELETE FROM photo WHERE id <> 'photo_b_1'")
                }
            }
        }

        private fun columns(db: SupportSQLiteDatabase, table: String): LinkedHashMap<String, String> =
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val out = LinkedHashMap<String, String>()
                while (c.moveToNext()) out[c.getString(c.getColumnIndexOrThrow("name"))] = c.getString(c.getColumnIndexOrThrow("type"))
                out
            }

        private fun foreignKeys(db: SupportSQLiteDatabase, table: String): Map<String, String> =
            db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(c.getColumnIndexOrThrow("from")), c.getString(c.getColumnIndexOrThrow("table"))) }
            }
    }
}
