package ir.comicreader.fa.desktop.ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun chooseDirectory(initial: File? = null): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "انتخاب پوشهٔ کمیک‌ها"
        if (initial != null) currentDirectory = initial
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

fun chooseComicFile(initial: File? = null): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = "باز کردن فایل کمیک"
        fileFilter = FileNameExtensionFilter("کمیک (zip, cbz, rar, cbr)", "zip", "cbz", "rar", "cbr")
        if (initial != null) currentDirectory = initial
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
