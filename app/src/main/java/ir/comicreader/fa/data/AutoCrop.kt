package ir.comicreader.fa.data

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

/**
 * Auto-crop: finds the page's content box by scanning the borders.
 *
 * A row/column counts as background when it is (almost) entirely the border colour. That
 * tolerates JPEG noise and scan specks far better than hunting for the first
 * non-background pixel (which a single speck defeats).
 */
object AutoCrop {

    private const val ANALYSIS_MAX = 320
    private const val TOLERANCE = 28
    private const val COVERAGE = 0.985f
    private const val MAX_REMOVAL = 0.35f

    fun crop(source: Bitmap, tolerance: Int = TOLERANCE, coverage: Float = COVERAGE): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 32 || h < 32) return source

        val scale = ANALYSIS_MAX.toFloat() / max(w, h)
        val sw = max(16, (w * scale).toInt())
        val sh = max(16, (h * scale).toInt())

        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small !== source) small.recycle()

        val bg = borderColor(pixels, sw, sh)

        fun isBg(p: Int): Boolean =
            abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) <= tolerance &&
                abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) <= tolerance &&
                abs((p and 0xFF) - (bg and 0xFF)) <= tolerance

        fun rowIsBg(y: Int): Boolean {
            val base = y * sw
            var count = 0
            for (x in 0 until sw) if (isBg(pixels[base + x])) count++
            return count >= sw * coverage
        }

        fun colIsBg(x: Int): Boolean {
            var count = 0
            for (y in 0 until sh) if (isBg(pixels[y * sw + x])) count++
            return count >= sh * coverage
        }

        var top = 0
        while (top < sh && rowIsBg(top)) top++
        var bottom = sh - 1
        while (bottom > top && rowIsBg(bottom)) bottom--
        var left = 0
        while (left < sw && colIsBg(left)) left++
        var right = sw - 1
        while (right > left && colIsBg(right)) right--

        if (top >= bottom || left >= right) return source

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val l = (left * fx).toInt().coerceIn(0, w - 1)
        val t = (top * fy).toInt().coerceIn(0, h - 1)
        val r = ((right + 1) * fx).toInt().coerceIn(l + 1, w)
        val b = ((bottom + 1) * fy).toInt().coerceIn(t + 1, h)

        // Ignore implausible results (would drop more than a third of a side).
        if (r - l < w * (1f - MAX_REMOVAL) || b - t < h * (1f - MAX_REMOVAL)) return source
        if (r - l >= w - 2 && b - t >= h - 2) return source

        return Bitmap.createBitmap(source, l, t, r - l, b - t)
    }

    /** Median colour of the border pixels (robust when content touches an edge). */
    private fun borderColor(pixels: IntArray, w: Int, h: Int): Int {
        val count = 2 * (w + h)
        val rs = IntArray(count)
        val gs = IntArray(count)
        val bs = IntArray(count)
        var i = 0
        fun add(p: Int) {
            rs[i] = (p shr 16) and 0xFF
            gs[i] = (p shr 8) and 0xFF
            bs[i] = p and 0xFF
            i++
        }
        for (x in 0 until w) {
            add(pixels[x])
            add(pixels[(h - 1) * w + x])
        }
        for (y in 0 until h) {
            add(pixels[y * w])
            add(pixels[y * w + w - 1])
        }
        rs.sort()
        gs.sort()
        bs.sort()
        val mid = rs.size / 2
        return (0xFF shl 24) or (rs[mid] shl 16) or (gs[mid] shl 8) or bs[mid]
    }
}
