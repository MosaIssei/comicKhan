package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pages of an MHTML/MHT file are the images embedded in it.
 *
 * The file is read and parsed lazily on first use; [ReaderViewModel.open] builds sources on
 * a background dispatcher, so this never runs on the main thread.
 */
class MhtmlComicSource(
    private val context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val pages: List<ByteArray> by lazy {
        runCatching {
            val bytes = context.contentResolver.openInputStream(item.uri)
                ?.use { it.readBytes() } ?: ByteArray(0)
            Mhtml.extractImages(bytes)
        }.getOrDefault(emptyList())
    }

    private val names: List<String> by lazy { List(pages.size) { "صفحهٔ ${it + 1}" } }

    override val pageNames: List<String> get() = names

    override val supportsRegion: Boolean = true

    override suspend fun pageBytes(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        pages.getOrNull(index)
    }

    override suspend fun pageBitmap(index: Int, maxDim: Int, minWidthPx: Int): Bitmap? =
        pageBytes(index)?.let { runCatching { decodeImageBytes(it, maxDim, minWidthPx) }.getOrNull() }

    override fun close() = Unit
}
