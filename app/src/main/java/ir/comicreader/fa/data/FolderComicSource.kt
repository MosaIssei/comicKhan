package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads pages from a folder of loose image files (SAF directory Uri). */
class FolderComicSource(
    private val context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val pages: List<Uri> = Saf
        .listChildrenOf(context.contentResolver, item.uri)
        .filter { !it.isDir && isImageName(it.name) }
        .sortedWith(compareBy(Ordering.Natural) { it.name })
        .map { it.uri }

    override val pageNames: List<String> = pages.map { it.lastPathSegment ?: "" }

    override suspend fun pageBitmap(index: Int, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(pages[index])?.use { it.readBytes() }
            ?: return@withContext null
        decodeImageBytes(bytes, maxDim)
    }

    override val supportsRegion: Boolean = true

    override suspend fun pageBytes(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(pages[index])?.use { it.readBytes() }
    }

    override fun close() = Unit
}
