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
    suspend fun pageBitmap(index: Int, maxDim: Int): Bitmap?

    /** Raw encoded bytes of a page, when the source can provide them (for region decode). */
    suspend fun pageBytes(index: Int): ByteArray? = null

    /** Whether region/tile decoding is available for this source. */
    val supportsRegion: Boolean get() = false

    fun close()
}

object ComicSourceFactory {
    fun open(context: Context, item: ComicItem): ComicSource = when (item.kind) {
        ComicKind.ZIP -> ZipComicSource(context, item)
        ComicKind.RAR -> RarComicSource(context, item)
        ComicKind.PDF -> PdfComicSource(context, item)
        ComicKind.FOLDER -> FolderComicSource(context, item)
    }
}

internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

internal fun isImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

/** Maps a file name to a supported comic kind, or null when unsupported. */
fun kindForName(name: String): ComicKind? = when (name.substringAfterLast('.', "").lowercase()) {
    "cbz", "zip" -> ComicKind.ZIP
    "cbr", "rar" -> ComicKind.RAR
    "pdf" -> ComicKind.PDF
    else -> null
}
