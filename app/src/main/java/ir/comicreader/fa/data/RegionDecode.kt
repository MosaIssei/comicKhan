package ir.comicreader.fa.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.Size
import java.io.ByteArrayInputStream

/**
 * Region ("tiled") decoding, mirroring what ComicScreen's native engine does:
 * only the visible area is decoded, at native resolution, so zoom stays sharp and
 * memory stays bounded regardless of the page size.
 */
object RegionDecode {

    fun size(bytes: ByteArray): Size? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) Size(opts.outWidth, opts.outHeight) else null
    }

    @Suppress("DEPRECATION")
    fun region(bytes: ByteArray, rect: Rect, sample: Int): Bitmap? {
        val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false) ?: return null
        try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferQualityOverSpeed = true
            }
            return decoder.decodeRegion(rect, opts)
        } finally {
            runCatching { decoder.recycle() }
        }
    }
}
