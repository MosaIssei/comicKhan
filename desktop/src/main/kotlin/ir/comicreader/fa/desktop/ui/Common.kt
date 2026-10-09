package ir.comicreader.fa.desktop.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.abs

enum class FitMode { FIT, WIDTH, HEIGHT }

fun contentSizeFor(boxW: Float, boxH: Float, aspect: Float, fit: FitMode): Size = when (fit) {
    FitMode.FIT -> if (boxW / boxH >= aspect) Size(boxH * aspect, boxH) else Size(boxW, boxW / aspect)
    FitMode.WIDTH -> Size(boxW, boxW / aspect)
    FitMode.HEIGHT -> Size(boxH * aspect, boxH)
}

/** Combined contrast + (optional) color inversion filter, or null when it has no effect. */
fun imageColorFilter(contrast: Float, invert: Boolean): ColorFilter? {
    if (!invert && abs(contrast - 1f) < 0.02f) return null
    val scale = if (invert) -contrast else contrast
    val offset = if (invert) 255f - (1f - contrast) * 128f else (1f - contrast) * 128f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}
