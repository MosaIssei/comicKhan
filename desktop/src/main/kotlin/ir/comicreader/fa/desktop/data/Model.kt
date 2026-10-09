package ir.comicreader.fa.desktop.data

import java.io.File

enum class Kind { ZIP, RAR, MHTML, FOLDER }

data class Comic(
    val name: String,
    val file: File,
    val kind: Kind,
)
