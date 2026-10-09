package ir.comicreader.fa.desktop.data

import androidx.compose.ui.unit.IntSize
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
private val ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "rar", "cbr")

private const val PREFIX_BYTES = 128 * 1024

fun isImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun isArchiveName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

private fun readPrefix(stream: InputStream): ByteArray = stream.use { it.readNBytes(PREFIX_BYTES) }

private fun newTempFile(name: String): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "comickhan").apply { mkdirs() }
    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(dir, safe + "_" + name.hashCode().toUInt().toString(16))
}

/** Ordered set of pages for one comic, read lazily as encoded bytes. */
interface ComicSource {
    val pageCount: Int
    fun pageBytes(index: Int): ByteArray

    /** Intrinsic size of a page (header-only read), used to size items before decode. */
    fun pageSize(index: Int): IntSize? = null

    fun close()
}

private class SinglePageSource(pageName: String, private val loader: () -> ByteArray) : ComicSource {
    private val bytes by lazy { loader() }
    override val pageCount: Int get() = 1
    override fun pageBytes(index: Int): ByteArray = bytes
    override fun pageSize(index: Int): IntSize? = imageSize(bytes)
    override fun close() = Unit
}

private class NestedSource(
    private val children: List<ComicSource>,
    private val tempFiles: List<File>,
) : ComicSource {
    private val starts = IntArray(children.size + 1).also { arr ->
        var sum = 0
        for (i in children.indices) {
            arr[i] = sum
            sum += children[i].pageCount
        }
        arr[children.size] = sum
    }

    override val pageCount: Int get() = starts.last()

    private fun childAt(index: Int): Pair<ComicSource, Int>? {
        for (i in children.indices) if (index < starts[i + 1]) return children[i] to (index - starts[i])
        return null
    }

    override fun pageBytes(index: Int): ByteArray =
        childAt(index)?.let { (child, local) -> child.pageBytes(local) }
            ?: throw IndexOutOfBoundsException("page $index")

    override fun pageSize(index: Int): IntSize? =
        childAt(index)?.let { (child, local) -> child.pageSize(local) }

    override fun close() {
        children.forEach { runCatching { it.close() } }
        tempFiles.forEach { runCatching { it.delete() } }
    }
}

private fun childSource(file: File, name: String): ComicSource =
    when (name.substringAfterLast('.', "").lowercase()) {
        "rar", "cbr" -> RarSource(file, allowNested = false)
        else -> ZipSource(file, allowNested = false)
    }

object ComicSourceFactory {
    fun open(comic: Comic): ComicSource = when (comic.kind) {
        Kind.ZIP -> ZipSource(comic.file)
        Kind.RAR -> RarSource(comic.file)
        Kind.FOLDER -> FolderSource(comic.file)
    }
}

class ZipSource(file: File, allowNested: Boolean = true) : ComicSource {
    private val zip = ZipFile(file)

    private val entries: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory }
        .map { it.name }
        .sortedWith(Ordering.Natural)
        .toList()

    private val images: List<String> = entries.filter { isImageName(it) }

    private val nested: NestedSource? =
        if (allowNested && entries.any { isArchiveName(it) }) buildNested() else null

    private fun buildNested(): NestedSource {
        val children = ArrayList<ComicSource>()
        val temps = ArrayList<File>()
        for (name in entries) {
            when {
                isImageName(name) -> children += SinglePageSource(name) {
                    zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
                }

                isArchiveName(name) -> runCatching {
                    val temp = newTempFile(name)
                    zip.getInputStream(zip.getEntry(name)).use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    temps += temp
                    val child = childSource(temp, name)
                    if (child.pageCount > 0) children += child
                }
            }
        }
        return NestedSource(children, temps)
    }

    override val pageCount: Int get() = nested?.pageCount ?: images.size

    override fun pageBytes(index: Int): ByteArray {
        nested?.let { return it.pageBytes(index) }
        val entry = zip.getEntry(images[index])
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun pageSize(index: Int): IntSize? =
        nested?.pageSize(index) ?: imageSize(readPrefix(zip.getInputStream(zip.getEntry(images[index]))))

    override fun close() {
        nested?.close()
        zip.close()
    }
}

class FolderSource(dir: File) : ComicSource {
    private val files: List<File> = (dir.listFiles() ?: emptyArray())
        .filter { it.isFile && isImageName(it.name) }
        .sortedWith(compareBy(Ordering.Natural) { it.name })

    override val pageCount: Int get() = files.size

    override fun pageBytes(index: Int): ByteArray = files[index].readBytes()

    override fun pageSize(index: Int): IntSize? = imageSize(readPrefix(files[index].inputStream()))

    override fun close() = Unit
}

class RarSource(file: File, allowNested: Boolean = true) : ComicSource {
    private val archive = Archive(file)

    private val headers: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory }
        .sortedWith(Comparator { a, b -> Ordering.compare(a.fileName ?: "", b.fileName ?: "") })

    private val imageHeaders: List<FileHeader> = headers.filter { isImageName(it.fileName ?: "") }

    private val nested: NestedSource? =
        if (allowNested && headers.any { isArchiveName(it.fileName ?: "") }) buildNested() else null

    private fun buildNested(): NestedSource {
        val children = ArrayList<ComicSource>()
        val temps = ArrayList<File>()
        for (header in headers) {
            val name = header.fileName ?: continue
            when {
                isImageName(name) -> children += SinglePageSource(name) {
                    archive.getInputStream(header).use { it.readBytes() }
                }

                isArchiveName(name) -> runCatching {
                    val temp = newTempFile(name)
                    archive.getInputStream(header).use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    temps += temp
                    val child = childSource(temp, name)
                    if (child.pageCount > 0) children += child
                }
            }
        }
        return NestedSource(children, temps)
    }

    override val pageCount: Int get() = nested?.pageCount ?: imageHeaders.size

    override fun pageBytes(index: Int): ByteArray {
        nested?.let { return it.pageBytes(index) }
        return archive.getInputStream(imageHeaders[index]).use { it.readBytes() }
    }

    override fun pageSize(index: Int): IntSize? =
        nested?.pageSize(index) ?: imageSize(readPrefix(archive.getInputStream(imageHeaders[index])))

    override fun close() {
        nested?.close()
        archive.close()
    }
}
