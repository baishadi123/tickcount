package io.github.zzpby.tickcount.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.Footprint
import io.github.zzpby.tickcount.data.FootprintMedia
import io.github.zzpby.tickcount.domain.ChinaPlaces
import io.github.zzpby.tickcount.domain.LocationPick
import io.github.zzpby.tickcount.ui.components.CheckIcon
import io.github.zzpby.tickcount.ui.components.CloseIcon
import io.github.zzpby.tickcount.ui.components.EditIcon
import io.github.zzpby.tickcount.ui.components.MapIcon
import io.github.zzpby.tickcount.ui.components.TrashIcon
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.hairline
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * 万疆 — the places the two of you went, a scrapbook of visits: each place a
 * heading, each visit a run of cells, each cell whatever was captured there —
 * a photo, a moving picture, a video.
 *
 * Grouped by name rather than sorted by date alone, because the page is for
 * remembering *a place*: every visit to it sits together, newest first, and
 * the grid under a heading holds as many cells as the visits brought.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WanJiangScreen(
    footprints: List<Footprint>,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)
    val context = LocalContext.current
    var viewerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerPhoto by rememberSaveable { mutableStateOf("") }
    // The scrapbook opens as a list every time; the map is a place to visit,
    // not the place to live, so it never takes over the first glance.
    var mapMode by rememberSaveable { mutableStateOf(false) }
    var sheetPlace by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the list has asked the map to fly to. The nonce lets the same
    // place be asked for twice; plain remember, for once consumed the map
    // simply stays where it landed.
    var focus by remember { mutableStateOf<MapFocus?>(null) }

    // The map is the one page that earns the turn of the wrist: while it
    // shows, the window follows the sensor; the moment it closes, the app
    // goes back to whatever it was before.
    if (mapMode) {
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val previous = activity?.requestedOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            onDispose { activity?.requestedOrientation = previous }
        }
    }

    // The places, in the order of their latest visit, with every visit kept
    // beneath the name it belongs to.
    val byPlace = remember(footprints) {
        footprints
            .groupBy { it.place }
            .entries
            .sortedByDescending { visits -> visits.value.maxOf { it.epochDay } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = 4.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.drawer_wanjiiang),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = XingKai,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (footprints.isNotEmpty()) {
                    val photoCount = footprints.sumOf { it.photos.size }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wanjiiang_summary, byPlace.size, photoCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (footprints.isNotEmpty()) {
                IconButton(onClick = { mapMode = !mapMode }) {
                    MapIcon(
                        iconSize = 22.dp,
                        color = if (mapMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        if (footprints.isEmpty()) {
            CentredNotice(stringResource(R.string.wanjiiang_empty))
        } else if (mapMode) {
            WanJiangMap(
                footprints = footprints,
                onPickPlace = { sheetPlace = it },
                focus = focus,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Gutter,
                    vertical = 4.dp,
                ),
            ) {
                byPlace.forEach { (place, visits) ->
                    item(key = "place-$place") {
                        // Where this place sits when its visits carry spots.
                        val centroid = remember(place, visits) {
                            val spotted = visits.mapNotNull { v ->
                                v.latitude?.let { lat -> v.longitude?.let { lon -> lat to lon } }
                            }
                            if (spotted.isEmpty()) {
                                null
                            } else {
                                spotted.sumOf { it.first } / spotted.size to
                                    spotted.sumOf { it.second } / spotted.size
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 8.dp)
                                .clip(CardShape)
                                .clickable(enabled = centroid != null) {
                                    centroid?.let { (lat, lon) ->
                                        focus = MapFocus(lat, lon, (focus?.nonce ?: 0) + 1)
                                        mapMode = true
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = place,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.wanjiiang_visits, visits.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // The small folded map says where this row leads.
                            if (centroid != null) {
                                Spacer(Modifier.width(8.dp))
                                MapIcon(
                                    iconSize = 15.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // The cells of one visit: a run of squares, wrapping like a
                    // page of photographs; the day and its pencil follow the
                    // last row, so every visit is dated and every visit edits.
                    visits.sortedByDescending { it.epochDay }.forEach { visit ->
                        val cells = visit.photos.ifEmpty { listOf("") }
                        cells.chunked(3).forEachIndexed { rowIndex, row ->
                            item(key = "${visit.id}-row-$rowIndex") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { name ->
                                        MediaCell(
                                            name = name,
                                            footprintId = visit.id,
                                            onOpen = {
                                                viewerFor = visit.id
                                                viewerPhoto = name
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "${visit.id}-line") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = LocalDate.ofEpochDay(visit.epochDay).format(dateFormatter),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // The spot the moment named, beside its day;
                                // the map stays silent when it cannot be honest.
                                // Tapping the name asks the map to fly there.
                                visit.locationName?.takeIf { it.isNotBlank() }?.let { named ->
                                    Spacer(Modifier.width(6.dp))
                                    val lat = visit.latitude
                                    val lon = visit.longitude
                                    Text(
                                        text = named,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clip(CardShape)
                                            .clickable(enabled = lat != null && lon != null) {
                                                if (lat != null && lon != null) {
                                                    focus = MapFocus(lat, lon, (focus?.nonce ?: 0) + 1)
                                                    mapMode = true
                                                }
                                            },
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable { onEdit(visit.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EditIcon(
                                        iconSize = 15.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerFor != null) {
        val visit = footprints.firstOrNull { it.id == viewerFor }
        if (visit != null && visit.photos.isNotEmpty()) {
            MediaViewer(
                photos = visit.photos,
                initialPhoto = viewerPhoto,
                onDismiss = { viewerFor = null },
            )
        }
    }

    // A tap on a place's dot: its every visit, held out from the bottom.
    if (sheetPlace != null) {
        val visits = footprints.filter { it.place == sheetPlace }
        if (visits.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = { sheetPlace = null }) {
                PlaceVisitsSheet(
                    place = sheetPlace!!,
                    visits = visits,
                    onOpenVisit = { visit ->
                        sheetPlace = null
                        if (visit.photos.isNotEmpty()) {
                            viewerFor = visit.id
                            viewerPhoto = visit.photos.first()
                        } else {
                            onEdit(visit.id)
                        }
                    },
                    onEdit = onEdit,
                )
            }
        }
    }
}

/**
 * One place's visits, risen from the bottom of the map: the days and their
 * spots, one row each, a pencil to amend them — the same visit the list
 * shows, only reached from above.
 */
@Composable
private fun PlaceVisitsSheet(
    place: String,
    visits: List<Footprint>,
    onOpenVisit: (Footprint) -> Unit,
    onEdit: (String) -> Unit,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = place,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.wanjiiang_visits, visits.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        visits.sortedByDescending { it.epochDay }.forEach { visit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { onOpenVisit(visit) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = LocalDate.ofEpochDay(visit.epochDay).format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge,
                )
                visit.locationName?.takeIf { it.isNotBlank() }?.let { named ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = named,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { onEdit(visit.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    EditIcon(
                        iconSize = 15.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One square of the grid: the thumbnail, or a quiet placeholder when the visit has no media. */
@Composable
private fun MediaCell(name: String, footprintId: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val thumb = remember(name) { FootprintMedia.thumbnail(context, name) }

    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .size(104.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .hairline(CardShape)
            .clickable(onClick = onOpen),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // A visit with nothing captured still holds its square: the page
            // is the going, not the picture.
            Text(
                text = stringResource(R.string.wanjiiang_no_media),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (FootprintMedia.isVideo(name)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * The media of one visit, one at a time, full-screen on black: stills and
 * GIFs as pictures — the GIF animating from its own file on API 28+ — and
 * videos through the platform's own player, a tap pausing either.
 */
@Composable
private fun MediaViewer(photos: List<String>, initialPhoto: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = (photos.indexOf(initialPhoto)).coerceAtLeast(0)
    // Keyed by the media list: another visit's viewer reuses this call site,
    // and a stale page index would point past its photos.
    var page by rememberSaveable(photos.toTypedArray()) { mutableStateOf(initial) }
    var pull by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (pull > 380f) onDismiss() else pull = 0f },
                            onDragCancel = { pull = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            pull = (pull + dragAmount).coerceAtLeast(0f)
                        }
                    },
            ) {
                val name = photos[page.coerceIn(photos.indices)]

                when {
                    FootprintMedia.isVideo(name) -> VideoPage(context, name)
                    FootprintMedia.isAnimated(name) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                        AnimatedPage(context, name)
                    else -> {
                        val bitmap = remember(name) { FootprintMedia.thumbnail(context, name, 2048) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            Text(
                text = "${page + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 18.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            // The pager is the two arrows rather than a swipe: a swipe down
            // already carries the page away, and a swipe across fights the
            // video's own touch.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ViewerArrow(pointsLeft = true, enabled = page > 0) { page-- }
                ViewerArrow(pointsLeft = false, enabled = page < photos.lastIndex) { page++ }
            }
        }
    }
}

/** One still of an animated GIF, animated: the platform's own decoder, drawn where it is. */
@Composable
private fun AnimatedPage(context: android.content.Context, name: String) {
    val drawable = remember(name) {
        runCatching {
            val source = ImageDecoder.createSource(FootprintMedia.file(context, name)!!)
            ImageDecoder.decodeDrawable(source)
        }.getOrNull()
    }
    if (drawable != null) {
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

/** A video through the platform's own player: a tap plays, another pauses. */
@Composable
private fun VideoPage(context: android.content.Context, name: String) {
    val file = remember(name) { FootprintMedia.file(context, name) }
    if (file == null) return
    var playing by remember { mutableStateOf(true) }
    val videoView = remember {
        VideoView(context).apply {
            setVideoPath(file.absolutePath)
            setOnPreparedListener { it.isLooping = true; start() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            videoView.stopPlayback()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (videoView.isPlaying) videoView.pause() else videoView.start()
                playing = videoView.isPlaying
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** One of the viewer's two arrows, drawn rather than an icon set. */
@Composable
private fun ViewerArrow(pointsLeft: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = if (pointsLeft) "‹" else "›",
        style = MaterialTheme.typography.headlineLarge,
        color = Color.White.copy(alpha = if (enabled) 0.85f else 0.3f),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    )
}

/**
 * The footprint editor: the place's name, the day, the media of the visit —
 * and, if the moment offers it, the coordinates it was noted from.
 */
@Composable
fun FootprintEditorScreen(
    initial: Footprint,
    onSave: (Footprint) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)

    var place by rememberSaveable(initial.id) { mutableStateOf(initial.place) }
    var epochDay by rememberSaveable(initial.id) { mutableStateOf(initial.epochDay) }
    var photoNames by rememberSaveable(initial.id) { mutableStateOf(ArrayList(initial.photos)) }
    var latitude by rememberSaveable(initial.id) { mutableStateOf(initial.latitude) }
    var longitude by rememberSaveable(initial.id) { mutableStateOf(initial.longitude) }
    var locationName by rememberSaveable(initial.id) { mutableStateOf(initial.locationName) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var mapPickerOpen by rememberSaveable { mutableStateOf(false) }

    // The offline map: every district's centre, parsed once, asked to name the
    // moment's coordinates. Beyond its reach a location stays unnamed rather
    // than wrongly named — the visit's own name carries it anyway.
    val chinaPlaces = remember {
        runCatching {
            context.resources.openRawResource(R.raw.china_places)
                .use { ChinaPlaces.parse(it.bufferedReader().readText()) }
        }.getOrDefault(emptyList())
    }

    fun notePlace(lat: Double, lon: Double): Boolean {
        val named = ChinaPlaces.nearest(chinaPlaces, lat, lon) ?: return false
        latitude = lat
        longitude = lon
        locationName = named.display
        return true
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = Footprint.MAX_PHOTOS),
    ) { uris ->
        uris.forEach { uri ->
            if (photoNames.size >= Footprint.MAX_PHOTOS) return@forEach
            val saved = FootprintMedia.save(context, initial.id, uri)
            if (saved != null && saved !in photoNames) {
                photoNames = ArrayList(photoNames + saved)
            }
        }
    }

    // The location is asked for when it is wanted, and only then — the page
    // works entirely without it, and a permission nobody asked about reads
    // worse than one the moment explains.
    // The location is asked for when it is wanted, and only then — the page
    // works entirely without it, and a permission nobody asked about reads
    // worse than one the moment explains. The fine permission is asked for
    // with the coarse one beside it; Android 12 and up may still answer with
    // the approximate choice the user made, and the honest naming rule
    // tolerates that — it just stays silent on a fuzzy fix.
    var locating by rememberSaveable { mutableStateOf(false) }
    var missed by rememberSaveable { mutableStateOf(false) }
    val locateScope = rememberCoroutineScope()

    fun locate() {
        if (locating) return
        locating = true
        missed = false
        locateScope.launch {
            val spot = currentLocation(context)
            // No fix, or one the map cannot name honestly, reads the same.
            missed = spot == null || !notePlace(spot.first, spot.second)
            locating = false
        }
    }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) locate()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = place,
            onValueChange = { place = it.take(Footprint.MAX_PLACE_LENGTH) },
            label = { Text(stringResource(R.string.footprint_place_hint)) },
            singleLine = true,
            shape = CardShape,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable { datePickerOpen = true }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .hairline(CardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.field_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = LocalDate.ofEpochDay(epochDay).format(dateFormatter),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable(enabled = !locating) {
                    when {
                        hasLocationPermission(context) -> locate()

                        else -> requestLocation.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .hairline(CardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.footprint_location_row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    locating -> stringResource(R.string.footprint_location_seeking)
                    locationName != null -> locationName!!
                    latitude != null -> stringResource(R.string.wanjiiang_picked_label)
                    missed -> stringResource(R.string.footprint_location_missed)
                    else -> stringResource(R.string.footprint_location_off)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 2f, fill = false),
            )
        }

        // Where the sky cannot be trusted: a spot chosen by eye on the map,
        // the crosshair's word taken over any satellite's.
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable { mapPickerOpen = true }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .hairline(CardShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.footprint_pick_on_map),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            MapIcon(
                iconSize = 18.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable {
                    if (photoNames.size < Footprint.MAX_PHOTOS) {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                            )
                        )
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .hairline(CardShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.field_photos),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (photoNames.isEmpty()) {
                    stringResource(R.string.background_unset)
                } else {
                    stringResource(R.string.photos_count, photoNames.size)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (photoNames.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                photoNames.forEach { name ->
                    val thumb = remember(name) { FootprintMedia.thumbnail(context, name, 240) }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CardShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .hairline(CardShape)
                            .clickable {
                                FootprintMedia.delete(context, name)
                                photoNames = ArrayList(photoNames - name)
                            },
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (FootprintMedia.isVideo(name)) {
                            Text(
                                text = "▶",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .padding(horizontal = 3.dp),
                            )
                        }
                        // A tap removes, and the badge says so once, quietly.
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp, 0.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.footprint_media_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        // The actions are icons rather than words: a bin to throw the visit
        // away, a cross to leave, a check to keep it — one tap each, the way
        // the rest of the app acts.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    TrashIcon(color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onDismiss) {
                CloseIcon(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = {
                    onSave(
                        initial.copy(
                            place = place.trim(),
                            epochDay = epochDay,
                            photos = photoNames.toList(),
                            latitude = latitude,
                            longitude = longitude,
                            locationName = locationName,
                        ),
                    )
                },
                enabled = place.isNotBlank(),
            ) {
                CheckIcon(
                    color = if (place.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }
    }

    if (datePickerOpen) {
        DatePickerSheet(
            initial = LocalDate.ofEpochDay(epochDay),
            onPick = {
                epochDay = it.toEpochDay()
                datePickerOpen = false
            },
            onDismiss = { datePickerOpen = false },
        )
    }

    if (mapPickerOpen) {
        MapPickerDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            onPick = { lat, lon ->
                // The point is kept whether the table can name it or not —
                // the pin on the map needs only the point; the name it can
                // give is a courtesy, not a condition.
                latitude = lat
                longitude = lon
                locationName = ChinaPlaces.nearest(chinaPlaces, lat, lon)?.display
                missed = false
                mapPickerOpen = false
            },
            onDismiss = { mapPickerOpen = false },
        )
    }
}

/** A row that scrolls sideways, sized to its content. */

/**
 * How long the whole asking may run — past this, the best of whatever was
 * heard is settled for. A cold GNSS start can take most of it.
 */
private const val SEEK_MILLIS = 20_000L

/** How long any one radio gets to answer before it is given up on. */
private const val ONE_SHOT_MILLIS = 18_000L

/** A fix this tight names a district honestly, so the asking ends there. */
private const val GOOD_ENOUGH_METRES = 100f

/** How often the settling loop looks at what the radios have said. */
private const val POLL_MILLIS = 200L

/** Whether any location permission at all stands granted. */
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Location.toFix(): LocationPick.Fix =
    LocationPick.Fix(latitude, longitude, accuracy, time)

/**
 * Where the phone says it is *now* — not where its cache last said it was.
 *
 * Every enabled radio is asked for one fresh answer, and each stale fix it
 * still holds is heard as well; the first answer tight enough to name a
 * district ends the asking early, and at the deadline whatever was heard
 * is settled by [LocationPick.best]. The winner is rounded to the
 * thousandth before it is kept — close enough to name a district, far too
 * coarse to follow anyone.
 */
private suspend fun currentLocation(context: Context): Pair<Double, Double>? = coroutineScope {
    if (!hasLocationPermission(context)) return@coroutineScope null
    val manager = context.getSystemService(LocationManager::class.java) ?: return@coroutineScope null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    if (providers.isEmpty()) return@coroutineScope null

    val heard = ConcurrentLinkedQueue<LocationPick.Fix>()
    val pending = AtomicInteger(providers.size)
    val asking = launch {
        providers.forEach { provider ->
            launch {
                // A fresh answer outranks a cache by arriving late-bound; a
                // silent radio still offers what it remembers.
                val answer = oneShot(manager, provider) ?: lastKnown(manager, provider)
                answer?.let(heard::add)
                pending.decrementAndGet()
            }
        }
    }
    var best: LocationPick.Fix? = null
    try {
        val deadline = SystemClock.elapsedRealtime() + SEEK_MILLIS
        while (true) {
            var fix = heard.poll()
            while (fix != null) {
                best = LocationPick.best(listOfNotNull(best, fix), System.currentTimeMillis())
                fix = heard.poll()
            }
            val settled = best
            if (settled != null && settled.accuracyMetres > 0f &&
                settled.accuracyMetres <= GOOD_ENOUGH_METRES
            ) {
                break
            }
            // Every radio has answered and every cache has spoken — an
            // asking with nothing to say ends now, not at the deadline.
            if (pending.get() == 0) break
            if (SystemClock.elapsedRealtime() >= deadline) break
            delay(POLL_MILLIS)
        }
    } finally {
        asking.cancel()
    }
    best?.let { fix ->
        val lat = (fix.latitude * 1000).roundToInt() / 1000.0
        val lon = (fix.longitude * 1000).roundToInt() / 1000.0
        lat to lon
    }
}

/**
 * One fresh answer from [provider], or null if it stays silent that long.
 * The listener spells out every method: on a phone older than the
 * interface's default methods, a listener that leaves one out is a crash,
 * not a listener. Whatever settles first — a fix, the silence, a
 * cancellation — unhooks everything else exactly once.
 */
private suspend fun oneShot(manager: LocationManager, provider: String): LocationPick.Fix? =
    suspendCancellableCoroutine { continuation ->
        val main = Handler(Looper.getMainLooper())
        val settled = AtomicBoolean(false)
        val watching = AtomicReference<LocationListener?>(null)

        fun finish(fix: LocationPick.Fix?) {
            if (!settled.compareAndSet(false, true)) return
            // The handler is ours alone; sweeping it takes the timeout too.
            main.removeCallbacksAndMessages(null)
            watching.get()?.let { runCatching { manager.removeUpdates(it) } }
            if (continuation.isActive) continuation.resume(fix)
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location.toFix())

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit
        }
        watching.set(listener)

        continuation.invokeOnCancellation { finish(null) }
        main.postDelayed({ finish(null) }, ONE_SHOT_MILLIS)
        val asked = runCatching {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.isSuccess
        if (!asked) finish(null)
    }

/** The stale fix [provider] still holds, if it holds one at all. */
private fun lastKnown(manager: LocationManager, provider: String): LocationPick.Fix? =
    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.toFix()

/** The activity a context belongs to, through however many wrappers carry it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
