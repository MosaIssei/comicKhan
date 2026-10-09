package ir.comicreader.fa.desktop.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize

/** Decodes encoded image bytes with Skia. */
fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** Reads just the dimensions from encoded bytes (cheap header parse, no full decode). */
fun imageSize(bytes: ByteArray): IntSize? = runCatching {
    val codec = org.jetbrains.skia.Codec.makeFromData(org.jetbrains.skia.Data.makeFromBytes(bytes))
    codec?.let { IntSize(it.imageInfo.width, it.imageInfo.height) }
}.getOrNull()
