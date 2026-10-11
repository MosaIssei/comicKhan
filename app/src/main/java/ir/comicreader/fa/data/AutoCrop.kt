package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Auto-crop: finds the page's content box by trimming uniform margins from each side.
 *
 * A row/column counts as margin when it is (almost) entirely one flat colour, which tolerates
 * JPEG noise far better than looking for the first non-background pixel. Each side gets its own
 * colour, so a page whose top margin is white but bottom margin carries scanner shadow is still
 * cropped on both.
 *
 * The awkward case is the page edge itself: a scan often carries a thin dark border, a film
 * edge, or a strip of shadow that is *not* the margin. Judging against only the outermost band
 * then reads that border as the background and the trim stops before it has even started, so
 * some pages get cropped and others — the ones with the border — do not. Two defenses: each side
 * is tested against both the outermost band and one sampled a little further in, and a short
 * non-margin prefix is stepped over before the margin run begins. Both are only honoured when a
 * real margin run follows, so artwork flush to the edge is never eaten.
 */
object AutoCrop {

    private const val SHORT_MIN = 150
    private const val LONG_MAX = 480

    /** Per-channel distance still counted as "the same colour". */
    private const val TOLERANCE = 30

    /** Fraction of a row/column that must match the side colour for it to be margin. */
    private const val COVERAGE = 0.96f

    /** Never trim more than this much of a side. */
    private const val MAX_REMOVAL_H = 0.5f
    private const val MAX_REMOVAL_V = 0.35f

    /** Shortest margin, in analysis rows/columns, worth trimming. */
    private const val MIN_RUN = 2

    /** Share of the dimension stepped over at most, to get past an edge artifact. */
    private const val EDGE_SKIP_SHARE = 0.05f

    /** How far inside the edge the second background sample is taken. */
    private const val INSET_SHARE = 0.03f

    /** Thickness of the band a side's colour is sampled from. */
    private const val BAND = 3

    /**
     * The content box of [source] in its own pixel coordinates, or null when there is nothing
     * worth trimming (or nothing that can be trimmed safely).
     */
    fun cropRect(
        source: Bitmap,
        keepH: Float = KEEP_SHARE_H,
        keepV: Float = KEEP_SHARE_V,
    ): Rect? {
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

        /**
         * How many rows/columns this side loses. Tries both background candidates, steps over at
         * most the edge-skip allowance first, and reports nothing at all unless a margin run of
         * at least [MIN_RUN] follows.
         */
        fun marginRun(limit: Int, c1: Int, c2: Int, isBg: (Int, Int) -> Boolean, reverse: Boolean): Int {
            val skip = max(2, (limit * EDGE_SKIP_SHARE).toInt())
            fun runFor(c: Int): Int {
                fun at(i: Int) = if (reverse) limit - 1 - i else i
                var i = 0
                while (i < limit && i < skip && !isBg(at(i), c)) i++
                val start = i
                while (i < limit && isBg(at(i), c)) i++
                return if (i - start >= MIN_RUN) i else 0
            }
            return max(runFor(c1), runFor(c2))
        }

        val insetY = max(1, (sh * INSET_SHARE).toInt())
        val insetX = max(1, (sw * INSET_SHARE).toInt())

        val maxRows = (sh * MAX_REMOVAL_V).toInt()
        val maxCols = (sw * MAX_REMOVAL_H).toInt()

        val topRun0 = marginRun(sh, bandColor(px, sw, sh, 0, 0), bandColor(px, sw, sh, 0, insetY),
            { y, c -> rowIsBg(y, c) }, false)
        val bottomRun0 = marginRun(sh, bandColor(px, sw, sh, 1, 0), bandColor(px, sw, sh, 1, insetY),
            { y, c -> rowIsBg(y, c) }, true)
        val leftRun0 = marginRun(sw, bandColor(px, sw, sh, 2, 0), bandColor(px, sw, sh, 2, insetX),
            { x, c -> colIsBg(x, c) }, false)
        val rightRun0 = marginRun(sw, bandColor(px, sw, sh, 3, 0), bandColor(px, sw, sh, 3, insetX),
            { x, c -> colIsBg(x, c) }, true)

        // A side pair that between them account for the whole dimension means there is nothing
        // to anchor on at all (a blank or uniformly low-contrast page).
        if (topRun0 + bottomRun0 >= sh || leftRun0 + rightRun0 >= sw) return null

        val topRun = topRun0.coerceAtMost(maxRows)
        val bottomRun = bottomRun0.coerceAtMost(maxRows)
        val leftRun = leftRun0.coerceAtMost(maxCols)
        val rightRun = rightRun0.coerceAtMost(maxCols)

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val lPx = (leftRun * fx).toInt()
        val tPx = (topRun * fy).toInt()
        val rPx = (rightRun * fx).toInt()
        val bPx = (bottomRun * fy).toInt()

        val rect = Rect(
            lPx - (lPx * keepH).toInt(),
            tPx - (tPx * keepV).toInt(),
            w - rPx + (rPx * keepH).toInt(),
            h - bPx + (bPx * keepV).toInt(),
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

    /** Share of each detected margin handed back, so a soft edge is never shaved flush. */
    const val KEEP_SHARE_H = 0.1f

    /**
     * Top and bottom are still handled a little more gently than left and right. A page's
     * vertical margins run much deeper than its side margins, and the top and bottom of the
     * artwork is often light enough to read as margin, so a rule that is right sideways eats
     * whole panels vertically.
     */
    const val KEEP_SHARE_V = 0.2f

    /**
     * Median colour of one border band, [inset] pixels in from that edge: 0 = top, 1 = bottom,
     * 2 = left, 3 = right.
     */
    private fun bandColor(px: IntArray, w: Int, h: Int, side: Int, inset: Int): Int {
        val vals = ArrayList<Int>()
        when (side) {
            0 -> for (y in min(inset, h - 1) until min(inset + BAND, h)) for (x in 0 until w) vals.add(px[y * w + x])
            1 -> for (y in max(0, h - inset - BAND) until max(0, h - inset)) for (x in 0 until w) vals.add(px[y * w + x])
            2 -> for (y in 0 until h) for (x in min(inset, w - 1) until min(inset + BAND, w)) vals.add(px[y * w + x])
            else -> for (y in 0 until h) for (x in max(0, w - inset - BAND) until max(0, w - inset)) vals.add(px[y * w + x])
        }
        if (vals.isEmpty()) return 0
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
