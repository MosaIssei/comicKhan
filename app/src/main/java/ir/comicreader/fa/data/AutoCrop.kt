package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max

/** Trims uniform (usually white/black) scanner margins around a page. */
object AutoCrop {

    fun crop(source: Bitmap, threshold: Int = 18): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 8 || h < 8) return source

        // Analyse a small copy for speed.
        val scale = 256f / max(w, h)
        val sw = max(1, (w * scale).toInt())
        val sh = max(1, (h * scale).toInt())
        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val bg = small.getPixel(0, 0)

        var left = sw
        var right = -1
        var top = sh
        var bottom = -1
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                if (!closeTo(small.getPixel(x, y), bg, threshold)) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (small !== source) small.recycle()
        if (right < 0 || bottom < 0) return source

        val fx = w.toFloat() / sw
        val fy = h.toFloat() / sh
        val l = (left * fx).toInt()
        val t = (top * fy).toInt()
        val r = ((right + 1) * fx).toInt().coerceAtMost(w)
        val b = ((bottom + 1) * fy).toInt().coerceAtMost(h)
        if (r - l < w * 0.25f || b - t < h * 0.25f) return source
        if (r - l >= w - 2 && b - t >= h - 2) return source

        return Bitmap.createBitmap(source, l, t, r - l, b - t)
    }

    private fun closeTo(a: Int, b: Int, threshold: Int): Boolean {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr <= threshold && dg <= threshold && db <= threshold
    }
}
