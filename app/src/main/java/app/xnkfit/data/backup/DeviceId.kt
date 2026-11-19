package app.xnkfit.data.backup

import java.io.File
import java.util.UUID

/**
 * A random id for this install, written once to `noBackupFilesDir/device-id` (B-6). It is not a hardware id and
 * never leaves the phone except inside the user's own backup manifest. "Hapus semua data" removes it (B-7).
 */
class DeviceId(private val dir: File, private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private val file get() = File(dir, FILE_NAME)

    @Synchronized
    fun get(): String {
        val existing = file.takeIf { it.isFile }?.readText()?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val id = newId()
        dir.mkdirs()
        file.writeText(id)
        return id
    }

    companion object {
        const val FILE_NAME = "device-id"
    }
}
