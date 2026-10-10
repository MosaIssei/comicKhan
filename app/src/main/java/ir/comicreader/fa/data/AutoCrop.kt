package ir.comicreader.fa.data

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Auto-crop: finds the page's content box by trimming uniform margins from each side.
 *
 * Each side gets its own background colour (taken from that side's border band), so a page
 * whose top margin is white but bottom margin carries scanner shadow is still cropped on
 * both. A row/column counts as margin when it is (almost) entirely its side's colour, which
 * tolerates JPEG noise far better than looking for the first non-background pixel.
 */
object AutoCrop {

    private const val SHORT_MIN = 150
    private const val LONG_MAX = 480
    private const val TOLERANCE = 30
    private const val COVERAGE = 0.96f
    private const val MAX_REMOVAL = 0.6f

    fun crop(source: Bitmap, tolerance: Int = TOLERANCE, coverage: Float = COVERAGE): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 32 || h < 32) return source

        var scale = SHORT_MIN.toFloat() / min(w, h)
        if (max(w, h) * scale > LONG_MAX) scale = LONG_MAX.toFloat() / max(w, h)
        val sw = max(16, (w * scale).toInt())
        val sh = max(16, (h * scale).toInt())

        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== source) small.recycle()

        val topBg = medianColor(px, sw, sh, 0)
        val bottomBg = medianColor(px, sw, sh, 1)
        val leftBg = medianColor(px, sw, sh, 2)
        val rightBg = medianColor(px, sw, sh, 3)

        fun close(p: Int, c: Int) =
            abs(((p shr 16) and 0xFF) - ((c shr 16) and 0xFF)) <= tolerance &&
                abs(((p shr 8) and 0xFF) - ((c shr 8) and 0xFF)) <= tolerance &&
                abs((p and 0xFF) - (c and 0xFF)) <= tolerance

        fun rowIsBg(y: Int, c: Int): Boolean {
            val base = y * sw
            var n = 0
            for (x in 0 until sw) if (close(px[base + x], c)) n++
            return n >= sw * coverage
        }

        fun colIsBg(x: Int, c: Int): Boolean {
            var n = 0
            for (y in 0 until sh) if (close(px[y * sw + x], c)) n++
            return n >= sh * coverage
        }

        var top = 0
        while (top < sh && rowIsBg(top, topBg)) top++
        var bottom = sh - 1
        while (bottom > top && rowIsBg(bottom, bottomBg)) bottom--
        var left = 0
        while (left < sw && colIsBg(left, leftBg)) left++
        var right = sw - 1
        while (right > left && colIsBg(right, rightBg)) right--

        if (top >= bottom || left >= right) return source

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val l = (left * fx).toInt().coerceIn(0, w - 1)
        val t = (top * fy).toInt().coerceIn(0, h - 1)
        val r = ((right + 1) * fx).toInt().coerceIn(l + 1, w)
        val b = ((bottom + 1) * fy).toInt().coerceIn(t + 1, h)

        // Ignore implausible results (would drop too much of a side).
        if (r - l < w * (1f - MAX_REMOVAL) || b - t < h * (1f - MAX_REMOVAL)) return source
        if (r - l >= w - 2 && b - t >= h - 2) return source

        return Bitmap.createBitmap(source, l, t, r - l, b - t)
    }

    /** Median colour of one border band: 0 = top, 1 = bottom, 2 = left, 3 = right. */
    private fun medianColor(px: IntArray, w: Int, h: Int, side: Int): Int {
        val band = 3
        val vals = ArrayList<Int>()
        when (side) {
            0 -> for (y in 0 until min(band, h)) for (x in 0 until w) vals.add(px[y * w + x])
            1 -> for (y in max(0, h - band) until h) for (x in 0 until w) vals.add(px[y * w + x])
            2 -> for (y in 0 until h) for (x in 0 until min(band, w)) vals.add(px[y * w + x])
            else -> for (y in 0 until h) for (x in max(0, w - band) until w) vals.add(px[y * w + x])
        }
        val r = IntArray(vals.size)
        val g = IntArray(vals.size)
        val b = IntArray(vals.size)
        for (i in vals.indices) {
            r[i] = (vals[i] shr 16) and 0xFF
            g[i] = (vals[i] shr 8) and 0xFF
            b[i] = vals[i] and 0xFF
        }
        r.sort()
        g.sort()
        b.sort()
        val m = vals.size / 2
        return (0xFF shl 24) or (r[m] shl 16) or (g[m] shl 8) or b[m]
    }
}
