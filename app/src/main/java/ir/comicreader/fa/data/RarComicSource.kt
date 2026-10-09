package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads pages from a RAR/CBR archive using junrar (RAR 1.5–4.x).
 *
 * Note: RAR5 archives are not supported by junrar; such a file will fail to open
 * and surface as an error in the reader.
 */
class RarComicSource(
    context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val lock = Mutex()
    private val localFile: File = Cache.copyToCache(context, item.uri, "rar")
    private val archive: Archive = Archive(localFile)

    private val headers: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory && isImageName(it.fileName ?: "") }
        .sortedWith(Comparator { a, b -> Ordering.compare(a.fileName ?: "", b.fileName ?: "") })

    override val pageNames: List<String> = headers.map { it.fileName ?: "" }

    override suspend fun pageBitmap(index: Int, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val bytes = archive.getInputStream(headers[index]).use { it.readBytes() }
            decodeImageBytes(bytes, maxDim)
        }
    }

    override fun close() {
        runCatching { archive.close() }
        runCatching { localFile.delete() }
    }
}
