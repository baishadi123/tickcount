package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.CountdownLineTextStyle
import io.github.zzpby.tickcount.ui.theme.eventAccent
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
    val dateFormatter = rememberDateFormatter(R.string.date_format_full)

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

    // Under a day to go, the big count would read "0" — arithmetic where a ticking
    // clock says more. There the big type *is* the clock: hours, minutes and seconds
    // to the moment, and the small clock under it would only say the same thing twice.
    val underADay = countdown.phase == CountdownPhase.FUTURE && totalDays == 0L

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

    // The target day, written out in full: the solar date with its weekday, and
    // the lunar day named after it when the date was entered that way.
    val dateLine = remember(bottomDate, event) {
        val solar = bottomDate.format(dateFormatter)
        val lunar = if (event.enteredAsLunar && event.lunarMonth != null && event.lunarDay != null) {
            val leap = if (event.lunarLeap) "闰" else ""
            " · 农历${leap}${event.lunarMonth}月${event.lunarDay}日"
        } else {
            ""
        }
        solar + lunar
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The pile is thumbnails rather than full decodes: nine of those would
        // hold more pixels than the rest of the app ever does at once. The
        // chosen photo leads the carousel.
        val photoThumbnails = remember(event.photoUris, event.photoCover, event.id) {
            val loaded = event.photoUris.mapNotNull { name ->
                EventBackgrounds.thumbnail(context, name)?.let { name to it }
            }
            loaded.sortedByDescending { it.first == event.photoCover }
        }
        var viewerOpen by rememberSaveable { mutableStateOf(false) }
        var viewerPage by rememberSaveable { mutableIntStateOf(0) }
        val carouselState = rememberPagerState { photoThumbnails.size }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            // The countdown itself, in the iOS register: a small spaced label, a
            // huge light number — the one place the countdown's colour lives —
            // with its unit riding the baseline, and the clock quiet and grey.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                if (underADay) {
                    Text(
                        text = bigTimeText(clockSeconds),
                        style = CountdownLineTextStyle.copy(
                            fontSize = 56.sp,
                            lineHeight = 68.sp,
                            letterSpacing = 0.sp,
                        ),
                        color = accent,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = totalDays.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 86.sp),
                            fontWeight = FontWeight.Light,
                            color = accent,
                        )
                        Text(
                            text = stringResource(R.string.detail_days_unit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 18.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "%02d:%02d:%02d".format(
                            clockSeconds / 3_600,
                            clockSeconds % 3_600 / 60,
                            clockSeconds % 60,
                        ),
                        style = CountdownLineTextStyle.copy(
                            fontSize = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (periodLine != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = periodLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The associated gallery photos, one large rounded card each, the
            // chosen photo first; a tap opens that photo full-screen. The pages
            // away from the finger sit back a little, the carousel's depth.
            if (photoThumbnails.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalPager(
                    state = carouselState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val away = abs((carouselState.currentPage - page) + carouselState.currentPageOffsetFraction)
                    Image(
                        bitmap = photoThumbnails[page].second.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .graphicsLayer {
                                val settle = 1f - (away.coerceIn(0f, 1f)) * 0.06f
                                scaleX = settle
                                scaleY = settle
                                alpha = 1f - (away.coerceIn(0f, 1f)) * 0.35f
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                viewerPage = page
                                viewerOpen = true
                            },
                    )
                }
                Spacer(Modifier.height(12.dp))
                PageDots(
                    count = photoThumbnails.size,
                    current = carouselState.currentPage,
                )
            }

            // The facts, as one grouped card: the day it counts to — with the
            // lunar day named when the date was entered that way — and how it
            // repeats, if it repeats.
            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                InfoRow(
                    label = stringResource(R.string.field_date),
                    value = dateLine,
                )
                if (event.yearlyRepeat) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    InfoRow(
                        label = stringResource(R.string.detail_row_repeat),
                        value = stringResource(
                            if (event.enteredAsLunar && event.lunarMonth != null) {
                                R.string.detail_repeat_lunar
                            } else {
                                R.string.detail_repeat_solar
                            }
                        ),
                    )
                }
            }

            if (event.tags.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                // The tags as a quiet footnote: a hairline, then the pills centred
                // under it — no card, so the page stays one column of air.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    event.tags.forEach { tag -> TagPill(tag) }
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        // The flip-through the carousel opens, at the photo that was tapped.
        if (event.photoUris.isNotEmpty() && viewerOpen) {
            PhotoViewer(
                photos = photoThumbnails.map { it.first },
                initialPage = viewerPage,
                onDismiss = { viewerOpen = false },
            )
        }
    }
}

/**
 * The big under-a-day countdown, shortest form that still says the distance:
 * `HH:MM:SS` while an hour remains, `MM:SS` under an hour, `SS` under a minute.
 */
private fun bigTimeText(seconds: Long): String = when {
    seconds >= 3_600L -> "%02d:%02d:%02d".format(
        seconds / 3_600,
        seconds % 3_600 / 60,
        seconds % 60,
    )
    seconds >= 60L -> "%02d:%02d".format(seconds / 60, seconds % 60)
    else -> seconds.toString()
}

/** The row of small dots under a carousel: the current page filled. */@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        }
                    ),
            )
        }
    }
}

/**
 * The photos one at a time, full-screen on black.
 *
 * The iOS reading gestures: a close button that is always where the thumb
 * expects it, a pull-down that carries the whole stack away, and dots that say
 * where in the pile you are. A tap does nothing — a stray touch should not end
 * the reading.
 */
@Composable
private fun PhotoViewer(
    photos: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, photos.size - 1)) {
        photos.size
    }
    // The pull-down: the whole stack follows the finger, and past a threshold
    // the release ends the reading rather than snapping home.
    var pull by remember { mutableFloatStateOf(0f) }

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
                            onDragEnd = {
                                if (pull > 380f) onDismiss() else pull = 0f
                            },
                            onDragCancel = { pull = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            pull = (pull + dragAmount).coerceAtLeast(0f)
                        }
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
            }

            // The count, top centre.
            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
            )

            // The close button, top right, above the drag layer.
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

            // The dots, bottom centre.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(photos.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    Color.White.copy(alpha = 0.9f)
                                } else {
                                    Color.White.copy(alpha = 0.3f)
                                }
                            ),
                    )
                }
            }
        }
    }
}

/** A label on the left, its value on the right, both on one row. */
@Composable
private fun InfoRow(label: String, value: String) {
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
