package ir.comicreader.fa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.ui.library.LibraryScreen
import ir.comicreader.fa.ui.library.LibraryViewModel
import ir.comicreader.fa.ui.reader.ReaderScreen
import ir.comicreader.fa.ui.reader.ReaderViewModel
import ir.comicreader.fa.ui.theme.ComicReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComicReaderTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val libraryVm: LibraryViewModel = viewModel()
    var opened by remember { mutableStateOf<ComicItem?>(null) }

    val current = opened
    if (current == null) {
        LibraryScreen(vm = libraryVm, onOpen = { opened = it })
    } else {
        val readerVm: ReaderViewModel = viewModel(key = current.uri.toString())
        LaunchedEffect(current) { readerVm.open(current) }
        DisposableEffect(current) { onDispose { readerVm.close() } }
        ReaderScreen(vm = readerVm, onBack = { opened = null })
    }
}
