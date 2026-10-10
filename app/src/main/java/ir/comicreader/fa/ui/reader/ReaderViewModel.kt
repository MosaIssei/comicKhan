package ir.comicreader.fa.ui.reader

import android.app.Application
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.comicreader.fa.data.AutoCrop
import ir.comicreader.fa.data.ComicSource
import ir.comicreader.fa.data.ComicSourceFactory
import ir.comicreader.fa.data.RegionDecode
import ir.comicreader.fa.data.decodeImageBytes
import ir.comicreader.fa.data.model.ComicItem
import kotlin.math.max
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

    /**
     * Crop box per page, in the page's own pixels. Analysing a page is not free, and both the
     * webtoon aspect pass and the decode itself need the answer: computing it twice is what
     * made auto-crop slow, and any disagreement between the two made the page's layout box and
     * its bitmap differ (which clips artwork off the top and bottom).
     */
    private val cropRectCache = LruCache<String, Rect>(64)

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

    /** Where reading stopped last time, read as part of [open] so the UI cannot race it. */
    var startPage by mutableIntStateOf(0)
        private set
    var startOffset by mutableFloatStateOf(0f)
        private set

    fun applyAutoCrop(enabled: Boolean) {
        if (autoCrop != enabled) {
            autoCrop = enabled
            cache.evictAll()
            regionCache.evictAll()
            decoderCache.evictAll()
            cropRectCache.evictAll()
            revision++
        }
    }

    /** How much of a detected crop margin to keep, in percent (see [Prefs.cropPadH]). */
    var cropPadH by mutableIntStateOf(prefs.cropPadH)
        private set
    var cropPadV by mutableIntStateOf(prefs.cropPadV)
        private set

    fun applyCropPad(horizontal: Int, vertical: Int) {
        if (cropPadH != horizontal || cropPadV != vertical) {
            cropPadH = horizontal
            cropPadV = vertical
            prefs.cropPadH = horizontal
            prefs.cropPadV = vertical
            cache.evictAll()
            regionCache.evictAll()
            decoderCache.evictAll()
            cropRectCache.evictAll()
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
                // Set before pageCount: the UI resumes as soon as it sees a page count, so
                // the restore target has to already be there or it would seek to page 0.
                startPage = prefs.lastPage(item.uri.toString())
                startOffset = prefs.lastPageOffset(item.uri.toString())
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
            if (crop) {
                croppedRegion(index, target, minWidthPx)?.let { return@withContext it }
            }
            val bitmap = runCatching { src.pageBitmap(index, target, minWidthPx) }.getOrNull()
                ?: return@withContext null
            bitmap.asImageBitmap()
        }

    /**
     * Decodes only the crop box, at the resolution it will be shown at, instead of decoding the
     * whole page and then copying the box out of it. For a tall strip that is the difference
     * between a few million pixels and twenty, and it guarantees the bitmap matches the box the
     * list laid out from [cropRectFor].
     */
    private suspend fun croppedRegion(index: Int, target: Int, minWidthPx: Int): ImageBitmap? {
        val rect = cropRectFor(index) ?: return null
        val bytes = pageBytes(index) ?: return null
        val decoder = runCatching { RegionDecode.newDecoder(bytes) }.getOrNull() ?: return null
        return try {
            val sample = regionSample(rect.width(), rect.height(), target, minWidthPx)
            runCatching { RegionDecode.decode(decoder, rect, sample) }.getOrNull()?.asImageBitmap()
        } finally {
            runCatching { decoder.recycle() }
        }
    }

    /** Mirrors [decodeImageBytes]' sampling, but for a sub-rectangle of the page. */
    private fun regionSample(w: Int, h: Int, maxDim: Int, minWidthPx: Int): Int {
        val longEdge = max(w, h)
        var sample = 1
        while (longEdge / (sample * 2) >= maxDim) sample *= 2
        if (minWidthPx > 0) {
            while (sample > 1 && w / sample < minWidthPx) sample /= 2
        }
        while ((w / sample).toLong() * (h / sample).toLong() > MAX_PIXELS) sample *= 2
        return sample
    }

    /**
     * The crop box for a page, in the page's own pixels, analysed once and cached. Returns null
     * when the page should not be cropped. Never throws.
     */
    suspend fun cropRectFor(index: Int): Rect? = withContext(Dispatchers.IO) {
        val key = index.toString() + "@" + cropPadH + "x" + cropPadV
        cropRectCache.get(key)?.let { return@withContext it }
        runCatching {
            val bytes = pageBytes(index) ?: return@runCatching null
            val size = RegionDecode.size(bytes) ?: return@runCatching null
            val small = decodeImageBytes(bytes, ANALYSIS_DIM, 0) ?: return@runCatching null
            val analysed = AutoCrop.cropRect(small, cropPadH / 100f, cropPadV / 100f)
                ?: return@runCatching null
            val fx = size.width.toFloat() / small.width
            val fy = size.height.toFloat() / small.height
            Rect(
                (analysed.left * fx).toInt().coerceIn(0, size.width - 1),
                (analysed.top * fy).toInt().coerceIn(0, size.height - 1),
                (analysed.right * fx).toInt().coerceIn(1, size.width),
                (analysed.bottom * fy).toInt().coerceIn(1, size.height),
            )
        }.getOrNull()?.also { cropRectCache.put(key, it) }
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
     * aspect, or the post-crop aspect when auto-crop is on.
     *
     * The webtoon list sizes every item from this value before any bitmap exists, so a null
     * here (which the caller turns into a guess) would make that item's height wrong and
     * shift everything when you scroll back up into it. The header read is therefore backed
     * by a small real decode, and only genuinely broken pages come back null. Never throws.
     */
    suspend fun displayAspect(index: Int): Float? = withContext(Dispatchers.IO) {
        runCatching {
            if (autoCrop) {
                // Same cached box the decode uses, so the item the list lays out and the bitmap
                // drawn into it always agree.
                val rect = cropRectFor(index) ?: return@runCatching null
                if (rect.height() > 0) rect.width().toFloat() / rect.height() else null
            } else {
                val header = runCatching { source?.pageHead(index, 128 * 1024) }.getOrNull()
                val size = header?.let { RegionDecode.size(it) }
                    ?: source?.pageBitmap(index, 256, 0)?.let { android.util.Size(it.width, it.height) }
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
        startPage = 0
        startOffset = 0f
        cache.evictAll()
        regionCache.evictAll()
        bytesCache.evictAll()
        decoderCache.evictAll()
        cropRectCache.evictAll()
        loading.clear()
    }

    override fun onCleared() {
        close()
    }

    companion object {
        const val MAX_DIM = 4096

        /** Size the crop box is measured on: big enough to see margins, small enough to be cheap. */
        private const val ANALYSIS_DIM = 480

        private const val MAX_PIXELS = 24_000_000L
    }
}
