package ir.comicreader.fa.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.R
import ir.comicreader.fa.data.model.ComicItem
import ir.comicreader.fa.data.model.ComicKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpen: (ComicItem) -> Unit) {
    val state by vm.state.collectAsState()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.onFolderPicked(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (state.hasFolder) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        IconButton(onClick = { pickFolder.launch(null) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.change_folder))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.items.isEmpty() -> EmptyLibrary(
                    title = if (state.hasFolder) stringResource(R.string.empty_library)
                    else stringResource(R.string.app_name),
                    message = if (state.hasFolder) stringResource(R.string.empty_library_hint)
                    else stringResource(R.string.pick_folder_hint),
                    actionLabel = if (state.hasFolder) stringResource(R.string.change_folder)
                    else stringResource(R.string.pick_folder),
                    onAction = { pickFolder.launch(null) },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
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

@Composable
private fun ComicCard(comic: ComicItem, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (comic.kind) {
                        ComicKind.ZIP -> Icons.Filled.MenuBook
                        ComicKind.RAR -> Icons.Filled.Archive
                        ComicKind.PDF -> Icons.Filled.PictureAsPdf
                        ComicKind.FOLDER -> Icons.Filled.Image
                    },
                    contentDescription = null,
                    modifier = Modifier.height(48.dp),
                )
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
                text = stringResource(
                    when (comic.kind) {
                        ComicKind.ZIP -> R.string.kind_zip
                        ComicKind.RAR -> R.string.kind_rar
                        ComicKind.PDF -> R.string.kind_pdf
                        ComicKind.FOLDER -> R.string.kind_folder
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
