package io.github.zzpby.tickcount.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.domain.VoyageStop
import io.github.zzpby.tickcount.domain.passed
import io.github.zzpby.tickcount.domain.voyageLength
import io.github.zzpby.tickcount.domain.voyageStops
import io.github.zzpby.tickcount.ui.theme.XingKai
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far up the river art the water the boat rides on sits. */
private const val WATERLINE_FRACTION = 0.67f

/** Where the boat sits across the screen: left of centre, so names float ahead of it. */
private const val BOAT_ANCHOR_FRACTION = 0.34f

/** The boat's own width; its height follows the art's proportions. */
private val BOAT_WIDTH = 150.dp

/** How fast the river flows, chosen so one station passes about every two and a half seconds. */
private const val VOYAGE_SPEED_PER_SECOND = 88f

/** The ink the launch page's line is written in, reused so the two pages rhyme. */
private val InkColor = Color(0xFF3A3226)

/** The paper the art is drawn on, as a translucent wash behind a floating name. */
private val PaperWash = Color(0xFFFDFCF9)

/**
 * The list as a journey: the river art scrolling under a boat that rides it,
 * every countdown a station that lights up and names itself as the bow passes.
 *
 * The boat is the reading point and the camera both — it holds its place on
 * the screen while the world slides by, which is what keeps the names legible
 * rather than shrinking into the distance. The layout of the stations is
 * [voyageStops]; this screen only draws what it is told.
 *
 * Landscape is requested rather than forced (`USER_LANDSCAPE`), so a device
 * with rotation locked keeps its own setting; the art is a landscape either
 * way, drawn to fill whatever it is given.
 */
@Composable
fun VoyageScreen(
    events: List<CountdownEvent>,
    today: LocalDate,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous }
    }

    val stops = remember(events, today) { voyageStops(events, today) }
    val journeyEnd = remember(stops) { voyageLength(stops) }

    // The boat's place on the journey, in the layout's own unit. Saveable so a
    // process death mid-river does not row the boat back to the first station.
    var boatX by rememberSaveable { mutableFloatStateOf(0f) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }

    // The river itself: advancing while under way, idle at either end. `dragging`
    // is a key so a finger on the water takes the oar away from the clock.
    LaunchedEffect(paused, dragging, journeyEnd) {
        if (paused || dragging || journeyEnd <= 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (boatX < journeyEnd) {
            val now = withFrameNanos { it }
            // A stall longer than this would jump the boat past whole stations;
            // the river may only ever move at its own pace.
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
            last = now
            boatX = min(boatX + VOYAGE_SPEED_PER_SECOND * dt, journeyEnd)
        }
    }

    val river = ImageBitmap.imageResource(R.drawable.voyage_river)
    val boatPainter = painterResource(R.drawable.voyage_boat)
    val boatAspect = boatPainter.intrinsicSize.let { if (it.width > 0f) it.height / it.width else 1f }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // A tap on the water takes the oar: pause while reading, tap again
            // to row on. A name's own tap is its own, and never pauses anything.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { paused = !paused })
            }
            // A drag scrubs the journey: the finger carries the boat, the clock
            // waits, and the release rows on from wherever it landed.
            .pointerInput(journeyEnd) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    boatX = (boatX - amount / density).coerceIn(0f, journeyEnd)
                }
            },
    ) {
        RiverBackdrop(river = river, stops = stops, boatX = boatX)

        Boat(aspect = boatAspect, canvasSize = canvasSize)

        // The stations' names, over the art rather than in the canvas, because a
        // name is a button and a dot is a mark.
        stops.forEachIndexed { index, stop ->
            key(stop.event.id) {
                val appear by animateFloatAsState(
                    targetValue = if (stop.passed(boatX)) 1f else 0f,
                    animationSpec = tween(600),
                    label = "name",
                )
                FloatingName(
                    stop = stop,
                    index = index,
                    boatX = boatX,
                    canvasSize = canvasSize,
                    appear = appear,
                    onOpen = onOpen,
                )
            }
        }

        if (stops.isEmpty()) {
            CentredNotice(stringResource(R.string.home_empty))
        }

        PauseToggle(
            paused = paused,
            onToggle = { paused = !paused },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** The river art, mirrored end over end so it runs without a seam, plus the stations' dots. */
@Composable
private fun RiverBackdrop(
    river: ImageBitmap,
    stops: List<VoyageStop>,
    boatX: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val scale = h / river.height
        val tileW = river.width * scale
        val cameraPx = boatX.dp.toPx()

        // Tiles alternate normal and mirrored, which is what makes the loop
        // seamless whatever the art's edges look like: every joint is the same
        // column meeting its own reflection.
        var x = -(cameraPx % (tileW * 2f))
        var index = 0
        while (x < w) {
            val left = x
            if (left + tileW > 0f) {
                if (index % 2 == 0) {
                    drawImage(
                        river,
                        dstOffset = IntOffset(left.roundToInt(), 0),
                        dstSize = IntSize(tileW.roundToInt() + 1, h.roundToInt() + 1),
                    )
                } else {
                    translate(left + tileW, 0f) {
                        scale(-1f, 1f, pivot = Offset.Zero) {
                            drawImage(
                                river,
                                dstSize = IntSize(tileW.roundToInt() + 1, h.roundToInt() + 1),
                            )
                        }
                    }
                }
            }
            x += tileW
            index++
        }

        // The stations: a dot on the water, faint until the boat has been there.
        val anchorPx = w * BOAT_ANCHOR_FRACTION
        val waterlinePx = h * WATERLINE_FRACTION
        stops.forEach { stop ->
            val sx = anchorPx + stop.position.dp.toPx() - cameraPx
            if (sx > -20f && sx < w + 20f) {
                val reached = stop.passed(boatX)
                drawCircle(
                    color = InkColor.copy(alpha = if (reached) 0.85f else 0.22f),
                    radius = (if (reached) 4.dp else 3.dp).toPx(),
                    center = Offset(sx, waterlinePx - 2.dp.toPx()),
                )
            }
        }
    }
}

/** The boat itself: held to its anchor and the waterline, riding a wave of its own. */
@Composable
private fun Boat(aspect: Float, canvasSize: IntSize) {
    val painter = painterResource(R.drawable.voyage_boat)
    val boatHeight = BOAT_WIDTH * aspect

    // The boat never stops riding the water — not while paused, not at the end
    // of the journey — because the river it sits in is the page's own life.
    val swell = rememberInfiniteTransition(label = "swell")
    val phase by swell.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .size(width = BOAT_WIDTH, height = boatHeight)
            .offset {
                if (canvasSize == IntSize.Zero) return@offset IntOffset.Zero
                IntOffset(
                    x = (canvasSize.width * BOAT_ANCHOR_FRACTION - BOAT_WIDTH.toPx() / 2f)
                        .roundToInt(),
                    // The hull sits a little into the water, not on a line above it.
                    y = (canvasSize.height * WATERLINE_FRACTION - boatHeight.toPx() + 6.dp.toPx())
                        .roundToInt(),
                )
            }
            .graphicsLayer {
                translationY = sin(phase) * 5.dp.toPx()
                rotationZ = sin(phase * 0.8f) * 2.5f
            },
    )
}

/** One station's name, fading up out of the river as the bow passes its dot. */
@Composable
private fun FloatingName(
    stop: VoyageStop,
    index: Int,
    boatX: Float,
    canvasSize: IntSize,
    appear: Float,
    onOpen: (String) -> Unit,
) {
    if (appear <= 0.01f) return

    // One unit of the layout's own measure, in pixels, computed once: the name
    // moves every frame, but the ruler it moves by does not.
    val density = LocalDensity.current
    val unit = with(density) { 1.dp.toPx() }
    val boatPx = boatX * unit
    val stopPx = stop.position * unit

    Text(
        text = stop.event.title,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp),
        fontFamily = XingKai,
        color = InkColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .centeredOn {
                if (canvasSize == IntSize.Zero) return@centeredOn Offset.Zero
                val anchorPx = canvasSize.width * BOAT_ANCHOR_FRACTION
                val x = anchorPx + stopPx - boatPx
                with(density) {
                    // Neighbouring names sit at slightly different heights, so a
                    // bend with many stations does not read as one long line.
                    val waterlinePx = canvasSize.height * WATERLINE_FRACTION
                    val lift = if (index % 2 == 1) 14.dp.toPx() else 0.dp.toPx()
                    val y = waterlinePx - BOAT_WIDTH.toPx() * 0.87f - 34.dp.toPx() - lift
                    Offset(x, y)
                }
            }
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 14.dp.toPx()
            }
            .clip(RoundedCornerShape(9.dp))
            // A wash of the paper behind the name: the art carries mountains
            // behind wherever a name lands, and ink on ink is not legible.
            .background(PaperWash.copy(alpha = 0.78f))
            .clickable { onOpen(stop.event.id) }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** The small round button that holds the oar: drawn rather than an icon set. */
@Composable
private fun PauseToggle(paused: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, end = 14.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(InkColor.copy(alpha = 0.08f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        val tint = InkColor.copy(alpha = 0.55f)
        Canvas(modifier = Modifier.size(13.dp)) {
            if (paused) {
                // A triangle, pointing the river onward.
                val path = Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(path, color = tint)
            } else {
                val bar = size.width / 3.4f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset.Zero,
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width - bar, 0f),
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Places the node's content centred on a point of the parent, whatever the
 * content's own size: the node itself takes no room, so the parent's alignment
 * of it cannot move it, and the point is read at placement time — a name laid
 * out this way slides with the river without recomposing anything.
 */
private fun Modifier.centeredOn(position: () -> Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        val point = position()
        placeable.placeRelative(
            (point.x - placeable.width / 2f).roundToInt(),
            point.y.roundToInt(),
        )
    }
}

/** The activity a context belongs to, through however many wrappers carry it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
