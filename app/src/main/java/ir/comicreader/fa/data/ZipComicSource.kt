package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Reads pages from a ZIP/CBZ archive (copied to cache for random access). */
class ZipComicSource(
    context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val lock = Mutex()
    private val localFile: File = Cache.copyToCache(context, item.uri, "zip")
    private val zip: ZipFile = ZipFile(localFile)

    override val pageNames: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && isImageName(it.name) }
        .map { it.name }
        .sortedWith(Ordering.Natural)
        .toList()

    override suspend fun pageBitmap(index: Int, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val entry = zip.getEntry(pageNames[index]) ?: return@withLock null
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            decodeImageBytes(bytes, maxDim)
        }
    }

    override fun close() {
        runCatching { zip.close() }
        runCatching { localFile.delete() }
    }
}
