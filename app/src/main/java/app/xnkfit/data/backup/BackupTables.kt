package app.xnkfit.data.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * One table in the backup. [where] limits which rows belong to the user (dump and delete use the same clause),
 * so restore replaces exactly what backup wrote and nothing else.
 */
internal data class BackupTable(val name: String, val where: String? = null) {
    val path: String get() = "$DATA_DIR$name.json"
}

/**
 * What a backup holds: everything the user made (Design §3). Left out on purpose:
 * - bundled content (`exercise`, `food` rows other than `own`, `content_meta`, `content_doc`, `meal_package`):
 *   reloaded from the APK, and a restore must not turn newer content back into older content;
 * - `food_fts`: Room's triggers rebuild it from `food`;
 * - `backup_meta` and `schema_meta`: they describe this phone and this file, not the user's data;
 * - `photo` unless photo backup is on (PV-3).
 * Parent tables come before children so the cascade foreign keys hold during insert.
 */
internal val BACKUP_TABLES: List<BackupTable> = listOf(
    BackupTable("profile"),
    BackupTable("measure"),
    BackupTable("equipment"),
    BackupTable("program"),
    BackupTable("program_day"),
    BackupTable("program_item"),
    BackupTable("workout"),
    BackupTable("workout_set"),
    BackupTable("activity"),
    BackupTable("food", "source = 'own'"),
    BackupTable("recipe", "origin = 'own'"),
    BackupTable("food_log"),
    BackupTable("meal_plan"),
    BackupTable("reminder"),
    BackupTable("points_event"),
    BackupTable("badge"),
    BackupTable("journey_moment"),
)

internal val PHOTO_TABLE = BackupTable("photo")

/** Reads every row of [table] as JSON objects in primary-key order (stable bytes for the same data). */
internal fun dumpTable(db: SupportSQLiteDatabase, table: BackupTable): JSONArray {
    val sql = buildString {
        append("SELECT * FROM `").append(table.name).append('`')
        if (table.where != null) append(" WHERE ").append(table.where)
        append(" ORDER BY rowid")
    }
    val out = JSONArray()
    db.query(sql).use { c ->
        while (c.moveToNext()) {
            val row = JSONObject()
            for (i in 0 until c.columnCount) {
                val name = c.getColumnName(i)
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                    Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                    Cursor.FIELD_TYPE_STRING -> row.put(name, c.getString(i))
                    else -> error("blob column ${table.name}.$name is not in schema v1")
                }
            }
            out.put(row)
        }
    }
    return out
}

internal fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
    db.query("PRAGMA table_info(`$table`)").use { c ->
        val idx = c.getColumnIndexOrThrow("name")
        buildSet { while (c.moveToNext()) add(c.getString(idx)) }
    }

/** Inserts rows; any column this schema does not know fails the restore (caller's transaction rolls back). */
internal fun insertRows(db: SupportSQLiteDatabase, table: String, rows: JSONArray, known: Set<String>) {
    for (i in 0 until rows.length()) {
        val o = rows.getJSONObject(i)
        val cv = ContentValues()
        for (key in o.keys()) {
            require(key in known) { "unknown column $table.$key" }
            when (val v = o.get(key)) {
                JSONObject.NULL -> cv.putNull(key)
                is Int -> cv.put(key, v.toLong())
                is Long -> cv.put(key, v)
                is Double -> cv.put(key, v)
                is Number -> cv.put(key, v.toDouble())
                is Boolean -> cv.put(key, if (v) 1L else 0L)
                is String -> cv.put(key, v)
                else -> throw IllegalArgumentException("bad value in $table.$key")
            }
        }
        // ABORT: a clash or a NOT NULL miss throws, never silently drops a row.
        db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
    }
}

internal fun deleteRows(db: SupportSQLiteDatabase, table: BackupTable) {
    db.delete(table.name, table.where, null)
}
