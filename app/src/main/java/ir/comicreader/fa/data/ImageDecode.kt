package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/** Hard ceiling on a decoded page's long edge (webtoon strips can be tall). */
private const val HARD_MAX = 8192

/** Hard ceiling on total decoded pixels, to bound memory. */
private const val MAX_PIXELS = 20_000_000L

/**
 * Decodes image bytes sized for what is on screen.
 *
 * [maxDim] is the wanted long edge. [minWidthPx] (when > 0) prevents the width from
 * being shrunk below that value, which keeps tall webtoon strips from being downsampled
 * in width and then upscaled (blurry) on screen.
 */
internal fun decodeImageBytes(bytes: ByteArray, maxDim: Int, minWidthPx: Int = 0): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val target = maxDim.coerceIn(1, HARD_MAX)
    val longEdge = max(bounds.outWidth, bounds.outHeight)

    var sample = 1
    while (longEdge / (sample * 2) >= target) sample *= 2
    // Never shrink the width below what is displayed.
    if (minWidthPx > 0) {
        while (sample > 1 && bounds.outWidth / sample < minWidthPx) sample /= 2
    }
    while (longEdge / sample > HARD_MAX) sample *= 2
    while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample).toLong() > MAX_PIXELS) {
        sample *= 2
    }

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        @Suppress("DEPRECATION")
        inPreferQualityOverSpeed = true
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
