package app.xnkfit.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a finished card to the system share sheet (PRD P-6, Design §14). The picture is a PNG in the app's private
 * cache, given out through the FileProvider for that one folder: no storage permission, no upload, no network.
 */
object ShareFiles {
    const val DIR = "share" // text-ok: folder name
    const val FILE_NAME = "xnk-kartu.png" // text-ok: file name
    private const val MIME = "image/png" // text-ok: media type
    private const val PNG_QUALITY = 100

    /** The FileProvider authority declared in the manifest. */
    fun authority(context: Context) = context.packageName + ".captures" // text-ok: authority suffix

    /** Writes [bitmap] over any earlier card, so at most one card file is ever in the cache. */
    fun write(cacheDir: File, bitmap: Bitmap): File {
        val dir = File(cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, FILE_NAME)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        return file
    }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** The chooser intent: ACTION_SEND with a read grant on the one picture. */
    fun chooser(context: Context, file: File, title: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            val u = uri(context, file)
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = android.content.ClipData.newRawUri(null, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
