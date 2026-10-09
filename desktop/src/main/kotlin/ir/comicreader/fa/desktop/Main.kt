package ir.comicreader.fa.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ir.comicreader.fa.desktop.data.Comic
import ir.comicreader.fa.desktop.ui.ComicReaderTheme
import ir.comicreader.fa.desktop.ui.LibraryScreen
import ir.comicreader.fa.desktop.ui.ReaderScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "کمیک‌خوان",
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
    ) {
        ComicReaderTheme {
            App()
        }
    }
}

@Composable
private fun App() {
    var opened by remember { mutableStateOf<Comic?>(null) }
    val current = opened
    if (current == null) {
        LibraryScreen(onOpen = { opened = it })
    } else {
        ReaderScreen(comic = current, onBack = { opened = null })
    }
}
