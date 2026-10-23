package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.domain.CountdownPhase
import io.github.zzpby.tickcount.domain.calendarSplit
import io.github.zzpby.tickcount.domain.countdownTo
import io.github.zzpby.tickcount.domain.daysFromToday
import io.github.zzpby.tickcount.domain.effectiveDate
import io.github.zzpby.tickcount.domain.effectiveTarget
import io.github.zzpby.tickcount.domain.widgetCountdownFor
import kotlin.math.abs
import io.github.zzpby.tickcount.ui.components.BackgroundCrop
import io.github.zzpby.tickcount.ui.components.BackgroundScrimAlpha
import io.github.zzpby.tickcount.ui.components.PhotoStack
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.CountdownLineTextStyle
import io.github.zzpby.tickcount.ui.theme.PanelShape
import io.github.zzpby.tickcount.ui.theme.eventAccent
import io.github.zzpby.tickcount.ui.theme.hairline
import io.github.zzpby.tickcount.ui.theme.tintWash
import java.time.ZonedDateTime

/**
 * One countdown on a screen of its own: what it is called, how long is left (or has
 * passed), and everything else known about it.
 *
 * The countdown reads the same way in every phase: the day count in large type, the
 * elapsed clock ticking under it, and the date in small type at the bottom. The clock
 * counts down while the day is to come and up once it has gone by — including the day
 * itself, which is what [CountdownPhase.TODAY] means everywhere else in the app.
 *
 * The countdown's own background image, when one was picked, sits behind everything:
 * image, then a scrim, then the content — the layers are what keep the words readable
 * over any picture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDetailScreen(
    event: CountdownEvent,
    now: ZonedDateTime,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdown = remember(now, event) {
        countdownTo(event.effectiveTarget(now.zone, now.toLocalDate()), now)
    }
    val accent = eventAccent(event.colorHue)
    val context = LocalContext.current

    val heading = when (countdown.phase) {
        CountdownPhase.FUTURE -> stringResource(R.string.label_countdown_left)
        CountdownPhase.TODAY -> stringResource(R.string.label_today_elapsed)
        CountdownPhase.PAST -> stringResource(R.string.label_countdown_ago)
    }

    // The big count goes WITH the clock: it is one less than the list's day count,
    // because the ticking clock covers the incomplete day. (列表 4 天 → 大字 3 天
    // 16:00:00；过去 8027 天 → 大字 8026 天 + 当天流逝。)
    val bottomDate = when (countdown.phase) {
        CountdownPhase.PAST -> event.date
        else -> event.effectiveDate(now.toLocalDate())
    }
    val totalDays = remember(bottomDate, now) {
        (abs(daysFromToday(bottomDate, now.toLocalDate())) - 1).coerceAtLeast(0L)
    }

    // The clock that completes the big count: the remaining time to the countdown's
    // moment while that is under a day away, the time left of today otherwise, and
    // the time elapsed since midnight once the day has gone by.
    val clockSeconds = remember(countdown, bottomDate, now) {
        when (countdown.phase) {
            CountdownPhase.FUTURE ->
                widgetCountdownFor(bottomDate, now, event.time).secondsToBoundary
            CountdownPhase.TODAY ->
                countdown.parts.hours * 3_600 + countdown.parts.minutes * 60 +
                    countdown.parts.seconds
            CountdownPhase.PAST -> now.toLocalTime().toSecondOfDay().toLong()
        }
    }

    // The small line at the bottom is the separate, date-based reading of the same
    // distance: years and months and days once it spans a year, months and days
    // under a year, and nothing under a month — the big count already says the days.
    val split = remember(bottomDate, now) {
        when (countdown.phase) {
            CountdownPhase.PAST -> calendarSplit(bottomDate, now.toLocalDate())
            else -> calendarSplit(now.toLocalDate(), bottomDate)
        }
    }
    val periodLine = when {
        split.years >= 1 -> stringResource(
            R.string.period_ymd,
            split.years,
            split.months,
            split.days,
        )
        split.months >= 1 -> stringResource(
            R.string.period_md,
            split.months,
            split.days,
        )
        else -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The countdown's own background, under everything: image, scrim, then content.
        val background = remember(event.backgroundUri, event.id) {
            EventBackgrounds.bitmap(context, event.backgroundUri)
        }
        // The pile is thumbnails rather than full decodes: nine of those would
        // hold more pixels than the rest of the app ever does at once.
        val photoThumbnails = remember(event.photoUris, event.id) {
            event.photoUris.mapNotNull { EventBackgrounds.thumbnail(context, it) }
        }
        var viewerOpen by rememberSaveable { mutableStateOf(false) }
        if (background != null) {
            BackgroundCrop(
                bitmap = background,
                zoom = event.backgroundZoom,
                focusX = event.backgroundFocusX,
                focusY = event.backgroundFocusY,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = BackgroundScrimAlpha)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = accent,
            )

            Spacer(Modifier.height(18.dp))

            // The countdown itself, bare on the page: the number reads as the subject
            // rather than as another row of information.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))

                // Every phase reads the same way: the big count with its unit in large
                // type, the clock that completes it ticking under it, and the separate,
                // date-based reading in small type at the bottom — nothing under a
                // month, where the big count already says the days.
                Text(
                    text = stringResource(R.string.detail_days, totalDays),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "%02d:%02d:%02d".format(
                        clockSeconds / 3_600,
                        clockSeconds % 3_600 / 60,
                        clockSeconds % 60,
                    ),
                    style = CountdownLineTextStyle.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (periodLine != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = periodLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The associated gallery photos, as one stacked pile: a tap flips
            // through them full-screen. The pile sits directly on the page, no
            // card behind it — the photos themselves are the thing.
            if (event.photoUris.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoStack(
                        photos = photoThumbnails,
                        onClick = { viewerOpen = true },
                    )
                }
            }

            if (event.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                // The heading goes inside the card with the tags rather than above it, so
                // the whole block reads as one thing: these are the tags.
                DetailCard {
                    Text(
                        text = stringResource(R.string.field_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        event.tags.forEach { tag -> TagPill(tag) }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        // The flip-through the pile opens: full-screen, one photo at a time,
        // the count at the top, a tap anywhere to leave.
        if (event.photoUris.isNotEmpty() && viewerOpen) {
            PhotoViewer(
                photos = event.photoUris,
                onDismiss = { viewerOpen = false },
            )
        }
    }
}

/**
 * The photos one at a time, full-screen on black.
 *
 * A plain horizontal pager rather than a zoomable one: the pile is for
 * remembering a day, and a tap on either side of the pager is the whole
 * gesture the flip-through needs.
 */
@Composable
private fun PhotoViewer(
    photos: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0) { photos.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val photo = remember(photos[page]) {
                    EventBackgrounds.bitmap(context, photos[page])
                }
                if (photo != null) {
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp),
            )
        }
    }
}

/** The rounded panel both kinds of detail card are cut from. */
@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .hairline(CardShape)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        content = content,
    )
}

/** A label on the left, its value on the right, both inside one panel. */
@Composable
private fun DetailCard(label: String, value: String) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * A tag, drawn as a pill rather than as a chip.
 *
 * There is nothing to press here, and a chip would ripple when tapped anyway — an
 * invitation that leads nowhere.
 */
@Composable
private fun TagPill(tag: String) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
