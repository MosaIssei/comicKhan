package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads pages from a ZIP/CBZ archive (copied to cache for random access).
 *
 * If the archive contains other archives (zip/cbz/rar/cbr) inside, it is treated as a
 * container: the pages of those nested archives are shown in order, one after another.
 */
class ZipComicSource private constructor(
    private val context: Context,
    override val item: ComicItem,
    private val localFile: File,
    private val ownsFile: Boolean,
    allowNested: Boolean,
) : ComicSource {

    constructor(context: Context, item: ComicItem) :
        this(context, item, Cache.copyToCache(context, item.uri, "zip"), true, true)

    internal constructor(context: Context, item: ComicItem, file: File) :
        this(context, item, file, false, false)

    private val zip: ZipFile = ZipFile(localFile)

    private val entries: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory }
        .map { it.name }
        .sortedWith(Ordering.Natural)
        .toList()

    private val imageEntries: List<String> = entries.filter { isImageName(it) }

    private val nested: NestedArchiveSource? =
        if (allowNested && entries.any { isArchiveName(it) }) buildNested() else null

    private fun buildNested(): NestedArchiveSource {
        val children = ArrayList<ComicSource>()
        val temps = ArrayList<File>()

        for (name in entries) {
            when {
                isImageName(name) -> children += SinglePageSource(item, name) {
                    zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
                }

                isArchiveName(name) -> runCatching {
                    val temp = Cache.newTempFile(context, name)
                    zip.getInputStream(zip.getEntry(name)).use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    temps += temp
                    val child = childSource(context, nestedItem(name, temp), temp)
                    if (child.pageCount > 0) children += child
                }
            }
        }
        return NestedArchiveSource(item, children, temps)
    }

    override val pageNames: List<String> get() = nested?.pageNames ?: imageEntries

    override val supportsRegion: Boolean get() = nested?.supportsRegion ?: true

    override suspend fun pageBitmap(index: Int, maxDim: Int, minWidthPx: Int): Bitmap? {
        nested?.let { return it.pageBitmap(index, maxDim, minWidthPx) }
        val bytes = readEntry(index) ?: return null
        return withContext(Dispatchers.IO) { decodeImageBytes(bytes, maxDim, minWidthPx) }
    }

    override suspend fun pageBytes(index: Int): ByteArray? {
        nested?.let { return it.pageBytes(index) }
        return readEntry(index)
    }

    override suspend fun pageHead(index: Int, limit: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val name = imageEntries.getOrNull(index) ?: return@runCatching null
            val entry = zip.getEntry(name) ?: return@runCatching null
            zip.getInputStream(entry).use { readPrefix(it, limit) }
        }.getOrNull()
    }

    /** ZipFile is safe for concurrent reads and each stream is independent. */
    private suspend fun readEntry(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val name = imageEntries.getOrNull(index) ?: return@runCatching null
            val entry = zip.getEntry(name) ?: return@runCatching null
            zip.getInputStream(entry).use { it.readBytes() }
        }.getOrNull()
    }

    override fun close() {
        nested?.close()
        runCatching { zip.close() }
        if (ownsFile) runCatching { localFile.delete() }
    }
}
