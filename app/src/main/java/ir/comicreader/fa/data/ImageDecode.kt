package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/** Hard ceiling on a decoded page's long edge, to bound memory. */
private const val HARD_MAX = 4096

/**
 * Decodes image bytes so the long edge is at least [maxDim] (when the source allows),
 * but never larger than [HARD_MAX]. This keeps pages sharp at the requested display
 * size without risking huge allocations.
 */
internal fun decodeImageBytes(bytes: ByteArray, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val target = maxDim.coerceIn(1, HARD_MAX)
    val longEdge = max(bounds.outWidth, bounds.outHeight)

    var sample = 1
    while (longEdge / (sample * 2) >= target) sample *= 2
    while (longEdge / sample > HARD_MAX) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        @Suppress("DEPRECATION")
        inPreferQualityOverSpeed = true
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
