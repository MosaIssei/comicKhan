package ir.comicreader.fa.ui.reader

import android.app.Application
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import ir.comicreader.fa.data.ComicSource
import ir.comicreader.fa.data.ComicSourceFactory
import ir.comicreader.fa.data.RegionDecode
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ir.comicreader.fa.data.Prefs(app)
    private var source: ComicSource? = null
    private val cache = LruCache<String, ImageBitmap>(2)
    private val regionCache = LruCache<String, ImageBitmap>(6)
    private val bytesCache = LruCache<Int, ByteArray>(2)

    /** Region/tile decoding is used unless auto-crop needs the whole page. */
    val useRegionDecoding: Boolean get() = source?.supportsRegion == true && !autoCrop

    var title by mutableStateOf("")
        private set
    var uri by mutableStateOf("")
        private set
    var pageCount by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var autoCrop by mutableStateOf(false)
        private set

    fun applyAutoCrop(enabled: Boolean) {
        if (autoCrop != enabled) {
            autoCrop = enabled
            cache.evictAll()
            regionCache.evictAll()
        }
    }

    /** Intrinsic size of a page, for region math. */
    suspend fun pageSize(index: Int): IntSize? = withContext(Dispatchers.IO) {
        val bytes = pageBytes(index) ?: return@withContext null
        RegionDecode.size(bytes)?.let { IntSize(it.width, it.height) }
    }

    /** Decodes just [rect] of a page at native resolution (downsampled by [sample]). */
    suspend fun pageRegion(index: Int, rect: Rect, sample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = index.toString() + ":" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + ":" + sample
        regionCache.get(key)?.let { return@withContext it }
        val bytes = pageBytes(index) ?: return@withContext null
        val bitmap = runCatching { RegionDecode.region(bytes, rect, sample) }.getOrNull()
            ?: return@withContext null
        val image = bitmap.asImageBitmap()
        regionCache.put(key, image)
        image
    }

    private suspend fun pageBytes(index: Int): ByteArray? {
        bytesCache.get(index)?.let { return it }
        val bytes = source?.pageBytes(index) ?: return null
        bytesCache.put(index, bytes)
        return bytes
    }

    fun open(item: ComicItem) {
        close()
        runCatching { ComicSourceFactory.open(getApplication(), item) }
            .onSuccess { src ->
                source = src
                title = item.name
                uri = item.uri.toString()
                pageCount = src.pageCount
                error = if (src.pageCount == 0) "صفحه‌ای یافت نشد" else null
                prefs.setLastOpened(item.uri.toString(), System.currentTimeMillis())
                prefs.recordOpened(item)
            }
            .onFailure { e -> error = e.message ?: "خطا در باز کردن فایل" }
    }

    /**
     * Decodes a page sized for what is actually on screen: [requiredPx] is the wanted
     * length of the long edge (already bucketed). Decoding to the display size keeps
     * zoomed-in pages sharp (up to the source resolution) instead of a fixed cap.
     */
    suspend fun loadPage(index: Int, requiredPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val target = requiredPx.coerceIn(512, MAX_DIM)
        val key = index.toString() + "@" + target
        cache.get(key)?.let { return@withContext it }
        val src = source ?: return@withContext null
        val bitmap = runCatching { src.pageBitmap(index, target) }.getOrNull()
            ?: return@withContext null
        val ready = if (autoCrop) {
            runCatching { ir.comicreader.fa.data.AutoCrop.crop(bitmap) }.getOrDefault(bitmap)
        } else {
            bitmap
        }
        val image = ready.asImageBitmap()
        cache.put(key, image)
        image
    }

    fun close() {
        source?.close()
        source = null
        cache.evictAll()
        regionCache.evictAll()
        bytesCache.evictAll()
    }

    override fun onCleared() {
        close()
    }

    companion object {
        const val MAX_DIM = 4096
    }
}
