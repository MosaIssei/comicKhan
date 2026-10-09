package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal val ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "rar", "cbr")

internal fun isArchiveName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

/** Serves a single page whose bytes are produced lazily by [loader]. */
internal class SinglePageSource(
    override val item: ComicItem,
    pageName: String,
    private val loader: () -> ByteArray,
) : ComicSource {

    override val pageNames: List<String> = listOf(pageName)

    override suspend fun pageBytes(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { loader() }.getOrNull()
    }

    override suspend fun pageBitmap(index: Int, maxDim: Int, minWidthPx: Int): Bitmap? =
        pageBytes(index)?.let { runCatching { decodeImageBytes(it, maxDim, minWidthPx) }.getOrNull() }

    override val supportsRegion: Boolean = true

    override fun close() = Unit
}

/** A container whose pages are the concatenation of several child sources (nested archives). */
internal class NestedArchiveSource(
    override val item: ComicItem,
    private val children: List<ComicSource>,
    private val tempFiles: List<File>,
) : ComicSource {

    private val starts = IntArray(children.size + 1)

    init {
        var sum = 0
        for (i in children.indices) {
            starts[i] = sum
            sum += children[i].pageCount
        }
        starts[children.size] = sum
    }

    override val pageNames: List<String> = children.flatMap { it.pageNames }

    private fun childAt(index: Int): Pair<ComicSource, Int>? {
        for (i in children.indices) {
            if (index < starts[i + 1]) return children[i] to (index - starts[i])
        }
        return null
    }

    override suspend fun pageBitmap(index: Int, maxDim: Int, minWidthPx: Int): Bitmap? =
        childAt(index)?.let { (child, local) -> child.pageBitmap(local, maxDim, minWidthPx) }

    override suspend fun pageBytes(index: Int): ByteArray? =
        childAt(index)?.let { (child, local) -> child.pageBytes(local) }

    override val supportsRegion: Boolean
        get() = children.isNotEmpty() && children.all { it.supportsRegion }

    override fun close() {
        children.forEach { runCatching { it.close() } }
        tempFiles.forEach { runCatching { it.delete() } }
    }
}

/** Opens a nested archive file, choosing ZIP or RAR by its extension. */
internal fun childSource(context: Context, item: ComicItem, file: File): ComicSource =
    when (item.name.substringAfterLast('.', "").lowercase()) {
        "rar", "cbr" -> RarComicSource(context, item, file)
        else -> ZipComicSource(context, item, file)
    }

internal fun nestedItem(name: String, file: File): ComicItem = ComicItem(
    name = name,
    uri = Uri.fromFile(file),
    kind = if (name.substringAfterLast('.', "").lowercase() in setOf("rar", "cbr")) ComicKind.RAR else ComicKind.ZIP,
)
