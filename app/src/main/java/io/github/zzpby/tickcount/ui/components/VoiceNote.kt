package io.github.zzpby.tickcount.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.media.MediaPlayer
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CapsuleVoice
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * One recorded voice letter, as the letter keeps it: a round seal that plays,
 * the wave it heard drawn beside it — the part already listened to in full
 * ink, the rest waiting in fainter strokes — and how long it runs, out at the
 * end. Tapping the seal starts and pauses; the wave is where the sound lives,
 * so it is drawn, not decorated.
 *
 * Everything is drawn in [ink] on whatever paper it is laid on, so the same
 * note sits at home on a letter sheet and in an editor field alike.
 */
@Composable
fun VoiceNote(
    fileName: String,
    bars: List<Int>,
    seconds: Int,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    // What the number at the end says: the whole take at rest, and — while it
    // plays — the seconds still left, counting down to the letter's end.
    var shownSeconds by remember { mutableIntStateOf(seconds) }
    var broken by remember { mutableStateOf(false) }

    fun release() {
        runCatching { player?.release() }
        player = null
    }

    DisposableEffect(fileName) {
        onDispose { release() }
    }

    fun toggle() {
        if (playing) {
            runCatching { player?.pause() }
            playing = false
            return
        }
        player?.let {
            runCatching { it.start() }
            playing = true
            return
        }
        val file = CapsuleVoice.file(context, fileName)
        if (file == null) {
            broken = true
            return
        }
        val fresh = MediaPlayer()
        runCatching {
            fresh.setDataSource(file.absolutePath)
            fresh.prepare()
            fresh.start()
            fresh.setOnCompletionListener {
                playing = false
                progress = 1f
                it.seekTo(0)
            }
            player = fresh
            playing = true
        }.onFailure {
            fresh.release()
            broken = true
        }
    }

    // While it plays, the wave fills and the number counts down to the end;
    // at rest the whole take's length is what the row says.
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { current ->
                val duration = runCatching { current.duration }.getOrDefault(0)
                if (duration > 0) {
                    progress = current.currentPosition.toFloat() / duration
                    shownSeconds = ((duration - current.currentPosition) / 1000L)
                        .toInt().coerceAtLeast(0)
                }
            }
            delay(100)
        }
        shownSeconds = seconds
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ink.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.2.dp, ink, CircleShape)
                .clickable(enabled = !broken, onClick = ::toggle),
            contentAlignment = Alignment.Center,
        ) {
            VoicePlayIcon(playing = playing, ink = ink)
        }

        Spacer(Modifier.width(12.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(30.dp),
        ) {
            val playable = bars.isNotEmpty() && !broken
            if (!playable) return@Canvas
            val slot = 5.dp.toPx()
            val barWidth = 2.6f.dp.toPx()
            val count = min(bars.size, (size.width / slot).toInt().coerceAtLeast(1))
            val step = bars.size.toFloat() / count
            val midY = size.height / 2f
            for (index in 0 until count) {
                val sample = bars[(
                    (index * step).toInt().coerceIn(0, bars.size - 1)
                    )] / 100f
                val barHeight = (size.height * 0.16f + sample * size.height * 0.78f)
                    .coerceAtMost(size.height)
                val lit = index.toFloat() / count <= progress
                drawRoundRect(
                    color = ink.copy(alpha = if (lit) 0.92f else 0.32f),
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, midY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = if (broken) {
                stringResource(R.string.capsule_voice_gone)
            } else {
                formatSeconds(shownSeconds)
            },
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = if (broken) 0.45f else 0.6f),
        )
    }
}

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

/** The round seal's own mark: a triangle at rest, two bars while it sounds. */
@Composable
private fun VoicePlayIcon(playing: Boolean, ink: Color) {
    val fill by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(180),
        label = "voice-play",
    )
    Canvas(modifier = Modifier.size(18.dp)) {
        if (fill < 0.5f) {
            val side = size.height * 0.62f
            val top = (size.height - side) / 2f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.34f, top)
                lineTo(size.width * 0.34f + side * 0.87f, size.height / 2f)
                lineTo(size.width * 0.34f, top + side)
                close()
            }
            drawPath(path, color = ink)
        } else {
            val barWidth = size.width * 0.16f
            val barHeight = size.height * 0.62f
            drawRoundRect(
                color = ink,
                topLeft = Offset(size.width * 0.28f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(size.width * 0.56f, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
