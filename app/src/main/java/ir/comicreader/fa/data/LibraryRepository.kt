package ir.comicreader.fa.data

import android.content.Context
import android.net.Uri
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRepository(private val context: Context) {

    suspend fun scan(treeUri: Uri): List<ComicItem> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        Saf.listChildren(cr, treeUri).mapNotNull { entry ->
            if (entry.isDir) {
                if (Saf.hasImageChild(cr, treeUri, entry.uri)) {
                    ComicItem(entry.name, entry.uri, ComicKind.FOLDER)
                } else null
            } else {
                when (entry.name.substringAfterLast('.', "").lowercase()) {
                    "cbz", "zip" -> ComicItem(entry.name, entry.uri, ComicKind.ZIP, entry.size)
                    "cbr", "rar" -> ComicItem(entry.name, entry.uri, ComicKind.RAR, entry.size)
                    "pdf" -> ComicItem(entry.name, entry.uri, ComicKind.PDF, entry.size)
                    else -> null
                }
            }
        }.sortedWith(compareBy(Ordering.Natural) { it.name })
    }
}
