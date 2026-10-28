package io.github.zzpby.tickcount.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CapsuleVoice
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.data.TimeCapsule
import io.github.zzpby.tickcount.data.VoiceRecorder
import io.github.zzpby.tickcount.domain.VoiceWaveform
import io.github.zzpby.tickcount.ui.components.CheckIcon
import io.github.zzpby.tickcount.ui.components.CloseIcon
import io.github.zzpby.tickcount.ui.components.PhotoStack
import io.github.zzpby.tickcount.ui.components.ReopenIcon
import io.github.zzpby.tickcount.ui.components.TrashIcon
import io.github.zzpby.tickcount.ui.components.VoiceNote
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.FieldShape
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.PillShape
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.XinWei
import io.github.zzpby.tickcount.ui.theme.hairline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.min

/**
 * The time capsules: words and photos sealed until a day, one page of its own —
 * what the page is for is the wait, so a sealed capsule only says how long is
 * left. The day itself counts as arrived, the way TODAY means everywhere else,
 * and an arrived capsule opens full-screen on a tap.
 *
 * A capsule is written in the editor, the same screen the title bar's plus opens;
 * renaming, photos and removing live there. The count under the title is the only
 * summary: how many are still sealed.
 */
@Composable
fun CapsuleScreen(
    capsules: List<TimeCapsule>,
    today: LocalDate,
    /** Asked on the tap that opens an arrived capsule; returns it stamped. */
    onOpen: (String) -> TimeCapsule?,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)

    // Sealed ones by how soon they open; arrived ones most recently opened first.
    val (arrived, sealed) = capsules.partition { it.opensEpochDay <= today.toEpochDay() }
    val orderedSealed = sealed.sortedBy { it.opensEpochDay }
    val orderedArrived = arrived.sortedByDescending { it.openedEpochDay ?: 0L }

    // The capsule being read, full-screen; the tap that fills this also stamps
    // the first-open day inside the ViewModel.
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    val readingCapsule = capsules.firstOrNull { it.id == reading }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 2.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.drawer_capsule),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = XingKai,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (capsules.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.capsule_summary, sealed.size, capsules.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            capsules.isEmpty() -> CentredNotice(stringResource(R.string.capsule_empty))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 4.dp),
            ) {
                items(orderedSealed, key = { it.id }) { capsule ->
                    SealedCapsuleCard(
                        capsule = capsule,
                        today = today,
                        dateFormatter = dateFormatter,
                        onLongClick = { onEdit(capsule.id) },
                    )
                }

                if (orderedArrived.isNotEmpty()) {
                    item(key = "capsule-arrived-heading") {
                        Text(
                            text = stringResource(R.string.capsule_arrived_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                items(orderedArrived, key = { it.id }) { capsule ->
                    ArrivedCapsuleCard(
                        capsule = capsule,
                        dateFormatter = dateFormatter,
                        onClick = {
                            onOpen(capsule.id)
                            reading = capsule.id
                        },
                        onLongClick = { onEdit(capsule.id) },
                    )
                }
            }
        }
    }

    if (readingCapsule != null) {
        CapsuleView(
            capsule = readingCapsule,
            onDismiss = { reading = null },
        )
    }
}

/**
 * A capsule still sealed: the day it opens, and the only thing the page says —
 * how long is left. The words themselves stay away until the day arrives.
 */
@Composable
private fun SealedCapsuleCard(
    capsule: TimeCapsule,
    today: LocalDate,
    dateFormatter: java.time.format.DateTimeFormatter,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val days = (capsule.opensEpochDay - today.toEpochDay()).coerceAtLeast(0L)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .hairline(CardShape)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capsule.title.ifBlank {
                    LocalDate.ofEpochDay(capsule.opensEpochDay).format(dateFormatter)
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.capsule_days_left, days),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        }
        Text(
            text = "🔒",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * A capsule whose day has come: a tap opens it. First open stamps the day —
 * "开启于" — which stays on the card afterwards, the way a wish keeps its day.
 */
@Composable
private fun ArrivedCapsuleCard(
    capsule: TimeCapsule,
    dateFormatter: java.time.format.DateTimeFormatter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .hairline(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capsule.title.ifBlank { stringResource(R.string.capsule_ready) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = scheme.primary,
            )
            if (capsule.openedEpochDay != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.capsule_opened_on,
                        LocalDate.ofEpochDay(capsule.openedEpochDay).format(dateFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "✉️",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * The capsule editor: a screen of its own, the way the other editors are — the
 * words, the day it opens, and the photos that travel with them.
 */
@SuppressLint("MutableCollectionMutableState")
@Composable
fun CapsuleEditorScreen(
    initial: TimeCapsule,
    onSave: (TimeCapsule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var message by rememberSaveable(initial.id) { mutableStateOf(initial.message) }
    var opensEpochDay by rememberSaveable(initial.id) { mutableLongStateOf(initial.opensEpochDay) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var photoNames by rememberSaveable(initial.id) { mutableStateOf(ArrayList(initial.photos)) }

    // The voice letter: a take goes straight to its final file, and is only
    // committed to the record on save — a cancelled edit throws the new take
    // away and leaves the voice it came in with untouched.
    var voiceName by rememberSaveable(initial.id) { mutableStateOf(initial.voice) }
    var voiceSeconds by rememberSaveable(initial.id) { mutableIntStateOf(initial.voiceSeconds) }
    var voiceBars by rememberSaveable(initial.id) { mutableStateOf(initial.voiceBars) }
    var recordedNow by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var liveAmps by remember { mutableStateOf(listOf<Int>()) }
    var liveSeconds by remember { mutableIntStateOf(0) }
    var tooShort by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }

    fun beginRecording() {
        val file = CapsuleVoice.newFile(context, initial.id)
        val fresh = VoiceRecorder(context, file)
        if (fresh.start()) {
            recorder = fresh
            recordedNow = file.name
            liveAmps = emptyList()
            liveSeconds = 0
            tooShort = false
            recording = true
        } else {
            fresh.release()
            file.delete()
        }
    }

    fun endRecording(commit: Boolean) {
        val current = recorder ?: return
        recording = false
        val usable = current.stop()
        current.release()
        recorder = null
        val take = recordedNow
        if (!commit || !usable || liveSeconds < 1) {
            CapsuleVoice.delete(context, take)
            recordedNow = null
            if (commit && !usable) tooShort = true
            return
        }
        // The old take is replaced only here — at save-time, never mid-edit.
        if (voiceName != null && voiceName != take) {
            CapsuleVoice.delete(context, voiceName)
        }
        voiceName = take
        voiceSeconds = liveSeconds
        voiceBars = VoiceWaveform.bars(liveAmps)
    }

    fun discardPendingTake() {
        if (recording) {
            recording = false
            runCatching { recorder?.stop() }
            recorder?.release()
            recorder = null
        }
        if (recordedNow != null && recordedNow != initial.voice) {
            CapsuleVoice.delete(context, recordedNow)
        }
        recordedNow = null
    }

    val askRecordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginRecording() else permissionDenied = true
    }

    fun requestRecording() {
        permissionDenied = false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) beginRecording() else askRecordPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // While it listens, the wave grows: one sample of the loudest sound every
    // sixth of a second, which becomes both the bars and the running seconds.
    LaunchedEffect(recording) {
        while (recording) {
            delay(150)
            val current = recorder ?: break
            liveAmps = liveAmps + current.amplitude()
            liveSeconds = liveAmps.size * 150 / 1000
        }
    }

    val dateFormatter = rememberDateFormatter(R.string.date_format_short)
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = TimeCapsule.MAX_PHOTOS),
    ) { uris ->
        uris.forEach { uri ->
            if (photoNames.size >= TimeCapsule.MAX_PHOTOS) return@forEach
            val saved = EventBackgrounds.save(context, initial.id, uri)
            if (saved != null && saved !in photoNames) {
                photoNames = ArrayList(photoNames + saved)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    TrashIcon(color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.weight(1f))

            IconButton(onClick = {
                discardPendingTake()
                onDismiss()
            }) {
                CloseIcon(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val canSeal = title.isNotBlank() && message.isNotBlank()
            IconButton(
                enabled = canSeal,
                onClick = {
                    // Commit: the voice it came in with is replaced only once
                    // the record it is being replaced in is saved.
                    if (initial.voice != null && initial.voice != voiceName) {
                        CapsuleVoice.delete(context, initial.voice)
                    }
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            message = message.trim(),
                            opensEpochDay = opensEpochDay,
                            createdAt = if (initial.createdAt == 0L) System.currentTimeMillis() else initial.createdAt,
                            photos = photoNames.toList(),
                            voice = voiceName,
                            voiceSeconds = voiceSeconds,
                            voiceBars = voiceBars,
                        )
                    )
                },
            ) {
                CheckIcon(
                    color = if (canSeal) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(TimeCapsule.MAX_TITLE_LENGTH) },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.capsule_message_label)) },
                placeholder = { Text(stringResource(R.string.capsule_message_hint)) },
                minLines = 4,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // —— 一段声音 ——
            when {
                recording -> VoiceRecordingBar(
                    seconds = liveSeconds,
                    amps = liveAmps,
                    onDone = { endRecording(commit = true) },
                    onDiscard = { endRecording(commit = false) },
                )

                voiceName != null -> {
                    VoiceNote(
                        fileName = voiceName ?: "",
                        bars = voiceBars,
                        seconds = voiceSeconds,
                        ink = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        IconButton(onClick = { requestRecording() }) {
                            ReopenIcon(color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            voiceName = null
                            voiceBars = emptyList()
                            voiceSeconds = 0
                        }) {
                            TrashIcon(color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                else -> {
                    FieldRow(
                        label = stringResource(R.string.capsule_voice_label),
                        value = stringResource(R.string.capsule_voice_unset),
                        onClick = ::requestRecording,
                    )
                    if (tooShort) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.capsule_voice_too_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (permissionDenied) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.capsule_voice_permission),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FieldShape)
                    .clickable { datePickerOpen = true }
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .hairline(FieldShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.field_opens),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = LocalDate.ofEpochDay(opensEpochDay).format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            FieldRow(
                label = stringResource(R.string.field_photos),
                value = if (photoNames.isEmpty()) {
                    stringResource(R.string.background_unset)
                } else {
                    stringResource(R.string.photos_count, photoNames.size)
                },
                onClick = {
                    if (photoNames.size < TimeCapsule.MAX_PHOTOS) {
                        pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                },
            )
            if (photoNames.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val pilePreviews = remember(photoNames) {
                    photoNames.mapNotNull { EventBackgrounds.thumbnail(context, it) }
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoStack(photos = pilePreviews)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    photoNames.forEach { name ->
                        PhotoThumb(
                            name = name,
                            onPick = {},
                            selected = false,
                            onDelete = {
                                EventBackgrounds.delete(context, name)
                                photoNames = ArrayList(photoNames - name)
                            },
                        )
                    }
                }
            }
        }
    }

    if (datePickerOpen) {
        DatePickerSheet(
            initial = LocalDate.ofEpochDay(opensEpochDay),
            onPick = {
                opensEpochDay = it.toEpochDay()
                datePickerOpen = false
            },
            onDismiss = { datePickerOpen = false },
        )
    }
}

/**
 * The capsule itself, as a letter that opens.
 *
 * The sequence is the point, and each stage has its own spring: the envelope
 * drops in and settles, the wax seal cracks in two, the flap swings open with a
 * little perspective, the letter rises out of the pocket with a shadow under it,
 * and the letter then becomes the full page — a sheet of 信笺, cream paper, a red
 * seal and the words in ink. A tap while it plays fast-forwards to the page; a
 * tap on the page leaves.
 */
@Composable
private fun CapsuleView(
    capsule: TimeCapsule,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0) { capsule.photos.size }
    val scope = rememberCoroutineScope()

    val flap = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val page = remember { Animatable(0f) }

    LaunchedEffect(capsule.id) {
        // Not an entrance: the envelope is already on the table. The flap is
        // unsealed unhurriedly, the letter is drawn out slowly — the whole
        // sequence is one continuous pull, which is what reads as 拆信.
        delay(550)
        flap.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        delay(260)
        rise.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
        delay(80)
        page.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF3EAD8), Color(0xFFE7D9BF))
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures {
                        // A tap only ever skips the animation — on the page itself
                        // it is nothing, so an accidental touch never closes the
                        // letter; the way out is the button, top left.
                        if (page.value < 1f) {
                            scope.launch {
                                flap.snapTo(1f)
                                rise.snapTo(1f)
                                page.snapTo(1f)
                            }
                        }
                    }
                },
        ) {
            // The sheet, fading in and growing a little as it arrives.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(page.value)
                    .graphicsLayer {
                        val scale = 0.92f + 0.08f * page.value
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                LetterSheet(
                    capsule = capsule,
                    pagerState = pagerState,
                    context = context,
                )
            }

            // The envelope stage, sinking and fading as the sheet takes over.
            if (page.value < 1f) {
                EnvelopeStage(
                    flap = flap.value,
                    rise = rise.value,
                    page = page.value,
                    preview = capsule.message,
                )
            }

            // The way out: one button, top left, from the first frame of the
            // animation to the last page of the letter.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 34.dp, start = 14.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF33302A).copy(alpha = 0.08f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF33302A).copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * The envelope and the letter being drawn out of it, one layered stack: dark
 * lining, letter, kraft front with the mark, flap — in that z-order, so the
 * letter genuinely passes out from behind the front.
 *
 * The palette is kraft paper and bronze: quiet, and the shading has somewhere
 * to live. The front carries the app's own mark — two rings, one held by each —
 * and nothing else: no date, no word, just the object and its pull.
 */
@Composable
private fun EnvelopeStage(
    flap: Float,
    rise: Float,
    page: Float,
    preview: String,
) {
    val easeIn = rise * rise

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha((1f - page).coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        // Headroom above the envelope for the letter to rise into.
        Box(
            modifier = Modifier
                .width(248.dp)
                .height(252.dp)
                .graphicsLayer {
                    // Held in the hand: as the letter is drawn out, the envelope
                    // leans back a little — the physics of the pull.
                    rotationX = -7f * rise
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    alpha = 1f - page * 0.9f
                    translationY = page * 30.dp.toPx()
                    shadowElevation = 16.dp.toPx()
                    shape = RoundedCornerShape(14.dp)
                    clip = false
                },
        ) {
            val envelope = Modifier.align(Alignment.BottomCenter).size(width = 236.dp, height = 158.dp)

            // The inside, the lining the flap uncovers.
            Box(
                modifier = envelope
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFC7B18C), Color(0xFFAD9469))
                        )
                    ),
            )

            // The letter, drawn out slowly, its shadow growing as it clears.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .graphicsLayer {
                        translationY = -easeIn * 138.dp.toPx()
                        shadowElevation = (2f + 16f * rise).dp.toPx()
                        shape = RoundedCornerShape(6.dp)
                        clip = false
                    },
            ) {
                RisingLetter(preview = preview)
            }

            // The kraft front: gradient, hairline, the mark — and the hinge
            // shadow under where the flap sat.
            Box(
                modifier = envelope
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFEDDFC4), Color(0xFFDFCCA6))
                        )
                    )
                    .border(1.dp, Color(0xFFB49B6E).copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
            ) {
                EnvelopeMark(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 30.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFFB49B6E).copy(alpha = 0.35f)),
                )
            }

            // The flap, hinged on the top edge: kraft without, the darker lining
            // once it turns past upright — one canvas, both faces.
            Canvas(
                modifier = envelope.graphicsLayer {
                    rotationX = -180f * flap
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 9f * density
                },
            ) {
                val tip = size.height * 0.66f
                val outer = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, tip)
                    close()
                }
                val front = lerp(Color(0xFFE7D7B6), Color(0xFFF2E6CB), flap)
                val lining = lerp(Color(0xFFC7B18C), Color(0xFFB79E72), flap)
                drawPath(outer, if (flap < 0.5f) front else lining)
                drawPath(outer, Color(0xFFB49B6E).copy(alpha = 0.7f), style = Stroke(2f))
            }
        }
    }
}

/**
 * The mark on the envelope: a double ring, and inside it two rings overlapping —
 * one each, interlocked. Drawn in bronze, quietly, as an emboss rather than a
 * badge.
 */
@Composable
private fun EnvelopeMark(modifier: Modifier = Modifier) {
    val bronze = Color(0xFFA98C58)
    Canvas(modifier = modifier.size(56.dp)) {
        val c = center
        drawCircle(bronze.copy(alpha = 0.9f), radius = 26f, center = c, style = Stroke(1.6f))
        drawCircle(bronze.copy(alpha = 0.55f), radius = 22.5f, center = c, style = Stroke(0.8f))
        drawCircle(bronze, radius = 9f, center = c - Offset(6.5f, 0f), style = Stroke(1.3f))
        drawCircle(bronze, radius = 9f, center = c + Offset(6.5f, 0f), style = Stroke(1.3f))
    }
}

/**
 * The letter that rises out of the envelope: cream paper carrying a ghost of the
 * real words — the first line of the message, faint, so the rising paper is
 * already the letter rather than a placeholder.
 */
@Composable
private fun RisingLetter(preview: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(width = 206.dp, height = 138.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFEFCF6), Color(0xFFF6EEDC))
                )
            )
            .border(1.dp, Color(0xFFD8CDBB))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = XingKai,
                color = Color(0xFF33302A).copy(alpha = 0.45f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
        }
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(Color(0xFFDDD2BD)),
            )
        }
    }
}

/**
 * The unfolded letter: a sheet of 信笺 — cream paper, a hairline frame, the
 * letter's own title at the head, and the words themselves in 华文新魏, every
 * paragraph opening with the two-character indent a letter keeps.
 *
 * The words are paged rather than scrolled: a page holds about what a sheet of
 * paper holds, and the pages flip like paper. The photos travel on the last
 * page, below whatever room the words left; the footer is the day it was opened
 * and the one hint the page needs.
 */
@Composable
private fun LetterSheet(
    capsule: TimeCapsule,
    pagerState: androidx.compose.foundation.pager.PagerState,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val paper = Color(0xFFF8F1E4)
    val ink = Color(0xFF33302A)

    // The paragraphs, split as they were written, each a paragraph of the letter.
    val paragraphs = remember(capsule.id, capsule.message) {
        capsule.message.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    // The pages: the title takes the first page's head, then whole paragraphs
    // fill page by page to a sheet's budget, and a paragraph that would not
    // fit starts the next sheet.
    val pages = remember(capsule.id, paragraphs, capsule.photos.isNotEmpty()) {
        buildPages(paragraphs, capsule.photos.isNotEmpty())
    }
    val textPager = rememberPagerState(initialPage = 0) { pages.size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paper),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.dp, ink.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
        ) {
            HorizontalPager(
                state = textPager,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                LetterPage(
                    title = capsule.title.takeIf { pageIndex == 0 },
                    paragraphs = pages[pageIndex],
                    isLast = pageIndex == pages.lastIndex,
                    capsule = capsule,
                    photoPagerState = pagerState,
                    context = context,
                )
            }

            // The page number, bottom centre, the way a letter's sheets carry one.
            if (pages.size > 1) {
                Text(
                    text = "${textPager.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            }
        }
    }
}

/** How many characters a sheet comfortably holds — the first sheet included. */
private const val PAGE_CHARS = 300

/** The first sheet's budget is smaller: the title shares the page with the words. */
private const val FIRST_PAGE_CHARS = 280

/**
 * One paragraph as a sheet holds it: the words, and whether it opens with the
 * two-character indent. The indent belongs to a paragraph's own opening — a
 * paragraph carried over from the last sheet continues 顶格, flush, because
 * its middle is not a new paragraph however it looks on the new page.
 */
private data class LetterParagraph(val text: String, val indented: Boolean)

/**
 * Fills pages with the paragraphs exactly as they were written, in order: the
 * words run on to a sheet's budget, and whatever a paragraph has left over
 * opens the next sheet flush. A paragraph longer than a sheet is carried
 * across as many sheets as it takes — the page scrolls rather than clipping.
 * The photos need the better part of a page, so a full last page hands them
 * one of their own.
 */
private fun buildPages(
    paragraphs: List<String>,
    leavesRoomForPhotos: Boolean,
): List<List<LetterParagraph>> {
    if (paragraphs.isEmpty()) return listOf(emptyList())

    val pages = mutableListOf<MutableList<LetterParagraph>>(mutableListOf())
    var used = 0

    paragraphs.forEach { paragraph ->
        var index = 0
        while (index < paragraph.length) {
            val budget = if (pages.lastIndex == 0) FIRST_PAGE_CHARS else PAGE_CHARS
            val room = budget - used
            if (room <= 0) {
                pages.add(mutableListOf())
                used = 0
                continue
            }
            var take = minOf(room, paragraph.length - index)
            // A surrogate pair is one letter — an emoji, say — and never gets
            // cut in half at a page boundary.
            if (
                index + take < paragraph.length &&
                Character.isHighSurrogate(paragraph[index + take - 1])
            ) {
                take -= 1
            }
            if (take <= 0) {
                pages.add(mutableListOf())
                used = 0
                continue
            }
            pages.last().add(
                LetterParagraph(
                    text = paragraph.substring(index, index + take),
                    indented = index == 0,
                )
            )
            // The two characters stand in for the breath between paragraphs.
            used += take + 2
            index += take
        }
    }

    if (leavesRoomForPhotos && pages.last().sumOf { it.text.length } > PAGE_CHARS * 3 / 5) {
        pages.add(mutableListOf())
    }
    return pages
}

/**
 * One sheet of the letter: its title when it is the first, its paragraphs in
 * 新魏 — the paragraph's own opening indented, a carried-over one flush —
 * and — on the last sheet — the photos and the footer.
 */
@Composable
private fun LetterPage(
    title: String?,
    paragraphs: List<LetterParagraph>,
    isLast: Boolean,
    capsule: TimeCapsule,
    photoPagerState: androidx.compose.foundation.pager.PagerState,
    context: android.content.Context,
) {
    val ink = Color(0xFF33302A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 60.dp, bottom = 30.dp),
    ) {
        // The words are selectable: a long press brings the handles, and whatever
        // they gather can be copied — a letter is for keeping.
        SelectionContainer {
            Column {
                if (title != null && title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = XingKai,
                        color = ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(22.dp))
                }

                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph.text,
                        fontFamily = XinWei,
                        fontSize = 20.sp,
                        lineHeight = 34.sp,
                        color = ink,
                        textAlign = TextAlign.Justify,
                        style = LocalTextStyle.current.copy(
                            textIndent = TextIndent(
                                firstLine = if (paragraph.indented) 40.sp else 0.sp,
                            ),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        if (isLast) {
            if (capsule.photos.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalPager(
                    state = photoPagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val photo = remember(capsule.photos[page]) {
                        EventBackgrounds.bitmap(context, capsule.photos[page])
                    }
                    if (photo != null) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                        )
                    }
                }
                Text(
                    text = "${photoPagerState.currentPage + 1} / ${capsule.photos.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ink.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            if (capsule.voice != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.capsule_voice_letter),
                    fontFamily = XinWei,
                    fontSize = 16.sp,
                    color = ink.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(8.dp))
                VoiceNote(
                    fileName = capsule.voice,
                    bars = capsule.voiceBars,
                    seconds = capsule.voiceSeconds,
                    ink = ink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (capsule.openedEpochDay != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(
                        R.string.capsule_opened_on,
                        LocalDate.ofEpochDay(capsule.openedEpochDay).format(
                            rememberDateFormatter(R.string.date_format_short)
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.55f),
                )
            }
        }
    }
}


/**
 * The recording, as it is being made: a red dot breathing at the head, the
 * wave growing as it listens, the seconds counting up — and two quiet ways
 * out, keep it or leave it.
 */
@Composable
private fun VoiceRecordingBar(
    seconds: Int,
    amps: List<Int>,
    onDone: () -> Unit,
    onDiscard: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.error
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val pulse = rememberInfiniteTransition(label = "voice-record")
    val breath by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-dot",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .border(1.dp, accent.copy(alpha = 0.4f), FieldShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = breath
                        scaleY = breath
                    }
                    .background(accent.copy(alpha = 0.4f + 0.6f * breath), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDiscard) {
                CloseIcon(color = onSurfaceVariant)
            }
            IconButton(onClick = onDone) {
                CheckIcon(color = primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            if (amps.isEmpty()) return@Canvas
            val slot = 5.dp.toPx()
            val barWidth = 2.6f.dp.toPx()
            val count = min(amps.size, (size.width / slot).toInt().coerceAtLeast(1))
            val step = amps.size.toFloat() / count
            val fromIndex = (amps.size - count).coerceAtLeast(0)
            for (index in 0 until count) {
                val sample = amps[(fromIndex + index).coerceAtMost(amps.size - 1)] / 32767f
                val barHeight = (size.height * 0.14f + sample * size.height * 0.8f)
                    .coerceAtMost(size.height)
                drawRoundRect(
                    color = primary.copy(alpha = 0.85f),
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height / 2f - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }
    }
}
