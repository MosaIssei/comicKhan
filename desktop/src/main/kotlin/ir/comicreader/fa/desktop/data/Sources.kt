package ir.comicreader.fa.desktop.data

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.util.zip.ZipFile

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

fun isImageName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

/** Ordered set of pages for one comic, read lazily as encoded bytes. */
interface ComicSource {
    val pageCount: Int
    fun pageBytes(index: Int): ByteArray
    fun close()
}

object ComicSourceFactory {
    fun open(comic: Comic): ComicSource = when (comic.kind) {
        Kind.ZIP -> ZipSource(comic.file)
        Kind.RAR -> RarSource(comic.file)
        Kind.FOLDER -> FolderSource(comic.file)
    }
}

class ZipSource(file: File) : ComicSource {
    private val zip = ZipFile(file)

    private val entries: List<String> = zip.entries().asSequence()
        .filter { !it.isDirectory && isImageName(it.name) }
        .map { it.name }
        .sortedWith(Ordering.Natural)
        .toList()

    override val pageCount: Int get() = entries.size

    override fun pageBytes(index: Int): ByteArray {
        val entry = zip.getEntry(entries[index])
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() = zip.close()
}

class FolderSource(dir: File) : ComicSource {
    private val files: List<File> = (dir.listFiles() ?: emptyArray())
        .filter { it.isFile && isImageName(it.name) }
        .sortedWith(compareBy(Ordering.Natural) { it.name })

    override val pageCount: Int get() = files.size

    override fun pageBytes(index: Int): ByteArray = files[index].readBytes()

    override fun close() = Unit
}

class RarSource(file: File) : ComicSource {
    private val archive = Archive(file)

    private val headers: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory && isImageName(it.fileName ?: "") }
        .sortedWith(Comparator { a, b -> Ordering.compare(a.fileName ?: "", b.fileName ?: "") })

    override val pageCount: Int get() = headers.size

    override fun pageBytes(index: Int): ByteArray =
        archive.getInputStream(headers[index]).use { it.readBytes() }

    override fun close() = archive.close()
}
