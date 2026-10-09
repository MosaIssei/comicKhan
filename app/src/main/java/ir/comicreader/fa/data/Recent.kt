package ir.comicreader.fa.data

import ir.comicreader.fa.data.model.ComicKind

data class Recent(
    val uriString: String,
    val name: String,
    val kind: ComicKind,
)
