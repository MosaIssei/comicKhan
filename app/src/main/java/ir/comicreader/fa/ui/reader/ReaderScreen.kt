package ir.comicreader.fa.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.R
import ir.comicreader.fa.data.Prefs

@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var rtl by remember { mutableStateOf(prefs.mangaRightToLeft) }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    val pageCount = vm.pageCount
    val pagerState = rememberPagerState(pageCount = { vm.pageCount })

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val message = vm.error
        when {
            message != null -> CenterMessage(message)
            pageCount == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            else -> HorizontalPager(
                state = pagerState,
                reverseLayout = rtl,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ComicPage(
                    vm = vm,
                    index = page,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChanged = { zoomed = it },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && pageCount > 0,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = vm.title,
                current = pagerState.currentPage + 1,
                total = pageCount,
                rtl = rtl,
                onBack = onBack,
                onToggleDirection = {
                    rtl = !rtl
                    prefs.mangaRightToLeft = rtl
                },
            )
        }
    }
}

@Composable
private fun ComicPage(
    vm: ReaderViewModel,
    index: Int,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index) {
        value = vm.loadPage(index)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) {
                        val maxX = (size.width * (scale - 1f)) / 2f
                        val maxY = (size.height * (scale - 1f)) / 2f
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    } else {
                        Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    ) {
        val image = bitmap
        if (image == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.cd_page),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    current: Int,
    total: Int,
    rtl: Boolean,
    onBack: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.65f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.page_of, current, total),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onToggleDirection) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(
                        if (rtl) R.string.dir_rtl else R.string.dir_ltr
                    ),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
