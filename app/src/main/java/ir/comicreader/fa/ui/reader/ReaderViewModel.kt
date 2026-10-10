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
    private val cache = LruCache<String, ImageBitmap>(12)
    private val regionCache = LruCache<String, ImageBitmap>(6)
    private val bytesCache = LruCache<Int, ByteArray>(4)
    private val loading = java.util.Collections.synchronizedSet(HashSet<String>())
    private var session = 0

    /** Bumped whenever a page finishes decoding, so the UI can pick it up. */
    var revision by mutableIntStateOf(0)
        private set

    /** Bumped on every successful open, so the UI re-requests pages after a reopen. */
    var generation by mutableIntStateOf(0)
        private set

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
            revision++
        }
    }

    fun open(item: ComicItem) {
        close()
        session++
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
                generation++
            }.onFailure { e -> error = e.message ?: "خطا در باز کردن فایل" }
        }
    }

    // ---- page bitmaps ----------------------------------------------------------

    private fun pageKey(index: Int, requiredPx: Int, minWidthPx: Int, crop: Boolean) =
        index.toString() + "@" + requiredPx + "#" + minWidthPx + "#" + (if (crop) 1 else 0)

    fun cachedPage(index: Int, requiredPx: Int, minWidthPx: Int): ImageBitmap? =
        cache.get(pageKey(index, requiredPx, minWidthPx, autoCrop))

    /**
     * Starts decoding a page in the ViewModel scope (so it is not cancelled when a list item
     * scrolls out of composition). The result shows up via [revision] + [cachedPage].
     */
    fun requestPage(index: Int, requiredPx: Int, minWidthPx: Int) {
        val crop = autoCrop
        val key = pageKey(index, requiredPx, minWidthPx, crop)
        if (cache.get(key) != null || !loading.add(key)) return
        val token = session
        viewModelScope.launch {
            val bitmap = decodePage(index, requiredPx, minWidthPx, crop)
            loading.remove(key)
            if (token == session && bitmap != null) {
                cache.put(key, bitmap)
                revision++
            }
        }
    }

    private suspend fun decodePage(index: Int, requiredPx: Int, minWidthPx: Int, crop: Boolean): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val target = requiredPx.coerceIn(512, MAX_DIM)
            val src = source ?: return@withContext null
            val bitmap = runCatching { src.pageBitmap(index, target, minWidthPx) }.getOrNull()
                ?: return@withContext null
            val ready = if (crop) {
                runCatching { ir.comicreader.fa.data.AutoCrop.crop(bitmap) }.getOrDefault(bitmap)
            } else {
                bitmap
            }
            ready.asImageBitmap()
        }

    // ---- region (tile) decoding ------------------------------------------------

    /** Intrinsic size of a page, for region math. Never throws. */
    suspend fun pageSize(index: Int): IntSize? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = pageBytes(index) ?: return@runCatching null
            RegionDecode.size(bytes)?.let { IntSize(it.width, it.height) }
        }.getOrNull()
    }

    /**
     * Aspect (width / height) of what will actually be shown for a page — the page's own
     * aspect, or the post-crop aspect when auto-crop is on. Never throws.
     */
    suspend fun displayAspect(index: Int): Float? = withContext(Dispatchers.IO) {
        runCatching {
            if (autoCrop) {
                val bitmap = source?.pageBitmap(index, 400, 0) ?: return@runCatching null
                val shown = runCatching { ir.comicreader.fa.data.AutoCrop.crop(bitmap) }.getOrDefault(bitmap)
                if (shown.height > 0) shown.width.toFloat() / shown.height else null
            } else {
                val bytes = source?.pageHead(index, 128 * 1024) ?: source?.pageBytes(index)
                val size = bytes?.let { RegionDecode.size(it) }
                if (size != null && size.height > 0) size.width.toFloat() / size.height else null
            }
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

    fun close() {
        session++
        source?.close()
        source = null
        // Reset published state so a re-open composes only once the new source is ready
        // (otherwise the UI would request pages against a closed source and get stuck).
        title = ""
        uri = ""
        pageCount = 0
        error = null
        cache.evictAll()
        regionCache.evictAll()
        bytesCache.evictAll()
        decoderCache.evictAll()
        loading.clear()
    }

    override fun onCleared() {
        close()
    }

    companion object {
        const val MAX_DIM = 4096
    }
}
