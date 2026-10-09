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
 * Reads pages from a RAR/CBR archive using junrar (RAR 1.5–4.x). RAR5 is rejected with a
 * clear message.
 *
 * Like [ZipComicSource], a RAR that contains other archives is treated as a container and
 * their pages are shown in order.
 */
class RarComicSource private constructor(
    private val context: Context,
    override val item: ComicItem,
    private val localFile: File,
    private val ownsFile: Boolean,
    allowNested: Boolean,
) : ComicSource {

    constructor(context: Context, item: ComicItem) :
        this(context, item, Cache.copyToCache(context, item.uri, "rar"), true, true)

    internal constructor(context: Context, item: ComicItem, file: File) :
        this(context, item, file, false, false)

    private val lock = Mutex()
    private val archive: Archive = run {
        rejectRar5(localFile)
        Archive(localFile)
    }

    private val headers: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory }
        .sortedWith(Comparator { a, b -> Ordering.compare(a.fileName ?: "", b.fileName ?: "") })

    private val imageHeaders: List<FileHeader> = headers.filter { isImageName(it.fileName ?: "") }

    private val nested: NestedArchiveSource? =
        if (allowNested && headers.any { isArchiveName(it.fileName ?: "") }) buildNested() else null

    private fun buildNested(): NestedArchiveSource {
        val children = ArrayList<ComicSource>()
        val temps = ArrayList<File>()

        for (header in headers) {
            val name = header.fileName ?: continue
            when {
                isImageName(name) -> children += SinglePageSource(item, name) {
                    archive.getInputStream(header).use { it.readBytes() }
                }

                isArchiveName(name) -> runCatching {
                    val temp = Cache.newTempFile(context, name)
                    archive.getInputStream(header).use { input ->
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

    override val pageNames: List<String> get() = nested?.pageNames ?: imageHeaders.map { it.fileName ?: "" }

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

    /** junrar's Archive is not thread-safe, so entry reads are serialised (decode is not). */
    private suspend fun readEntry(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val header = imageHeaders.getOrNull(index) ?: return@runCatching null
                archive.getInputStream(header).use { it.readBytes() }
            }.getOrNull()
        }
    }

    override fun close() {
        nested?.close()
        runCatching { archive.close() }
        if (ownsFile) runCatching { localFile.delete() }
    }

    private fun rejectRar5(file: File) {
        val header = ByteArray(7)
        val read = file.inputStream().use { it.read(header) }
        val isRar5 = read >= 7 &&
            header[0] == 'R'.code.toByte() && header[1] == 'a'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == '!'.code.toByte() &&
            header[4] == 0x1A.toByte() && header[5] == 0x07.toByte() && header[6] == 0x01.toByte()
        if (isRar5) {
            throw IllegalStateException(
                "این فایل RAR5 است و پشتیبانی نمی‌شود؛ لطفاً با 7-Zip آن را به ZIP/CBZ تبدیل کنید"
            )
        }
    }
}
