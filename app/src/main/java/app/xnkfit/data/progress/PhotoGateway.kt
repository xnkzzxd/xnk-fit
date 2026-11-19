package app.xnkfit.data.progress

import app.xnkfit.data.db.PhotoEntity
import app.xnkfit.data.db.XnkDatabase
import java.io.File

/** What the Saya screen needs from the photo folder and the `photo` table (PV-3). */
interface PhotoGateway {
    suspend fun list(): List<PhotoEntity>

    /** Moves a camera picture into private storage and adds the row. False when the picture cannot be read. */
    suspend fun add(captured: File, id: String, kind: String): Boolean

    suspend fun delete(id: String)

    fun captureFile(id: String): File
}

class RoomPhotoGateway(
    private val db: XnkDatabase,
    private val store: PhotoStore,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoGateway {
    private val dao get() = db.progressDao()

    override suspend fun list() = dao.photos()

    override suspend fun add(captured: File, id: String, kind: String): Boolean {
        val stored = store.import(captured, id)
        captured.delete()
        if (stored == null) return false
        val t = now()
        // hiddenFromBackup stays true: photos are left out of every backup unless photo backup is on (B-16).
        dao.upsertPhoto(PhotoEntity(id, t, t, stored.path, t, kind, hiddenFromBackup = true))
        return true
    }

    override suspend fun delete(id: String) {
        val row = dao.photo(id) ?: return
        store.delete(row.path)
        dao.deletePhoto(id)
    }

    override fun captureFile(id: String): File = store.captureFile(id)
}
