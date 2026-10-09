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
 * RAR5 archives are detected up front and rejected with a clear message, since
 * junrar cannot read them.
 */
class RarComicSource(
    context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val lock = Mutex()
    private val localFile: File = Cache.copyToCache(context, item.uri, "rar")
    private val archive: Archive = run {
        rejectRar5(localFile)
        Archive(localFile)
    }

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
