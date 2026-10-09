package ir.comicreader.fa.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.desktop.data.Comic
import ir.comicreader.fa.desktop.data.ComicSource
import ir.comicreader.fa.desktop.data.ComicSourceFactory
import ir.comicreader.fa.desktop.data.Prefs
import ir.comicreader.fa.desktop.data.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(comic: Comic, onBack: () -> Unit) {
    val prefs = remember { Prefs() }
    val source = remember(comic) { ComicSourceFactory.open(comic) }
    DisposableEffect(comic) { onDispose { runCatching { source.close() } } }

    val pageCount = source.pageCount
    val key = comic.file.absolutePath

    var webtoon by remember { mutableStateOf(prefs.webtoon) }
    var rtl by remember { mutableStateOf(prefs.rtl) }
    var fit by remember { mutableStateOf(FitMode.entries[prefs.fitOrdinal.coerceIn(0, 2)]) }
    var contrast by remember { mutableStateOf(prefs.contrast.coerceIn(0.5f, 2f)) }
    var invert by remember { mutableStateOf(prefs.invert) }
    var brightness by remember { mutableStateOf(prefs.brightness.coerceIn(0.2f, 1f)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var chrome by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }

    val pager = rememberPagerState(pageCount = { pageCount })
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val index = if (webtoon) listState.firstVisibleItemIndex else pager.currentPage

    val colorFilter = remember(contrast, invert) { imageColorFilter(contrast, invert) }

    fun goTo(target: Int) {
        val bounded = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (webtoon) listState.scrollToItem(bounded) else pager.scrollToPage(bounded)
        }
    }

    LaunchedEffect(pageCount) {
        val saved = prefs.lastPage(key)
        if (pageCount > 0 && saved > 0) goTo(saved)
    }
    LaunchedEffect(index, pageCount) { if (pageCount > 0) prefs.setLastPage(key, index) }
    LaunchedEffect(pager.currentPage) {
        zoom = 1f
        pan = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            pageCount == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("صفحه‌ای پیدا نشد", color = Color.White)
            }

            webtoon -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = pageCount, key = { it }) { i ->
                    WebtoonPage(source, i, colorFilter)
                }
            }

            else -> HorizontalPager(
                state = pager,
                reverseLayout = rtl,
                modifier = Modifier.fillMaxSize(),
            ) { i ->
                PagedPage(
                    source = source,
                    index = i,
                    fit = fit,
                    zoom = zoom,
                    pan = pan,
                    colorFilter = colorFilter,
                    onPan = { pan = it },
                    onTap = { chrome = !chrome },
                    onScroll = { down -> goTo(pager.currentPage + if (down) 1 else -1) },
                )
            }
        }

        if (brightness < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - brightness) * 0.85f)),
            )
        }

        if (chrome) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                    }
                    Text(
                        text = comic.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${index + 1} / $pageCount", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { rtl = !rtl; prefs.rtl = rtl }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = { settings = !settings }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        if (chrome && pageCount > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (settings) {
                    SettingsPanel(
                        webtoon = webtoon,
                        onWebtoon = { webtoon = it; prefs.webtoon = it; goTo(index) },
                        fit = fit,
                        onFit = { fit = it; prefs.fitOrdinal = it.ordinal },
                        zoom = zoom,
                        onZoom = { zoom = it },
                        contrast = contrast,
                        onContrast = { contrast = it; prefs.contrast = it },
                        invert = invert,
                        onInvert = { invert = it; prefs.invert = it },
                        brightness = brightness,
                        onBrightness = { brightness = it; prefs.brightness = it },
                    )
                } else if (pageCount > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1} / $pageCount", color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = (index + 1).toFloat(),
                            onValueChange = { v -> goTo(v.roundToInt() - 1) },
                            valueRange = 1f..pageCount.toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedPage(
    source: ComicSource,
    index: Int,
    fit: FitMode,
    zoom: Float,
    pan: Offset,
    colorFilter: ColorFilter?,
    onPan: (Offset) -> Unit,
    onTap: () -> Unit,
    onScroll: (Boolean) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeImage(source.pageBytes(index)) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) {
                                onScroll(dy > 0f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onPan(pan + drag)
                }
            }
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            val aspect = image.width.toFloat() / image.height
            val content = if (box.width > 0 && box.height > 0) {
                contentSizeFor(box.width.toFloat(), box.height.toFloat(), aspect, fit)
            } else {
                Size(image.width.toFloat(), image.height.toFloat())
            }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
                modifier = with(density) {
                    Modifier
                        .requiredSize((content.width * zoom).toDp(), (content.height * zoom).toDp())
                        .graphicsLayer(translationX = pan.x, translationY = pan.y)
                },
            )
        }
    }
}

@Composable
private fun WebtoonPage(source: ComicSource, index: Int, colorFilter: ColorFilter?) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeImage(source.pageBytes(index)) }.getOrNull()
        }
    }
    val image = bitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            Box(Modifier.fillMaxWidth().height(360.dp), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.width.toFloat() / image.height),
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    webtoon: Boolean,
    onWebtoon: (Boolean) -> Unit,
    fit: FitMode,
    onFit: (FitMode) -> Unit,
    zoom: Float,
    onZoom: (Float) -> Unit,
    contrast: Float,
    onContrast: (Float) -> Unit,
    invert: Boolean,
    onInvert: (Boolean) -> Unit,
    brightness: Float,
    onBrightness: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("حالت نمایش", color = Color.White)
        ChoiceRow(listOf("صفحه‌به‌صفحه", "پیوسته (وب‌تُون)"), if (webtoon) 1 else 0) { onWebtoon(it == 1) }

        Text("تناسب صفحه", color = Color.White)
        ChoiceRow(listOf("کل صفحه", "عرض صفحه", "ارتفاع صفحه"), fit.ordinal) { onFit(FitMode.entries[it]) }

        Text("بزرگ‌نمایی", color = Color.White)
        Slider(value = zoom, onValueChange = onZoom, valueRange = 0.5f..4f)

        Text("کنتراست", color = Color.White)
        Slider(value = contrast, onValueChange = onContrast, valueRange = 0.5f..2f)

        Text("روشنایی", color = Color.White)
        Slider(value = brightness, onValueChange = onBrightness, valueRange = 0.2f..1f)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("معکوس‌کردن رنگ‌ها", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = invert, onCheckedChange = onInvert)
        }
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            if (index == selected) {
                Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
}
