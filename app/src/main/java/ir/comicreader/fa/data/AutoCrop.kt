package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.Rect
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
 *
 * Deliberately conservative, because eating artwork is far worse than leaving a sliver of
 * margin behind: a side is only trimmed when its margin run is at least [MIN_RUN] deep, no
 * side ever loses more than [MAX_REMOVAL] of its dimension, and the box is then pushed back
 * outward by [PAD] so a soft edge can never be shaved.
 */
object AutoCrop {

    private const val SHORT_MIN = 150
    private const val LONG_MAX = 480

    /** Per-channel distance still counted as "the same colour". */
    private const val TOLERANCE = 30

    /** Fraction of a row/column that must match the side colour for it to be margin. */
    private const val COVERAGE = 0.96f

    /** Never trim more than this much of a side. */
    private const val MAX_REMOVAL = 0.35f

    /** Shortest margin, in analysis rows/columns, worth trimming. */
    private const val MIN_RUN = 4

    /** Fraction of each dimension pushed back outward after the trim. */
    private const val PAD = 0.015f

    /**
     * The content box of [source] in its own pixel coordinates, or null when there is nothing
     * worth trimming (or nothing that can be trimmed safely).
     */
    fun cropRect(source: Bitmap): Rect? {
        val w = source.width
        val h = source.height
        if (w < 32 || h < 32) return null

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
            abs(((p shr 16) and 0xFF) - ((c shr 16) and 0xFF)) <= TOLERANCE &&
                abs(((p shr 8) and 0xFF) - ((c shr 8) and 0xFF)) <= TOLERANCE &&
                abs((p and 0xFF) - (c and 0xFF)) <= TOLERANCE

        fun rowIsBg(y: Int, c: Int): Boolean {
            val base = y * sw
            var n = 0
            for (x in 0 until sw) if (close(px[base + x], c)) n++
            return n >= sw * COVERAGE
        }

        fun colIsBg(x: Int, c: Int): Boolean {
            var n = 0
            for (y in 0 until sh) if (close(px[y * sw + x], c)) n++
            return n >= sh * COVERAGE
        }

        val maxRows = (sh * MAX_REMOVAL).toInt()
        val maxCols = (sw * MAX_REMOVAL).toInt()

        // Walk each side inward over its margin run first, with no capping yet: the raw runs
        // are what tells us whether there is any content at all. Capping before this check
        // would turn a page the detector reads as uniformly background (a blank or very
        // low-contrast page) into a crop of its middle third.
        var top = 0
        while (top < sh && rowIsBg(top, topBg)) top++
        var bottom = sh - 1
        while (bottom > top && rowIsBg(bottom, bottomBg)) bottom--
        var left = 0
        while (left < sw && colIsBg(left, leftBg)) left++
        var right = sw - 1
        while (right > left && colIsBg(right, rightBg)) right--

        if (top >= bottom || left >= right) return null

        // Only now apply the "is this a real margin" floor and the per-side cap.
        val topRun = if (top >= MIN_RUN) top.coerceAtMost(maxRows) else 0
        val bottomRun = (sh - 1 - bottom).let { if (it >= MIN_RUN) it.coerceAtMost(maxRows) else 0 }
        val leftRun = if (left >= MIN_RUN) left.coerceAtMost(maxCols) else 0
        val rightRun = (sw - 1 - right).let { if (it >= MIN_RUN) it.coerceAtMost(maxCols) else 0 }

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val padX = (w * PAD).toInt()
        val padY = (h * PAD).toInt()

        val rect = Rect(
            (leftRun * fx).toInt() - padX,
            (topRun * fy).toInt() - padY,
            ((sw - rightRun) * fx).toInt() + padX,
            ((sh - bottomRun) * fy).toInt() + padY,
        ).also {
            it.left = it.left.coerceIn(0, w - 1)
            it.top = it.top.coerceIn(0, h - 1)
            it.right = it.right.coerceIn(it.left + 1, w)
            it.bottom = it.bottom.coerceIn(it.top + 1, h)
        }

        // Report "nothing to do" when the box is already the whole page.
        if (rect.width() >= w - 2 && rect.height() >= h - 2) return null
        return rect
    }

    /** Crops [source] to its content box; returns [source] unchanged when there is nothing to do. */
    fun crop(source: Bitmap): Bitmap {
        val rect = cropRect(source) ?: return source
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
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
