package io.github.zzpby.tickcount.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.data.Footprint
import io.github.zzpby.tickcount.data.FootprintMedia
import io.github.zzpby.tickcount.domain.MILESTONE_DAYS
import io.github.zzpby.tickcount.domain.VoyageStop
import io.github.zzpby.tickcount.domain.effectiveDate
import io.github.zzpby.tickcount.domain.passed
import io.github.zzpby.tickcount.domain.voyageLength
import io.github.zzpby.tickcount.domain.voyageStops
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.eventAccent
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far down the night the star river runs. */
private const val RIVER_FRACTION = 0.60f

/** Where the comet rides across the screen: left of centre, so names float ahead of it. */
private const val COMET_ANCHOR_FRACTION = 0.34f

/** The comet's own width, tail and all. */
private val COMET_SIZE = 130.dp

/** How fast the river flows, chosen so one station passes about every two and a half seconds. */
private const val VOYAGE_SPEED_PER_SECOND = 88f

/** How far off the river's line a star may hang, as a share of the height. */
private const val STAR_BAND = 0.085f

/** The travel over which the journey eases into and out of its ends, in the layout's unit. */
private const val RAMP_TRAVEL = 60f

/** How far past a star the arrival burst still plays, in the layout's unit. */
private const val BURST_TRAVEL = 42f

/** The night itself: deep indigo easing to a violet horizon — fixed, the one page above the theme. */
private val SkyTop = Color(0xFF04060F)
private val SkyMiddle = Color(0xFF0A1026)
private val SkyLow = Color(0xFF141B3D)
private val SkyHorizon = Color(0xFF1D1F45)

/** The light the names are written in. */
private val Starlight = Color(0xFFF4EFE3)

/** The rose a milestone burns, the anniversary's own colour carried into the night. */
private val MilestoneGlow = Color(0xFFE8837A)

/** The teal a footprint burns — 万疆's own colour, a place on the chart of days. */
private val FootprintGlow = Color(0xFF54C6B4)

/** The prefix that marks a stop as a footprint's, the way milestone ids are marked. */
private const val FOOTPRINT_PREFIX = "footprint-"

/** One soft cloud of the sky: how far along, how far down, how wide, and its colour. */
private data class Nebula(val along: Float, val down: Float, val radius: Float, val colour: Color)

/** The sky's own clouds, drawn behind everything and moving slowest of all. */
private val Nebulae = listOf(
    Nebula(0.10f, 0.30f, 0.55f, Color(0xFF7B6CF6)),
    Nebula(0.46f, 0.62f, 0.62f, Color(0xFF2EC4B6)),
    Nebula(0.78f, 0.22f, 0.50f, Color(0xFF4A7DFF)),
    Nebula(0.96f, 0.55f, 0.45f, Color(0xFF9A6CF6)),
)

/**
 * The list as a chart of the night: a constellation strung along a river of
 * stars, a comet riding it as the reading point, and every countdown a star
 * that flares and names itself as the comet passes — 星河也是河.
 *
 * The picture is built the way a night sky actually reads: clouds of faint
 * colour behind, three depths of stars turning at their own distances, a
 * soft band of starlight where the river runs, and the constellation drawn
 * over it all — its lines gradient-faded, its stars breathing in their own
 * colours, the stretch already sailed glowing the brighter.
 *
 * The comet is the camera: it holds its place on the screen while the
 * heavens slide by, which keeps the names legible rather than shrinking
 * into the distance. The layout of the stations is [voyageStops]; this
 * screen only draws what it is told.
 *
 * Landscape is requested rather than forced (`USER_LANDSCAPE`), so a device
 * with rotation locked keeps its own setting.
 */
@Composable
fun VoyageScreen(
    events: List<CountdownEvent>,
    today: LocalDate,
    /** The milestone days from the anniversary, by the day number they land on. */
    milestones: Map<LocalDate, Long> = emptyMap(),
    /** The footprints, which ride the chart as stars of their own colour. */
    footprints: List<Footprint> = emptyList(),
    onOpen: (String) -> Unit,
    /** The picked soundtrack's private filename; empty plays the night's own embedded song. */
    musicFile: String = "",
    onMusicPicked: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        // The whole window, not just this screen: the sky is the page, and a
        // status bar over it is a ceiling the night does not have. The bars
        // come back with a swipe while it is showing, and fully when it ends.
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, window.decorView) }
        val previousBars = controller?.systemBarsBehavior
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBars != null) controller?.systemBarsBehavior = previousBars
            activity?.requestedOrientation = previousOrientation
        }
    }

    // A milestone is not a countdown, but the river does not care: it rides as
    // a pseudo one — an id no real countdown could collide with, so tapping its
    // name simply goes nowhere — sorted and passed by the same rules as the rest.
    // A footprint rides the same way, carrying its place as the name and its
    // first photo as the thing to float when its day has come.
    val togetherTitles = MILESTONE_DAYS.associateWith {
        stringResource(R.string.anniversary_together, it)
    }
    val footprintPhotos = remember(footprints) {
        footprints
            .filter { it.photos.isNotEmpty() }
            .associate { FOOTPRINT_PREFIX + it.id to it.photos.first() }
    }
    val stops = remember(events, today, milestones, footprints, togetherTitles) {
        voyageStops(
            events +
                milestones.map { (date, day) ->
                    CountdownEvent(id = "milestone-$day", date = date, title = togetherTitles[day] ?: day.toString())
                } +
                footprints.map {
                    CountdownEvent(
                        id = FOOTPRINT_PREFIX + it.id,
                        date = LocalDate.ofEpochDay(it.epochDay),
                        title = it.place,
                    )
                },
            today,
        )
    }
    val journeyEnd = remember(stops) { voyageLength(stops) }

    // Which stations are days that have actually come: a star lights when the
    // comet reaches it *and* its day has arrived. The comet flies past a day
    // still to come and leaves its outline dark — flying through time is not
    // the same as time having passed.
    val arrivedIds = remember(stops, today) {
        stops
            .filter { !it.event.effectiveDate(today).isAfter(today) }
            .mapTo(mutableSetOf()) { it.event.id }
    }

    // How far the comet sails on its own: to the last day that has come, and
    // no further. The days still to come are there to be seen — dragged or
    // flung into — but the clock does not carry the comet into them; it waits
    // at the frontier of now.
    val sailEnd = remember(stops, arrivedIds) {
        stops.lastOrNull { it.event.id in arrivedIds }?.position ?: 0f
    }

    // The comet's place on the journey, in the layout's own unit. Saveable so a
    // process death mid-flight does not row the comet back to the first star.
    var boatX by rememberSaveable { mutableFloatStateOf(0f) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var flinging by remember { mutableStateOf(false) }

    // How hard the comet is moving — 1 is a cruise, more is a fling: the tail
    // stretches with it, so speed is something the eye can see.
    var surge by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // The voyage's own soundtrack: one audio file picked from the device,
    // copied into the app's own ground the way photographs are — the sky
    // never depends on the file staying where the picker found it — and
    // looped while the comet rows. Shipping the oar (the pause) hushes it;
    // leaving the night releases it entirely.
    val pickMusic = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            VoyageMusic.save(context, uri)?.let(onMusicPicked)
        }
    }
    var musicPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    // Whether the music should be sounding, kept where the focus listener
    // can see it: focus lost hushes the river, focus regained brings it
    // back only if the oar is not shipped.
    var musicWanted by remember { mutableStateOf(false) }
    DisposableEffect(musicFile) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val custom = musicFile.takeIf { it.isNotEmpty() }
            ?.let { VoyageMusic.file(context, it) }
        // The night carries a song of its own: one file shipped inside the
        // app, played until the two of you pick one that is more theirs.
        // A picked file takes over; clearing it hands the oar back.
        val player = if (custom != null) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(custom.absolutePath)
                    isLooping = true
                    prepare()
                }
            }.getOrNull()
        } else {
            runCatching {
                MediaPlayer.create(context, R.raw.voyage_default)?.apply { isLooping = true }
            }.getOrNull()
        }
        musicPlayer = player
        val focus = player?.let { sounding ->
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        -> runCatching { sounding.pause() }

                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                            sounding.setVolume(0.2f, 0.2f)

                        AudioManager.AUDIOFOCUS_GAIN -> {
                            sounding.setVolume(1f, 1f)
                            if (musicWanted) runCatching { sounding.start() }
                        }
                    }
                }
                .build()
                .also { audioManager?.requestAudioFocus(it) }
        }
        onDispose {
            focus?.let { audioManager?.abandonAudioFocusRequest(it) }
            player?.release()
            musicPlayer = null
        }
    }
    LaunchedEffect(musicPlayer, paused) {
        val player = musicPlayer ?: return@LaunchedEffect
        musicWanted = !paused
        if (paused) {
            runCatching { if (player.isPlaying) player.pause() }
        } else {
            runCatching { player.start() }
        }
    }

    // A star arrived at ticks in the hand — only a day that has truly come:
    // the frontier of lit stars moves forward through a sail, a drag or a
    // fling, and scrubbing back resets it quietly so it can tick again.
    LaunchedEffect(stops, arrivedIds) {
        var frontier = stops.count { it.passed(boatX) && it.event.id in arrivedIds }
        snapshotFlow { boatX }.collect { x ->
            val reached = stops.count { it.passed(x) && it.event.id in arrivedIds }
            if (reached > frontier) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            frontier = reached
        }
    }

    // The river itself: advancing while under way, idle at either end — and
    // its end is the last day that has come, not the last day on the chart.
    // The journey eases out of its first star and into that frontier, rather
    // than stepping off at full speed and slamming into it.
    LaunchedEffect(paused, dragging, flinging, sailEnd) {
        if (paused || dragging || flinging || sailEnd <= 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (boatX < sailEnd) {
            val now = withFrameNanos { it }
            // A stall longer than this would jump the comet past whole stars;
            // the river may only ever move at its own pace.
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
            last = now
            val ramp = minOf(1f, boatX / RAMP_TRAVEL, (sailEnd - boatX) / RAMP_TRAVEL)
                .coerceAtLeast(0.12f)
            boatX = min(boatX + VOYAGE_SPEED_PER_SECOND * ramp * dt, sailEnd)
        }
    }

    // The comet's own motion, beyond the path it follows: its height chases the
    // constellation with a spring's slight overshoot, and its body turns to
    // face the way it travels — a flyer, not a bead on a wire.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cometY by remember { mutableFloatStateOf(Float.NaN) }
    var cometVy by remember { mutableFloatStateOf(0f) }
    var cometAngle by remember { mutableFloatStateOf(0f) }
    var lastBoatX by remember { mutableFloatStateOf(Float.NaN) }
    val pxPerUnit = with(LocalDensity.current) { 1.dp.toPx() }

    LaunchedEffect(paused, dragging, flinging, journeyEnd) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNanos = now

            // The spring: chase the star's height, overshoot a touch on the
            // rise, settle on the fall — flying, not sliding.
            if (canvasSize != IntSize.Zero) {
                val targetY = canvasSize.height * RIVER_FRACTION +
                    constellationFraction(stops, boatX) * canvasSize.height * STAR_BAND
                if (cometY.isNaN()) {
                    cometY = targetY
                    cometVy = 0f
                } else {
                    cometVy += ((targetY - cometY) * 300f - cometVy * 24f) * dt
                    cometY += cometVy * dt
                }
            }

            // The turn: the body faces along the path's tangent — measured a
            // little ahead and behind — flipped when travelling backward, so a
            // scrub home swings the comet fully around through the turn.
            if (canvasSize != IntSize.Zero) {
                val ahead = constellationFraction(stops, (boatX + 4f).coerceAtMost(journeyEnd))
                val behind = constellationFraction(stops, (boatX - 4f).coerceAtLeast(0f))
                val forward = if (lastBoatX.isNaN() || boatX >= lastBoatX) 1f else -1f
                val targetAngle = atan2(
                    (ahead - behind) * canvasSize.height * STAR_BAND,
                    8f * pxPerUnit,
                ) + (if (forward < 0f) PI.toFloat() else 0f)
                var turn = targetAngle - cometAngle
                while (turn > PI) turn -= 2f * PI.toFloat()
                while (turn < -PI) turn += 2f * PI.toFloat()
                cometAngle += turn * (dt * 7f).coerceAtMost(1f)
            }
            lastBoatX = boatX

            // The surge relaxes back toward nothing whenever nothing is
            // driving it — after a fling the tail settles, it does not snap.
            if (!dragging && !flinging) {
                val target = if (!paused && boatX < sailEnd && sailEnd > 0f) 1f else 0f
                surge += (target - surge) * 0.07f
            }
        }
    }

    // One clock for every twinkle: the stars share a phase the way a real sky
    // shares the night — each at its own point of it.
    val nightPhase = rememberInfiniteTransition(label = "night")
    val phase by nightPhase.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    // Three depths of stars, each seeded once: the far ones small and slow, the
    // near ones brighter and quicker — the night is layered, not flat.
    val starDepths = remember {
        fun layer(seed: Int, count: Int, minSize: Float, maxSize: Float) =
            kotlin.random.Random(seed).let { random ->
                List(count) {
                    Star(
                        along = random.nextFloat(),
                        down = random.nextFloat(),
                        size = minSize + random.nextFloat() * (maxSize - minSize),
                        twinkleAt = random.nextFloat() * (2f * Math.PI).toFloat(),
                    )
                }
            }
        listOf(
            layer(11, 60, 0.7f, 1.2f),
            layer(29, 34, 1.0f, 1.8f),
            layer(53, 16, 1.5f, 2.4f),
        )
    }

    // The river's own dust: finer than stars, flowing with the journey.
    val riverDust = remember {
        kotlin.random.Random(83).let { random ->
            List(70) { Star(random.nextFloat(), random.nextFloat(), 0.6f + random.nextFloat() * 1.1f, random.nextFloat() * (2f * Math.PI).toFloat()) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // A tap on the sky takes the oar: pause while reading, tap again
            // to row on. A name's own tap is its own, and never pauses anything.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { paused = !paused })
            }
            // A drag carries the comet and remembers how hard it was thrown:
            // on release the journey keeps the momentum, decaying like
            // anything moving through water, until the clock takes over again.
            .pointerInput(journeyEnd) {
                var lastDragAt = 0L
                var velocity = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        velocity = 0f
                        lastDragAt = 0L
                    },
                    onDragEnd = {
                        dragging = false
                        val thrown = velocity
                        if (abs(thrown) > 60f && journeyEnd > 0f) {
                            scope.launch {
                                flinging = true
                                var speed = thrown
                                var last = withFrameNanos { it }
                                while (abs(speed) > 14f) {
                                    val now = withFrameNanos { it }
                                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
                                    last = now
                                    val next = (boatX + speed * dt).coerceIn(0f, journeyEnd)
                                    if (next == boatX) break // the shore
                                    boatX = next
                                    surge = maxOf(surge, (abs(speed) / VOYAGE_SPEED_PER_SECOND).coerceIn(1f, 3f))
                                    speed *= Math.pow(0.025, dt.toDouble()).toFloat()
                                }
                                flinging = false
                            }
                        }
                    },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    val nowAt = change.uptimeMillis
                    if (lastDragAt > 0L) {
                        val dt = ((nowAt - lastDragAt) / 1000f).coerceAtLeast(0.001f)
                        velocity = 0.72f * velocity + 0.28f * (-amount / density) / dt
                    }
                    lastDragAt = nowAt
                    boatX = (boatX - amount / density).coerceIn(0f, journeyEnd)
                    surge = maxOf(surge, (abs(velocity) / VOYAGE_SPEED_PER_SECOND).coerceIn(1f, 3f))
                }
            },
    ) {
        StarBackdrop(
            phase = phase,
            boatX = boatX,
            canvasSize = canvasSize,
            stops = stops,
            arrivedIds = arrivedIds,
            // The colours are mixed here, in composition, where mixing them is
            // allowed — the canvas only draws what it is handed.
            stopColours = stops.associate { stop ->
                stop.event.id to when {
                    stop.event.id.startsWith("milestone-") -> MilestoneGlow
                    stop.event.id.startsWith(FOOTPRINT_PREFIX) -> FootprintGlow
                    else -> eventAccent(stop.event.colorHue)
                }
            },
            starDepths = starDepths,
            riverDust = riverDust,
        )

        Comet(
            phase = phase,
            canvasSize = canvasSize,
            stops = stops,
            boatX = boatX,
            surge = surge,
            yPx = cometY,
            angleDeg = cometAngle * 180f / PI.toFloat(),
        )

        // The stations' names, over the sky rather than in the canvas, because a
        // name is a button and a star is a mark. Each name reports where its
        // top edge really landed, and the photograph above it hangs from that
        // measurement — the two are separate floaters, joined by fact.
        val nameTops = remember { mutableStateMapOf<String, Float>() }
        stops.forEachIndexed { index, stop ->
            key(stop.event.id) {
                val appear by animateFloatAsState(
                    targetValue = if (stop.passed(boatX)) 1f else 0f,
                    // A spring rather than a fade: the name arrives the way the
                    // star did — overshooting a little, then settling.
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessVeryLow,
                    ),
                    label = "name",
                )

                FloatingName(
                    stop = stop,
                    index = index,
                    boatX = boatX,
                    canvasSize = canvasSize,
                    appear = appear,
                    arrived = stop.event.id in arrivedIds,
                    onOpen = onOpen,
                    onLaidOut = { top -> nameTops[stop.event.id] = top },
                )

                // A station that has come floats its photograph above the
                // name — a footprint its first picture, a countdown the photo
                // its pile leads with. A day still to come floats nothing;
                // the name alone is the promise.
                val photoName = when {
                    stop.event.id.startsWith(FOOTPRINT_PREFIX) ->
                        footprintPhotos[stop.event.id]

                    stop.event.id.startsWith("milestone-") -> null
                    else -> stop.event.photoCover ?: stop.event.photoUris.lastOrNull()
                }
                val arrivedStation = stop.event.id in arrivedIds
                val photo = if (photoName != null && arrivedStation) {
                    remember(photoName, stop.event.id) {
                        if (stop.event.id.startsWith(FOOTPRINT_PREFIX)) {
                            FootprintMedia.thumbnail(context, photoName, 360)
                        } else {
                            EventBackgrounds.thumbnail(context, photoName, 360)
                        }
                    }?.asImageBitmap()
                } else {
                    null
                }
                if (photo != null) {
                    FloatingPhoto(
                        stop = stop,
                        index = index,
                        boatX = boatX,
                        canvasSize = canvasSize,
                        appear = appear,
                        photo = photo,
                        nameTopPx = nameTops[stop.event.id],
                    )
                }
            }
        }

        if (stops.isEmpty()) {
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(color = Color(0xCC04060F), blurRadius = 10f),
                ),
                color = Starlight.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .wrapCentred(),
            )
        }

        Row(verticalAlignment = Alignment.Top) {
            MusicToggle(
                custom = musicFile.isNotEmpty(),
                sounding = !paused,
                onPick = { pickMusic.launch("audio/*") },
                onClear = {
                    VoyageMusic.clear(context)
                    onMusicPicked("")
                },
            )
            PauseToggle(paused = paused, onToggle = { paused = !paused })
        }
    }
}

/** One point of light in the sky: where it sits, how big, and where in the night it twinkles. */
private data class Star(val along: Float, val down: Float, val size: Float, val twinkleAt: Float)

/**
 * The whole night in one canvas, redrawn per frame without recomposing
 * anything: the sky and its clouds, three depths of stars, the soft band the
 * river runs in, and the constellation drawn over it all.
 */
@Composable
private fun StarBackdrop(
    phase: Float,
    boatX: Float,
    canvasSize: IntSize,
    stops: List<VoyageStop>,
    arrivedIds: Set<String>,
    stopColours: Map<String, Color>,
    starDepths: List<List<Star>>,
    riverDust: List<Star>,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cameraPx = boatX.dp.toPx()
        val riverY = h * RIVER_FRACTION

        // The sky itself: deep indigo easing towards a violet horizon.
        drawRect(
            Brush.verticalGradient(
                0.0f to SkyTop,
                0.45f to SkyMiddle,
                0.8f to SkyLow,
                1.0f to SkyHorizon,
                endY = h,
            )
        )

        // The sky's own clouds: great soft circles of colour, moving slowest of
        // all — the depth behind every other layer.
        val cloudSpan = w * 1.6f
        Nebulae.forEach { cloud ->
            var x = cloud.along * cloudSpan - cameraPx * 0.05f
            x = ((x % cloudSpan) + cloudSpan) % cloudSpan - w * 0.3f
            val centre = Offset(x, cloud.down * h)
            val radius = cloud.radius * h
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cloud.colour.copy(alpha = 0.10f), Color.Transparent),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }

        // Three depths of stars: each layer turning at its own distance, and
        // every star twinkling at its own point of the shared night.
        starDepths.forEachIndexed { depth, layer ->
            val parallax = 0.08f + depth * 0.07f
            val tile = w * 1.25f
            layer.forEach { star ->
                var x = star.along * tile - cameraPx * parallax
                x = ((x % tile) + tile) % tile
                if (x > w + 4f) return@forEach
                val twinkle = 0.30f + 0.55f * (0.5f + 0.5f * sin(phase + star.twinkleAt))
                drawCircle(
                    color = Starlight.copy(alpha = twinkle),
                    radius = star.size.dp.toPx() / 2f,
                    center = Offset(x, star.down * h),
                )
            }
        }

        // The river: a broad wash of starlight with a brighter core, and fine
        // dust drifting in it — the band the constellation is strung along.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Starlight.copy(alpha = 0.05f), Color.Transparent),
                startY = riverY - h * 0.16f,
                endY = riverY + h * 0.16f,
            ),
            size = this.size,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFF9FB4FF).copy(alpha = 0.05f), Color.Transparent),
                startY = riverY - h * 0.05f,
                endY = riverY + h * 0.05f,
            ),
            size = this.size,
        )
        val dustTile = w * 1.5f
        riverDust.forEachIndexed { index, dust ->
            var x = dust.along * dustTile - cameraPx * 0.75f
            x = ((x % dustTile) + dustTile) % dustTile
            if (x > w + 4f) return@forEachIndexed
            val y = riverY + (dust.down - 0.5f) * h * 0.11f +
                sin(phase * 0.5f + dust.twinkleAt) * 6.dp.toPx()
            drawCircle(
                color = Starlight.copy(alpha = 0.18f + 0.22f * (0.5f + 0.5f * sin(phase + index))),
                radius = dust.size.dp.toPx() / 2f,
                center = Offset(x, y),
            )
        }

        // Meteors: now and then a streak crosses the high sky, seeded by which
        // stretch of the clock it is — alive, but never announcing itself.
        val nowNanos = System.nanoTime()
        val meteorCycle = 5_200_000_000L
        val meteorLocal = (nowNanos % meteorCycle) / 1_100_000_000f
        if (meteorLocal in 0f..1f) {
            val random = kotlin.random.Random((nowNanos / meteorCycle).toInt())
            val meteorX = random.nextFloat() * w * 1.2f - w * 0.1f
            val meteorY = random.nextFloat() * h * 0.35f + h * 0.05f
            val travel = w * (0.3f + random.nextFloat() * 0.3f)
            val headP = Offset(meteorX + travel * meteorLocal, meteorY + travel * 0.3f * meteorLocal)
            val tailP = Offset(headP.x - 70.dp.toPx(), headP.y - 21.dp.toPx())
            val fade = sin(meteorLocal * Math.PI).toFloat()
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Starlight.copy(alpha = 0.55f * fade)),
                    start = tailP,
                    end = headP,
                ),
                start = tailP,
                end = headP,
                strokeWidth = 1.1.dp.toPx(),
            )
        }

        // The constellation: every star hanging at its own height along a smooth
        // undulation, every pair joined by a line that fades along its length —
        // the stretch already sailed glowing the brighter.
        val anchorPx = w * COMET_ANCHOR_FRACTION
        val bandPx = h * STAR_BAND

        fun starPoint(index: Int, stop: VoyageStop): Offset {
            val sx = anchorPx + stop.position.dp.toPx() - cameraPx
            val sy = riverY + starOffsetFraction(index, stop.event.id) * bandPx
            return Offset(sx, sy)
        }

        // The strings first, so the stars sit over their own ends.
        for (index in 0 until stops.size - 1) {
            val from = starPoint(index, stops[index])
            val to = starPoint(index + 1, stops[index + 1])
            if (maxOf(from.x, to.x) > w + 30f || minOf(from.x, to.x) < -30f) continue
            val sailed = stops[index + 1].passed(boatX)
            drawLine(
                brush = Brush.linearGradient(
                    colors = if (sailed) {
                        listOf(Starlight.copy(alpha = 0.40f), Starlight.copy(alpha = 0.10f))
                    } else {
                        listOf(Starlight.copy(alpha = 0.17f), Starlight.copy(alpha = 0.09f))
                    },
                    start = from,
                    end = to,
                ),
                start = from,
                end = to,
                strokeWidth = 1.1.dp.toPx(),
            )
        }

        stops.forEachIndexed { index, stop ->
            val point = starPoint(index, stop)
            if (point.x < -30f || point.x > w + 30f) return@forEachIndexed
            val colour = stopColours[stop.event.id] ?: Starlight
            val breath = 0.5f + 0.5f * sin(phase * 1.4f + index * 1.3f)

            // Lit means two things at once: the comet has been here, and the
            // day itself has come. Anything else keeps its outline dark.
            if (!(stop.passed(boatX) && stop.event.id in arrivedIds)) {
                // Not yet: the outline of its own colour, a star waiting to be
                // lit — plainly there against the sky, plainly not yet
                // burning, and nothing a background twinkle mimics.
                drawCircle(
                    color = colour.copy(alpha = 0.50f + 0.15f * breath),
                    radius = 3.dp.toPx(),
                    center = point,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = colour.copy(alpha = 0.15f),
                    radius = 1.2.dp.toPx(),
                    center = point,
                )
            } else {
                // Reached: a glow of its own colour, a bright core washed with
                // starlight, a thin ring — breathing, never blinking off.
                val glow = (13f + 3f * breath).dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colour.copy(alpha = 0.38f), colour.copy(alpha = 0.10f), Color.Transparent),
                        center = point,
                        radius = glow,
                    ),
                    radius = glow,
                    center = point,
                )
                drawCircle(
                    color = lerpTowardsStarlight(colour),
                    radius = 3.dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = colour.copy(alpha = 0.50f),
                    radius = 6.5.dp.toPx(),
                    center = point,
                    style = Stroke(width = 0.9.dp.toPx()),
                )

                // The moment of arrival: for one stretch of travel after the
                // comet reaches the star, a shockwave ring runs out from it and
                // seven sparks fly — the arrival the hand already felt.
                val age = boatX - stop.position
                if (age in 0f..BURST_TRAVEL) {
                    val t = age / BURST_TRAVEL
                    val eased = 1f - (1f - t) * (1f - t)
                    drawCircle(
                        color = colour.copy(alpha = (1f - t) * (1f - t) * 0.5f),
                        radius = (5f + eased * 26f).dp.toPx(),
                        center = point,
                        style = Stroke(width = (1.6f - t * 1.2f).dp.toPx().coerceAtLeast(0.4f)),
                    )
                    for (spark in 0 until 7) {
                        val angle = spark * 2.399963f + index * 0.7f
                        val dist = (5f + eased * 24f).dp.toPx()
                        val at = Offset(
                            point.x + cos(angle) * dist,
                            point.y + sin(angle) * dist,
                        )
                        drawCircle(
                            color = colour.copy(alpha = (1f - t) * 0.7f),
                            radius = (1.8f * (1f - t) + 0.4f).dp.toPx(),
                            center = at,
                        )
                    }
                }
            }
        }

        // The vignette: the edges fall away, and the middle of the night holds.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color(0xB3000000)),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = (maxOf(w, h) * 0.72f),
            ),
            size = this.size,
        )
    }
}

/** A colour washed with starlight, the way a lit star's core reads. */
private fun lerpTowardsStarlight(colour: Color): Color = Color(
    red = colour.red + (Starlight.red - colour.red) * 0.55f,
    green = colour.green + (Starlight.green - colour.green) * 0.55f,
  blue = colour.blue + (Starlight.blue - colour.blue) * 0.55f,
    alpha = 1f,
)

/**
 * Where a star hangs off the river's line, as a share of [STAR_BAND]: a smooth
 * undulation whose phase each journey sets from its own first star — the
 * constellation sways, but as one body, not as scattered noise.
 */
private fun starOffsetFraction(index: Int, id: String): Float {
    val seed = (((id.hashCode() % 8) + 8) % 8) * 0.78f
    return sin(index * 0.9f + seed) * 0.62f
}

/**
 * Where the comet hangs between the stars, as the same share of [STAR_BAND]:
 * between two neighbouring stars it eases in and out of each — arriving at a
 * star exactly as the star flares, leaving it gently rather than cutting a
 * corner — so the light and the chart move as one body.
 */
private fun constellationFraction(stops: List<VoyageStop>, boatX: Float): Float {
    if (stops.isEmpty()) return 0f
    val first = stops.first()
    val last = stops.last()
    if (boatX <= first.position) return starOffsetFraction(0, first.event.id)
    if (boatX >= last.position) return starOffsetFraction(stops.lastIndex, last.event.id)
    for (index in 0 until stops.size - 1) {
        val from = stops[index]
        val to = stops[index + 1]
        if (boatX >= from.position && boatX <= to.position) {
            var t = (boatX - from.position) / (to.position - from.position)
            t = t * t * (3f - 2f * t)
            val a = starOffsetFraction(index, from.event.id)
            val b = starOffsetFraction(index + 1, to.event.id)
            return a + (b - a) * t
        }
    }
    return 0f
}

/**
 * The comet: a head of light riding the constellation itself, its body turned
 * to face the way it travels — rising to each star as it reaches it, easing
 * out of it as it leaves — with a two-layer tail that stretches when it is
 * thrown and settles when it is not.
 */
@Composable
private fun Comet(
    phase: Float,
    canvasSize: IntSize,
    stops: List<VoyageStop>,
    boatX: Float,
    surge: Float,
    yPx: Float,
    angleDeg: Float,
) {
    Canvas(
        modifier = Modifier
            .size(COMET_SIZE)
            .offset {
                if (canvasSize == IntSize.Zero || yPx.isNaN()) return@offset IntOffset.Zero
                // The head sits at this canvas's own three-quarter point, so
                // the canvas is placed to put the head exactly on its star —
                // arriving at one and sitting on one are the same place.
                IntOffset(
                    x = (canvasSize.width * COMET_ANCHOR_FRACTION - COMET_SIZE.toPx() * 0.74f).roundToInt(),
                    y = (yPx - COMET_SIZE.toPx() * 0.5f).roundToInt(),
                )
            },
    ) {
        val head = Offset(size.width * 0.74f, size.height * 0.5f)
        val stretch = 1f + surge.coerceIn(0f, 2.5f) * 0.55f

        rotate(degrees = angleDeg, pivot = head) {
            // The tail: a soft underlayer with a bright core over it, both
            // tapering and fading and swaying a little with the water —
            // lengthened by speed, so a fling reads as a fling.
            val steps = 26
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                val point = Offset(
                    head.x - t * size.width * 0.68f * stretch,
                    head.y + sin(phase * 1.3f + t * 2.6f) * 5.dp.toPx() * t,
                )
                val fade = (1f - t)
                drawCircle(
                    color = Starlight.copy(alpha = fade * fade * 0.13f),
                    radius = (4.6f * fade + 0.6f).dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = Starlight.copy(alpha = fade * fade * 0.32f),
                    radius = (2.1f * fade + 0.3f).dp.toPx(),
                    center = point,
                )
            }

            // The head: a bloom washed with the sky's own blue, a streak of
            // light across it the way a lens smears a bright point, then the core.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFBFD2FF).copy(alpha = 0.45f),
                        Color(0xFF6C8CFF).copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = head,
                    radius = 15.dp.toPx(),
                ),
                radius = 15.dp.toPx(),
                center = head,
            )
            val streakHalf = 26.dp.toPx() + surge * 8.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFBFD2FF).copy(alpha = 0.30f),
                        Color.Transparent,
                    ),
                    startX = head.x - streakHalf,
                    endX = head.x + streakHalf,
                ),
                start = Offset(head.x - streakHalf, head.y),
                end = Offset(head.x + streakHalf, head.y),
                strokeWidth = 1.2.dp.toPx(),
            )
            drawCircle(color = Starlight, radius = 3.2.dp.toPx(), center = head)
        }
    }
}

/**
 * One station's name, hanging above its star: bottom edge fixed at a clearance
 * from the star's glow, growing upward however tall the font renders — and
 * reporting its real top edge so the photograph above can hang from it
 * exactly, not from an estimate of the font.
 */
@Composable
private fun FloatingName(
    stop: VoyageStop,
    index: Int,
    boatX: Float,
    canvasSize: IntSize,
    appear: Float,
    arrived: Boolean,
    onOpen: (String) -> Unit,
    onLaidOut: (Float) -> Unit,
) {
    if (appear <= 0.01f) return

    val density = LocalDensity.current
    val unit = with(density) { 1.dp.toPx() }
    val boatPx = boatX * unit
    val stopPx = stop.position * unit

    Text(
        text = stop.event.title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 1.sp,
            shadow = Shadow(color = Color(0xCC04060F), blurRadius = 12f),
        ),
        fontFamily = XingKai,
        color = Starlight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .hungAbove {
                if (canvasSize == IntSize.Zero) return@hungAbove Offset.Zero
                val x = canvasSize.width * COMET_ANCHOR_FRACTION + stopPx - boatPx
                with(density) {
                    // The name's bottom edge rides a fixed clearance above the
                    // star's own glow; how tall it renders is its own business.
                    val starY = canvasSize.height * RIVER_FRACTION +
                        starOffsetFraction(index, stop.event.id) * canvasSize.height * STAR_BAND
                    val lift = if (index % 2 == 1) 14.dp.toPx() else 0.dp.toPx()
                    Offset(x, starY - 26.dp.toPx() - lift)
                }
            }
            .graphicsLayer {
                // A day still to come names itself more quietly than a day
                // that has come — there, but held back, like its star.
                alpha = appear.coerceIn(0f, 1f) * (if (arrived) 1f else 0.55f)
                translationY = (1f - appear) * 14.dp.toPx()
                val scale = 0.85f + 0.15f * appear.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { coordinates ->
                // The photograph above hangs from this, measured — the layout
                // itself says where the name's top edge is, font and all.
                onLaidOut(coordinates.positionInWindow().y)
            }
            .clickable { onOpen(stop.event.id) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The photograph a station floats above its name, when the day has come: hung
 * from the name's measured top edge with a breath of sky between, tilted the
 * way a photograph pinned to the night would sit.
 */
@Composable
private fun FloatingPhoto(
    stop: VoyageStop,
    index: Int,
    boatX: Float,
    canvasSize: IntSize,
    appear: Float,
    photo: ImageBitmap,
    nameTopPx: Float?,
) {
    if (appear <= 0.01f || nameTopPx == null) return

    val density = LocalDensity.current
    val unit = with(density) { 1.dp.toPx() }
    val boatPx = boatX * unit

    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 58.dp)
            .hungAbove {
                if (canvasSize == IntSize.Zero) return@hungAbove Offset.Zero
                val x = canvasSize.width * COMET_ANCHOR_FRACTION + stop.position * unit - boatPx
                // The name's own top edge, as the layout measured it — no
                // estimate, no font metric guesswork, just where it is.
                with(density) { Offset(x, nameTopPx - 8.dp.toPx()) }
            }
            .graphicsLayer {
                alpha = appear.coerceIn(0f, 1f)
                rotationZ = -3.5f
                val scale = 0.85f + 0.15f * appear.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(0.8.dp, Starlight.copy(alpha = 0.28f), RoundedCornerShape(8.dp)),
    ) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The small round button that holds the oar: drawn rather than an icon set. */
@Composable
private fun PauseToggle(paused: Boolean, onToggle: () -> Unit) {    Box(
        modifier = Modifier
            .padding(top = 10.dp, end = 14.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Starlight.copy(alpha = 0.10f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            if (paused) {
                // A triangle, pointing the river onward.
                val path = Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(path, color = Starlight.copy(alpha = 0.75f))
            } else {
                val tint = Starlight.copy(alpha = 0.6f)
                val bar = size.width / 3.4f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(bar, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width - bar, 0f),
                    size = androidx.compose.ui.geometry.Size(bar, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * The soundtrack's seat beside the oar: a note that opens the device's own
 * audio picker — drawn, like everything on this night, rather than taken
 * from an icon set. The night always carries its own song; picking one
 * makes it yours, and the small cross hands the night back its own.
 */
@Composable
private fun MusicToggle(
    custom: Boolean,
    sounding: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, end = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Starlight.copy(alpha = 0.10f))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                // An eighth note: head, stem, and the little flag off the top.
                // A song of your own burns a little brighter than the night's.
                val bright = when {
                    sounding && custom -> 0.85f
                    sounding -> 0.70f
                    custom -> 0.55f
                    else -> 0.45f
                }
                val ink = Starlight.copy(alpha = bright)
                drawCircle(
                    color = ink,
                    radius = size.width * 0.21f,
                    center = Offset(size.width * 0.36f, size.height * 0.74f),
                )
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(size.width * 0.52f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.09f, size.height * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.045f),
                )
                val flag = Path().apply {
                    moveTo(size.width * 0.61f, size.height * 0.12f)
                    quadraticTo(
                        size.width * 0.98f, size.height * 0.22f,
                        size.width * 0.72f, size.height * 0.44f,
                    )
                }
                drawPath(
                    path = flag,
                    color = ink,
                    style = Stroke(width = size.width * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                // While the music sounds, two breaths of it drift right.
                if (sounding) {
                    drawArc(
                        color = Starlight.copy(alpha = 0.40f),
                        startAngle = -50f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.78f, size.height * 0.30f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.40f),
                        style = Stroke(width = size.width * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        if (custom) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    val ink = Starlight.copy(alpha = 0.5f)
                    val bar = size.width * 0.11f
                    drawLine(
                        ink,
                        Offset(bar, bar),
                        Offset(size.width - bar, size.height - bar),
                        strokeWidth = bar,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    drawLine(
                        ink,
                        Offset(size.width - bar, bar),
                        Offset(bar, size.height - bar),
                        strokeWidth = bar,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * The one audio file the night keeps, held the way photographs are: copied
 * into the app's own directory the moment it is chosen, so playback never
 * depends on the file staying where the picker found it, and no permission
 * to read the device is ever asked for — the picker's own grant covers the
 * single copy. One slot; a new choice replaces the old.
 */
private object VoyageMusic {
    private const val NAME = "voyage-music"

    /** Copies the picked audio into the app's ground; the filename, or null. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val type = context.contentResolver.getType(uri)
        val name = NAME + when {
            type?.contains("mpeg") == true || type?.contains("mp3") == true -> ".mp3"
            type?.contains("flac") == true -> ".flac"
            type?.contains("wav") == true -> ".wav"
            type?.contains("ogg") == true -> ".ogg"
            type?.contains("aac") == true || type?.contains("mp4") == true -> ".m4a"
            else -> ".audio"
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            context.filesDir.resolve(name).outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        name
    }.getOrNull()

    /** The copied file itself, or null when nothing is aboard. */
    fun file(context: Context, name: String): File? =
        context.filesDir.resolve(name).takeIf { it.isFile && it.length() > 0L }

    /** Puts the night back to its own silence. */
    fun clear(context: Context) {
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith(NAME) }
            ?.forEach { it.delete() }
    }
}

/** Centres the content of a full-size box, for the empty night's own words. */
private fun Modifier.wrapCentred(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2,
            )
        }
    },
)

/**
 * Places the node's content horizontally centred on a point of the parent,
 * with its bottom edge resting on the point — and, unlike the zero-size
 * trick, reporting the content's own real size, so [onGloballyPositioned]
 * can read true bounds from it and whatever hangs above can hang from
 * exactly where this one ends.
 */
private fun Modifier.hungAbove(point: () -> Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val anchor = point()
        placeable.placeRelative(
            (anchor.x - placeable.width / 2f).roundToInt(),
            (anchor.y - placeable.height).roundToInt(),
        )
    }
}

/** The activity a context belongs to, through however many wrappers carry it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
