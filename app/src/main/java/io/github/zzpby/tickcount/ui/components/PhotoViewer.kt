package io.github.zzpby.tickcount.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.data.FootprintMedia
import io.github.zzpby.tickcount.data.PhotoExif
import io.github.zzpby.tickcount.ui.theme.textHalo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

/**
 * Where one viewer's entries come from and what each of them is: the two
 * pages the app flips through — the countdowns' album photos and the
 * footprints' mixed media — answer these three questions differently, and
 * nothing else about the viewer needs to know which is on screen.
 */
class ViewerMedia(
    val kind: (String) -> Kind,
    /** The page a still shows at full screen — the whole picture, whatever it costs. */
    val page: (Context, String) -> Bitmap?,
    /** The little one a strip slot shows — a miniature, whatever is cheapest. */
    val decode: (Context, String, Int) -> Bitmap?,
    val uri: (Context, String) -> Uri?,
) {
    enum class Kind { PHOTO, ANIMATED, VIDEO }

    companion object {
        /** The countdowns' own photos: album references and scaled copies. */
        val Photos = ViewerMedia(
            kind = { Kind.PHOTO },
            page = { context, entry -> EventBackgrounds.bitmap(context, entry) },
            decode = { context, entry, edge -> EventBackgrounds.thumbnail(context, entry, edge) },
            uri = { context, entry -> EventBackgrounds.mediaUri(context, entry) },
        )

        /** The footprints' visits: stills, moving GIFs, videos. */
        val Footprints = ViewerMedia(
            kind = { entry ->
                when {
                    FootprintMedia.isVideo(entry) -> Kind.VIDEO
                    FootprintMedia.isAnimated(entry) -> Kind.ANIMATED
                    else -> Kind.PHOTO
                }
            },
            page = { context, entry -> FootprintMedia.bitmap(context, entry) },
            decode = { context, entry, edge -> FootprintMedia.thumbnail(context, entry, edge) },
            uri = { context, entry -> FootprintMedia.mediaUri(context, entry) },
        )
    }
}

/**
 * The photos one at a time, full-screen on black, the viewer guide's whole
 * chapter: pinch and double-tap zoom with the edges held, a pager that gives
 * up scrolling while a picture is enlarged, a pull-down that carries the
 * whole stack away — distance or speed, either earns the close — chrome a
 * tap hides, a thumbnail strip that stays in step with the page, and an
 * information panel of glass that reads what the photo says about itself.
 */
@Composable
fun PhotoViewer(
    photos: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit,
    media: ViewerMedia = ViewerMedia.Photos,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, photos.size - 1),
    ) { photos.size }
    val scope = rememberCoroutineScope()

    var uiVisible by remember { mutableStateOf(true) }
    var infoOpen by remember { mutableStateOf(false) }
    // The watershed of the whole component: an enlarged page silences the
    // pager, and the pull-down with it.
    var zoomed by remember { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    // The drag's recent past, for the speed half of the close judgement.
    val history = remember { ArrayDeque<Pair<Long, Float>>() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .dialogWindowGuard()
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = pull
                        val settle = 1f - (pull / 1400f).coerceIn(0f, 0.4f)
                        scaleX = settle
                        scaleY = settle
                    }
                    // The pull-down only exists at rest: an enlarged picture's
                    // single finger is panning, not closing.
                    .pointerInput(zoomed) {
                        if (!zoomed) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val recent = history.lastOrNull()
                                    val old = history.firstOrNull()
                                    val velocity = if (recent != null && old != null &&
                                        recent.first > old.first
                                    ) {
                                        (recent.second - old.second) /
                                            ((recent.first - old.first) / 1000f)
                                    } else {
                                        0f
                                    }
                                    history.clear()
                                    if (pull > 380f || velocity > 1000f) {
                                        onDismiss()
                                    } else {
                                        // A spring home, not a tween: the stack
                                        // falls back with its own weight.
                                        scope.launch {
                                            animate(pull, 0f, animationSpec = spring<Float>(dampingRatio = 0.7f)) { v, _ -> pull = v }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    history.clear()
                                    scope.launch {
                                        animate(pull, 0f, animationSpec = spring<Float>(dampingRatio = 0.7f)) { v, _ -> pull = v }
                                    }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                pull = (pull + dragAmount).coerceAtLeast(0f)
                                history.addLast(System.currentTimeMillis() to pull)
                                while (history.size > 2 && history.last().first - history.first().first > 120L) {
                                    history.removeFirst()
                                }
                            }
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !zoomed,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (media.kind(photos[page])) {
                        ViewerMedia.Kind.PHOTO -> ZoomablePhoto(
                            context = context,
                            entry = photos[page],
                            media = media,
                            onToggleUi = { uiVisible = !uiVisible },
                            onZoomChanged = { zoomed = it },
                        )
                        // A moving picture takes a tap the way the stills take
                        // theirs — the chrome owes it the same courtesy.
                        ViewerMedia.Kind.ANIMATED -> AnimatedPage(
                            context = context,
                            entry = photos[page],
                            media = media,
                            onTap = { uiVisible = !uiVisible },
                        )
                        // A video owns its own touch: the tap plays and pauses,
                        // and the chrome keeps out of the argument.
                        ViewerMedia.Kind.VIDEO -> VideoPage(context, photos[page], media)
                    }
                }
            }

            // The count, top centre.
            AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = textHalo()),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 40.dp),
                )
            }

            // The close button, top right.
            AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
                GlassCircleButton(
                    contentDescription = null,
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 34.dp, end = 18.dp),
                ) {
                    CloseIcon(iconSize = 16.dp, color = Color.White.copy(alpha = 0.9f))
                }
            }

            // The information toggle, top left — photographs are the only
            // entries with something to say.
            val currentEntry = photos[pagerState.currentPage.coerceIn(photos.indices)]
            val currentIsPhoto = media.kind(currentEntry) == ViewerMedia.Kind.PHOTO
            if (currentIsPhoto) {
                AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
                    GlassCircleButton(
                        contentDescription = stringResource(R.string.exif_show),
                        onClick = { infoOpen = !infoOpen },
                        modifier = Modifier.padding(top = 34.dp, start = 18.dp),
                    ) {
                        InfoIcon(iconSize = 18.dp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            // The strip gives way to the panel it feeds: one bottom, never both.
            if (!infoOpen) {
                AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    ThumbnailStrip(
                        photos = photos,
                        currentPage = pagerState.currentPage,
                        media = media,
                        onPick = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.padding(bottom = 22.dp),
                    )
                }
            } else {
                val exif by produceState<PhotoExif?>(null, currentEntry) {
                    value = PhotoExif.load(context, media.uri(context, currentEntry))
                }
                AnimatedVisibility(
                    uiVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    GlassInfoPanel(exif = exif)
                }
            }
        }
    }
}

/**
 * One photo, at any size the finger asks of it: two fingers pinch between
 * one and five, a single finger pans inside the edges the picture's own
 * fitted size draws, a double-tap jumps between rest and 2.5×, and a tap
 * asks the chrome to show or hide itself.
 */
@Composable
private fun ZoomablePhoto(
    context: Context,
    entry: String,
    media: ViewerMedia,
    onToggleUi: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    // The whole picture, the page's own right: the miniature the strip
    // reads would go soft the moment the screen is larger than it, and a
    // camera's original never apologises.
    val bitmap by produceState<Bitmap?>(null, entry) {
        value = withContext(Dispatchers.IO) { media.page(context, entry) }
    }
    var scale by remember(entry) { mutableFloatStateOf(1f) }
    var offset by remember(entry) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    // The gesture arbitration the guide draws in prose, written out by hand:
    // two fingers are always the picture's own (zoom, and pan beneath it),
    // a single finger belongs to the picture only while it is enlarged — at
    // rest it belongs to the pager, unconsumed, so the page still turns.
    // (The stock `transformable` claims one-finger pans too, and a pager
    // behind it never scrolls again.)
    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(entry) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        when {
                            pressed >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                val next = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = next
                                offset = if (next > 1f) offset + panChange else Offset.Zero
                            }
                            // 1.01, not 1: a released pinch settles on values
                            // like 1.0001, and a pager waiting for exactly 1
                            // would wait forever.
                            pressed == 1 && scale > 1.01f -> {
                                val panChange = event.calculatePan()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                offset += panChange
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(entry) {
                detectTapGestures(
                    onTap = { onToggleUi() },
                    // animate(), not an Animatable: the gesture writes the same
                    // value every frame, and a second writer makes the picture
                    // jump at the first touch after the double-tap.
                    onDoubleTap = {
                        scope.launch {
                            if (scale > 1.01f) {
                                animate(scale, 1f, animationSpec = tween(260)) { v, _ -> scale = v }
                                if (scale <= 1.01f) offset = Offset.Zero
                            } else {
                                animate(scale, 2.5f, animationSpec = tween(260)) { v, _ -> scale = v }
                            }
                        }
                    },
                )
            },
    ) {
        val pixels = bitmap ?: return@Box
        Image(
            bitmap = pixels.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // Clamped where it is drawn, by the fitted size rather than
                    // the container's: a landscape letterboxed in a portrait
                    // screen must not drag out into its own black borders.
                    val fit = min(
                        size.width.toFloat() / pixels.width,
                        size.height.toFloat() / pixels.height,
                    )
                    val maxX = pixels.width * fit * (scale - 1f) / 2f
                    val maxY = pixels.height * fit * (scale - 1f) / 2f
                    translationX = if (maxX > 0f) offset.x.coerceIn(-maxX, maxX) else 0f
                    translationY = if (maxY > 0f) offset.y.coerceIn(-maxY, maxY) else 0f
                },
        )
    }
}

/**
 * The photos in miniature, in step with the page both ways: the strip walks
 * to keep the current thumbnail centred, a thumbnail brings its page over.
 */
@Composable
private fun ThumbnailStrip(
    photos: List<String>,
    currentPage: Int,
    media: ViewerMedia,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    // Minus two: the picked thumbnail lands near the middle, not hard
    // against the leading edge.
    LaunchedEffect(currentPage) {
        listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
    ) {
        itemsIndexed(photos) { index, entry ->
            // Sampled at strip size: fifty originals in fifty slots is an
            // out-of-memory notice, not a strip.
            val thumb by produceState<android.graphics.Bitmap?>(null, entry) {
                value = withContext(Dispatchers.IO) {
                    media.decode(context, entry, 256)
                }
            }
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val pixels = thumb
                if (pixels != null) {
                    Image(
                        bitmap = pixels.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * What the photo says about itself, on the viewer guide's three-layer glass
 * — the thickness gradient, the top highlight, the one bright line along
 * the top edge. A panel flush with the bottom draws no bottom edge of its
 * own: nothing is standing on it.
 */
@Composable
private fun GlassInfoPanel(exif: PhotoExif?) {
    val labelColor = Color.White.copy(alpha = 0.62f)
    val valueColor = Color.White.copy(alpha = 0.95f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .drawBehind {
                // ① The thickness gradient.
                drawRect(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.16f),
                            0.52f to Color.White.copy(alpha = 0.06f),
                            1.0f to Color.White.copy(alpha = 0.12f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                // ② The top highlight.
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.35f to Color.Transparent,
                    ),
                )
                // ③ The bright line along the top edge, the only edge.
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        if (exif == null) {
            Text(
                text = stringResource(R.string.exif_none),
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        InfoRow(stringResource(R.string.exif_taken), exif.takenAtLine(), labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_camera), exif.camera, labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_aperture), exif.aperture, labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_shutter), exif.shutter, labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_iso), exif.iso, labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_resolution), exif.resolution, labelColor, valueColor)
        InfoRow(stringResource(R.string.exif_size), exif.fileSize?.let { sizeLine(it) }, labelColor, valueColor)
    }
}

@Composable
private fun InfoRow(label: String, value: String?, labelColor: Color, valueColor: Color) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** The viewer's own glass circles — close, information — always where the thumb expects them. */
@Composable
private fun GlassCircleButton(
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

private fun sizeLine(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** One still of an animated GIF, animated: the platform's own decoder, drawn where it is. */
@Composable
private fun AnimatedPage(
    context: Context,
    entry: String,
    media: ViewerMedia,
    onTap: () -> Unit,
) {
    val drawable = remember(entry) {
        runCatching {
            val uri = media.uri(context, entry) ?: return@runCatching null
            ImageDecoder.createSource(context.contentResolver, uri)
                .let { ImageDecoder.decodeDrawable(it) }
        }.getOrNull()
    }
    if (drawable != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                },
        ) {
            AndroidView(
                factory = { viewContext ->
                    android.widget.ImageView(viewContext).apply {
                        setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A video through the platform's own player: a tap plays, another pauses. */
@Composable
private fun VideoPage(
    context: Context,
    entry: String,
    media: ViewerMedia,
) {
    val media3Uri = remember(entry) { media.uri(context, entry) } ?: return
    val videoView = remember(media3Uri) {
        android.widget.VideoView(context).apply {
            setVideoURI(media3Uri)
            setOnPreparedListener { it.isLooping = true; start() }
        }
    }
    DisposableEffect(media3Uri) {
        onDispose {
            videoView.stopPlayback()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (videoView.isPlaying) videoView.pause() else videoView.start()
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
