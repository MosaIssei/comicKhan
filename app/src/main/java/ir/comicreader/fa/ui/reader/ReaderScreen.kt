package ir.comicreader.fa.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.comicreader.fa.R
import ir.comicreader.fa.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class FitMode { FIT, WIDTH, HEIGHT }

enum class OrientationMode { AUTO, PORTRAIT, LANDSCAPE }

private fun contentSizeFor(boxW: Float, boxH: Float, aspect: Float, fit: FitMode): Size = when (fit) {
    FitMode.FIT -> if (boxW / boxH >= aspect) Size(boxH * aspect, boxH) else Size(boxW, boxW / aspect)
    FitMode.WIDTH -> Size(boxW, boxW / aspect)
    FitMode.HEIGHT -> Size(boxH * aspect, boxH)
}

private fun FitMode.toContentScale(): ContentScale = when (this) {
    FitMode.FIT -> ContentScale.Fit
    FitMode.WIDTH -> ContentScale.FillWidth
    FitMode.HEIGHT -> ContentScale.FillHeight
}

/** Rounds a wanted pixel size up to a 512px step so tiny zoom changes don't re-decode. */
private fun bucketSize(px: Float): Int {
    val clamped = px.coerceIn(512f, ReaderViewModel.MAX_DIM.toFloat())
    val steps = ((clamped + 511f) / 512f).toInt()
    return (steps * 512).coerceIn(512, ReaderViewModel.MAX_DIM)
}

private const val REGION_CAP = 4096

/** How far the webtoon strip can be magnified in continuous mode. */
private const val MAX_WEBTOON_ZOOM = 4f

private fun regionSample(rect: Rect): Int {
    val longEdge = max(rect.width(), rect.height())
    var sample = 1
    while (longEdge / sample > REGION_CAP) sample *= 2
    return sample
}

/** The part of the page (in source pixels) currently visible in the viewport. */
private fun visibleSourceRect(
    content: Size,
    box: IntSize,
    scale: Float,
    offset: Offset,
    src: IntSize,
): Rect {
    val contentW = content.width * scale
    val contentH = content.height * scale
    val left = box.width / 2f - contentW / 2f + offset.x
    val top = box.height / 2f - contentH / 2f + offset.y
    val sxPerPx = src.width / contentW
    val syPerPx = src.height / contentH
    val l = ((0f - left) * sxPerPx)
    val t = ((0f - top) * syPerPx)
    val r = ((box.width - left) * sxPerPx)
    val b = ((box.height - top) * syPerPx)
    val rect = Rect(
        floor(l.toDouble()).toInt().coerceAtLeast(0),
        floor(t.toDouble()).toInt().coerceAtLeast(0),
        ceil(r.toDouble()).toInt().coerceAtMost(src.width),
        ceil(b.toDouble()).toInt().coerceAtMost(src.height),
    )
    if (rect.width() <= 0 || rect.height() <= 0) return Rect(0, 0, src.width, src.height)
    return rect
}

/** Combined contrast + (optional) color inversion filter, or null when it has no effect. */
private fun imageColorFilter(contrast: Float, invert: Boolean): ColorFilter? {
    if (!invert && abs(contrast - 1f) < 0.02f) return null
    val scale = if (invert) -contrast else contrast
    val offset = if (invert) 255f - (1f - contrast) * 128f else (1f - contrast) * 128f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
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
    var invert by remember { mutableStateOf(prefs.invertColors) }
    var autoCrop by remember { mutableStateOf(prefs.autoCrop) }
    var continuous by remember { mutableStateOf(prefs.continuous) }
    var webtoonZoom by remember { mutableStateOf(prefs.webtoonZoom.coerceIn(1f, 4f)) }
    var orientation by remember {
        mutableStateOf(
            OrientationMode.entries[prefs.orientationOrdinal.coerceIn(0, OrientationMode.entries.size - 1)]
        )
    }
    var twoPage by remember { mutableStateOf(prefs.twoPage) }
    var chromeVisible by remember { mutableStateOf(true) }
    var settingsVisible by remember { mutableStateOf(false) }
    var jumpOpen by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var zoomed by remember { mutableStateOf(false) }
    var bookmarks by remember(vm.uri) { mutableStateOf(prefs.bookmarks(vm.uri)) }

    val pageCount = vm.pageCount
    val step = if (twoPage && !continuous) 2 else 1
    val slots = if (pageCount == 0) 0 else (pageCount + step - 1) / step

    val pagerState = rememberPagerState(pageCount = { slots })
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val colorFilter = remember(contrast, invert) { imageColorFilter(contrast, invert) }
    val density = LocalDensity.current
    // Webtoon items are `fillMaxWidth()` inside a column that is always exactly one screen
    // wide, so a page's on-screen height is just its width divided by its aspect.
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

    val displayIndex = if (continuous) listState.firstVisibleItemIndex else pagerState.currentPage * step

    // Webtoon item heights must be known before the list is built: every item is sized from
    // its pre-read aspect and never resized afterwards, so the list never re-anchors while
    // you scroll back up into pages whose bitmaps only load later.
    var webtoonAspects by remember(vm.uri, vm.generation, autoCrop) { mutableStateOf<List<Float>?>(null) }

    /** How far into the first visible page the list is scrolled, as a fraction (0..1). */
    fun currentPageFraction(): Float {
        if (!continuous) return 0f
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
        if (first.size <= 0) return 0f
        return (-first.offset.toFloat() / first.size).coerceIn(0f, 1f)
    }

    suspend fun applySeek(page: Int, fraction: Float) {
        val bounded = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (continuous) {
            val ratio = webtoonAspects?.getOrNull(bounded) ?: 0f
            val height = if (ratio > 0f) screenWidthPx / ratio else 0f
            val offset = (fraction * height).roundToInt().coerceIn(0, max(0, height.toInt() - 1))
            listState.scrollToItem(bounded, offset)
        } else {
            pagerState.scrollToPage((bounded / step).coerceIn(0, (slots - 1).coerceAtLeast(0)))
        }
    }

    fun seekToPage(page: Int, fraction: Float = 0f) {
        scope.launch { applySeek(page, fraction) }
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
    LaunchedEffect(webtoonZoom) { prefs.webtoonZoom = webtoonZoom }

    LaunchedEffect(continuous, pageCount, vm.generation, autoCrop) {
        if (continuous && pageCount > 0) {
            if (webtoonAspects == null) {
                webtoonAspects = withContext(Dispatchers.IO) {
                    List(pageCount) { i -> vm.displayAspect(i) ?: 0.75f }
                }
            }
        } else if (!continuous) {
            webtoonAspects = null
        }
    }

    // Resume only once the list actually exists (the webtoon shows a spinner while the
    // aspects are read), otherwise the seek lands on a list that isn't there yet.
    var seekDone by remember(vm.generation) { mutableStateOf(false) }
    LaunchedEffect(pageCount, continuous, webtoonAspects, vm.generation) {
        if (!seekDone && pageCount > 0 && (!continuous || webtoonAspects != null)) {
            if (vm.startPage > 0) applySeek(vm.startPage, vm.startOffset)
            // Armed only once the jump has landed, so the position that was just restored
            // can never be overwritten with page 0 on the way there.
            seekDone = true
        }
    }
    LaunchedEffect(displayIndex, seekDone, vm.uri, vm.generation) {
        if (seekDone && pageCount > 0 && vm.uri.isNotEmpty()) {
            prefs.setLastPage(vm.uri, displayIndex)
            prefs.setLastPageOffset(vm.uri, currentPageFraction())
        }
    }

    BackHandler {
        // Leaving the reader is the moment that matters most: store exactly where we are,
        // including the offset inside the page (ComicScreen's PAGEOFFSET).
        if (seekDone && pageCount > 0 && vm.uri.isNotEmpty()) {
            prefs.setLastPage(vm.uri, displayIndex)
            prefs.setLastPageOffset(vm.uri, currentPageFraction())
        }
        onBack()
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

            continuous -> {
                val a = webtoonAspects
                if (a == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    val baseWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp }
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    var gestureZoom by remember { mutableFloatStateOf(1f) }
                    var pan by remember { mutableStateOf(Offset.Zero) }
                    var panReady by remember { mutableStateOf(false) }

                    // Zoom is a draw-time transform on the column — the same thing the
                    // original does with a canvas Matrix — so the column is never re-measured
                    // and page heights never change. That is why nothing shifts when the
                    // fingers lift and nothing jumps when you scroll back up.
                    val scale = (webtoonZoom * gestureZoom).coerceIn(1f, MAX_WEBTOON_ZOOM)

                    fun clampPan(value: Offset, forScale: Float): Offset {
                        val w = boxSize.width.toFloat()
                        val h = boxSize.height.toFloat()
                        if (w <= 0f || h <= 0f) return Offset.Zero
                        return Offset(
                            value.x.coerceIn(w * (1f - forScale), 0f),
                            value.y.coerceIn(h * (1f - forScale), 0f),
                        )
                    }

                    // A restored zoom starts centred and clipped instead of pinned to a corner.
                    LaunchedEffect(boxSize) {
                        if (!panReady && boxSize.width > 0) {
                            panReady = true
                            val s = webtoonZoom.coerceIn(1f, MAX_WEBTOON_ZOOM)
                            pan = clampPan(
                                Offset(boxSize.width * (1f - s) / 2f, boxSize.height * (1f - s) / 2f),
                                s,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { boxSize = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var pinching = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) {
                                            if (pinching && gestureZoom != 1f) {
                                                // Fold the live factor into the saved one. The
                                                // rendered transform is identical either way, so
                                                // releasing the pinch moves nothing.
                                                webtoonZoom =
                                                    (webtoonZoom * gestureZoom).coerceIn(1f, MAX_WEBTOON_ZOOM)
                                                gestureZoom = 1f
                                            }
                                            break
                                        }
                                        if (event.changes.size >= 2) {
                                            pinching = true
                                            val focal = event.calculateCentroid()
                                            val old = (webtoonZoom * gestureZoom).coerceIn(1f, MAX_WEBTOON_ZOOM)
                                            val next = (old * event.calculateZoom()).coerceIn(1f, MAX_WEBTOON_ZOOM)
                                            if (next != old) {
                                                // Move the translation so the point under the
                                                // fingers stays put: t' = f - (f - t) * k.
                                                val k = next / old
                                                pan = clampPan(focal - (focal - pan) * k, next)
                                                gestureZoom = next / webtoonZoom
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else if (!pinching &&
                                            (webtoonZoom * gestureZoom) > 1.01f
                                        ) {
                                            // While zoomed a sideways drag pans; vertical drags
                                            // still belong to the list.
                                            val s = (webtoonZoom * gestureZoom).coerceIn(1f, MAX_WEBTOON_ZOOM)
                                            val change = event.changes.firstOrNull { it.pressed } ?: continue
                                            val delta = change.positionChange()
                                            if (abs(delta.x) > abs(delta.y)) {
                                                val next = clampPan(pan + Offset(delta.x, 0f), s)
                                                if (next != pan) {
                                                    pan = next
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(baseWidth)
                                .fillMaxHeight()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    transformOrigin = TransformOrigin(0f, 0f),
                                    translationX = pan.x,
                                    translationY = pan.y,
                                ),
                        ) {
                            items(count = pageCount, key = { it }) { index ->
                                ContinuousPage(
                                    vm = vm,
                                    index = index,
                                    aspect = a.getOrElse(index) { 0.75f },
                                    colorFilter = colorFilter,
                                    onTap = { chromeVisible = !chromeVisible },
                                )
                            }
                        }
                    }
                }
            }

            else -> HorizontalPager(
                state = pagerState,
                reverseLayout = rtl,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    // A small drag is enough to turn the page (default is 50%).
                    snapPositionalThreshold = 0.12f,
                ),
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
                    onTap = { chromeVisible = !chromeVisible },
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
                        seekToPage(prefs.lastPage(vm.uri), prefs.lastPageOffset(vm.uri))
                    },
                    brightness = brightness,
                    onBrightness = { brightness = it; prefs.brightness = it },
                    contrast = contrast,
                    onContrast = { contrast = it; prefs.contrast = it },
                    invert = invert,
                    onInvert = { invert = it; prefs.invertColors = it },
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
                    onJumpToPage = { jumpOpen = true },
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

    if (jumpOpen) {
        AlertDialog(
            onDismissRequest = { jumpOpen = false },
            title = { Text(stringResource(R.string.jump_to_page)) },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { input -> jumpText = input.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    jumpText.toIntOrNull()?.let { seekToPage(it - 1) }
                    jumpOpen = false
                    jumpText = ""
                }) { Text(stringResource(R.string.go)) }
            },
            dismissButton = {
                TextButton(onClick = { jumpOpen = false; jumpText = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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

@Composable
private fun PageSlot(
    vm: ReaderViewModel,
    baseIndex: Int,
    step: Int,
    rtl: Boolean,
    fit: FitMode,
    colorFilter: ColorFilter?,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val single = step == 1

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { box = it }
            .pointerInput(fit, single) {
                fun bounds(s: Float): Pair<Float, Float> {
                    val bw = box.width.toFloat()
                    val bh = box.height.toFloat()
                    if (bw <= 0f || bh <= 0f) return 0f to 0f
                    if (single) {
                        val a = aspect ?: return 0f to 0f
                        if (a <= 0f) return 0f to 0f
                        val content = contentSizeFor(bw, bh, a, fit)
                        return max(0f, (content.width * s - bw) / 2f) to
                            max(0f, (content.height * s - bh) / 2f)
                    }
                    return max(0f, (bw * s - bw) / 2f) to max(0f, (bh * s - bh) / 2f)
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
                        } else if (!zooming && scale > 1.01f) {
                            // Only pan when actually zoomed; otherwise leave the drag to the
                            // pager so turning pages stays easy.
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
        contentAlignment = Alignment.Center,
    ) {
        if (single) {
            val a = aspect
            val bw = box.width.toFloat()
            val bh = box.height.toFloat()
            val content = if (a != null && a > 0f && bw > 0f && bh > 0f) {
                contentSizeFor(bw, bh, a, fit)
            } else {
                null
            }
            if (vm.useRegionDecoding) {
                RegionImage(
                    vm = vm,
                    index = baseIndex,
                    content = content,
                    box = box,
                    scale = scale,
                    offset = offset,
                    colorFilter = colorFilter,
                    onAspect = { if (aspect != it) aspect = it },
                )
            } else {
                val sizeModifier = if (content != null) {
                    with(density) {
                        Modifier.requiredSize((content.width * scale).toDp(), (content.height * scale).toDp())
                    }
                } else {
                    Modifier.fillMaxSize()
                }
                val requiredPx = if (content != null) {
                    bucketSize(max(content.width, content.height) * scale)
                } else {
                    2048
                }
                PageImage(
                    vm = vm,
                    index = baseIndex,
                    requiredPx = requiredPx,
                    contentScale = if (content != null) ContentScale.FillBounds else fit.toContentScale(),
                    colorFilter = colorFilter,
                    onAspect = { if (aspect != it) aspect = it },
                    modifier = sizeModifier.graphicsLayer(
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                )
            }
        } else {
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
                        requiredPx = 2560,
                        contentScale = fit.toContentScale(),
                        colorFilter = colorFilter,
                        onAspect = {},
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageImage(
    vm: ReaderViewModel,
    index: Int,
    requiredPx: Int,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    onAspect: (Float) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(index, requiredPx, vm.autoCrop, vm.generation) {
        vm.requestPage(index, requiredPx, 0)
    }
    val revision = vm.revision
    val image = remember(revision, vm.generation, index, requiredPx, vm.autoCrop) {
        vm.cachedPage(index, requiredPx, 0)
    }
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
                contentScale = contentScale,
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RegionImage(
    vm: ReaderViewModel,
    index: Int,
    content: Size?,
    box: IntSize,
    scale: Float,
    offset: Offset,
    colorFilter: ColorFilter?,
    onAspect: (Float) -> Unit,
) {
    val src by produceState<IntSize?>(initialValue = null, index, vm.generation) { value = vm.pageSize(index) }
    val srcSize = src
    LaunchedEffect(srcSize) {
        if (srcSize != null && srcSize.height > 0) onAspect(srcSize.width.toFloat() / srcSize.height)
    }
    if (srcSize == null || content == null || box.width == 0 || box.height == 0) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }

    // Grid-snapped decode window (about 2x the visible area, aligned to steps of one
    // viewport). The key changes only when panning crosses a step, so most pans just
    // translate the already-decoded bitmap instead of re-decoding every frame.
    val viewport = visibleSourceRect(content, box, scale, offset, srcSize)
    val cellW = max(1, viewport.width())
    val cellH = max(1, viewport.height())
    val gx = (viewport.left / cellW) * cellW
    val gy = (viewport.top / cellH) * cellH
    val decodeRect = Rect(
        gx.coerceAtLeast(0),
        gy.coerceAtLeast(0),
        (gx + cellW * 2).coerceAtMost(srcSize.width),
        (gy + cellH * 2).coerceAtMost(srcSize.height),
    )

    var current by remember(index, vm.generation) { mutableStateOf<Pair<Rect, ImageBitmap>?>(null) }
    LaunchedEffect(index, decodeRect, vm.generation) {
        val bitmap = vm.pageRegion(index, decodeRect, regionSample(decodeRect))
        if (bitmap != null) current = decodeRect to bitmap
    }

    val shown = current
    if (shown == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    val rect = shown.first
    val image = shown.second

    val contentW = content.width * scale
    val contentH = content.height * scale
    val left = box.width / 2f - contentW / 2f + offset.x
    val top = box.height / 2f - contentH / 2f + offset.y
    val kx = contentW / srcSize.width.toFloat()
    val ky = contentH / srcSize.height.toFloat()

    Canvas(Modifier.fillMaxSize()) {
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (left + rect.left * kx).roundToInt(),
                (top + rect.top * ky).roundToInt(),
            ),
            dstSize = IntSize(
                (rect.width() * kx).roundToInt().coerceAtLeast(1),
                (rect.height() * ky).roundToInt().coerceAtLeast(1),
            ),
            colorFilter = colorFilter,
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
private fun ContinuousPage(
    vm: ReaderViewModel,
    index: Int,
    aspect: Float,
    colorFilter: ColorFilter?,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    LaunchedEffect(index, widthPx, vm.autoCrop, vm.generation) {
        vm.requestPage(index, ReaderViewModel.MAX_DIM, widthPx)
    }
    val revision = vm.revision
    val image = remember(revision, vm.generation, index, widthPx, vm.autoCrop) {
        vm.cachedPage(index, ReaderViewModel.MAX_DIM, widthPx)
    }
    // The item is sized from the pre-read aspect only. Falling back to the decoded bitmap's
    // own ratio would resize the item the moment it loads, which shifts every page below it
    // — the jump you see when scrolling back up into pages that loaded late.
    val ratio = if (aspect > 0f) aspect else 0.75f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.cd_page),
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxWidth(),
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
    invert: Boolean,
    onInvert: (Boolean) -> Unit,
    autoCrop: Boolean,
    onAutoCrop: (Boolean) -> Unit,
    orientation: OrientationMode,
    onOrientation: (OrientationMode) -> Unit,
    twoPage: Boolean,
    onTwoPage: (Boolean) -> Unit,
    bookmarks: Set<Int>,
    onJump: (Int) -> Unit,
    onJumpToPage: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.88f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onJumpToPage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.jump_to_page))
            }

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
                    text = stringResource(R.string.invert_colors),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = invert, onCheckedChange = onInvert)
            }

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
