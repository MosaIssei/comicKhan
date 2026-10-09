package ir.comicreader.fa.desktop.data

import java.io.File

object Library {

    fun scan(dir: File): List<Comic> {
        val out = ArrayList<Comic>()
        dir.listFiles()?.forEach { f ->
            when {
                f.isDirectory && containsImages(f) -> out += Comic(f.name, f, Kind.FOLDER)
                f.isFile -> kindFor(f)?.let { out += Comic(f.name, f, it) }
            }
        }
        return out.sortedWith(compareBy(Ordering.Natural) { it.name })
    }

    fun kindFor(file: File): Kind? = when (file.extension.lowercase()) {
        "zip", "cbz" -> Kind.ZIP
        "rar", "cbr" -> Kind.RAR
        "mht", "mhtml" -> Kind.MHTML
        else -> null
    }

    fun containsImages(dir: File): Boolean =
        dir.listFiles()?.any { it.isFile && isImageName(it.name) } == true
}
