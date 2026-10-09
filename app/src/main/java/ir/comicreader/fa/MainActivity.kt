package ir.comicreader.fa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
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
import ir.comicreader.fa.ui.CrashScreen
import ir.comicreader.fa.ui.library.LibraryScreen
import ir.comicreader.fa.ui.library.LibraryViewModel
import ir.comicreader.fa.ui.reader.ReaderScreen
import ir.comicreader.fa.ui.reader.ReaderViewModel
import ir.comicreader.fa.ui.theme.ComicReaderTheme
import ir.comicreader.fa.util.CrashLog

class MainActivity : ComponentActivity() {

    private val incomingUri = mutableStateOf<Uri?>(null)
    private val incomingToken = mutableStateOf(0L)

    private fun deliver(uri: Uri?) {
        if (uri != null) {
            incomingUri.value = uri
            incomingToken.value = incomingToken.value + 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()

        deliver(intent?.data)
        // Consume it: if the activity is recreated (rotation, process restore) we must not
        // try to reopen a file whose "open with" permission is long gone.
        intent?.setData(null)

        setContent {
            var crash by remember { mutableStateOf(CrashLog.read(this@MainActivity)) }
            ComicReaderTheme {
                val text = crash
                if (text != null) {
                    CrashScreen(text) {
                        CrashLog.clear(this@MainActivity)
                        crash = null
                    }
                } else {
                    AppRoot(incomingUri = incomingUri.value, incomingToken = incomingToken.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliver(intent.data)
    }
}

@Composable
private fun AppRoot(incomingUri: Uri?, incomingToken: Long) {
    val context = LocalContext.current
    val libraryVm: LibraryViewModel = viewModel()
    var opened by remember { mutableStateOf<ComicItem?>(null) }

    LaunchedEffect(incomingToken) {
        if (incomingToken == 0L || incomingUri == null) return@LaunchedEffect
        resolveIncoming(context, incomingUri)?.let { opened = it }
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

/**
 * Resolves an incoming "open with" Uri. Returns null (safely) when the Uri has no read
 * permission anymore or is not a supported file — never throws, so a stale intent can
 * never crash the app on startup.
 */
private fun resolveIncoming(context: Context, uri: Uri): ComicItem? = runCatching {
    val granted = context.checkUriPermission(
        uri,
        Process.myPid(),
        Process.myUid(),
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return@runCatching null

    val info = Saf.queryFileInfo(context.contentResolver, uri) ?: return@runCatching null
    val kind = kindForName(info.first) ?: return@runCatching null
    Saf.takePermission(context, uri)
    ComicItem(info.first, uri, kind, info.second)
}.getOrNull()
