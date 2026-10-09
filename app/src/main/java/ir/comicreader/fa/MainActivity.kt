package ir.comicreader.fa

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.comicreader.fa.data.Saf
import ir.comicreader.fa.data.kindForName
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
        val initialUri = intent?.data
        setContent {
            ComicReaderTheme {
                AppRoot(initialUri = initialUri)
            }
        }
    }
}

@Composable
private fun AppRoot(initialUri: Uri?) {
    val context = LocalContext.current
    val libraryVm: LibraryViewModel = viewModel()
    var opened by remember { mutableStateOf<ComicItem?>(null) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            val info = Saf.queryFileInfo(context.contentResolver, initialUri)
            val name = info?.first ?: "comic"
            val kind = kindForName(name)
            if (kind != null) {
                Saf.takePermission(context, initialUri)
                opened = ComicItem(name, initialUri, kind, info?.second ?: 0L)
            }
        }
    }

    val current = opened
    if (current == null) {
        LibraryScreen(vm = libraryVm, onOpen = { opened = it })
    } else {
        val readerVm: ReaderViewModel = viewModel(key = current.uri.toString())
        LaunchedEffect(current) { readerVm.open(current) }
        DisposableEffect(current) { onDispose { readerVm.close() } }
        ReaderScreen(
            vm = readerVm,
            onBack = {
                opened = null
                libraryVm.refreshRecents()
            },
        )
    }
}
