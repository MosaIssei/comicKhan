package ir.comicreader.fa.data.model

import android.net.Uri

enum class ComicKind { ZIP, RAR, PDF, MHTML, FOLDER }

data class ComicItem(
    val name: String,
    val uri: Uri,
    val kind: ComicKind,
    val sizeBytes: Long = 0L,
)
