package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/** Hard ceiling on a decoded page's long edge (for the paged/whole-page path). */
private const val HARD_MAX = 8192

/** Hard ceiling on total decoded pixels, to bound memory. */
private const val MAX_PIXELS = 24_000_000L

/**
 * Decodes image bytes sized for what is on screen.
 *
 * - Paged reading passes [minWidthPx] = 0: the long edge is trimmed to about [maxDim].
 * - Continuous (webtoon) reading passes the on-screen width as [minWidthPx]: then the
 *   width is never shrunk below it and the long-edge cap is skipped, so a tall strip is
 *   decoded at its native width (sharp) rather than downsampled in width and upscaled.
 * In both cases total pixels are capped to bound memory.
 */
internal fun decodeImageBytes(bytes: ByteArray, maxDim: Int, minWidthPx: Int = 0): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val target = maxDim.coerceIn(1, HARD_MAX)
    val longEdge = max(bounds.outWidth, bounds.outHeight)

    var sample = 1
    while (longEdge / (sample * 2) >= target) sample *= 2

    if (minWidthPx > 0) {
        while (sample > 1 && bounds.outWidth / sample < minWidthPx) sample /= 2
    } else {
        while (longEdge / sample > HARD_MAX) sample *= 2
    }

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
