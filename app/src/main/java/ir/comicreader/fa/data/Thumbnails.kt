package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

/**
 * Lightweight cover thumbnails for the library grid.
 *
 * Only folder and ZIP/CBZ sources are read (a single pass, no full copy). RAR/PDF
 * covers are skipped to avoid copying large archives just for a thumbnail.
 */
object Thumbnails {

    private val cache = LruCache<String, ImageBitmap>(80)

    fun clear() = cache.evictAll()

    suspend fun load(context: Context, item: ComicItem, maxDim: Int = 320): ImageBitmap? {
        val key = item.uri.toString()
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            when (item.kind) {
                ComicKind.FOLDER -> fromFolder(context, item, maxDim)
                ComicKind.ZIP -> fromZip(context, item, maxDim)
                else -> null
            }
        } ?: return null

        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun fromFolder(context: Context, item: ComicItem, maxDim: Int): Bitmap? {
        val first = Saf.listChildrenOf(context.contentResolver, item.uri)
            .firstOrNull { !it.isDir && isImageName(it.name) } ?: return null
        val bytes = context.contentResolver.openInputStream(first.uri)?.use { it.readBytes() }
            ?: return null
        return decodeImageBytes(bytes, maxDim)
    }

    private fun fromZip(context: Context, item: ComicItem, maxDim: Int): Bitmap? {
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isImageName(entry.name)) {
                        return decodeImageBytes(zip.readBytes(), maxDim)
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }
}
