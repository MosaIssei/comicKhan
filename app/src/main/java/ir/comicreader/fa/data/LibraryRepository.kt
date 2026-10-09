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
        val items = ArrayList<ComicItem>()

        // The picked folder itself counts as a comic when it directly holds images.
        val rootDoc = Saf.rootDocumentUri(treeUri)
        if (Saf.hasImageChild(cr, rootDoc)) {
            val rootName = Saf.displayName(cr, rootDoc) ?: "پوشه"
            items += ComicItem(rootName, rootDoc, ComicKind.FOLDER)
        }

        for (entry in Saf.listChildren(cr, treeUri)) {
            if (entry.isDir) {
                if (Saf.hasImageChild(cr, entry.uri)) {
                    items += ComicItem(entry.name, entry.uri, ComicKind.FOLDER)
                }
            } else {
                kindForName(entry.name)?.let { kind ->
                    items += ComicItem(entry.name, entry.uri, kind, entry.size)
                }
            }
        }
        items.sortedWith(compareBy(Ordering.Natural) { it.name })
    }
}
