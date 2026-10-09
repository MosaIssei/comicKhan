package ir.comicreader.fa.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** Helpers over the Storage Access Framework (no broad storage permission needed). */
object Saf {

    data class Entry(
        val name: String,
        val uri: Uri,
        val isDir: Boolean,
        val size: Long,
        val mime: String,
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    /** Children of the picked tree root. */
    fun listChildren(cr: ContentResolver, treeUri: Uri): List<Entry> =
        queryChildren(cr, treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** Children of a directory document inside the tree. */
    fun listChildrenOf(cr: ContentResolver, treeUri: Uri, dirUri: Uri): List<Entry> =
        queryChildren(cr, treeUri, DocumentsContract.getDocumentId(dirUri))

    private fun queryChildren(cr: ContentResolver, treeUri: Uri, parentDocId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<Entry>()
        cr.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                out.add(Entry(name, uri, isDir, size, mime))
            }
        }
        return out
    }

    fun hasImageChild(cr: ContentResolver, treeUri: Uri, dirUri: Uri): Boolean =
        listChildrenOf(cr, treeUri, dirUri).any { !it.isDir && isImageName(it.name) }

    fun takePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
