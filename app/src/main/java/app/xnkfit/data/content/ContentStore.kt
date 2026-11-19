package app.xnkfit.data.content

import androidx.room.withTransaction
import app.xnkfit.data.db.ContentMetaEntity
import app.xnkfit.data.db.XnkDatabase
import org.json.JSONObject

/**
 * Writes parsed content into Room. Everything (rows of every kind, the version, the file hashes) goes in
 * ONE transaction, so a failure anywhere leaves the old content exactly as it was (PV-2, Content.md).
 */
class ContentStore(private val db: XnkDatabase, private val now: () -> Long = System::currentTimeMillis) {

    suspend fun meta(): ContentMetaEntity? = db.contentDao().meta()

    /** Installed hashes: file name to SHA-256 hex. */
    suspend fun installedHashes(): Map<String, String> = parseHashes(meta()?.filesJson)

    /**
     * Replaces the rows of each parsed file and records [version], [source] and the [hashes] of the bytes.
     * Hashes of files not in [parsed] are kept. [checkedAt] is stored when this came from a verified manifest.
     */
    suspend fun apply(parsed: List<ParsedFile>, version: Int, source: String, hashes: Map<String, String>, checkedAt: Long? = null) {
        val dao = db.contentDao()
        db.withTransaction {
            val t = now()
            for (p in parsed) {
                when (p.file) {
                    ContentFile.EXERCISES -> { dao.deleteExercises(); dao.insertExercises(p.exercises) }
                    ContentFile.MEAL_PACKAGES -> { dao.deleteMealPackages(); dao.insertMealPackages(p.mealPackages) }
                    else -> if (p.file.idPrefix != null) {
                        dao.deleteFoodsWithPrefix(p.file.idPrefix)
                        dao.insertFoods(p.foods)
                    }
                }
                p.docs.forEach { dao.upsertDoc(it) }
            }
            val old = dao.meta()
            val merged = parseHashes(old?.filesJson).toMutableMap().apply { putAll(hashes) }
            dao.upsertMeta(
                ContentMetaEntity(
                    createdAt = old?.createdAt ?: t, updatedAt = t,
                    contentVersion = version, source = source,
                    filesJson = JSONObject(merged.toSortedMap() as Map<*, *>).toString(),
                    lastCheckAt = checkedAt ?: old?.lastCheckAt,
                    lastAttemptAt = old?.lastAttemptAt,
                ),
            )
        }
    }

    /** Records the start of a check (6 h rule) and, when [checked], a completed one (24 h rule). Never touches content. */
    suspend fun recordCheck(attemptAt: Long?, checkedAt: Long?) {
        val dao = db.contentDao()
        db.withTransaction {
            val old = dao.meta() ?: return@withTransaction // no content installed yet: nothing to throttle
            dao.upsertMeta(
                old.copy(
                    lastAttemptAt = attemptAt ?: old.lastAttemptAt,
                    lastCheckAt = checkedAt ?: old.lastCheckAt,
                ),
            )
        }
    }

    companion object {
        fun parseHashes(json: String?): Map<String, String> {
            if (json.isNullOrEmpty()) return emptyMap()
            val o = JSONObject(json)
            return o.keys().asSequence().associateWith { o.getString(it) }
        }
    }
}
