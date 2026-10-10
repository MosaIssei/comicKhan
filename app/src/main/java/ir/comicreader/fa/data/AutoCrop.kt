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
 * both.
 *
 * Deliberately conservative. A row only counts as margin when it is almost entirely its
 * side's colour *and* that colour actually looks like a margin (a uniform band), and the
 * whole trim is capped so a busy page can never lose a quarter of a side to a misread. It is
 * much worse to eat artwork than to leave a little white behind.
 */
object AutoCrop {

    /** Analysis bitmap: the short edge is grown to this, the long edge capped to [LONG_MAX]. */
    private const val SHORT_MIN = 160
    private const val LONG_MAX = 512

    /** Per-channel distance still counted as "the same colour". */
    private const val TOLERANCE = 22

    /** Fraction of a row/column that must match the side colour for it to be margin. */
    private const val COVERAGE = 0.985f

    /** Never trim more than this much of a side, whatever the analysis says. */
    private const val MAX_REMOVAL = 0.25f

    /** A margin has to be at least this many rows/columns thick to be believable. */
    private const val MIN_RUN = 3

    /** Thickness of the border band the side colour is sampled from. */
    private const val BAND = 2

    /** How uniform the sampled band must be before it is trusted as a margin colour. */
    private const val BAND_UNIFORMITY = 0.9f

    /**
     * The content box of [source] in its own pixel coordinates, or null when the page looks
     * like it has nothing worth trimming (or nothing that can be trimmed safely).
     */
    fun cropRect(source: Bitmap, tolerance: Int = TOLERANCE, coverage: Float = COVERAGE): Rect? {
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

        /** Length of the uniform run at the start (forward) or end (reverse) of the axis. */
        fun uniformRun(limit: Int, isBg: (Int) -> Boolean, reverse: Boolean): Int {
            var n = 0
            while (n < limit && isBg(if (reverse) limit - 1 - n else n)) n++
            return n
        }

        // A side is only cropped when its own border band is genuinely flat: a page whose
        // edge is busy artwork (a full-bleed dark panel, say) must not be trimmed at all.
        val maxY = (sh * MAX_REMOVAL).toInt()
        val maxX = (sw * MAX_REMOVAL).toInt()

        val top = trimOf(
            uniformRun(sh, { rowIsBg(it, topBg) }, false),
            bandIsUniform(px, sw, sh, 0, topBg, tolerance), maxY,
        )
        val bottomRun = trimOf(
            uniformRun(sh, { rowIsBg(it, bottomBg) }, true),
            bandIsUniform(px, sw, sh, 1, bottomBg, tolerance), maxY,
        )
        val left = trimOf(
            uniformRun(sw, { colIsBg(it, leftBg) }, false),
            bandIsUniform(px, sw, sh, 2, leftBg, tolerance), maxX,
        )
        val rightRun = trimOf(
            uniformRun(sw, { colIsBg(it, rightBg) }, true),
            bandIsUniform(px, sw, sh, 3, rightBg, tolerance), maxX,
        )

        val right = sw - rightRun
        val bottom = sh - bottomRun
        if (right - left < 2 || bottom - top < 2) return null

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val rect = Rect(
            (left * fx).toInt().coerceIn(0, w - 1),
            (top * fy).toInt().coerceIn(0, h - 1),
            (right * fx).toInt().coerceIn(1, w),
            (bottom * fy).toInt().coerceIn(1, h),
        )

        // Report "nothing to do" when the box is already the whole page.
        if (rect.width() >= w - 2 && rect.height() >= h - 2) return null
        return rect
    }

    /** A side's trim: zero unless the band looks like a margin and the run is long enough. */
    private fun trimOf(run: Int, uniform: Boolean, cap: Int): Int =
        if (uniform && run >= MIN_RUN) run.coerceAtMost(cap) else 0

    /** Median colour of one border band: 0 = top, 1 = bottom, 2 = left, 3 = right. */
    private fun medianColor(px: IntArray, w: Int, h: Int, side: Int): Int {
        val vals = bandPixels(px, w, h, side)
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

    /** True when most of the band really is one flat colour, i.e. it looks like a margin. */
    private fun bandIsUniform(px: IntArray, w: Int, h: Int, side: Int, colour: Int, tolerance: Int): Boolean {
        val vals = bandPixels(px, w, h, side)
        if (vals.isEmpty()) return false
        var n = 0
        for (p in vals) {
            val ok = abs(((p shr 16) and 0xFF) - ((colour shr 16) and 0xFF)) <= tolerance &&
                abs(((p shr 8) and 0xFF) - ((colour shr 8) and 0xFF)) <= tolerance &&
                abs((p and 0xFF) - (colour and 0xFF)) <= tolerance
            if (ok) n++
        }
        return n >= vals.size * BAND_UNIFORMITY
    }

    private fun bandPixels(px: IntArray, w: Int, h: Int, side: Int): IntArray = when (side) {
        0 -> IntArray(min(BAND, h) * w) { i -> px[(i / w) * w + i % w] }
        1 -> IntArray(min(BAND, h) * w) { i -> px[(max(0, h - BAND) + i / w) * w + i % w] }
        2 -> IntArray(h * min(BAND, w)) { i -> px[(i / min(BAND, w)) * w + i % min(BAND, w)] }
        else -> IntArray(h * min(BAND, w)) { i -> px[(i / min(BAND, w)) * w + max(0, w - BAND) + i % min(BAND, w)] }
    }
}
