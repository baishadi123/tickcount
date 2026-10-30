package io.github.zzpby.tickcount.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.Footprint
import io.github.zzpby.tickcount.domain.ChinaOutline
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
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
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

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
    onPickPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: MapFocus? = null,
) {
    val context = LocalContext.current
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
    val litYears = remember { mutableStateListOf<Int>() }

    if (places.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.wanjiiang_map_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        return
    }

    val dotColour = MaterialTheme.colorScheme.primary.toArgb()
    val maskColour = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val borderColour = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    // The country's own edge: the border drawn as a line, everything beyond
    // it laid over with the app's paper — no other country's tiles, no
    // ocean, only the ground the two of you walk.
    val borderRings = remember {
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
            // China framed and no farther out: the page is the country the
            // two of you walk, not the planet it sits on. The centre cannot
            // leave the country either — wandering ends at the border.
            minZoomLevel = 4.0
            controller.setZoom(4.6)
            controller.setCenter(GeoPoint(35.5, 104.5))
            setScrollableAreaLimitDouble(CHINA)
            overlays.add(ChinaMask(borderRings, maskColour, borderColour))
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }),
            )
            onResume()
        }
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
    LaunchedEffect(footprints, litSnapshot) {
        rebuildMarks(map, places, footprints, litSnapshot, dotColour, onPickPlace)
    }
    // The first frame after the map has its real size: the floor is the
    // zoom at which the whole country still fits — never a step further
    // out — and the frame is the visits when they sit close enough to be
    // the view themselves, the country whenever they do not.
    LaunchedEffect(Unit) {
        map.addOnFirstLayoutListener { _, _, _, _, _ ->
            map.minZoomLevel = zoomFor(map, CHINA).coerceAtLeast(4.0)
            val pins = BoundingBox.fromGeoPoints(places.map { GeoPoint(it.latitude, it.longitude) })
            if (zoomFor(map, pins) >= 6.5) fitTo(map, pins) else fitTo(map, CHINA)
        }
    }
    // The list asking the map to fly to a visit's own spot.
    LaunchedEffect(focus) {
        focus?.let { point ->
            map.controller.setZoom(11.5)
            map.controller.animateTo(GeoPoint(point.latitude, point.longitude))
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
                modifier = Modifier.fillMaxSize(),
                factory = { map },
                onRelease = { it.onDetach() },
            )

            // The tiles' own debt, said plainly in the corner.
            Text(
                text = stringResource(R.string.wanjiiang_map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
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
    remember { configureOsmdroid(context) }
    val pickerMaskColour = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val pickerBorderColour = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
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
            minZoomLevel = 4.0
            // The picking map keeps the same honesty as the other: the
            // country only, its border drawn, the beyond kept as paper.
            setScrollableAreaLimitDouble(CHINA)
            overlays.add(ChinaMask(pickerRings, pickerMaskColour, pickerBorderColour))
            val lat = initialLatitude ?: 35.5
            val lon = initialLongitude ?: 104.5
            controller.setZoom(if (initialLatitude != null) 12.0 else 4.6)
            controller.setCenter(GeoPoint(lat, lon))
            onResume()
        }
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
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { map })

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
 * visits in the order they were lived. Fixed overlays — the ground, the
 * events — keep their places; only marks are lifted and laid again.
 */
private fun rebuildMarks(
    map: MapView,
    places: List<PinnedPlace>,
    footprints: List<Footprint>,
    litYears: List<Int>,
    dotColour: Int,
    onPickPlace: (String) -> Unit,
) {
    map.overlays.removeAll { it is Marker || it is Polyline }

    places.forEach { place ->
        val marker = Marker(map)
        marker.position = GeoPoint(place.latitude, place.longitude)
        marker.icon = dotDrawable(map.context, dotColour)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.title = place.name
        marker.setOnMarkerClickListener { _, _ ->
            onPickPlace(place.name)
            true
        }
        map.overlays.add(marker)
    }

    litYears.forEachIndexed { index, year ->
        val line = Polyline(map)
        line.outlinePaint.color = yearColour(index)
        line.outlinePaint.strokeWidth = 4f * map.context.resources.displayMetrics.density
        val points = footprints.asSequence()
            .filter { it.latitude != null && it.longitude != null }
            .filter { LocalDate.ofEpochDay(it.epochDay).year == year }
            .sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
            .map { GeoPoint(it.latitude!!, it.longitude!!) }
            .toList()
        if (points.size >= 2) {
            line.setPoints(points)
            map.overlays.add(line)
        }
    }

    map.invalidate()
}

/**
 * The ground under everything: the whole country as a stipple of its
 * district centres, drawn faint and only while zoomed out far enough for
 * districts to mean anything. Where tiles arrive they cover it; where none
 * can — a walled network, a plane flight — it alone makes the page a map.
 */
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

/** A spot the list asks the map to fly to; [nonce] makes twice the same ask twice. */
data class MapFocus(val latitude: Double, val longitude: Double, val nonce: Int)

/**
 * The zoom at which [box] fills the view, by hand rather than by the
 * library's own fitter: the zoom that fits is the one where the world,
 * 256-pixel tiles edge to edge, covers the span — taken once, after the map
 * has its real size.
 */
private fun zoomFor(map: MapView, box: BoundingBox): Double {
    val spanLat = max(box.latitudeSpan, 1e-4)
    val spanLon = max(box.longitudeSpan, 1e-4)
    if (spanLat < 0.02 && spanLon < 0.02) return 12.0
    val width = max(map.width, 256)
    val height = max(map.height, 256)
    val lonAdjusted = spanLon / cos(Math.toRadians(box.center.latitude)).coerceAtLeast(0.05)
    val zoomLon = log2(360.0 * width / (256.0 * lonAdjusted))
    val zoomLat = log2(180.0 * height / (256.0 * spanLat))
    return min(zoomLon, zoomLat) - 0.45
}

/** Frames [box] with room to breathe, never out past the whole country. */
private fun fitTo(map: MapView, box: BoundingBox) {
    map.controller.setZoom(zoomFor(map, box).coerceIn(4.0, 17.0))
    map.controller.setCenter(box.center)
}

/** A dot with a white ring: a pin quiet enough to sit beside photographs. */
private fun dotDrawable(context: Context, colour: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (22 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.42f, paint)
    paint.color = colour
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.30f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** Each lit year its own hue, so the migration lines never share a colour. */
private fun yearColour(index: Int): Int {
    val hues = floatArrayOf(196f, 24f, 340f, 130f, 268f, 60f)
    val hsv = floatArrayOf(hues[index.mod(hues.size)], 0.62f, 0.78f)
    return android.graphics.Color.HSVToColor(235, hsv)
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
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColour
        alpha = 110
    }
    private val mask = android.graphics.Path()
    private val border = android.graphics.Path()
    private val spot = android.graphics.Point()
    private val here = GeoPoint(0.0, 0.0)

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || rings.isEmpty()) return
        line.strokeWidth = 1.5f * osmv.context.resources.displayMetrics.density
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
                box.maxLng < visible.lonWest || box.minLng > visible.lonEast
            ) {
                return@forEachIndexed
            }
            var first = true
            for ((lat, lng) in ring) {
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
            }
            mask.close()
            border.close()
        }

        c.drawPath(mask, paper)
        c.drawPath(border, line)
    }
}
