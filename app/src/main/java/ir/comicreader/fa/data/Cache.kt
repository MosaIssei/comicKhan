package ir.comicreader.fa.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Archives/PDFs are reached through SAF content Uris, which are not seekable files.
 * Sources that need random access copy the document once into the app cache.
 */
internal object Cache {
    fun copyToCache(context: Context, uri: Uri, prefix: String): File {
        val dir = File(context.cacheDir, "comics").apply { mkdirs() }
        val target = File(dir, prefix + "_" + uri.toString().hashCode().toUInt().toString(16))
        if (target.isFile && target.length() > 0L) return target
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("باز کردن فایل ممکن نشد")
        return target
    }
}
