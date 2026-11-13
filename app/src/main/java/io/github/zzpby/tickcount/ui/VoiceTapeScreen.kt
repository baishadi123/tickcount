package io.github.zzpby.tickcount.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.TapeAudio
import io.github.zzpby.tickcount.data.TapeImport
import io.github.zzpby.tickcount.data.VoiceRecorder
import io.github.zzpby.tickcount.data.VoiceTape
import io.github.zzpby.tickcount.data.newTapeId
import io.github.zzpby.tickcount.ui.components.CloseIcon
import io.github.zzpby.tickcount.ui.components.PauseIcon
import io.github.zzpby.tickcount.ui.components.PlayIcon
import io.github.zzpby.tickcount.ui.components.SectionLabel
import io.github.zzpby.tickcount.ui.components.dialogWindowGuard
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.glassSurface
import io.github.zzpby.tickcount.ui.theme.isDarkScheme
import io.github.zzpby.tickcount.ui.theme.textHalo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The physics of a compact cassette, scaled to a voice note: the spools
 * trade tape by area conservation, and the reel angles are pure functions
 * of how far in the tape is — seek, pause and speed all come out right by
 * construction, because nothing accumulates.
 *
 * The real machine runs its tape at a fixed 4.76 cm/s, which for a
 * half-minute note would barely wind a spool. So the turn count is staged
 * instead, while the radius curve keeps the honest shape:
 * r(f) = √(hub² + (1−hub²)·f), turns linear in (r − hub).
 */
private class TapeSpec(
    val hubRatio: Float,
    val totalTurns: Float,
) {
    fun packRadius(wound: Float): Float {
        val f = wound.coerceIn(0f, 1f)
        return sqrt(hubRatio * hubRatio + (1f - hubRatio * hubRatio) * f)
    }

    private fun turns(wound: Float): Float =
        (packRadius(wound) - hubRatio) * totalTurns / (1f - hubRatio)

    fun takeUpTurns(progress: Float): Float = turns(progress)
    fun supplyTurns(progress: Float): Float = totalTurns - turns(1f - progress)
}

private const val TURNS_PER_SECOND = 0.42f

/** The doctrine's one palette, day and night — saturation low, brass alone. */
private class TapePalette(
    val colorways: List<TapeColorway>,
    val shellHi: Color,
    val shellEdge: Color,
    val labelFill: Color,
    val labelLine: Color,
    val labelInk: Color,
    val winTop: Color,
    val winBottom: Color,
    val tapeHi: Color,
    val tapeMid: Color,
    val tapeLo: Color,
    val hub: Color,
    val hubSlot: Color,
    val brass: Color,
    val inkStrong: Color,
    val muted: Color,
    val stageCenter: Color,
    val stageEdge: Color,
)

/**
 * One shell's colour: three stops along the light — lit face, body, shaded
 * edge. Four ways a tape can be dressed, all whispers of saturation, so a
 * shelf of them reads as a collection, not a carnival.
 */
private class TapeColorway(val lit: Color, val body: Color, val shade: Color)

private val DayPalette = TapePalette(
    colorways = listOf(
        // 象牙 — the family the first tape wore.
        TapeColorway(Color(0xFFFBF6EE), Color(0xFFF3ECDD), Color(0xFFD8CAB6)),
        // 鼠尾草 — sage, grey more than green.
        TapeColorway(Color(0xFFEDF0E7), Color(0xFFE2E7DB), Color(0xFFC2C8B8)),
        // 豆沙 — dusty rose, clay more than pink.
        TapeColorway(Color(0xFFF4E7E1), Color(0xFFECDCD4), Color(0xFFD3BBB0)),
        // 雾蓝 — mist, slate more than blue.
        TapeColorway(Color(0xFFE7ECF0), Color(0xFFDCE3E9), Color(0xFFBCC6CF)),
    ),
    shellHi = Color.White.copy(alpha = 0.85f),
    shellEdge = Color(0xFF766149).copy(alpha = 0.20f),
    labelFill = Color(0xFFFCF8F1),
    labelLine = Color(0xFF766149).copy(alpha = 0.16f),
    labelInk = Color(0xFF6B5B49),
    winTop = Color(0xFF332C26),
    winBottom = Color(0xFF161311),
    tapeHi = Color(0xFF8A7761),
    tapeMid = Color(0xFF3B322A),
    tapeLo = Color(0xFF1B1511),
    hub = Color(0xFFEFE8DC),
    hubSlot = Color(0xFF241E1A),
    brass = Color(0xFFB8834A),
    inkStrong = Color(0xFF2C2721),
    muted = Color(0xFF8B8172),
    stageCenter = Color(0xFFEDE7DC),
    stageEdge = Color(0xFFDED3C2),
)

private val NightPalette = TapePalette(
    colorways = listOf(
        TapeColorway(Color(0xFF312C26), Color(0xFF272320), Color(0xFF1A1613)),
        TapeColorway(Color(0xFF272B26), Color(0xFF22261F), Color(0xFF161A15)),
        TapeColorway(Color(0xFF322826), Color(0xFF2A2220), Color(0xFF1B1514)),
        TapeColorway(Color(0xFF262B30), Color(0xFF20252A), Color(0xFF151A1F)),
    ),
    shellHi = Color.White.copy(alpha = 0.12f),
    shellEdge = Color.Black.copy(alpha = 0.55f),
    labelFill = Color(0xFF3A332B),
    labelLine = Color(0xFFE2A45C).copy(alpha = 0.20f),
    labelInk = Color(0xFFD9CDBC),
    winTop = Color(0xFF0F0D0B),
    winBottom = Color(0xFF050404),
    tapeHi = Color(0xFF5E5245),
    tapeMid = Color(0xFF2A231C),
    tapeLo = Color(0xFF120E0B),
    hub = Color(0xFFD6CCBC),
    hubSlot = Color(0xFF171310),
    brass = Color(0xFFE2A45C),
    inkStrong = Color(0xFFE8DFD2),
    muted = Color(0xFF8A7F70),
    stageCenter = Color(0xFF14120F),
    stageEdge = Color(0xFF0A0908),
)

/**
 * The wall of voice tapes — her voice, shelved. The tapes are real now:
 * grouped by the month they were taken in, played in place by a tap,
 * opened on the deck by a long press, added by the record key. One voice
 * at a time — whichever tape is playing owns the room.
 */
@Composable
fun VoiceTapeScreen(
    tapes: List<VoiceTape>,
    onSaveTape: (VoiceTape) -> Unit,
    onDeleteTape: (VoiceTape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var deckTape by remember { mutableStateOf<VoiceTape?>(null) }
    var recording by remember { mutableStateOf(false) }
    val grain = remember { makeShellGrain() }
    val dark = isDarkScheme()
    val palette = if (dark) NightPalette else DayPalette

    // The wall's one player: a tap sets a tape turning with its own voice;
    // another tap, or a different tape, ends it.
    var playingId by remember { mutableStateOf<String?>(null) }
    val wallPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopWallPlayback() {
        wallPlayer.value?.runCatching { stop(); release() }
        wallPlayer.value = null
        playingId = null
    }
    fun toggleWallPlayback(tape: VoiceTape) {
        if (playingId == tape.id) {
            stopWallPlayback()
            return
        }
        stopWallPlayback()
        val file = tape.fileName?.let { TapeAudio.file(context, it) } ?: return
        wallPlayer.value = MediaPlayer().apply {
            runCatching {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stopWallPlayback() }
                start()
            }.onFailure { runCatching { release() } }
        }
        if (wallPlayer.value != null) playingId = tape.id
    }
    // The room empties when the page does.
    DisposableEffect(Unit) {
        onDispose { stopWallPlayback() }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) recording = true }

    // The back door: any audio or video file, taken in and made a tape.
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }
    val pickImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            importing = true
            val id = newTapeId()
            val imported = TapeImport.import(context, uri, id)
            importing = false
            if (imported != null) {
                onSaveTape(
                    VoiceTape(
                        id = id,
                        title = imported.displayName,
                        createdAt = imported.takenAt,
                        fileName = imported.fileName,
                        seconds = imported.seconds,
                        bars = imported.bars,
                        colorway = tapes.size % 4,
                    ),
                )
            } else {
                importFailed = true
            }
        }
    }
    LaunchedEffect(importFailed) {
        if (importFailed) {
            kotlinx.coroutines.delay(2200)
            importFailed = false
        }
    }

    // The deck is the page itself: while it is open, the wall is not
    // here — a page away, not a veil over the list.
    if (deckTape != null) {
        stopWallPlayback()
        TapeDeck(
            tape = deckTape!!,
            grain = grain,
            onDismiss = { deckTape = null },
            onDelete = {
                onDeleteTape(deckTape!!)
                deckTape = null
            },
            onRecolor = { shell ->
                val redressed = deckTape!!.copy(colorway = shell)
                onSaveTape(redressed)
                deckTape = redressed
            },
        )
        return
    }

    // One shelf to a month, the newest at the top.
    val byMonth = remember(tapes) {
        tapes.groupBy {
            java.time.YearMonth.from(
                java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()),
            )
        }.toSortedMap(compareByDescending { it })
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Two tapes to a shelf, sized from the page instead of a fixed 160dp:
        // a pair at that width used to run past the gutter and off the screen.
        val rowWidth = maxWidth - Gutter * 2 - 16.dp
        val cardWidth = ((rowWidth - 22.dp) / 2).coerceAtLeast(120.dp)

        // Lazy: a wall of fifty tapes composes the shelves on screen, not all
        // fifty at once. The month label travels with its first shelf.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 26.dp, bottom = 28.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tape_wall_title),
                            fontFamily = XingKai,
                            fontSize = 30.sp,
                            color = palette.inkStrong,
                            style = MaterialTheme.typography.titleLarge.copy(shadow = textHalo()),
                        )
                        Text(
                            text = stringResource(R.string.tape_wall_count, tapes.size),
                            fontSize = 12.sp,
                            color = palette.muted,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                        )
                    }
                    // The back door first, then the record key: the line coming
                    // in and the tape already wound, both drawn by the same hand.
                    WallKey(onClick = { pickImport.launch(arrayOf("audio/*", "video/*")) }) {
                        ImportMark(color = palette.inkStrong.copy(alpha = 0.88f))
                    }
                    Spacer(Modifier.width(14.dp))
                    WallKey(
                        onClick = {
                            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) recording = true else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    ) {
                        RecordMark(color = palette.brass)
                    }
                }
            }

            if (importing) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = stringResource(R.string.tape_importing),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = palette.brass,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (importFailed) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = stringResource(R.string.tape_import_failed),
                            fontSize = 12.sp,
                            color = palette.brass,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            if (tapes.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tape_wall_empty),
                        fontFamily = XingKai,
                        fontSize = 15.sp,
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                    )
                }
            }

            byMonth.forEach { (month, monthTapes) ->
                item(key = "month-${month.year}-${month.monthValue}") {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(
                        if (month.year == java.time.Year.now().value) {
                            "${month.monthValue}月"
                        } else {
                            "${month.year}年${month.monthValue}月"
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(monthTapes.chunked(2)) { row ->
                    TapeShelf(
                        tapes = row,
                        cardWidth = cardWidth,
                        playingId = playingId,
                        player = wallPlayer.value,
                        grain = grain,
                        onPlay = { tape -> toggleWallPlayback(tape) },
                        onOpenDeck = { deckTape = it },
                    )
                }
            }
        }
    }

    if (recording) {
        RecordSheet(
            nextColorway = tapes.size,
            onDismiss = { recording = false },
            onSave = { tape ->
                onSaveTape(tape)
                recording = false
            },
        )
    }
}

/** One glass plank with the month's tapes standing on it. */
@Composable
private fun TapeShelf(
    tapes: List<VoiceTape>,
    cardWidth: Dp,
    playingId: String?,
    player: MediaPlayer?,
    grain: ImageBitmap,
    onPlay: (VoiceTape) -> Unit,
    onOpenDeck: (VoiceTape) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            tapes.forEach { tape ->
                VoiceTapeCard(
                    tape = tape,
                    width = cardWidth,
                    playing = playingId == tape.id,
                    player = player,
                    grain = grain,
                    onToggle = { onPlay(tape) },
                    onOpenDeck = { onOpenDeck(tape) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .glassSurface(shape = RoundedCornerShape(4.dp)),
        )
    }
}

/** A tape on the wall: a tap plays it in place, a long press takes it to the deck. */
@Composable
private fun VoiceTapeCard(
    tape: VoiceTape,
    width: Dp,
    playing: Boolean,
    player: MediaPlayer?,
    grain: ImageBitmap,
    onToggle: () -> Unit,
    onOpenDeck: () -> Unit,
) {
    // The position comes from the player itself while it runs — the same
    // pure-function rule the deck obeys — and rests wherever it stopped.
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, player) {
        while (playing && player != null) {
            awaitFrame()
            val duration = player.duration
            progress = if (duration > 0) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val settle by animateFloatAsState(
        targetValue = if (playing) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "tapePress",
    )

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(1000f / 635f)
            .graphicsLayer {
                scaleX = settle
                scaleY = settle
            }
            .combinedClickable(onClick = onToggle, onLongClick = onOpenDeck),
    ) {
        CassetteTape(
            title = tape.title.ifEmpty { stringResource(R.string.tape_untitled) },
            subtitle = takeDate(tape.createdAt),
            seconds = tape.seconds,
            variant = tape.colorway,
            playing = playing,
            progress = progress,
            grain = grain,
            bars = tape.bars,
        )
    }
}

/** "11.08" — the day the tape was taken, the wall's short form. */
private fun takeDate(createdAt: Long): String {
    val day = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault())
    return "%02d.%02d".format(day.monthValue, day.dayOfMonth)
}

/**
 * A key on the wall's head: no ring, no plate — the brush mark itself, with
 * a circle of quiet around it for the finger. Pressed, it sinks a little.
 */
@Composable
private fun WallKey(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "wallKey",
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/**
 * The four shells, laid out for the picking: each a little tape face lit from
 * above, the chosen one wearing the brass ring. The picking is a size of its
 * own — 48dp of air around a 38dp face — so a finger lands on one shell and
 * not its neighbour.
 */
@Composable
private fun ShellRail(
    palette: TapePalette,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palette.colorways.forEachIndexed { index, colorway ->
            val chosen = index == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onPick(index) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(width = 38.dp, height = 26.dp)
                        .then(
                            if (chosen) {
                                Modifier.border(1.4.dp, palette.brass, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    val inset = 3.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to colorway.lit,
                            0.55f to colorway.body,
                            1f to colorway.shade,
                        ),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                }
            }
        }
    }
}

/**
 * The wall's two marks, drawn as a brush draws: one stroke each, swelling in
 * the middle and thinning to nothing at both ends, rather than a pen of one
 * width all the way round.
 *
 * The record key is the tape already wound — a spiral drawn inward to its
 * seed. The import key is the same idea caught earlier: a line falling from
 * above and curling into a coil. One hand, two moments.
 */
private val MarkSize = 30.dp

/**
 * The weight a brush carries along a stroke: nothing at either end, most of
 * it a little past the middle. The sine is clamped before the root because
 * sin(π), in floats, arrives a hair below zero — and a negative base under a
 * fractional power is NaN, which would void the whole path.
 */
private fun brushWeight(t: Float): Float =
    sin(Math.PI.toFloat() * t).coerceAtLeast(0f).pow(0.55f)

@Composable
private fun RecordMark(color: Color) {
    Canvas(modifier = Modifier.size(MarkSize)) {
        val side = size.width
        val cx = side / 2f
        val cy = side / 2f
        val count = 180
        val points = ArrayList<Offset>(count)
        val widths = FloatArray(count)
        for (index in 0 until count) {
            val t = index / (count - 1f)
            val angle = 2f * Math.PI.toFloat() * 1.62f * t
            val radius = side * (0.055f + (0.40f - 0.055f) * t)
            points += Offset(cx + radius * cos(angle), cy + radius * sin(angle))
            widths[index] = side * 0.085f * brushWeight(t)
        }
        brushStroke(points, widths, color)
        // The seed the spiral winds down to.
        drawCircle(color, radius = side * 0.045f, center = Offset(cx, cy))
    }
}

@Composable
private fun ImportMark(color: Color) {
    Canvas(modifier = Modifier.size(MarkSize)) {
        val side = size.width
        // The coil sits low in the box and the tail falls into its rim; the
        // centre is nudged left so the drawing, not its bounds, sits true
        // under the key and reads level with the record mark beside it.
        val coilCx = side * 0.451f
        val coilCy = side * 0.58f
        val coilCount = 150
        val phase = Math.PI.toFloat() * 0.15f
        val coil = ArrayList<Offset>(coilCount)
        for (index in 0 until coilCount) {
            val t = index / (coilCount - 1f)
            val angle = 2f * Math.PI.toFloat() * 0.86f * t + phase
            val radius = side * (0.19f + (0.04f - 0.19f) * t)
            coil += Offset(coilCx + radius * cos(angle), coilCy + radius * sin(angle))
        }
        // The tail leaves the rim along the rim's own tangent, so the join
        // reads as one stroke rather than two meeting at a corner.
        val start = coil.first()
        val near = coil[1]
        val runX = near.x - start.x
        val runY = near.y - start.y
        val run = sqrt(runX * runX + runY * runY).takeIf { it > 0.0001f } ?: 1f
        val ux = runX / run
        val uy = runY / run
        val tail = side * 0.54f
        val p0 = Offset(start.x - ux * tail * 0.15f, start.y - uy * tail)
        val p1 = Offset(start.x - ux * tail * 0.02f, start.y - uy * tail * 0.55f)
        val p2 = Offset(start.x - ux * tail * 0.01f, start.y - uy * tail * 0.10f)
        val tailCount = 60
        val points = ArrayList<Offset>(tailCount + coilCount)
        for (index in 0 until tailCount) {
            val t = index / (tailCount - 1f)
            val u = 1f - t
            points += Offset(
                u * u * u * p0.x + 3f * u * u * t * p1.x + 3f * u * t * t * p2.x + t * t * t * start.x,
                u * u * u * p0.y + 3f * u * u * t * p1.y + 3f * u * t * t * p2.y + t * t * t * start.y,
            )
        }
        points += coil
        val widths = FloatArray(points.size)
        for (index in points.indices) {
            val t = index / (points.size - 1f)
            val bell = brushWeight(t)
            val weight = if (t < 0.55f) {
                bell * (0.55f + 0.45f * (t / 0.55f))
            } else {
                bell * (1f - 0.55f * ((t - 0.55f) / 0.45f))
            }
            widths[index] = side * 0.072f * weight.coerceAtLeast(0f)
        }
        brushStroke(points, widths, color)
    }
}

/**
 * Lays a stroke the way a brush lays one: the centre line offset by half the
 * weight on either side, so the ribbon swells through the middle and dies at
 * the ends instead of stopping square.
 */
private fun DrawScope.brushStroke(points: List<Offset>, widths: FloatArray, color: Color) {
    val count = points.size
    if (count < 2) return
    val normals = ArrayList<Offset>(count)
    for (index in 0 until count) {
        val before = points[(index - 1).coerceAtLeast(0)]
        val after = points[(index + 1).coerceAtMost(count - 1)]
        var dx = after.x - before.x
        var dy = after.y - before.y
        val length = sqrt(dx * dx + dy * dy).takeIf { it > 0.0001f } ?: 1f
        dx /= length
        dy /= length
        normals += Offset(-dy, dx)
    }
    val path = Path()
    path.moveTo(
        points[0].x + normals[0].x * widths[0],
        points[0].y + normals[0].y * widths[0],
    )
    for (index in 1 until count) {
        path.lineTo(
            points[index].x + normals[index].x * widths[index],
            points[index].y + normals[index].y * widths[index],
        )
    }
    for (index in count - 1 downTo 0) {
        path.lineTo(
            points[index].x - normals[index].x * widths[index],
            points[index].y - normals[index].y * widths[index],
        )
    }
    path.close()
    drawPath(path, color)
}

/**
 * The recording booth: one take, live on the glass — the wave drawn as it
 * listens, the clock beside it. A stop that held a word leads to the
 * naming; a stop that held nothing is told so and thrown away. The take
 * is shelved only by the ✓ — until then it is air.
 */
@Composable
private fun RecordSheet(
    nextColorway: Int,
    onDismiss: () -> Unit,
    onSave: (VoiceTape) -> Unit,
) {
    val context = LocalContext.current
    val palette = if (isDarkScheme()) NightPalette else DayPalette
    val id = remember { newTapeId() }
    val file = remember { TapeAudio.newFile(context, id) }
    val recorder = remember { VoiceRecorder(context, file) }
    val bars = remember { mutableStateListOf<Int>() }
    var seconds by remember { mutableIntStateOf(0) }
    var review by remember { mutableStateOf(false) }
    var tooShort by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    // Starts on the next shell in the rotation, so a run of takes still comes
    // out varied — but it is the finger's to change before the take is shelved.
    var colorway by remember { mutableIntStateOf(nextColorway % 4) }
    val scope = rememberCoroutineScope()

    fun stopRecording() {
        val usable = recorder.stop()
        if (!usable || bars.size < 12) {
            tooShort = true
        } else {
            review = true
        }
    }

    fun discard() {
        TapeAudio.delete(context, file.name)
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }
    // The take begins the moment the booth does.
    LaunchedEffect(Unit) {
        val started = recorder.start()
        while (!review && !tooShort && started) {
            kotlinx.coroutines.delay(100)
            bars.add((recorder.amplitude() / 32767f * 100f).coerceIn(4f, 100f).toInt())
            seconds = bars.size / 10
            if (seconds >= 300) stopRecording()
        }
        if (!started) tooShort = true
    }
    LaunchedEffect(tooShort) {
        if (tooShort) {
            kotlinx.coroutines.delay(1400)
            discard()
        }
    }

    Dialog(onDismissRequest = { discard() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .dialogWindowGuard()
                .fillMaxSize()
                .background(if (isDarkScheme()) Color(0xE614120F) else Color(0xE6EDE7DC))
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            if (!review) {
                // The wave as it happens, drawn in brass on the dark pane.
                Text(
                    text = stringResource(R.string.tape_recording),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    color = palette.brass,
                )
                Spacer(Modifier.height(18.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    if (bars.isEmpty()) return@Canvas
                    // The last few seconds, walking right to left as the
                    // wall's meter does.
                    val pitch = 4.dp.toPx()
                    val count = (size.width / pitch).toInt().coerceAtLeast(1)
                    val window = bars.takeLast(count)
                    val midY = size.height / 2
                    window.forEachIndexed { i, amp ->
                        val h = (amp / 100f) * size.height * 0.92f
                        drawRoundRect(
                            color = palette.brass.copy(alpha = 0.85f),
                            topLeft = Offset(i * pitch, midY - h / 2),
                            size = Size(2.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "%02d:%02d".format(seconds / 60, seconds % 60),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp,
                    color = palette.inkStrong,
                )
                if (tooShort) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tape_too_short),
                        fontSize = 13.sp,
                        color = palette.brass,
                    )
                }
                Spacer(Modifier.height(26.dp))
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(1.dp, palette.shellEdge, CircleShape)
                        .clickable { stopRecording() },
                    contentAlignment = Alignment.Center,
                ) {
                    // The stop key: the square the transports all wear.
                    Canvas(Modifier.size(20.dp)) {
                        drawRoundRect(
                            color = palette.inkStrong,
                            cornerRadius = CornerRadius(size.width * 0.12f),
                            size = Size(size.width * 0.6f, size.width * 0.6f),
                            topLeft = Offset(size.width * 0.2f, size.width * 0.2f),
                        )
                    }
                }
            } else {
                // The naming: what the label will say, in the letters' own hand.
                Text(
                    text = stringResource(R.string.tape_title_hint),
                    fontSize = 12.sp,
                    color = palette.muted,
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(12) },
                    singleLine = true,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XingKai,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                // The shell it will wear — picked here rather than handed out
                // by the order of the wall.
                ShellRail(
                    palette = palette,
                    selected = colorway,
                    onPick = { colorway = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(14.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    val pitch = 4.dp.toPx()
                    val count = (size.width / pitch).toInt().coerceAtLeast(1)
                    val step = bars.size / count
                    val midY = size.height / 2
                    for (i in 0 until count) {
                        val amp = bars.getOrElse(i * step) { 0 }
                        val h = (amp / 100f) * size.height * 0.92f
                        drawRoundRect(
                            color = palette.inkStrong.copy(alpha = 0.30f),
                            topLeft = Offset(i * pitch, midY - h / 2),
                            size = Size(2.dp.toPx(), h),
                            cornerRadius = CornerRadius(1.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable { discard() },
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseIcon(iconSize = 20.dp, color = palette.inkStrong.copy(alpha = 0.6f))
                    }
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .border(1.dp, palette.shellEdge, CircleShape)
                            .clickable {
                                // The wave travels at the meter's own
                                // resolution: about a bar to a breath.
                                val carried = bars.size / (bars.size / 96 + 1)
                                val step = if (carried > 0) bars.size / carried else 1
                                val wave = buildList {
                                    var i = 0
                                    while (i < bars.size) {
                                        add(bars[i])
                                        i += step
                                    }
                                }.take(120)
                                onSave(
                                    VoiceTape(
                                        id = id,
                                        title = title.trim(),
                                        createdAt = System.currentTimeMillis(),
                                        fileName = file.name,
                                        seconds = seconds,
                                        bars = wave,
                                        colorway = colorway,
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        io.github.zzpby.tickcount.ui.components.CheckIcon(iconSize = 24.dp, color = palette.brass)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * The deck: one tape, large, alone on the app's own picture — a third of
 * the height, a fifth of air above and below. The voice is real now: the
 * reels are driven by the player's own position, a drag scrubs the sound
 * itself, and the machine answers in kind.
 */
@Composable
private fun TapeDeck(
    tape: VoiceTape,
    grain: ImageBitmap,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRecolor: (Int) -> Unit,
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    // The machine's own voice — prepared once, released on leaving.
    val player = remember(tape.id) {
        tape.fileName?.let { name -> TapeAudio.file(context, name) }?.let { file ->
            runCatching {
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { playing = false; progress = 1f }
                    prepare()
                }
            }.getOrNull()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player?.runCatching { release() }
        }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val duration = p.duration
        if (duration > 0) p.seekTo((fraction * duration).toInt())
        progress = fraction.coerceIn(0f, 1f)
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            playing = false
        } else {
            if (progress >= 1f) seekTo(0f)
            p.start()
            playing = true
        }
    }

    // The reels' first breath: settling in from a turned-back start.
    val intro = remember { Animatable(26f) }
    LaunchedEffect(tape.id) {
        intro.animateTo(0f, tween(400))
    }
    // The position is read from the player each frame while it plays —
    // the pure function the whole machine is built on.
    LaunchedEffect(playing, scrubbing, player) {
        while (playing && !scrubbing && player != null) {
            awaitFrame()
            val duration = player.duration
            if (duration > 0) {
                progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            }
        }
    }

    val shown = if (scrubbing) scrubValue else progress
    val palette = if (isDarkScheme()) NightPalette else DayPalette
    fun timeLine(seconds: Int): String =
        "%02d:%02d".format(seconds / 60, seconds % 60)

    // The arrival and the leaving: the tape settles onto the stage, the
    // pane rises after it, and leaving is the same in reverse — a page
    // is entered and left, not switched on and off.
    var closing by remember { mutableStateOf(false) }
    val exitAlpha by animateFloatAsState(
        targetValue = if (closing) 0f else 1f,
        animationSpec = tween(220),
        label = "deckExit",
    )
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tape.id) {
        enter.animateTo(1f, tween(380))
    }
    fun dismiss() {
        player?.pause()
        if (!closing) closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            kotlinx.coroutines.delay(240)
            onDismiss()
        }
    }
    // A page of its own, not a veil over the list: the back key leaves it
    // like any page, and the list is simply not here.
    BackHandler(onBack = ::dismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha },
        // No wash, no veil: the app's own picture is the stage, the
        // tape is a thing sitting on it, and the instruments sit on
        // a pane of the app's own glass.
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.9f))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1000f / 635f)
                        .graphicsLayer {
                            val t = enter.value
                            alpha = t
                            translationY = (1f - t) * 34.dp.toPx()
                            val s = 0.96f + 0.04f * t
                            scaleX = s
                            scaleY = s
                        }
                        .pointerInput(tape.seconds) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    scrubbing = true
                                    scrubValue = progress
                                    player?.pause()
                                    playing = false
                                },
                                onDragEnd = {
                                    seekTo(scrubValue)
                                    scrubbing = false
                                },
                                onDragCancel = { scrubbing = false },
                            ) { change, dx ->
                                change.consume()
                                scrubValue = (scrubValue - dx / size.width).coerceIn(0f, 1f)
                            }
                        }
                        .clickable { togglePlay() },
                ) {
                    CassetteTape(
                        title = tape.title.ifEmpty { stringResource(R.string.tape_untitled) },
                        subtitle = takeDate(tape.createdAt),
                        seconds = tape.seconds,
                        variant = tape.colorway,
                        playing = playing,
                        progress = shown,
                        grain = grain,
                        bars = tape.bars,
                        introDegrees = intro.value,
                    )
                }

                Spacer(Modifier.weight(1.1f))

                // The instruments — waveform, keys, the one hint — on a
                // pane of the app's own glass, at rest (no spring, no
                // glint): the picture behind stays the stage, and the
                // pane is what keeps the instruments readable on it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val t = enter.value
                            alpha = t
                            translationY = (1f - t) * 52.dp.toPx()
                        }
                        .glassSurface(alpha = 0.6f, interactive = false)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    WaveformMeter(
                        shown = shown,
                        seconds = tape.seconds,
                        bars = tape.bars,
                        palette = palette,
                        onTap = { seekTo(it) },
                        onDragStart = {
                            scrubbing = true
                            scrubValue = it
                        },
                        onDrag = { scrubValue = it },
                        onDragEnd = {
                            seekTo(scrubValue)
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(22.dp))

                    // The keys, the spec's own manners: the secondaries are
                    // bare — no rings, arrows at half ink — and only the
                    // primary earns its hairline circle.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable { seekTo(progress - 10f / tape.seconds) },
                            contentAlignment = Alignment.Center,
                        ) {
                            SeekGlyph(pointsLeft = true, color = palette.inkStrong.copy(alpha = 0.55f))
                        }
                        val pressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressScale by animateFloatAsState(
                            targetValue = if (pressed.collectIsPressedAsState().value) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = 0.7f),
                            label = "keyPress",
                        )
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .graphicsLayer {
                                    scaleX = pressScale
                                    scaleY = pressScale
                                }
                                .clip(CircleShape)
                                .border(1.dp, palette.shellEdge, CircleShape)
                                .clickable(
                                    interactionSource = pressed,
                                    indication = null,
                                ) { togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playing) {
                                PauseIcon(iconSize = 24.dp, color = palette.brass)
                            } else {
                                PlayIcon(iconSize = 24.dp, color = palette.inkStrong)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable { seekTo(progress + 10f / tape.seconds) },
                            contentAlignment = Alignment.Center,
                        ) {
                            SeekGlyph(pointsLeft = false, color = palette.inkStrong.copy(alpha = 0.55f))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tape_scrub_hint),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = palette.muted.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    // Re-dressing: the same four shells, and the tape on the
                    // machine changes into the one tapped, there and then.
                    Spacer(Modifier.height(20.dp))
                    ShellRail(
                        palette = palette,
                        selected = tape.colorway,
                        onPick = onRecolor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.weight(0.6f))
            }

            // The delete key: two taps, the second one meaning it — a
            // voice, once shelved, should not leave by a slip.
            var deleteArmed by remember { mutableStateOf(false) }
            LaunchedEffect(deleteArmed) {
                if (deleteArmed) {
                    kotlinx.coroutines.delay(2200)
                    deleteArmed = false
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 34.dp, start = 18.dp)
                    .size(34.dp)
                    .graphicsLayer { alpha = enter.value }
                    .clip(CircleShape)
                    .clickable {
                        if (deleteArmed) onDelete() else deleteArmed = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (deleteArmed) {
                    io.github.zzpby.tickcount.ui.components.CheckIcon(
                        iconSize = 16.dp,
                        color = palette.brass,
                    )
                } else {
                    io.github.zzpby.tickcount.ui.components.TrashIcon(
                        iconSize = 16.dp,
                        color = palette.inkStrong.copy(alpha = 0.8f),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 18.dp)
                    .size(34.dp)
                    .graphicsLayer { alpha = enter.value }
                    .clip(CircleShape)
                    .clickable(onClick = ::dismiss),
                contentAlignment = Alignment.Center,
            ) {
                CloseIcon(iconSize = 16.dp, color = palette.inkStrong)
            }
    }
}

/**
 * The meter as the voice itself: a row of waveform bars — etched ink
 * where the tape has not been, brass where it has — with the time beside
 * it in the machine's tabular voice. A finger lands anywhere on it.
 */
@Composable
private fun WaveformMeter(
    shown: Float,
    seconds: Int,
    bars: List<Int>,
    palette: TapePalette,
    onTap: (Float) -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The tape's silhouette: the wave it was recorded with — and a seeded
    // walk for the silence of a tape that predates its wave.
    val wave = if (bars.isNotEmpty()) {
        bars.map { it / 100f }
    } else {
        remember(seconds) { seededBars(seconds) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .pointerInput(seconds) {
                    detectTapGestures { offset ->
                        onTap((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(seconds) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onDragStart((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    ) { change, _ ->
                        change.consume()
                        onDrag((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gap = 3.dp.toPx()
                val barW = 2.dp.toPx()
                val pitch = barW + gap
                val count = (size.width / pitch).toInt().coerceAtLeast(1)
                val played = shown * count
                val etched = palette.inkStrong.copy(alpha = 0.16f)
                val lit = palette.brass.copy(alpha = 0.90f)
                val midY = size.height / 2
                for (i in 0 until count) {
                    val amp = wave[i * wave.size / count]
                    val h = amp * size.height * 0.92f
                    drawRoundRect(
                        color = if (i < played) lit else etched,
                        topLeft = Offset(i * pitch, midY - h / 2),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Row {
            Text(
                text = "%02d:%02d".format((shown * seconds).toInt() / 60, (shown * seconds).toInt() % 60),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = palette.inkStrong.copy(alpha = 0.8f),
            )
            Text(
                text = " / %02d:%02d".format(seconds / 60, seconds % 60),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = palette.muted,
            )
        }
    }
}

/** A stand-in wave for a tape that never carried one: a seeded walk that breathes like speech. */
private fun seededBars(seconds: Int): List<Float> {
    val rnd = kotlin.random.Random(seconds.toLong() * 131L + 7L)
    var level = 0.55f
    return List(120) {
        level = (level + (rnd.nextFloat() - 0.5f) * 0.42f + (0.55f - level) * 0.18f)
            .coerceIn(0.14f, 1f)
        level
    }
}

/**
 * One of the meter's ends: the spec's seek glyph — a chevron with its
 * curved tail, drawn as two strokes of the round pen, ten seconds either
 * way.
 */
@Composable
private fun SeekGlyph(pointsLeft: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val dir = if (pointsLeft) 1f else -1f
        val cx = w * 0.5f + dir * w * 0.02f
        val stroke = w * 0.07f

        val head = Path().apply {
            moveTo(cx + dir * w * 0.26f, h * 0.20f)
            lineTo(cx - dir * w * 0.26f, h * 0.50f)
            lineTo(cx + dir * w * 0.26f, h * 0.80f)
        }
        drawPath(
            head,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )

        val tail = Path().apply {
            moveTo(cx - dir * w * 0.26f, h * 0.50f)
            lineTo(cx + dir * w * 0.10f, h * 0.50f)
            val r = w * 0.25f
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = cx + dir * w * 0.10f - r,
                    top = h * 0.50f,
                    right = cx + dir * w * 0.10f + r,
                    bottom = h * 0.50f + 2 * r,
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = if (pointsLeft) 180f else -180f,
                forceMoveTo = false,
            )
        }
        drawPath(
            tail,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/**
 * A cassette, drawn whole to the visual spec: the body keeps a seventh of
 * the height for its contact shadow, the label is a short strip of print
 * (the title twice — a hair off-register, the way real printing sits — a
 * rule, the side and the length), and the window takes the rest, the one
 * high-contrast place, where the spools trade tape by area conservation
 * and four strands run out to the capstans below.
 *
 * Still layers build once per size behind [drawWithCache]; only spools,
 * strands, reels and the playing light follow the frames.
 */
@Composable
private fun CassetteTape(
    title: String,
    subtitle: String,
    seconds: Int,
    variant: Int,
    playing: Boolean,
    progress: Float,
    grain: ImageBitmap,
    bars: List<Int> = emptyList(),
    introDegrees: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val spec = remember(seconds) {
        TapeSpec(hubRatio = 0.44f, totalTurns = (seconds * TURNS_PER_SECOND).coerceIn(4f, 40f))
    }
    val palette = if (isDarkScheme()) NightPalette else DayPalette
    val colorway = palette.colorways[variant % palette.colorways.size]
    val dark = isDarkScheme()
    // The label is printed, not laid out: every line measured once and
    // placed at the spec's own coordinates, so the print sits the same at
    // 160dp and full width, at any font scale.
    val measurer = rememberTextMeasurer()
    val sideText = stringResource(R.string.tape_side_a)

    BoxWithConstraints(modifier = modifier) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // The spec's frame, in its own fractions: body 35..965
                    // by 35..587 on a 1000×635 stage, the last seventh of
                    // the height left to the contact shadow.
                    val body = Rect(
                        offset = Offset(w * 0.035f, h * 0.055f),
                        size = Size(w * 0.93f, h * 0.87f),
                    )
                    val bodyRadius = CornerRadius(body.height * 0.10f)

                    val shellBrush = Brush.linearGradient(
                        listOf(colorway.lit, colorway.body, colorway.shade),
                        start = Offset(w * 0.88f, h * 0.04f),
                        end = Offset(w * 0.10f, h * 0.98f),
                    )
                    val contactBrush = Brush.verticalGradient(
                        listOf(Color(0xFF161311).copy(alpha = if (dark) 0.5f else 0.20f), Color.Transparent),
                        startY = body.bottom,
                        endY = h,
                    )

                    onDrawBehind {
                        // The contact shadow: broad and soft, the weight of
                        // the thing — it rests, it does not float.
                        drawRoundRect(
                            brush = contactBrush,
                            topLeft = Offset(body.left, body.top + body.height * 0.985f),
                            size = Size(body.width, h - body.top - body.height * 0.985f),
                            cornerRadius = CornerRadius(body.height * 0.06f),
                        )

                        // Matte shell: one gradient along the light, and
                        // the spec's two rims — bright outside, quiet in.
                        drawRoundRect(shellBrush, topLeft = body.topLeft, size = body.size, cornerRadius = bodyRadius)
                        drawRoundRect(
                            color = palette.shellHi,
                            topLeft = body.topLeft,
                            size = body.size,
                            cornerRadius = bodyRadius,
                            style = Stroke(width = 1.4.dp.toPx()),
                        )
                        drawRoundRect(
                            color = palette.shellEdge,
                            topLeft = body.topLeft + Offset(1.2f, 1.2f),
                            size = Size(body.width - 2.4f, body.height - 2.4f),
                            cornerRadius = bodyRadius,
                            style = Stroke(width = 1.6.dp.toPx()),
                        )
                        // The paper's grain: one whisper over everything.
                        drawImage(
                            image = grain,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(w.toInt(), h.toInt()),
                            alpha = 0.05f,
                        )

                        // The label: a short strip of print near the top.
                        val label = Rect(
                            offset = Offset(
                                body.left + body.width * 0.070f,
                                body.top + body.height * 0.058f,
                            ),
                            size = Size(body.width * 0.860f, body.height * 0.230f),
                        )
                        drawRoundRect(
                            color = palette.labelFill,
                            topLeft = label.topLeft,
                            size = label.size,
                            cornerRadius = CornerRadius(body.height * 0.02f),
                        )
                        drawRoundRect(
                            color = palette.labelLine,
                            topLeft = label.topLeft,
                            size = label.size,
                            cornerRadius = CornerRadius(body.height * 0.02f),
                            style = Stroke(width = 1.4.dp.toPx()),
                        )

                        // The print, at the spec's own coordinates: pixels
                        // on the 1000×635 stage, scaled by the shell, the
                        // baselines exactly where the spec rules put them.
                        val sx = w / 1000f
                        val sy = h / 635f
                        val pxToSp = 1f / (density * fontScale)
                        val titleLayout = measurer.measure(
                            title,
                            TextStyle(
                                fontFamily = XingKai,
                                fontSize = (37f * sx * pxToSp).sp,
                                letterSpacing = (1.2f * sx * pxToSp).sp,
                                color = palette.labelInk,
                            ),
                            constraints = Constraints(maxWidth = (560f * sx).toInt().coerceAtLeast(8)),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val ghostLayout = measurer.measure(
                            title,
                            TextStyle(
                                fontFamily = XingKai,
                                fontSize = (37f * sx * pxToSp).sp,
                                letterSpacing = (1.2f * sx * pxToSp).sp,
                                color = palette.brass.copy(alpha = if (dark) 0.22f else 0.30f),
                            ),
                            constraints = Constraints(maxWidth = (560f * sx).toInt().coerceAtLeast(8)),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val subLayout = measurer.measure(
                            subtitle,
                            TextStyle(
                                fontFamily = XingKai,
                                fontSize = (20f * sx * pxToSp).sp,
                                letterSpacing = (1.4f * sx * pxToSp).sp,
                                color = palette.labelInk.copy(alpha = 0.55f),
                            ),
                            constraints = Constraints(maxWidth = (560f * sx).toInt().coerceAtLeast(8)),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val sideLayout = measurer.measure(
                            sideText,
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (12f * sx * pxToSp).sp,
                                letterSpacing = (2.4f * sx * pxToSp).sp,
                                color = palette.labelInk.copy(alpha = 0.50f),
                            ),
                        )
                        val timeLayout = measurer.measure(
                            "%d:%02d".format(seconds / 60, seconds % 60),
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = (12f * sx * pxToSp).sp,
                                letterSpacing = (2.4f * sx * pxToSp).sp,
                                color = palette.labelInk.copy(alpha = 0.32f),
                            ),
                        )

                        // The ghost impression first, a hair off-register;
                        // then the ink itself, on the spec's baselines.
                        drawText(ghostLayout, topLeft = Offset((139.3f + 1.3f) * sx, 132.3f * sy - ghostLayout.firstBaseline + 1.3f * sy))
                        drawText(titleLayout, topLeft = Offset(138f * sx, 131f * sy - titleLayout.firstBaseline))
                        drawText(subLayout, topLeft = Offset(138f * sx, 172f * sy - subLayout.firstBaseline))
                        drawText(sideLayout, topLeft = Offset(862f * sx - sideLayout.size.width, 126f * sy - sideLayout.firstBaseline))
                        drawText(timeLayout, topLeft = Offset(862f * sx - timeLayout.size.width, 150f * sy - timeLayout.firstBaseline))

                        // The window: cut into the shell, its upper wall
                        // darkened, a hairline of a rim catching the light.
                        val win = Rect(
                            offset = Offset(
                                body.left + body.width * 0.075f,
                                body.top + body.height * 0.333f,
                            ),
                            size = Size(body.width * 0.850f, body.height * 0.575f),
                        )
                        val winRadius = CornerRadius(body.height * 0.04f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(palette.winTop, palette.winBottom),
                                startY = win.top,
                                endY = win.bottom,
                            ),
                            topLeft = win.topLeft,
                            size = win.size,
                            cornerRadius = winRadius,
                        )

                        // Four screws, placed as the real machine places
                        // them; one arc of light on the catchers, with the
                        // main light, from the upper right.
                        val screwR = body.height * 0.018f
                        listOf(
                            Offset(body.left + body.width * 0.040f, body.top + body.height * 0.512f),
                            Offset(body.left + body.width * 0.960f, body.top + body.height * 0.512f),
                            Offset(body.left + body.width * 0.086f, body.top + body.height * 0.958f),
                            Offset(body.left + body.width * 0.914f, body.top + body.height * 0.958f),
                        ).forEachIndexed { i, screw ->
                            drawCircle(Color.Black.copy(alpha = 0.05f), screwR, screw)
                            drawCircle(
                                palette.shellEdge,
                                screwR,
                                screw,
                                style = Stroke(width = 1.2.dp.toPx()),
                            )
                            drawLine(
                                palette.shellEdge,
                                screw - Offset(screwR * 0.4f, screwR * 0.4f),
                                screw + Offset(screwR * 0.4f, screwR * 0.4f),
                                1.2.dp.toPx(),
                            )
                            if (i <= 1) {
                                drawArc(
                                    color = Color.White.copy(alpha = if (i == 1) 0.60f else 0.50f),
                                    startAngle = -64f,
                                    sweepAngle = 52f,
                                    useCenter = false,
                                    topLeft = Offset(screw.x - screwR, screw.y - screwR),
                                    size = Size(screwR * 2, screwR * 2),
                                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }
                    }
                },
        )

        // The moving picture: glow, spools, strands, reels — all inside
        // the window, the one place the eye is allowed to rest.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val body = Rect(
                offset = Offset(w * 0.035f, h * 0.055f),
                size = Size(w * 0.93f, h * 0.87f),
            )
            val win = Rect(
                offset = Offset(body.left + body.width * 0.075f, body.top + body.height * 0.333f),
                size = Size(body.width * 0.850f, body.height * 0.575f),
            )
            val hubBase = body.height * 0.101f
            val hubMax = body.height * 0.24f
            val leftHub = Offset(body.left + body.width * 0.323f, body.top + body.height * 0.621f)
            val rightHub = Offset(body.left + body.width * 0.677f, body.top + body.height * 0.621f)

            fun radiusAt(share: Float): Float = hubBase + (hubMax - hubBase) * spec.packRadius(share)
            val leftR = radiusAt(1f - progress)
            val rightR = radiusAt(progress)
            val supplyDeg = spec.supplyTurns(progress) * 360f + introDegrees
            val takeUpDeg = spec.takeUpTurns(progress) * 360f + introDegrees

            clipRect(left = win.left, top = win.top, right = win.right, bottom = win.bottom) {
                // The lamp: amber, playing, from below — and at night a
                // pilot glow at rest, the one light left on.
                val lamp = when {
                    playing -> 0.24f
                    dark -> 0.07f
                    else -> 0f
                }
                if (lamp > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(
                                palette.brass.copy(alpha = lamp),
                                palette.brass.copy(alpha = lamp * 0.3f),
                                Color.Transparent,
                            ),
                            center = Offset(win.center.x, win.bottom),
                            radius = win.width * 0.72f,
                        ),
                        topLeft = win.topLeft,
                        size = win.size,
                    )
                }

                // The four strands: tape running out of each spool to the
                // capstans at the window's foot — the path real tape takes.
                listOf(
                    Triple(leftHub, leftR, -1f),
                    Triple(rightHub, rightR, 1f),
                ).forEach { (hub, r, sign) ->
                    val y1 = hub.y + r * 0.72f
                    drawLine(
                        palette.tapeHi.copy(alpha = 0.35f),
                        Offset(hub.x + sign * r * 0.66f, y1),
                        Offset(hub.x + sign * r * 0.30f, win.bottom),
                        strokeWidth = 2.4f.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        palette.tapeHi.copy(alpha = 0.22f),
                        Offset(hub.x + sign * r * 0.34f, y1 + 1.2f.dp.toPx()),
                        Offset(hub.x + sign * r * 0.16f, win.bottom),
                        strokeWidth = 2f.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // The spools: wound tape, its gradient focal point catching
                // the same upper-right light, an edge, and the sheen arc
                // riding the reel angle so even a stop looks chosen.
                listOf(
                    Triple(leftHub, leftR, supplyDeg),
                    Triple(rightHub, rightR, takeUpDeg),
                ).forEach { (c, radius, angle) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(palette.tapeHi, palette.tapeMid, palette.tapeLo),
                            center = Offset(c.x + radius * 0.30f, c.y - radius * 0.34f),
                            radius = radius * 1.35f,
                        ),
                        radius = radius,
                        center = c,
                    )
                    drawCircle(
                        Color.Black.copy(alpha = 0.22f),
                        radius,
                        c,
                        style = Stroke(width = radius * 0.09f),
                    )
                    rotate(angle, c) {
                        val sheenR = radius * 0.91f
                        drawArc(
                            color = Color.White.copy(alpha = 0.15f),
                            startAngle = -58f,
                            sweepAngle = 55f,
                            useCenter = false,
                            topLeft = Offset(c.x - sheenR, c.y - sheenR),
                            size = Size(sheenR * 2, sheenR * 2),
                            style = Stroke(width = radius * 0.14f),
                        )
                    }

                    // The hub: an ivory disk, six slots around a dark eye,
                    // turning at its own speed — the read of the machine.
                    drawCircle(palette.hub, hubBase * 0.96f, c)
                    drawCircle(
                        Color.Black.copy(alpha = 0.16f),
                        hubBase * 0.96f,
                        c,
                        style = Stroke(width = 1.4f.dp.toPx()),
                    )
                    rotate(angle, c) {
                        val slotLen = hubBase * 0.53f
                        val slotW = hubBase * 0.30f
                        val slotMid = hubBase * 0.68f
                        repeat(6) { i ->
                            val a = Math.toRadians((i * 60).toDouble())
                            val center = Offset(
                                c.x + slotMid * Math.cos(a).toFloat(),
                                c.y + slotMid * Math.sin(a).toFloat(),
                            )
                            rotate(i * 60f + 90f, center) {
                                drawRoundRect(
                                    color = palette.hubSlot,
                                    topLeft = Offset(center.x - slotW / 2, center.y - slotLen / 2),
                                    size = Size(slotW, slotLen),
                                    cornerRadius = CornerRadius(slotW / 2),
                                )
                            }
                        }
                    }
                    drawCircle(Color(0xFF15110E), hubBase * 0.24f, c)
                    drawCircle(
                        Color.White.copy(alpha = 0.12f),
                        hubBase * 0.24f,
                        c,
                        style = Stroke(width = 0.9f.dp.toPx()),
                    )
                }

                // The window's own glass: one diagonal from the light,
                // the shade of its upper wall, its hairline rim.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                        startY = win.top,
                        endY = win.top + win.height * 0.38f,
                    ),
                    topLeft = win.topLeft,
                    size = win.size,
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        start = Offset(win.right, win.top),
                        end = Offset(win.left, win.top + win.height * 0.95f),
                    ),
                    topLeft = win.topLeft,
                    size = win.size,
                    cornerRadius = CornerRadius(body.height * 0.04f),
                )
            }
            drawRoundRect(
                Color.White.copy(alpha = 0.13f),
                topLeft = Offset(win.left, win.top),
                size = win.size,
                cornerRadius = CornerRadius(body.height * 0.04f),
                style = Stroke(width = 1.6f.dp.toPx()),
            )

            // The brass hairline at the label's head: quiet at rest (35%),
            // itself only while playing (90%) — the machine's one word.
            val sx = w / 1000f
            val sy = h / 635f
            drawLine(
                palette.brass.copy(alpha = if (playing) 0.90f else 0.35f),
                Offset(118f * sx, 81f * sy),
                Offset(882f * sx, 81f * sy),
                strokeWidth = 2.2f * sx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The one sheet of paper grain every shell shares: grey noise at a
 * whisper of alpha, built once and reused.
 */
private fun makeShellGrain(): ImageBitmap {
    val w = 160
    val h = 112
    val rnd = kotlin.random.Random(7)
    val pixels = IntArray(w * h) {
        val v = rnd.nextInt(256)
        (0x14 shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
        .asImageBitmap()
}
