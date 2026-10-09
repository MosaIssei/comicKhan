package ir.comicreader.fa.data

import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Minimal MHTML (.mht / .mhtml) reader: a saved web page is a MIME multipart document;
 * we pull out the embedded images, in file order, and treat them as the pages.
 */
object Mhtml {

    private val BOUNDARY = Regex("boundary\\s*=\\s*\"?([^\";\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
    private val IMAGE_CTYPE = Regex("content-type\\s*:\\s*image/", RegexOption.IGNORE_CASE)

    fun extractImages(bytes: ByteArray): List<ByteArray> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val mainSep = indexOfBlankLine(text, 0)
        if (mainSep < 0) return emptyList()

        val mainHeaders = text.substring(0, mainSep)
        val boundary = BOUNDARY.find(mainHeaders)?.groupValues?.get(1)?.trim()
        if (boundary.isNullOrEmpty()) return emptyList()

        val body = text.substring(mainSep)
        val images = ArrayList<ByteArray>()
        for (part in body.split("--$boundary")) {
            val trimmed = part.trim('\r', '\n')
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue

            val sep = indexOfBlankLine(trimmed, 0)
            if (sep < 0) continue

            val headers = trimmed.substring(0, sep)
            if (!IMAGE_CTYPE.containsMatchIn(headers)) continue

            val content = trimmed.substring(sep).trim('\r', '\n')
            val decoded = when {
                headers.contains("base64", ignoreCase = true) -> decodeBase64(content)
                headers.contains("quoted-printable", ignoreCase = true) -> decodeQuotedPrintable(content)
                else -> content.toByteArray(Charsets.ISO_8859_1)
            }
            if (decoded.isNotEmpty()) images += decoded
        }
        return images
    }

    private fun indexOfBlankLine(s: String, from: Int): Int {
        val a = s.indexOf("\r\n\r\n", from)
        val b = s.indexOf("\n\n", from)
        return when {
            a < 0 -> b
            b < 0 -> a
            else -> minOf(a, b)
        }
    }

    private fun decodeBase64(s: String): ByteArray {
        val clean = s.filterNot { it.isWhitespace() }
        return runCatching { Base64.decode(clean, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
    }

    private fun decodeQuotedPrintable(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '=' && i + 2 < s.length && s[i + 1] == '\r' && s[i + 2] == '\n' -> i += 3
                c == '=' && i + 1 < s.length && s[i + 1] == '\n' -> i += 2
                c == '=' && i + 2 < s.length -> {
                    val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) {
                        out.write(v)
                        i += 3
                    } else {
                        out.write(c.code)
                        i++
                    }
                }
                else -> {
                    out.write(c.code)
                    i++
                }
            }
        }
        return out.toByteArray()
    }
}
