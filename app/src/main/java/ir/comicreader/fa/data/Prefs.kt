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

    /** 0 = fit screen, 1 = fit width, 2 = fit height. */
    var fitModeOrdinal: Int
        get() = sp.getInt(KEY_FIT, 0)
        set(value) = sp.edit().putInt(KEY_FIT, value).apply()

    /** 1.0 = normal, lower = dimmer (night reading). */
    var brightness: Float
        get() = sp.getFloat(KEY_BRIGHT, 1f)
        set(value) = sp.edit().putFloat(KEY_BRIGHT, value).apply()

    /** 1.0 = normal, >1 increases contrast, <1 lowers it. */
    var contrast: Float
        get() = sp.getFloat(KEY_CONTRAST, 1f)
        set(value) = sp.edit().putFloat(KEY_CONTRAST, value).apply()

    /** Invert colors (for negative/inverted scans). */
    var invertColors: Boolean
        get() = sp.getBoolean(KEY_INVERT, false)
        set(value) = sp.edit().putBoolean(KEY_INVERT, value).apply()

    /** Zoom factor for continuous (webtoon) mode. */
    var webtoonZoom: Float
        get() = sp.getFloat(KEY_WEBTOON_ZOOM, 1f)
        set(value) = sp.edit().putFloat(KEY_WEBTOON_ZOOM, value).apply()

    var twoPage: Boolean
        get() = sp.getBoolean(KEY_TWO, false)
        set(value) = sp.edit().putBoolean(KEY_TWO, value).apply()

    /** Continuous (webtoon) vertical scrolling instead of paged reading. */
    var continuous: Boolean
        get() = sp.getBoolean(KEY_CONTINUOUS, false)
        set(value) = sp.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    var autoCrop: Boolean
        get() = sp.getBoolean(KEY_AUTOCROP, false)
        set(value) = sp.edit().putBoolean(KEY_AUTOCROP, value).apply()

    /**
     * How much of a detected auto-crop margin to keep, in percent, at the sides and at the top
     * and bottom. Mirrors ComicScreen's `set_img_crop_auto_pad_h` / `pad_v`: 0 trims the whole
     * margin, 80 keeps most of it.
     */
    var cropPadH: Int
        get() = sp.getInt(KEY_CROP_PAD_H, 10)
        set(value) = sp.edit().putInt(KEY_CROP_PAD_H, value.coerceIn(0, 100)).apply()

    var cropPadV: Int
        get() = sp.getInt(KEY_CROP_PAD_V, 70)
        set(value) = sp.edit().putInt(KEY_CROP_PAD_V, value.coerceIn(0, 100)).apply()

    /** 0 = auto, 1 = portrait, 2 = landscape. */
    var orientationOrdinal: Int
        get() = sp.getInt(KEY_ORIENT, 0)
        set(value) = sp.edit().putInt(KEY_ORIENT, value).apply()

    /** 0 = name asc, 1 = name desc, 2 = most recently read. */
    var sortOrdinal: Int
        get() = sp.getInt(KEY_SORT, 0)
        set(value) = sp.edit().putInt(KEY_SORT, value).apply()

    fun lastPage(comicKey: String): Int = sp.getInt(KEY_LAST_PREFIX + comicKey, 0)

    fun setLastPage(comicKey: String, page: Int) {
        sp.edit().putInt(KEY_LAST_PREFIX + comicKey, page).apply()
    }

    /**
     * How far into [lastPage] reading stopped, as a fraction of that page (0..1). Mirrors
     * ComicScreen's `PAGEOFFSET FLOAT`, which is what lets it reopen exactly where you were
     * instead of at the top of the page.
     */
    fun lastPageOffset(comicKey: String): Float = sp.getFloat(KEY_LAST_OFF_PREFIX + comicKey, 0f)

    fun setLastPageOffset(comicKey: String, fraction: Float) {
        sp.edit().putFloat(KEY_LAST_OFF_PREFIX + comicKey, fraction.coerceIn(0f, 1f)).apply()
    }

    fun totalPages(comicKey: String): Int = sp.getInt(KEY_TOTAL_PREFIX + comicKey, 0)

    fun setTotalPages(comicKey: String, count: Int) {
        sp.edit().putInt(KEY_TOTAL_PREFIX + comicKey, count).apply()
    }

    /** 0 = grid, 1 = list. */
    var libraryView: Int
        get() = sp.getInt(KEY_LIBVIEW, 0)
        set(value) = sp.edit().putInt(KEY_LIBVIEW, value).apply()

    fun lastOpened(comicKey: String): Long = sp.getLong(KEY_OPENED_PREFIX + comicKey, 0L)

    fun setLastOpened(comicKey: String, millis: Long) {
        sp.edit().putLong(KEY_OPENED_PREFIX + comicKey, millis).apply()
    }

    fun bookmarks(comicKey: String): Set<Int> =
        sp.getStringSet(KEY_BM_PREFIX + comicKey, null)
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    fun toggleBookmark(comicKey: String, page: Int): Set<Int> {
        val current = bookmarks(comicKey).toMutableSet()
        if (!current.add(page)) current.remove(page)
        sp.edit().putStringSet(KEY_BM_PREFIX + comicKey, current.map { it.toString() }.toSet()).apply()
        return current
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_RTL = "rtl"
        const val KEY_FIT = "fit"
        const val KEY_BRIGHT = "brightness"
        const val KEY_CONTRAST = "contrast"
        const val KEY_INVERT = "invert_colors"
        const val KEY_WEBTOON_ZOOM = "webtoon_zoom"
        const val KEY_TWO = "two_page"
        const val KEY_CONTINUOUS = "continuous"
        const val KEY_AUTOCROP = "auto_crop"
        const val KEY_CROP_PAD_H = "crop_pad_h"
        const val KEY_CROP_PAD_V = "crop_pad_v"
        const val KEY_ORIENT = "orientation"
        const val KEY_SORT = "sort"
        const val KEY_LAST_PREFIX = "last_"
        const val KEY_LAST_OFF_PREFIX = "last_off_"
        const val KEY_OPENED_PREFIX = "opened_"
        const val KEY_BM_PREFIX = "bm_"
        const val KEY_TOTAL_PREFIX = "total_"
        const val KEY_LIBVIEW = "library_view"
    }
}
