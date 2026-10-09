package ir.comicreader.fa.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.desktop.data.Comic
import ir.comicreader.fa.desktop.data.Kind
import ir.comicreader.fa.desktop.data.Library
import ir.comicreader.fa.desktop.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.ZIP -> Icons.Filled.MenuBook
    Kind.RAR -> Icons.Filled.Archive
    Kind.MHTML -> Icons.Filled.Language
    Kind.FOLDER -> Icons.Filled.Image
}

private fun kindLabel(kind: Kind): String = when (kind) {
    Kind.ZIP -> "آرشیو ZIP"
    Kind.RAR -> "آرشیو RAR"
    Kind.MHTML -> "صفحهٔ وب (MHTML)"
    Kind.FOLDER -> "پوشهٔ تصاویر"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Comic) -> Unit) {
    val prefs = remember { Prefs() }
    var folder by remember { mutableStateOf(prefs.lastFolder?.let { File(it) }) }
    var items by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(folder) {
        items = folder?.let { withContext(Dispatchers.IO) { Library.scan(it) } } ?: emptyList()
    }

    val visible = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کمیک‌خوان") },
                actions = {
                    TextButton(onClick = {
                        chooseDirectory(folder)?.let {
                            prefs.lastFolder = it.absolutePath
                            folder = it
                        }
                    }) { Text("انتخاب پوشه") }
                    TextButton(onClick = {
                        chooseComicFile(folder)?.let { f ->
                            Library.kindFor(f)?.let { k -> onOpen(Comic(f.name, f, k)) }
                        }
                    }) { Text("باز کردن فایل") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("جست‌وجو") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )

            if (folder == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = "برای شروع، یک پوشهٔ حاوی کمیک یا یک فایل ZIP/CBZ/CBR/RAR انتخاب کن.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("چیزی پیدا نشد", modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(visible, key = { it.file.absolutePath }) { comic ->
                        ComicRow(comic = comic, onClick = { onOpen(comic) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicRow(comic: Comic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kindIcon(comic.kind), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comic.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = kindLabel(comic.kind),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
