package ir.comicreader.fa.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

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

    /** Document Uri for the picked tree root (safe to use as a directory Uri). */
    fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** Children of the picked tree root. */
    fun listChildren(cr: ContentResolver, treeUri: Uri): List<Entry> =
        queryChildren(cr, treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** Children of a directory document inside the tree. */
    fun listChildrenOf(cr: ContentResolver, dirUri: Uri): List<Entry> {
        val treeUri = dirUri // built "using tree", so it carries the tree id
        return queryChildren(cr, treeUri, DocumentsContract.getDocumentId(dirUri))
    }

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

    fun hasImageChild(cr: ContentResolver, dirUri: Uri): Boolean =
        listChildrenOf(cr, dirUri).any { !it.isDir && isImageName(it.name) }

    fun displayName(cr: ContentResolver, docUri: Uri): String? {
        cr.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    /** Name + size for a picked file Uri (OpenDocument). */
    fun queryFileInfo(cr: ContentResolver, uri: Uri): Pair<String, Long>? {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0) ?: return null
                    val size = if (c.isNull(1)) 0L else c.getLong(1)
                    return name to size
                }
            }
        return null
    }

    fun takePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
