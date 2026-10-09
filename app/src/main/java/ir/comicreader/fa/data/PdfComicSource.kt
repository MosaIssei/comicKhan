package ir.comicreader.fa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Renders PDF pages with the platform [PdfRenderer] (copied to cache for a seekable fd). */
class PdfComicSource(
    context: Context,
    override val item: ComicItem,
) : ComicSource {

    private val lock = Mutex()
    private val localFile: File = Cache.copyToCache(context, item.uri, "pdf")
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)

    override val pageNames: List<String> = List(renderer.pageCount) { "صفحهٔ ${it + 1}" }

    override suspend fun pageBitmap(index: Int, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val page = renderer.openPage(index)
            try {
                val srcW = page.width
                val srcH = page.height
                val scale = maxDim.toFloat() / max(srcW, srcH).toFloat()
                val w = max(1, (srcW * scale).toInt())
                val h = max(1, (srcH * scale).toInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { localFile.delete() }
    }
}
