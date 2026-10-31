package io.github.zzpby.tickcount.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.Footprint
import io.github.zzpby.tickcount.domain.ChinaOutline
import io.github.zzpby.tickcount.domain.ChinaProvinces
import io.github.zzpby.tickcount.ui.components.ChevronIcon
import io.github.zzpby.tickcount.ui.components.GlobeIcon
import io.github.zzpby.tickcount.ui.theme.hairline
import io.github.zzpby.tickcount.ui.theme.isDarkScheme
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * 万疆's map — the same visits the scrapbook keeps, seen from above.
 *
 * Every footprint that carries coordinates is a dot pinned where it stood,
 * one dot per place; a tap opens that place's visits, and the list's own
 * place names can ask the map to fly to their spot. Years can be switched
 * on, and each lit year draws its visits as a line in its own colour, in
 * the order they were lived — a migration map of the two of you. Tiles
 * come from Amap's raster service and are cached in the app's own cache
 * directory, so a place once seen stays on the map with the network gone.
 * The first frame is the country — the page is where the two of you walk —
 * unless the pins sit close enough that the visits themselves are the view.
 */
@Composable
fun WanJiangMap(
    footprints: List<Footprint>,
    litProvinces: Set<String>,
    onPickPlace: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
    focus: MapFocus? = null,
) {
    val context = LocalContext.current
    val dark = isDarkScheme()
    remember { configureOsmdroid(context) }

    // The places that can be pinned: their visits' coordinates averaged.
    val places = remember(footprints) { pinnedPlaces(footprints) }
    // The years those visits lived in, newest first.
    val years = remember(footprints) {
        footprints.asSequence()
            .filter { it.latitude != null && it.longitude != null }
            .map { LocalDate.ofEpochDay(it.epochDay).year }
            .distinct()
            .sortedDescending()
            .toList()
    }
    // A year keeps the colour it was given for as long as the map is open,
    // so lighting a second year never repaints the first.
    val yearColours = remember(years) {
        years.mapIndexed { index, year -> year to yearColour(index) }.toMap()
    }
    val litYears = remember { mutableStateListOf<Int>() }

    if (places.isEmpty()) {
        // Even with nothing to pin, the map is a place one enters from the
        // list; the way back must not depend on there being a pin.
        Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.wanjiiang_map_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
            MapBackButton(
                onClick = onBackToList,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
        }
        return
    }

    val dotColour = MaterialTheme.colorScheme.primary.toArgb()
    // The country sits on the page rather than in a grey box: everything
    // beyond the border is the same paper the screen is made of.
    val maskColour = MaterialTheme.colorScheme.surface.toArgb()
    val borderColour = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // The country's ground at the frame view: a breath of the theme's
    // colour inside the border, enough to read the shape, never a fill.
    val washColour = MaterialTheme.colorScheme.primary
        .copy(alpha = if (dark) 0.10f else 0.065f)
        .toArgb()
    // The provinces arrived in: the same colour again over the wash, so
    // the country frame reads as a checklist — walked provinces coloured,
    // the rest still paper.
    val litColour = MaterialTheme.colorScheme.primary
        .copy(alpha = if (dark) 0.22f else 0.16f)
        .toArgb()
    // The place labels are little paper tags, written in the page's hand.
    val labelInk = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelPaper = MaterialTheme.colorScheme.surface.toArgb()
    val labelEdge = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // The route casing is the page showing through under the coloured line:
    // the same trick as a hairline around a card, in one dimension.
    val casingColour = MaterialTheme.colorScheme.surface.toArgb()
    // The country's own edge: the border drawn as a line, everything beyond
    // it laid over with the app's paper — no other country's tiles, no
    // ocean, only the ground the two of you walk.
    val borderRings = remember {
        runCatching {
            context.resources.openRawResource(R.raw.china_outline)
                .use { ChinaOutline.rings(it.bufferedReader().readText()) }
        }.getOrDefault(emptyList())
    }
    // The provinces themselves, for the ones the footprints light.
    val provinceTable = remember {
        runCatching {
            context.resources.openRawResource(R.raw.china_provinces)
                .use { ChinaProvinces.parse(it.bufferedReader().readText()) }
        }.getOrDefault(emptyList())
    }
    // Timestamp of the last finger-driven map movement; a tap that was
    // really a drag must not open a place card when it ends over a pin.
    val lastMoveAt = remember { AtomicLong(0L) }
    // Whether the map is looking closer than the country: the way back to
    // the whole country appears only when it has something to do.
    var zoomedIn by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    val countryPresence by animateFloatAsState(
        targetValue = if (zoomedIn) 1f else 0f,
        label = "map-country-button",
    )
    val map = remember {
        MapView(context).apply {
            setTileSource(AmapTiles)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            isTilesScaledToDpi = true
            // China framed and no farther out: the page is the country the
            // two of you walk, not the planet it sits on. The centre cannot
            // leave the country either — wandering ends at the border.
            minZoomLevel = 3.0
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(35.5, 104.5))
            setScrollableAreaLimitDouble(CHINA)
            // No grey grid while a tile is in flight: the page's own paper
            // shows until the tile lands, and beyond the border always.
            mapOverlay.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
            mapOverlay.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
            // Off until the first layout says how far in the view begins;
            // its strength from there on is [syncTileLayer]'s to decide.
            mapOverlay.isEnabled = false
            background = ColorDrawable(maskColour)
            overlays.add(ChinaMask(borderRings, maskColour, borderColour, washColour))
            overlays.add(ProvinceFill(provinceTable, litColour))
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }),
            )
            // The first real size is the first chance to know how much of
            // the country fits: register before the view is ever laid out,
            // because a listener added after the fact would wait forever.
            addOnFirstLayoutListener { _, _, _, _, _ ->
                minZoomLevel = countryZoomFor(this)
                val pins = BoundingBox.fromGeoPoints(
                    places.map { GeoPoint(it.latitude, it.longitude) },
                )
                // The map opens where the walk is, at the scale of the
                // province capitals — the first zoom at which the tiles
                // name the cities — rather than at the whole-country
                // frame; the country stays one tap away on the 全国 pill.
                // Several places close enough to frame together open on
                // their own span instead, never at street level.
                val pinZoom = zoomFor(this, pins)
                if (places.size >= 2 && pinZoom >= PIN_FRAME_MIN_ZOOM) {
                    controller.setZoom(pinZoom.coerceAtMost(PIN_FRAME_MAX_ZOOM))
                    controller.setCenter(pins.center)
                } else {
                    controller.setZoom(ENTRY_ZOOM)
                    controller.setCenter(pins.center)
                }
                syncTileLayer(dark)
                zoomedIn = zoomLevelDouble > minZoomLevel + 0.6
                mapReady = true
            }
            onResume()
        }
    }

    // The tiles come back muted, and dark in the dark theme, so the map sits
    // inside the page rather than glaring out of it — and only as present as
    // the current zoom has carried them past the country frame.
    LaunchedEffect(dark) {
        map.syncTileLayer(dark)
    }

    // A theme can change while the map is open (a hue, the system's dark
    // switch): the paper under and around the tiles follows it.
    LaunchedEffect(maskColour, borderColour, washColour) {
        map.background = ColorDrawable(maskColour)
        map.overlays.filterIsInstance<ChinaMask>().forEach {
            it.repaint(maskColour, borderColour, washColour)
        }
        map.invalidate()
    }

    // The lit provinces are a reading of the footprints: when a visit is
    // added or moved, the country frame's checklist follows the same beat.
    LaunchedEffect(litProvinces, litColour) {
        map.overlays.filterIsInstance<ProvinceFill>().forEach {
            it.repaint(litColour)
            it.light(litProvinces)
        }
        map.invalidate()
    }

    // Last movement, so a drag ending over a pin is not mistaken for a tap.
    DisposableEffect(map) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                lastMoveAt.set(SystemClock.uptimeMillis())
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                lastMoveAt.set(SystemClock.uptimeMillis())
                map.syncTileLayer(dark)
                zoomedIn = map.zoomLevelDouble > map.minZoomLevel + 0.6
                return false
            }
        }
        map.addMapListener(listener)
        onDispose { map.removeMapListener(listener) }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            map.onPause()
        }
    }

    // The marks are rebuilt when the visits or the lit years change — and
    // only then. Rebuilding on every recomposition would blink the pins with
    // each tick of the app's one-second clock.
    val litSnapshot = litYears.toList()
    LaunchedEffect(
        footprints,
        litSnapshot,
        dotColour,
        casingColour,
        labelInk,
        labelPaper,
        labelEdge,
    ) {
        rebuildMarks(
            map = map,
            places = places,
            footprints = footprints,
            litYears = litSnapshot,
            yearColours = yearColours,
            dotColour = dotColour,
            casingColour = casingColour,
            labelInk = labelInk,
            labelPaper = labelPaper,
            labelEdge = labelEdge,
            lastMoveAt = lastMoveAt,
            onPickPlace = onPickPlace,
        )
    }

    // The same frame after a rotation: the map may turn on its side, and
    // the country no longer fits the shape it did. At the country view the
    // frame is re-cut for the new shape, so the whole country — and every
    // pin on it — stays on the page. Closer in, the camera is left where
    // the fingers put it.
    DisposableEffect(map) {
        val listener = View.OnLayoutChangeListener {
            _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom,
            ->
            val floor = countryZoomFor(map)
            if (map.minZoomLevel != floor) map.minZoomLevel = floor
            val resized = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (!resized || !mapReady) return@OnLayoutChangeListener
            if (!zoomedIn) {
                fitCountry(map)
            } else if (map.zoomLevelDouble < floor) {
                map.controller.setZoom(floor)
                map.controller.setCenter(CHINA.center)
            }
            map.syncTileLayer(dark)
        }
        map.addOnLayoutChangeListener(listener)
        onDispose { map.removeOnLayoutChangeListener(listener) }
    }
    // The list asking the map to fly to a visit's own spot. Waits for the
    // first layout so the animation has a size to aim at, and moves the zoom
    // with the centre rather than cutting to it.
    LaunchedEffect(focus, mapReady) {
        if (!mapReady) return@LaunchedEffect
        focus?.let { point ->
            map.controller.animateTo(
                GeoPoint(point.latitude, point.longitude),
                FOCUS_ZOOM,
                700L,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (years.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                years.forEach { year ->
                    FilterChip(
                        selected = year in litYears,
                        onClick = {
                            if (year in litYears) litYears.remove(year) else litYears.add(year)
                        },
                        label = { Text(year.toString()) },
                        leadingIcon = {
                            // The legend the line itself cannot be: the year's
                            // own colour, in the chip that lights it.
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(yearColours[year] ?: dotColour)),
                            )
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                factory = { map },
                onRelease = { it.onDetach() },
            )

            // Back to the scrapbook, without leaving the page. The system
            // back key does the same thing; this is the visible half.
            MapBackButton(
                onClick = onBackToList,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )

            // The whole country, one tap away — shown only when the map has
            // wandered closer, so the corner stays quiet at the overview.
            if (countryPresence > 0.01f) {
                MapCountryButton(
                    onClick = {
                        zoomedIn = false
                        map.controller.animateTo(
                            CHINA.center,
                            countryZoomFor(map),
                            650L,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .graphicsLayer {
                            alpha = countryPresence
                            translationY = (1f - countryPresence) * -4.dp.toPx()
                        },
                )
            }

            // The tiles' own debt, said plainly in the corner.
            Text(
                text = stringResource(R.string.wanjiiang_map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * The one visible way out of the map, drawn in the page's own hand — the
 * same chevron the title bar uses for back, in a small paper button.
 */
@Composable
private fun MapBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.action_back)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .hairline(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ChevronIcon(
            pointsLeft = true,
            size = 21.dp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The way back out to the whole country: a small paper pill with a drawn
 * globe, so someone who has flown into a single place can see the shape of
 * all of it again without pinching their way there.
 */
@Composable
private fun MapCountryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.wanjiiang_map_country)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .hairline(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlobeIcon(
            iconSize = 18.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The map picker inside the footprint editor: a map under a still crosshair
 * — the pin is where the crosshair stands, because that is the one spot on
 * the map the hand cannot lose while dragging. Choose the view, take the
 * point; no satellite has to be involved.
 */
@Composable
fun MapPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onPick: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dark = isDarkScheme()
    remember { configureOsmdroid(context) }
    val pickerMaskColour = MaterialTheme.colorScheme.surface.toArgb()
    val pickerBorderColour = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // The same breath of ground inside the border as the main map, so the
    // two maps speak with one voice.
    val pickerWashColour = MaterialTheme.colorScheme.primary
        .copy(alpha = if (dark) 0.10f else 0.065f)
        .toArgb()
    val pickerRings = remember {
        runCatching {
            context.resources.openRawResource(R.raw.china_outline)
                .use { ChinaOutline.rings(it.bufferedReader().readText()) }
        }.getOrDefault(emptyList())
    }
    val map = remember {
        MapView(context).apply {
            setTileSource(AmapTiles)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            // The picking map keeps the same honesty as the other: the
            // country only, its border drawn, the beyond kept as paper.
            mapOverlay.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
            mapOverlay.setLoadingLineColor(android.graphics.Color.TRANSPARENT)
            mapOverlay.isEnabled = false
            background = ColorDrawable(pickerMaskColour)
            setScrollableAreaLimitDouble(CHINA)
            overlays.add(
                ChinaMask(pickerRings, pickerMaskColour, pickerBorderColour, pickerWashColour),
            )
            val lat = initialLatitude ?: 35.5
            val lon = initialLongitude ?: 104.5
            controller.setZoom(if (initialLatitude != null) 12.0 else 3.0)
            controller.setCenter(GeoPoint(lat, lon))
            addOnFirstLayoutListener { _, _, _, _, _ ->
                minZoomLevel = countryZoomFor(this)
                if (initialLatitude == null) fitCountry(this)
                syncTileLayer(dark)
            }
            onResume()
        }
    }

    LaunchedEffect(dark) {
        map.syncTileLayer(dark)
    }

    LaunchedEffect(pickerMaskColour, pickerBorderColour, pickerWashColour) {
        map.background = ColorDrawable(pickerMaskColour)
        map.overlays.filterIsInstance<ChinaMask>().forEach {
            it.repaint(pickerMaskColour, pickerBorderColour, pickerWashColour)
        }
        map.invalidate()
    }

    DisposableEffect(map) {
        val listener = View.OnLayoutChangeListener {
            _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom,
            ->
            val floor = countryZoomFor(map)
            if (map.minZoomLevel != floor) map.minZoomLevel = floor
            val resized = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (!resized) return@OnLayoutChangeListener
            if (initialLatitude == null) {
                fitCountry(map)
            } else if (map.zoomLevelDouble < floor) {
                map.controller.setZoom(floor)
            }
            map.syncTileLayer(dark)
        }
        map.addOnLayoutChangeListener(listener)
        onDispose { map.removeOnLayoutChangeListener(listener) }
    }

    DisposableEffect(map) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean = false

            override fun onZoom(event: ZoomEvent): Boolean {
                map.syncTileLayer(dark)
                return false
            }
        }
        map.addMapListener(listener)
        onDispose { map.removeMapListener(listener) }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    factory = { map },
                )

                // The crosshair sits at the centre and the map moves under
                // it, so what the eye frames is what the pick takes.
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp),
                ) {
                    val centre = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val reach = size.minDimension * 0.46f
                    val stroke = 2.2.dp.toPx()
                    // A dark backing under white ink keeps the cross readable
                    // over both light tiles and the photograph-dark mode.
                    val backing = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)
                    val ink = androidx.compose.ui.graphics.Color.White
                    drawLine(backing, centre.copy(x = centre.x - reach), centre.copy(x = centre.x + reach), strokeWidth = stroke + 4f)
                    drawLine(ink, centre.copy(x = centre.x - reach), centre.copy(x = centre.x + reach), strokeWidth = stroke)
                    drawLine(backing, centre.copy(y = centre.y - reach), centre.copy(y = centre.y + reach), strokeWidth = stroke + 4f)
                    drawLine(ink, centre.copy(y = centre.y - reach), centre.copy(y = centre.y + reach), strokeWidth = stroke)
                    drawCircle(ink, radius = stroke * 1.2f, center = centre)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val centre = map.mapCenter as GeoPoint
                        onPick(centre.latitude, centre.longitude)
                    },
                ) {
                    Text(stringResource(R.string.map_pick_confirm))
                }
            }
        }
    }
}

/** One place on the map: the averaged point of its visits that carry one. */
private data class PinnedPlace(val name: String, val latitude: Double, val longitude: Double)

/**
 * Lays the marks down: every place a dot, every lit year a line through its
 * visits in the order they were lived. Routes go under the dots — a line
 * crossing a place must not hide the place — and each line gets a paper
 * casing so two years crossing stay apart. Fixed overlays — the ground, the
 * events — keep their places; only marks are lifted and laid again.
 */
private fun rebuildMarks(
    map: MapView,
    places: List<PinnedPlace>,
    footprints: List<Footprint>,
    litYears: List<Int>,
    yearColours: Map<Int, Int>,
    dotColour: Int,
    casingColour: Int,
    labelInk: Int,
    labelPaper: Int,
    labelEdge: Int,
    lastMoveAt: AtomicLong,
    onPickPlace: (String) -> Unit,
) {
    map.overlays.removeAll {
        it is Marker || it is Polyline || it is PlaceLabelOverlay
    }
    val density = map.context.resources.displayMetrics.density

    // Points first, then the drawing: every casing under every line keeps a
    // later year's paper from cutting through an earlier year's colour.
    val routes = litYears.mapNotNull { year ->
        val points = footprints.asSequence()
            .filter { it.latitude != null && it.longitude != null }
            .filter { LocalDate.ofEpochDay(it.epochDay).year == year }
            .sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
            .map { GeoPoint(it.latitude!!, it.longitude!!) }
            .toList()
        if (points.size >= 2) year to points else null
    }

    routes.forEach { (_, points) ->
        val casing = Polyline(map)
        casing.outlinePaint.color = casingColour
        casing.outlinePaint.alpha = 190
        casing.outlinePaint.strokeWidth = 6.5f * density
        casing.outlinePaint.strokeCap = Paint.Cap.ROUND
        casing.outlinePaint.strokeJoin = Paint.Join.ROUND
        casing.setPoints(points)
        map.overlays.add(casing)
    }

    routes.forEach { (year, points) ->
        val line = Polyline(map)
        line.outlinePaint.color = yearColours[year] ?: yearColour(0)
        line.outlinePaint.strokeWidth = 3.5f * density
        line.outlinePaint.strokeCap = Paint.Cap.ROUND
        line.outlinePaint.strokeJoin = Paint.Join.ROUND
        line.setPoints(points)
        map.overlays.add(line)
    }

    places.forEach { place ->
        val marker = Marker(map)
        marker.position = GeoPoint(place.latitude, place.longitude)
        marker.icon = dotDrawable(map.context, dotColour)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = place.name
        marker.setOnMarkerClickListener { _, _ ->
            // A finger that has just dragged the map is still over the
            // marker it set out from; that is a pan, not a tap on a place.
            if (SystemClock.uptimeMillis() - lastMoveAt.get() < TAP_GUARD_MS) {
                true
            } else {
                onPickPlace(place.name)
                true
            }
        }
        map.overlays.add(marker)
    }

    // Names ride above the dots as little paper tags: the map should say
    // where a place is without asking for a tap first. The tags are part of
    // the tap target too — a finger goes to the word before it goes to the
    // dot.
    map.overlays.add(
        PlaceLabelOverlay(
            places = places,
            context = map.context,
            textColour = labelInk,
            paperColour = labelPaper,
            edgeColour = labelEdge,
            lastMoveAt = lastMoveAt,
            onPickPlace = onPickPlace,
        ),
    )

    map.invalidate()
}

/** The places that carry coordinates: one entry per place name, its visits averaged. */
private fun pinnedPlaces(footprints: List<Footprint>): List<PinnedPlace> =
    footprints
        .groupBy { it.place }
        .mapNotNull { (name, visits) ->
            val spotted = visits.filter { it.latitude != null && it.longitude != null }
            if (spotted.isEmpty()) return@mapNotNull null
            PinnedPlace(
                name = name,
                latitude = spotted.sumOf { it.latitude!! } / spotted.size,
                longitude = spotted.sumOf { it.longitude!! } / spotted.size,
            )
        }

/** osmdroid's home, set once per process: our own cache, our own name. */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
    config.osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
}

/**
 * The tiles, tuned to the page they sit on.
 *
 * Amap ships a bright road map. On the warm paper themes it is calmed with a
 * little less colour and a touch of warmth. On the dark theme it is turned
 * into quiet ink: desaturated, inverted and dimmed, which keeps the labels
 * light and the roads as thin dark lines instead of a white window glaring
 * out of a near-black page.
 *
 * [mix] is how present the tiles are: stepping past the country view they
 * arrive as a wash that deepens to a full map, and stepping back they
 * dissolve into the paper again.
 */
private fun tileColourFilter(dark: Boolean, mix: Float): ColorFilter {
    val matrix = ColorMatrix().apply {
        setSaturation(if (dark) 0.16f else 0.42f)
    }
    if (dark) {
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        // Lift the blacks off the floor and warm them, so the map belongs
        // to the same near-black family as the dark page.
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.82f, 0f, 0f, 0f, 8f,
                    0f, 0.83f, 0f, 0f, 8f,
                    0f, 0f, 0.84f, 0f, 9f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    } else {
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1.02f, 0f, 0f, 0f, 3f,
                    0f, 1.00f, 0f, 0f, 0f,
                    0f, 0f, 0.97f, 0f, -2f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    // The handover itself: whatever the palette did, the tile's own alpha
    // is scaled to how far the view has come past the country frame, so
    // the paper shows through until the map has truly arrived.
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, mix, 0f,
            ),
        ),
    )
    return ColorMatrixColorFilter(matrix)
}

/** Amap's raster tile hosts, one name four doors, load spread by tile. */
private val AMAP_HOSTS = arrayOf(
    "https://webrd01.is.autonavi.com/appmaptile",
    "https://webrd02.is.autonavi.com/appmaptile",
    "https://webrd03.is.autonavi.com/appmaptile",
    "https://webrd04.is.autonavi.com/appmaptile",
)

/**
 * Amap's raster road tiles: fast in China, labelled in Chinese, no key
 * asked. Unofficial for third-party apps — a note the README carries too —
 * and replaceable by one tile-source change if it ever fades.
 */
private object AmapTiles : OnlineTileSourceBase(
    "Amap",
    3, 18, 256, ".png",
    AMAP_HOSTS,
    "© 高德",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val host = AMAP_HOSTS[(x + y).mod(AMAP_HOSTS.size)]
        return "$host?x=$x&y=$y&z=$z&lang=zh_cn&size=1&scale=1&style=8"
    }
}

/** The country the page is about, framed the first time the map opens. */
private val CHINA = BoundingBox(53.55, 135.08, 18.16, 73.66)

/** The zoom a place card's spot is flown to. */
private const val FOCUS_ZOOM = 11.5

/**
 * The country is the app's own drawing until a place is close enough to be
 * a place: below this zoom the tile layer is put away, so its province
 * lines and country-sized lettering never sit over the border and its
 * wash. Tiles come back at the regional view, where streets and Chinese
 * names are wanted.
 */
private const val TILES_FROM_ZOOM = 4.0

/**
 * The zoom by which the lit provinces have thinned away entirely. Over the
 * country frame and the regional tiles they stay lit — the checklist is
 * the point — but a street view is about the street, not the province.
 */
private const val LIT_TO_ZOOM = 7.0

/**
 * How far past [TILES_FROM_ZOOM] the tiles take to reach full strength: the
 * ground changes from the page's own drawing to the road map as a dissolve,
 * never as a blink.
 */
private const val TILE_FADE_SPAN = 0.8

/**
 * The closest the first frame may be when several places sit together: a
 * city is a view, a street is not. Below [PIN_FRAME_MIN_ZOOM] the pins are
 * too far apart to be the subject, so the country keeps the frame.
 */
private const val PIN_FRAME_MIN_ZOOM = 6.5
private const val PIN_FRAME_MAX_ZOOM = 10.0

/**
 * The zoom the map opens at when the pins do not frame a span of their
 * own: one step wider than the zoom at which the tiles begin to name the
 * province capitals — the region reads as a whole before the cities do,
 * and the whole country stays one 全国 tap away.
 */
private const val ENTRY_ZOOM = 5.0

/** Closer than the country, a place name is worth drawing on the map. */
private const val LABEL_MIN_ZOOM = 7.0

/** At the country view, names are drawn only while the pins are few. */
private const val LABELS_AT_OVERVIEW_MAX = 10

/** How soon after a pan a tap still belongs to that pan rather than to a pin. */
private const val TAP_GUARD_MS = 320L

/** A spot the list asks the map to fly to; [nonce] makes twice the same ask twice. */
data class MapFocus(val latitude: Double, val longitude: Double, val nonce: Int)

/**
 * The zoom at which [box] fills the view, by hand rather than by the
 * library's own fitter: the zoom that fits is the one where the world,
 * its tiles edge to edge, covers the span — taken once, after the map
 * has its real size. The tile size comes from osmdroid rather than from
 * the nominal 256: the map scales tiles to the display's density, and a
 * fit that assumed 256 would cut the country's edges off.
 */
private fun zoomFor(map: MapView, box: BoundingBox, padding: Double = 0.45): Double {
    val spanLat = max(box.latitudeSpan, 1e-4)
    val spanLon = max(box.longitudeSpan, 1e-4)
    if (spanLat < 0.02 && spanLon < 0.02) return 12.0
    val width = max(map.width, 256)
    val height = max(map.height, 256)
    val tile = org.osmdroid.util.TileSystem.getTileSize().toDouble()
    val lonAdjusted = spanLon / cos(Math.toRadians(box.center.latitude)).coerceAtLeast(0.05)
    val zoomLon = log2(360.0 * width / (tile * lonAdjusted))
    val zoomLat = log2(180.0 * height / (tile * spanLat))
    return min(zoomLon, zoomLat) - padding
}

/**
 * The zoom at which the whole country sits large in the view, a slim
 * margin of page around it — the map is the subject, not a stamp on an
 * envelope. Latitude is measured the way the projection measures it — in
 * Mercator, where a degree of latitude grows as one goes north — so a
 * wide, short frame (a phone turned on its side) steps back far enough
 * for China's height to fit, rather than leaving its north and south cut
 * off.
 */
private fun countryZoomFor(map: MapView): Double {
    val width = max(map.width, 256)
    val height = max(map.height, 256)
    val tile = org.osmdroid.util.TileSystem.getTileSize().toDouble()
    val zoomLon = log2(360.0 * width / (tile * CHINA.longitudeSpan))
    val spanLat = mercatorY(CHINA.latNorth) - mercatorY(CHINA.latSouth)
    val zoomLat = log2(2.0 * Math.PI * height / (tile * spanLat))
    return min(zoomLon, zoomLat) - 0.05
}

/** Mercator's y, in radians of the map's own vertical measure. */
private fun mercatorY(latitude: Double): Double =
    ln(tan(Math.PI / 4.0 + Math.toRadians(latitude) / 2.0))

/**
 * Shows the raster tiles only where the map is closer than the country
 * overview, and only as present as the zoom has carried them. The country
 * frame is the app's own drawing; the tile layer is put away there rather
 * than merely covered, so a cached z=3 tile (with its country-sized
 * lettering) cannot surface after a zoom out. Past it the tiles fade in
 * over the paper through [TILE_FADE_SPAN], and fade back out on the way
 * down, so the ground never changes in one jump.
 */
private fun MapView.syncTileLayer(dark: Boolean) {
    val mix = ((zoomLevelDouble - TILES_FROM_ZOOM) / TILE_FADE_SPAN)
        .coerceIn(0.0, 1.0)
        .toFloat()
    mapOverlay.setColorFilter(tileColourFilter(dark, mix))
    if (mapOverlay.isEnabled != (mix > 0f)) {
        mapOverlay.isEnabled = mix > 0f
    }
    invalidate()
}

/**
 * Whether a point sits inside this frame. A frame wider than a screenful of
 * the world can have crossed the antimeridian, and then its east lies west
 * of its west on the number line: the side that wraps is kept, not
 * mistaken for an empty view.
 */
private fun BoundingBox.covers(latitude: Double, longitude: Double): Boolean {
    if (latitude < latSouth || latitude > latNorth) return false
    return if (lonWest <= lonEast) {
        longitude in lonWest..lonEast
    } else {
        longitude >= lonWest || longitude <= lonEast
    }
}

/** Whether a longitude range [west]..[east] touches this (possibly wrapped) frame. */
private fun BoundingBox.touchesLongitude(west: Double, east: Double): Boolean {
    if (lonWest <= lonEast) {
        return if (west <= east) {
            west <= lonEast && east >= lonWest
        } else {
            west <= lonEast || east >= lonWest
        }
    }
    // The frame wraps; a range that wraps too can only touch it.
    return west > east || east >= lonWest || west <= lonEast
}

/** Frames the whole country, centred, at [countryZoomFor]. */
private fun fitCountry(map: MapView) {
    map.controller.setZoom(countryZoomFor(map))
    map.controller.setCenter(CHINA.center)
}

/**
 * A dot with a white ring and a soft shadow under it: a pin quiet enough to
 * sit beside photographs, yet still lifted off a busy tile at a glance.
 */
private fun dotDrawable(context: Context, colour: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (24 * density).toInt()
    val centre = sizePx / 2f
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    // The halo: a half-transparent shadow rather than a hard drop shadow,
    // so the pin reads as resting on the paper instead of pasted on it.
    paint.color = 0x30000000
    canvas.drawCircle(centre, centre, sizePx * 0.47f, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(centre, centre, sizePx * 0.38f, paint)
    paint.color = colour
    canvas.drawCircle(centre, centre, sizePx * 0.27f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** Each lit year its own hue, so the migration lines never share a colour. */
private fun yearColour(index: Int): Int {
    val hues = floatArrayOf(196f, 24f, 340f, 130f, 268f, 60f)
    val hsv = floatArrayOf(hues[index.mod(hues.size)], 0.62f, 0.78f)
    return android.graphics.Color.HSVToColor(235, hsv)
}

/**
 * The place names, laid over the dots as small paper tags written in the
 * page's own hand.
 *
 * At the country-wide view only a handful of names can be read without
 * turning into a pile of labels, so they are shown there only while there
 * are few enough to matter; closer in, every place earns its name. Tags
 * that would overlap a neighbour step aside rather than stack on it. The
 * tags are also tap targets, because a finger aiming at a name should not
 * have to find the dot underneath it.
 */
private class PlaceLabelOverlay(
    private val places: List<PinnedPlace>,
    context: Context,
    textColour: Int,
    paperColour: Int,
    edgeColour: Int,
    private val lastMoveAt: AtomicLong,
    private val onPickPlace: (String) -> Unit,
) : Overlay() {

    private class Hit(val rect: RectF, val name: String)

    private val density = context.resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColour
        textAlign = Paint.Align.CENTER
        typeface = runCatching { context.resources.getFont(R.font.xingkai) }
            .getOrNull() ?: Typeface.DEFAULT
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            context.resources.displayMetrics,
        )
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = paperColour
        alpha = 238
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = edgeColour
        alpha = 72
        strokeWidth = 0.9f * density
    }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x16000000
    }
    private val metrics = textPaint.fontMetrics
    private val textHeight = metrics.descent - metrics.ascent
    private val padH = 6.5f * density
    private val padV = 3.2f * density
    private val gap = 5f * density
    private val dotRadius = 12f * density
    private val corner = 5.5f * density
    private val maxTextWidth = 150f * density
    // A tag is small by design; the finger does not have to be. The hit
    // area reaches a little past the paper on every side.
    private val hitSlack = 6f * density

    private val hits = ArrayList<Hit>()
    private val spot = Point()
    private val here = GeoPoint(0.0, 0.0)

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        hits.clear()
        if (shadow || places.isEmpty()) return
        val zoom = osmv.zoomLevelDouble
        if (zoom < LABEL_MIN_ZOOM && places.size > LABELS_AT_OVERVIEW_MAX) return
        if (osmv.width == 0 || osmv.height == 0) return

        val projection = osmv.projection
        val visible = projection.boundingBox
        val width = osmv.width
        val height = osmv.height
        val tagHeight = textHeight + padV * 2f

        for (place in places) {
            if (!visible.covers(place.latitude, place.longitude)) continue
            here.setCoords(place.latitude, place.longitude)
            projection.toPixels(here, spot)
            if (spot.x < -40 || spot.y < -40 || spot.x > width + 40 || spot.y > height + 40) {
                continue
            }

            val text = fit(place.name)
            val textWidth = textPaint.measureText(text)
            val tagWidth = textWidth + padH * 2f
            // Above the dot by default; a dot at the very top of the view
            // gets its name below instead, never off the paper.
            val above = spot.y - dotRadius - gap
            val below = above - tagHeight < 2f
            val top = if (below) spot.y + dotRadius + gap else above - tagHeight
            if (top < 2f || top + tagHeight > height - 2f) continue
            val left = (spot.x - tagWidth / 2f)
                .coerceIn(2f, (width - tagWidth - 2f).coerceAtLeast(2f))
            val rect = RectF(left, top, left + tagWidth, top + tagHeight)
            if (hits.any { RectF.intersects(it.rect, rect) }) continue

            hits.add(Hit(RectF(rect), place.name))

            // A soft shadow, the tag's own paper, a hairline edge, then the
            // little tail pointing down at the place.
            val shadowRect = RectF(rect)
            shadowRect.offset(0f, 1.4f * density)
            c.drawRoundRect(shadowRect, corner, corner, shade)

            val tail = android.graphics.Path()
            val tipX = spot.x.toFloat()
                .coerceIn(rect.left + corner * 2f, rect.right - corner * 2f)
            if (below) {
                tail.moveTo(tipX - 3.5f * density, rect.top + 1f)
                tail.lineTo(tipX + 3.5f * density, rect.top + 1f)
                tail.lineTo(tipX, rect.top - 4f * density)
            } else {
                tail.moveTo(tipX - 3.5f * density, rect.bottom - 1f)
                tail.lineTo(tipX + 3.5f * density, rect.bottom - 1f)
                tail.lineTo(tipX, rect.bottom + 4f * density)
            }
            tail.close()
            c.drawPath(tail, fill)

            c.drawRoundRect(rect, corner, corner, fill)
            c.drawRoundRect(rect, corner, corner, edge)
            c.drawText(
                text,
                rect.centerX(),
                rect.top + padV - metrics.ascent,
                textPaint,
            )
        }
    }

    private fun fit(name: String): String {
        if (textPaint.measureText(name) <= maxTextWidth) return name
        var end = name.length
        while (end > 1 && textPaint.measureText(name.substring(0, end) + "…") > maxTextWidth) {
            end--
        }
        return name.substring(0, end) + "…"
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (SystemClock.uptimeMillis() - lastMoveAt.get() < TAP_GUARD_MS) return false
        val hit = hits.lastOrNull { tag ->
            e.x >= tag.rect.left - hitSlack && e.x <= tag.rect.right + hitSlack &&
                e.y >= tag.rect.top - hitSlack && e.y <= tag.rect.bottom + hitSlack
        } ?: return false
        onPickPlace(hit.name)
        return true
    }
}

/**
 * The country's edge, drawn every frame from the embedded border: a rect
 * the size of the screen (and then some) under an even-odd fill with the
 * border's rings, so everything beyond the border comes out as the app's
 * own paper — and the border itself walks as a quiet line. Rings whose
 * ground the eye cannot see are skipped before a single point is
 * projected; the one reused GeoPoint spares the collector a country a
 * second.
 */
private class ChinaMask(
    private val rings: List<List<Pair<Double, Double>>>,
    fillColour: Int,
    borderColour: Int,
    washColour: Int,
) : Overlay() {

    private class Box(ring: List<Pair<Double, Double>>) {
        val minLat = ring.minOf { it.first }
        val maxLat = ring.maxOf { it.first }
        val minLng = ring.minOf { it.second }
        val maxLng = ring.maxOf { it.second }
    }

    private val boxes = rings.map { Box(it) }
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColour
    }
    // The country's own ground at the frame view: a breath of the theme's
    // colour inside the border, enough that the country reads as a shape on
    // the page before any tile is there to colour it. As the tiles arrive
    // it thins away, handing the ground over to them.
    // The base colour is kept in its own field: `Paint.setAlpha` overwrites
    // the alpha inside the paint's colour, so reading it back after a faded
    // frame would fade the next frame too — a wash that had once dissolved
    // could never return.
    private var washColour: Int = washColour
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = washColour
    }
    // The border is laid twice: a wide, nearly transparent band of its own
    // colour that fades the tiles into the paper, then the line itself. It
    // is the same trick a brush wash uses — a soft edge before the stroke.
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColour
        alpha = 10
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColour
        alpha = 78
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val mask = android.graphics.Path()
    private val border = android.graphics.Path()
    private val spot = android.graphics.Point()
    private val here = GeoPoint(0.0, 0.0)

    /** Follows a theme change while the map is open. */
    fun repaint(fillColour: Int, borderColour: Int, washColour: Int) {
        paper.color = fillColour
        halo.color = borderColour
        line.color = borderColour
        this.washColour = washColour
        wash.color = washColour
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || rings.isEmpty()) return
        val density = osmv.context.resources.displayMetrics.density
        line.strokeWidth = 0.85f * density
        halo.strokeWidth = 2.4f * density
        // At the country-wide zoom the border is a sketch rather than a
        // survey: every other point is enough to draw it honestly, and the
        // halved walk is what keeps a pinch-out smooth.
        val step = if (osmv.zoomLevelDouble < 5.5) 2 else 1
        val projection = osmv.projection
        val visible = projection.boundingBox

        mask.reset()
        mask.fillType = android.graphics.Path.FillType.EVEN_ODD
        mask.addRect(
            -c.width.toFloat(),
            -c.height.toFloat(),
            c.width * 2f,
            c.height * 2f,
            android.graphics.Path.Direction.CW,
        )
        border.reset()

        rings.forEachIndexed { index, ring ->
            val box = boxes[index]
            if (box.maxLat < visible.latSouth || box.minLat > visible.latNorth ||
                !visible.touchesLongitude(box.minLng, box.maxLng)
            ) {
                return@forEachIndexed
            }
            var first = true
            var i = 0
            while (i < ring.size) {
                val (lat, lng) = ring[i]
                here.setCoords(lat, lng)
                projection.toPixels(here, spot)
                if (first) {
                    mask.moveTo(spot.x.toFloat(), spot.y.toFloat())
                    border.moveTo(spot.x.toFloat(), spot.y.toFloat())
                    first = false
                } else {
                    mask.lineTo(spot.x.toFloat(), spot.y.toFloat())
                    border.lineTo(spot.x.toFloat(), spot.y.toFloat())
                }
                i += step
            }
            mask.close()
            border.close()
        }

        // The wash dissolves as the tiles rise, so the ground is handed
        // over rather than swapped beneath the eye. The strength is taken
        // from the stored base, never from the paint, whose alpha carries
        // last frame's fade.
        val mix = ((osmv.zoomLevelDouble - TILES_FROM_ZOOM) / TILE_FADE_SPAN)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val washAlpha = (android.graphics.Color.alpha(washColour) * (1f - mix)).toInt()
        if (washAlpha > 0) {
            wash.alpha = washAlpha
            c.drawPath(border, wash)
        }
        c.drawPath(mask, paper)
        c.drawPath(border, halo)
        c.drawPath(border, line)
    }
}

/**
 * The provinces arrived in, lit on the country frame: their ground a shade
 * stronger than the country's wash, so the page reads at a glance as a
 * checklist — walked provinces coloured, the rest still paper.
 *
 * When the tiles rise the light does not go out with the paper: it keeps
 * its ground *over* the road map — a checklist that vanished the moment
 * detail arrived would read as a glitch — and thins away gently until the
 * street is the subject ([LIT_TO_ZOOM]).
 */
private class ProvinceFill(
    private val provinces: List<ChinaProvinces.Province>,
    colour: Int,
) : Overlay() {

    private class Box(ring: List<Pair<Double, Double>>) {
        val minLat = ring.minOf { it.first }
        val maxLat = ring.maxOf { it.first }
        val minLng = ring.minOf { it.second }
        val maxLng = ring.maxOf { it.second }
    }

    private val boxes = provinces.map { p -> p.rings.map { Box(it) } }
    // The base colour in its own field, for the same reason ChinaMask keeps
    // its wash's: the paint's own alpha carries the last frame's fade, and
    // reading the fade back as the base is how a light goes out for good.
    private var colour: Int = colour
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colour
    }
    private val path = android.graphics.Path()
    private val spot = android.graphics.Point()
    private val here = GeoPoint(0.0, 0.0)
    private var lit: Set<String> = emptySet()

    /** Follows a theme change while the map is open. */
    fun repaint(colour: Int) {
        this.colour = colour
        paint.color = colour
    }

    /** Follows the footprints: the provinces they now stand in. */
    fun light(names: Set<String>) {
        lit = names
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || lit.isEmpty() || provinces.isEmpty()) return

        // Full strength on the country's own paper; once the tiles rise the
        // light carries over them and thins by the zoom, so the handover
        // from drawing to road map never blinks the checklist away.
        val zoom = osmv.zoomLevelDouble
        val strength = when {
            zoom < TILES_FROM_ZOOM -> 1f
            zoom < LIT_TO_ZOOM ->
                1f - ((zoom - TILES_FROM_ZOOM) / (LIT_TO_ZOOM - TILES_FROM_ZOOM)).toFloat()
            else -> return
        }
        val alpha = (android.graphics.Color.alpha(colour) * strength).toInt()
        if (alpha <= 0) return
        paint.alpha = alpha

        // At the country frame every other point is enough to fill a
        // province honestly — the same walk ChinaMask takes.
        val step = if (zoom < 5.5) 2 else 1
        val projection = osmv.projection
        val visible = projection.boundingBox

        path.reset()
        var any = false
        provinces.forEachIndexed { index, province ->
            if (province.name !in lit) return@forEachIndexed
            province.rings.forEachIndexed { ringIndex, ring ->
                val box = boxes[index][ringIndex]
                if (box.maxLat < visible.latSouth || box.minLat > visible.latNorth ||
                    !visible.touchesLongitude(box.minLng, box.maxLng)
                ) {
                    return@forEachIndexed
                }
                var first = true
                var i = 0
                while (i < ring.size) {
                    val (lat, lng) = ring[i]
                    here.setCoords(lat, lng)
                    projection.toPixels(here, spot)
                    if (first) {
                        path.moveTo(spot.x.toFloat(), spot.y.toFloat())
                        first = false
                    } else {
                        path.lineTo(spot.x.toFloat(), spot.y.toFloat())
                    }
                    i += step
                }
                path.close()
                any = true
            }
        }
        if (any) c.drawPath(path, paint)
    }
}
