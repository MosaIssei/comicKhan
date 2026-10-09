package ir.comicreader.fa.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    var treeUri: String?
        get() = sp.getString(KEY_TREE, null)
        set(value) = sp.edit().putString(KEY_TREE, value).apply()

    /** Reading direction: true = right-to-left (manga), false = left-to-right. */
    var mangaRightToLeft: Boolean
        get() = sp.getBoolean(KEY_RTL, true)
        set(value) = sp.edit().putBoolean(KEY_RTL, value).apply()

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_RTL = "rtl"
    }
}
