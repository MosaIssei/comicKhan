package ir.comicreader.fa.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.R
import ir.comicreader.fa.data.Prefs
import ir.comicreader.fa.data.Recent
import ir.comicreader.fa.data.Saf
import ir.comicreader.fa.data.Thumbnails
import ir.comicreader.fa.data.kindForName
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind
import java.io.File

private fun clearCaches(context: Context) {
    File(context.cacheDir, "comics").deleteRecursively()
    Thumbnails.clear()
}

private fun kindIcon(kind: ComicKind): ImageVector = when (kind) {
    ComicKind.ZIP -> Icons.Filled.MenuBook
    ComicKind.RAR -> Icons.Filled.Archive
    ComicKind.PDF -> Icons.Filled.PictureAsPdf
    ComicKind.MHTML -> Icons.Filled.Language
    ComicKind.FOLDER -> Icons.Filled.Image
}

private fun kindLabel(kind: ComicKind): Int = when (kind) {
    ComicKind.ZIP -> R.string.kind_zip
    ComicKind.RAR -> R.string.kind_rar
    ComicKind.PDF -> R.string.kind_pdf
    ComicKind.MHTML -> R.string.kind_mhtml
    ComicKind.FOLDER -> R.string.kind_folder
}

private fun progressOf(lastPage: Int, total: Int): Float =
    if (total > 0) ((lastPage + 1).toFloat() / total).coerceIn(0f, 1f) else 0f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpen: (ComicItem) -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onFolderPicked(uri) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val item = runCatching {
                Saf.takePermission(context, uri)
                val info = Saf.queryFileInfo(context.contentResolver, uri) ?: return@runCatching null
                val kind = kindForName(info.first) ?: return@runCatching null
                ComicItem(info.first, uri, kind, info.second)
            }.getOrNull()
            if (item != null) {
                onOpen(item)
            } else {
                Toast.makeText(context, R.string.unsupported_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { vm.setView(if (state.viewMode == 0) 1 else 0) }) {
                        Icon(
                            imageVector = if (state.viewMode == 0) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = stringResource(
                                if (state.viewMode == 0) R.string.view_list else R.string.view_grid
                            ),
                        )
                    }
                    IconButton(onClick = { openFile.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = stringResource(R.string.open_file))
                    }
                    if (state.hasFolder) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { pickFolder.launch(null) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.change_folder))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            Text(
                                text = stringResource(R.string.sort),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            SortItem(R.string.sort_name_asc, 0, state.sortOrdinal) { vm.setSort(0); menuOpen = false }
                            SortItem(R.string.sort_name_desc, 1, state.sortOrdinal) { vm.setSort(1); menuOpen = false }
                            SortItem(R.string.sort_recent, 2, state.sortOrdinal) { vm.setSort(2); menuOpen = false }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_cache)) },
                                onClick = {
                                    clearCaches(context)
                                    vm.refresh()
                                    menuOpen = false
                                    Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (state.recents.isNotEmpty() && state.query.isBlank()) {
                RecentSection(
                    recents = state.recents,
                    onOpen = onOpen,
                    onRemove = { vm.removeRecent(it) },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.items.isEmpty() && state.query.isNotBlank() -> CenterText(
                        stringResource(R.string.no_results)
                    )

                    state.items.isEmpty() -> EmptyLibrary(
                        title = if (state.hasFolder) stringResource(R.string.empty_library)
                        else stringResource(R.string.app_name),
                        message = if (state.hasFolder) stringResource(R.string.empty_library_hint)
                        else stringResource(R.string.pick_folder_hint),
                        onPickFolder = { pickFolder.launch(null) },
                        onOpenFile = { openFile.launch(arrayOf("*/*")) },
                    )

                    state.viewMode == 1 -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.items.forEach { comic ->
                            item(key = comic.uri.toString()) {
                                ComicRow(comic = comic, onClick = { onOpen(comic) })
                            }
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.uri.toString() }) { comic ->
                            ComicCard(comic = comic, onClick = { onOpen(comic) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressInfo(lastPage: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 0 || lastPage <= 0) return
    val fraction = progressOf(lastPage, total)
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.progress_percent, (fraction * 100).toInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RecentSection(
    recents: List<Recent>,
    onOpen: (ComicItem) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.recent_continue),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            recents.forEach { recent ->
                item(key = recent.uriString) {
                    val item = ComicItem(recent.name, Uri.parse(recent.uriString), recent.kind)
                    RecentCard(
                        comic = item,
                        onClick = { onOpen(item) },
                        onRemove = { onRemove(recent.uriString) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(comic: ComicItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, comic.uri) {
        value = Thumbnails.load(context, comic)
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(120.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                contentAlignment = Alignment.Center,
            ) {
                val image = thumb
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(kindIcon(comic.kind), contentDescription = null, modifier = Modifier.height(36.dp))
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove_item),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = comic.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SortItem(labelRes: Int, ordinal: Int, current: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = {
            if (ordinal == current) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ComicCard(comic: ComicItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val lastPage = remember(comic.uri) { prefs.lastPage(comic.uri.toString()) }
    val total = remember(comic.uri) { prefs.totalPages(comic.uri.toString()) }
    val thumb by produceState<ImageBitmap?>(initialValue = null, comic.uri) {
        value = Thumbnails.load(context, comic)
    }

    Card(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                contentAlignment = Alignment.Center,
            ) {
                val image = thumb
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(kindIcon(comic.kind), contentDescription = null, modifier = Modifier.height(48.dp))
                }
            }
            Text(
                text = comic.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = stringResource(kindLabel(comic.kind)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp),
            )
            ProgressInfo(
                lastPage = lastPage,
                total = total,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ComicRow(comic: ComicItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val lastPage = remember(comic.uri) { prefs.lastPage(comic.uri.toString()) }
    val total = remember(comic.uri) { prefs.totalPages(comic.uri.toString()) }
    val thumb by produceState<ImageBitmap?>(initialValue = null, comic.uri) {
        value = Thumbnails.load(context, comic)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val image = thumb
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(kindIcon(comic.kind), contentDescription = null, modifier = Modifier.height(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comic.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(kindLabel(comic.kind)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ProgressInfo(lastPage = lastPage, total = total)
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyLibrary(
    title: String,
    message: String,
    onPickFolder: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.height(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPickFolder) { Text(stringResource(R.string.pick_folder)) }
            OutlinedButton(onClick = onOpenFile) { Text(stringResource(R.string.open_file)) }
        }
    }
}
