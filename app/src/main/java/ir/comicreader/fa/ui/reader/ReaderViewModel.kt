package ir.comicreader.fa.ui.reader

import android.app.Application
import android.graphics.BitmapRegionDecoder
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
import androidx.lifecycle.viewModelScope
import ir.comicreader.fa.data.ComicSource
import ir.comicreader.fa.data.ComicSourceFactory
import ir.comicreader.fa.data.RegionDecode
import ir.comicreader.fa.data.model.ComicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ir.comicreader.fa.data.Prefs(app)
    private var source: ComicSource? = null
    private val cache = LruCache<String, ImageBitmap>(2)
    private val regionCache = LruCache<String, ImageBitmap>(6)
    private val bytesCache = LruCache<Int, ByteArray>(2)
    private val decoderLock = Mutex()
    private val decoderCache = object : LruCache<Int, BitmapRegionDecoder>(1) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: BitmapRegionDecoder, newValue: BitmapRegionDecoder?) {
            runCatching { oldValue.recycle() }
        }
    }

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
            decoderCache.evictAll()
        }
    }

    /** Intrinsic size of a page, for region math. Never throws. */
    suspend fun pageSize(index: Int): IntSize? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = pageBytes(index) ?: return@runCatching null
            RegionDecode.size(bytes)?.let { IntSize(it.width, it.height) }
        }.getOrNull()
    }

    /** Decodes just [rect] of a page at native resolution (downsampled by [sample]). */
    suspend fun pageRegion(index: Int, rect: Rect, sample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = index.toString() + ":" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + ":" + sample
        regionCache.get(key)?.let { return@withContext it }
        val bytes = runCatching { pageBytes(index) }.getOrNull() ?: return@withContext null
        val bitmap = decoderLock.withLock {
            val decoder = decoderCache.get(index)
                ?: RegionDecode.newDecoder(bytes)?.also { decoderCache.put(index, it) }
                ?: return@withContext null
            runCatching { RegionDecode.decode(decoder, rect, sample) }.getOrNull()
        } ?: return@withContext null
        val image = bitmap.asImageBitmap()
        regionCache.put(key, image)
        image
    }

    private suspend fun pageBytes(index: Int): ByteArray? {
        bytesCache.get(index)?.let { return it }
        val bytes = runCatching { source?.pageBytes(index) }.getOrNull() ?: return null
        bytesCache.put(index, bytes)
        return bytes
    }

    fun open(item: ComicItem) {
        close()
        viewModelScope.launch {
            // Opening (copying archives, parsing MHTML) can be heavy: do it off the main
            // thread, including the first page-count read which may trigger a lazy parse.
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val src = ComicSourceFactory.open(getApplication(), item)
                    src to src.pageCount
                }
            }
            opened.onSuccess { (src, count) ->
                source = src
                title = item.name
                uri = item.uri.toString()
                pageCount = count
                error = if (count == 0) "صفحه‌ای یافت نشد" else null
                prefs.setLastOpened(item.uri.toString(), System.currentTimeMillis())
                prefs.setTotalPages(item.uri.toString(), count)
            }.onFailure { e -> error = e.message ?: "خطا در باز کردن فایل" }
        }
    }

    /**
     * Decodes a page sized for what is actually on screen: [requiredPx] is the wanted
     * length of the long edge (already bucketed). Decoding to the display size keeps
     * zoomed-in pages sharp (up to the source resolution) instead of a fixed cap.
     */
    suspend fun loadPage(index: Int, requiredPx: Int, minWidthPx: Int = 0): ImageBitmap? = withContext(Dispatchers.IO) {
        val target = requiredPx.coerceIn(512, MAX_DIM)
        val key = index.toString() + "@" + target + "#" + minWidthPx
        cache.get(key)?.let { return@withContext it }
        val src = source ?: return@withContext null
        val bitmap = runCatching { src.pageBitmap(index, target, minWidthPx) }.getOrNull()
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
        decoderCache.evictAll()
    }

    override fun onCleared() {
        close()
    }

    companion object {
        const val MAX_DIM = 4096
    }
}
