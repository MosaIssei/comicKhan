package ir.comicreader.fa.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.R
import ir.comicreader.fa.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FitMode { FIT, WIDTH, HEIGHT }

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

private fun FitMode.toContentScale(): ContentScale = when (this) {
    FitMode.FIT -> ContentScale.Fit
    FitMode.WIDTH -> ContentScale.FillWidth
    FitMode.HEIGHT -> ContentScale.FillHeight
}

private fun contrastFilter(contrast: Float): ColorFilter {
    val t = (1f - contrast) * 128f
    val values = floatArrayOf(
        contrast, 0f, 0f, 0f, t,
        0f, contrast, 0f, 0f, t,
        0f, 0f, contrast, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(values))
}

@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    var rtl by remember { mutableStateOf(prefs.mangaRightToLeft) }
    var fit by remember {
        mutableStateOf(FitMode.entries[prefs.fitModeOrdinal.coerceIn(0, FitMode.entries.size - 1)])
    }
    var brightness by remember { mutableStateOf(prefs.brightness.coerceIn(0.2f, 1f)) }
    var contrast by remember { mutableStateOf(prefs.contrast.coerceIn(0.5f, 2f)) }
    var autoCrop by remember { mutableStateOf(prefs.autoCrop) }
    var continuous by remember { mutableStateOf(prefs.continuous) }
    var orientation by remember {
        mutableStateOf(
            OrientationMode.entries[prefs.orientationOrdinal.coerceIn(0, OrientationMode.entries.size - 1)]
        )
    }
    var twoPage by remember { mutableStateOf(prefs.twoPage) }
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var bookmarks by remember(vm.uri) { mutableStateOf(prefs.bookmarks(vm.uri)) }

    val pageCount = vm.pageCount
    val step = if (twoPage && !continuous) 2 else 1
    val slots = if (pageCount == 0) 0 else (pageCount + step - 1) / step

    val pagerState = rememberPagerState(pageCount = { slots })
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val colorFilter = remember(contrast) {
        if (abs(contrast - 1f) < 0.02f) null else contrastFilter(contrast)
    }

    val displayIndex = if (continuous) listState.firstVisibleItemIndex else pagerState.currentPage * step

    fun seekToPage(page: Int) {
        val bounded = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (continuous) {
                listState.scrollToItem(bounded)
            } else {
                pagerState.scrollToPage((bounded / step).coerceIn(0, (slots - 1).coerceAtLeast(0)))
            }
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(orientation) {
        activity?.requestedOrientation = when (orientation) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    LaunchedEffect(autoCrop) { vm.applyAutoCrop(autoCrop) }

    BackHandler(onBack = onBack)

    val savedPage = remember { prefs.lastPage(vm.uri) }
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && savedPage > 0) seekToPage(savedPage)
    }
    LaunchedEffect(displayIndex, pageCount) {
        if (pageCount > 0) prefs.setLastPage(vm.uri, displayIndex)
    }

    val isBookmarked = displayIndex in bookmarks

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

            continuous -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = pageCount, key = { it }) { index ->
                    ContinuousPage(
                        vm = vm,
                        index = index,
                        colorFilter = colorFilter,
                        onTap = { chromeVisible = !chromeVisible },
                    )
                }
            }

            else -> HorizontalPager(
                state = pagerState,
                reverseLayout = rtl,
                modifier = Modifier.fillMaxSize(),
            ) { slot ->
                PageSlot(
                    vm = vm,
                    baseIndex = slot * step,
                    step = step,
                    rtl = rtl,
                    fit = fit,
                    colorFilter = colorFilter,
                    onZoomChanged = { zoomed = it },
                    onTapCenter = { chromeVisible = !chromeVisible },
                    onTapLeft = { turn(scope, pagerState, if (rtl) +1 else -1, slots) },
                    onTapRight = { turn(scope, pagerState, if (rtl) -1 else +1, slots) },
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

        AnimatedVisibility(
            visible = chromeVisible && pageCount > 0,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = vm.title,
                current = displayIndex + 1,
                total = pageCount,
                rtl = rtl,
                bookmarked = isBookmarked,
                onBack = onBack,
                onToggleDirection = {
                    rtl = !rtl
                    prefs.mangaRightToLeft = rtl
                },
                onToggleBookmark = {
                    bookmarks = prefs.toggleBookmark(vm.uri, displayIndex)
                },
                onToggleSettings = { settingsVisible = !settingsVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && pageCount > 0,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (settingsVisible) {
                ReaderSettings(
                    rtl = rtl,
                    onRtl = { rtl = it; prefs.mangaRightToLeft = it },
                    fit = fit,
                    onFit = { fit = it; prefs.fitModeOrdinal = it.ordinal },
                    continuous = continuous,
                    onContinuous = { value ->
                        continuous = value
                        prefs.continuous = value
                        seekToPage(prefs.lastPage(vm.uri))
                    },
                    brightness = brightness,
                    onBrightness = { brightness = it; prefs.brightness = it },
                    contrast = contrast,
                    onContrast = { contrast = it; prefs.contrast = it },
                    autoCrop = autoCrop,
                    onAutoCrop = { autoCrop = it; prefs.autoCrop = it },
                    orientation = orientation,
                    onOrientation = { orientation = it; prefs.orientationOrdinal = it.ordinal },
                    twoPage = twoPage,
                    onTwoPage = {
                        twoPage = it
                        prefs.twoPage = it
                        seekToPage(prefs.lastPage(vm.uri))
                    },
                    bookmarks = bookmarks,
                    onJump = { page -> seekToPage(page) },
                )
            } else if (pageCount > 1) {
                ReaderBottomBar(
                    current = displayIndex + 1,
                    total = pageCount,
                    onSeek = { page -> seekToPage(page - 1) },
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun turn(scope: CoroutineScope, state: PagerState, delta: Int, slots: Int) {
    val target = (state.currentPage + delta).coerceIn(0, (slots - 1).coerceAtLeast(0))
    scope.launch { state.animateScrollToPage(target) }
}

@Composable
private fun PageSlot(
    vm: ReaderViewModel,
    baseIndex: Int,
    step: Int,
    rtl: Boolean,
    fit: FitMode,
    colorFilter: ColorFilter?,
    onZoomChanged: (Boolean) -> Unit,
    onTapCenter: () -> Unit,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    val single = step == 1
    val highQuality = scale > 1.25f

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(fit) {
                fun bounds(s: Float): Pair<Float, Float> {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val a = aspect
                    var contentW = w
                    var contentH = h
                    if (single && a != null && a > 0f) {
                        when (fit) {
                            FitMode.FIT -> {
                                val v = min(w, h * a)
                                contentW = v
                                contentH = v / a
                            }
                            FitMode.WIDTH -> {
                                contentW = w
                                contentH = w / a
                            }
                            FitMode.HEIGHT -> {
                                contentH = h
                                contentW = h * a
                            }
                        }
                    }
                    return max(0f, (contentW * s - w) / 2f) to max(0f, (contentH * s - h) / 2f)
                }

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        if (event.changes.size >= 2) {
                            zooming = true
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                            val pan = event.calculatePan()
                            val (maxX, maxY) = bounds(newScale)
                            scale = newScale
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!zooming) {
                            val change = event.changes.firstOrNull { it.pressed } ?: continue
                            val pan = change.positionChange()
                            if (pan != Offset.Zero) {
                                val (maxX, maxY) = bounds(scale)
                                val next = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                                if (next != offset) {
                                    offset = next
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { position ->
                        val w = size.width.toFloat()
                        when {
                            position.x < w / 3f -> onTapLeft()
                            position.x > w * 2f / 3f -> onTapRight()
                            else -> onTapCenter()
                        }
                    },
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        ) {
            val indices = (0 until step).map { baseIndex + it }.filter { it < vm.pageCount }
            val ordered = if (rtl) indices else indices.reversed()
            ordered.forEach { index ->
                PageImage(
                    vm = vm,
                    index = index,
                    highQuality = highQuality && single,
                    fit = fit,
                    colorFilter = colorFilter,
                    onAspect = { if (single) aspect = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PageImage(
    vm: ReaderViewModel,
    index: Int,
    highQuality: Boolean,
    fit: FitMode,
    colorFilter: ColorFilter?,
    onAspect: (Float) -> Unit,
    modifier: Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index, highQuality) {
        value = vm.loadPage(index, highQuality)
    }
    val image = bitmap
    LaunchedEffect(image) {
        if (image != null && image.height > 0) onAspect(image.width.toFloat() / image.height)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.cd_page),
                contentScale = fit.toContentScale(),
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ContinuousPage(
    vm: ReaderViewModel,
    index: Int,
    colorFilter: ColorFilter?,
    onTap: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index) {
        value = vm.loadPage(index, false)
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
            val ratio = image.width.toFloat() / image.height
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.cd_page),
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .pointerInput(Unit) { detectTapGestures { onTap() } },
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
    bookmarked: Boolean,
    onBack: () -> Unit,
    onToggleDirection: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleSettings: () -> Unit,
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
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(
                        if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark
                    ),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleDirection) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(if (rtl) R.string.dir_rtl else R.string.dir_ltr),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.reader_settings),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(current: Int, total: Int, onSeek: (Int) -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.65f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.page_of, current, total),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = current.toFloat(),
                onValueChange = { onSeek(it.roundToInt()) },
                valueRange = 1f..total.coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReaderSettings(
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
    fit: FitMode,
    onFit: (FitMode) -> Unit,
    continuous: Boolean,
    onContinuous: (Boolean) -> Unit,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    contrast: Float,
    onContrast: (Float) -> Unit,
    autoCrop: Boolean,
    onAutoCrop: (Boolean) -> Unit,
    orientation: OrientationMode,
    onOrientation: (OrientationMode) -> Unit,
    twoPage: Boolean,
    onTwoPage: (Boolean) -> Unit,
    bookmarks: Set<Int>,
    onJump: (Int) -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.88f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bookmarks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bookmarks),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bookmarks.sorted().forEach { page ->
                        OutlinedButton(onClick = { onJump(page) }) {
                            Text(stringResource(R.string.page_short, page + 1))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.view_mode),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            ChoiceRow(
                labels = listOf(
                    stringResource(R.string.view_paged),
                    stringResource(R.string.view_continuous),
                ),
                selected = if (continuous) 1 else 0,
                onSelect = { onContinuous(it == 1) },
            )

            Text(
                text = stringResource(R.string.direction),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            ChoiceRow(
                labels = listOf(stringResource(R.string.dir_rtl), stringResource(R.string.dir_ltr)),
                selected = if (rtl) 0 else 1,
                onSelect = { onRtl(it == 0) },
            )

            Text(
                text = stringResource(R.string.fit_mode),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            ChoiceRow(
                labels = listOf(
                    stringResource(R.string.fit_screen),
                    stringResource(R.string.fit_width),
                    stringResource(R.string.fit_height),
                ),
                selected = fit.ordinal,
                onSelect = { onFit(FitMode.entries[it]) },
            )

            Text(
                text = stringResource(R.string.orientation),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            ChoiceRow(
                labels = listOf(
                    stringResource(R.string.orient_auto),
                    stringResource(R.string.orient_portrait),
                    stringResource(R.string.orient_landscape),
                ),
                selected = orientation.ordinal,
                onSelect = { onOrientation(OrientationMode.entries[it]) },
            )

            Text(
                text = stringResource(R.string.brightness),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = brightness,
                onValueChange = onBrightness,
                valueRange = 0.2f..1f,
            )

            Text(
                text = stringResource(R.string.contrast),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = contrast,
                onValueChange = onContrast,
                valueRange = 0.5f..2f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.auto_crop),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoCrop, onCheckedChange = onAutoCrop)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.two_page),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = twoPage, onCheckedChange = onTwoPage)
            }
        }
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            if (index == selected) {
                Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
