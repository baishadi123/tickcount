package io.github.zzpby.tickcount.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.zzpby.tickcount.ui.components.CheckIcon
import io.github.zzpby.tickcount.ui.components.CloseIcon
import io.github.zzpby.tickcount.ui.components.EditIcon
import io.github.zzpby.tickcount.ui.components.TrashIcon
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.hairline
import java.time.LocalDate
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
@Composable
fun WanJiangScreen(
    footprints: List<Footprint>,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)
    var viewerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerPhoto by rememberSaveable { mutableStateOf("") }

    // The places, in the order of their latest visit, with every visit kept
    // beneath the name it belongs to.
    val byPlace = remember(footprints) {
        footprints
            .groupBy { it.place }
            .entries
            .sortedByDescending { visits -> visits.value.maxOf { it.epochDay } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 2.dp, bottom = 12.dp),
        ) {
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

        if (footprints.isEmpty()) {
            CentredNotice(stringResource(R.string.wanjiiang_empty))
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 8.dp),
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
                            Spacer(Modifier.weight(1f))
                            // The whole place edits as one: its heading is the
                            // handle to every visit under it.
                            Text(
                                text = LocalDate.ofEpochDay(visits.first().epochDay)
                                    .format(dateFormatter),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }

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
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            noteLocation(context)?.let { (lat, lon) ->
                latitude = lat
                longitude = lon
            }
        }
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
                .clickable {
                    when {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            noteLocation(context)?.let { (lat, lon) ->
                                latitude = lat
                                longitude = lon
                            }
                        }

                        else -> requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                text = if (latitude != null && longitude != null) {
                    stringResource(
                        R.string.footprint_location_noted,
                        "%.3f".format(latitude),
                        "%.3f".format(longitude),
                    )
                } else {
                    stringResource(R.string.footprint_location_off)
                },
                style = MaterialTheme.typography.bodyLarge,
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
}

/** A row that scrolls sideways, sized to its content. */

/**
 * Where the phone says it is, rounded to the thousandth — close enough to
 * name a bridge, far too coarse to follow anyone.
 */
private fun noteLocation(context: android.content.Context): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    val manager = context.getSystemService(android.location.LocationManager::class.java) ?: return null
    val location = runCatching {
        listOf(
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER,
        ).firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
    }.getOrNull() ?: return null
    val lat = (location.latitude * 1000).roundToInt() / 1000.0
    val lon = (location.longitude * 1000).roundToInt() / 1000.0
    return lat to lon
}
