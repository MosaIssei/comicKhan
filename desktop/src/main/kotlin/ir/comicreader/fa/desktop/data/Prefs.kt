package ir.comicreader.fa.desktop.data

import java.io.File
import java.util.Properties

/** Simple file-backed preferences under the user's home directory. */
class Prefs {
    private val file = File(System.getProperty("user.home"), ".comickhan/prefs.properties")
    private val props = Properties()

    init {
        runCatching { if (file.isFile) file.inputStream().use { props.load(it) } }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, null) }
        }
    }

    private fun get(key: String) = props.getProperty(key)

    private fun set(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }

    fun lastPage(comicKey: String): Int = get("last_${comicKey.hashCode()}")?.toIntOrNull() ?: 0
    fun setLastPage(comicKey: String, page: Int) = set("last_${comicKey.hashCode()}", page.toString())

    var rtl: Boolean
        get() = get("rtl")?.toBoolean() ?: true
        set(value) = set("rtl", value.toString())

    var fitOrdinal: Int
        get() = get("fit")?.toIntOrNull() ?: 0
        set(value) = set("fit", value.toString())

    var contrast: Float
        get() = get("contrast")?.toFloatOrNull() ?: 1f
        set(value) = set("contrast", value.toString())

    var invert: Boolean
        get() = get("invert")?.toBoolean() ?: false
        set(value) = set("invert", value.toString())

    var brightness: Float
        get() = get("brightness")?.toFloatOrNull() ?: 1f
        set(value) = set("brightness", value.toString())

    var webtoon: Boolean
        get() = get("webtoon")?.toBoolean() ?: false
        set(value) = set("webtoon", value.toString())

    var lastFolder: String?
        get() = get("last_folder")
        set(value) {
            if (value == null) props.remove("last_folder") else props.setProperty("last_folder", value)
            save()
        }
}
