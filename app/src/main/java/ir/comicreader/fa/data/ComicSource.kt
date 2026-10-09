package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind

/** Ordered set of pages for one comic, produced lazily as bitmaps. */
interface ComicSource {
    val item: ComicItem
    val pageNames: List<String>
    val pageCount: Int get() = pageNames.size

    /** Renders/decodes page [index]; the long edge should be about [maxDim]. */
    suspend fun pageBitmap(index: Int, maxDim: Int, minWidthPx: Int = 0): Bitmap?

    /** Raw encoded bytes of a page, when the source can provide them (for region decode). */
    suspend fun pageBytes(index: Int): ByteArray? = null

    /** Just the first [limit] bytes of a page (enough for a size probe), when possible. */
    suspend fun pageHead(index: Int, limit: Int): ByteArray? = null

    /** Whether region/tile decoding is available for this source. */
    val supportsRegion: Boolean get() = false

    fun close()
}

object ComicSourceFactory {
    fun open(context: Context, item: ComicItem): ComicSource = when (item.kind) {
        ComicKind.ZIP -> ZipComicSource(context, item)
        ComicKind.RAR -> RarComicSource(context, item)
        ComicKind.PDF -> PdfComicSource(context, item)
        ComicKind.MHTML -> MhtmlComicSource(context, item)
        ComicKind.FOLDER -> FolderComicSource(context, item)
    }
}

internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

internal fun isImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

/** Reads up to [limit] bytes without relying on InputStream.readNBytes (API 33). */
internal fun readPrefix(stream: java.io.InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var offset = 0
    while (offset < limit) {
        val read = stream.read(buffer, offset, limit - offset)
        if (read <= 0) break
        offset += read
    }
    return if (offset == limit) buffer else buffer.copyOf(offset)
}

/** Maps a file name to a supported comic kind, or null when unsupported. */
fun kindForName(name: String): ComicKind? = when (name.substringAfterLast('.', "").lowercase()) {
    "cbz", "zip" -> ComicKind.ZIP
    "cbr", "rar" -> ComicKind.RAR
    "pdf" -> ComicKind.PDF
    "mht", "mhtml" -> ComicKind.MHTML
    else -> null
}
