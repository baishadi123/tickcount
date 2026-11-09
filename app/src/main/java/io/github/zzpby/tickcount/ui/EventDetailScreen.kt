package io.github.zzpby.tickcount.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import io.github.zzpby.tickcount.domain.effectiveDate
import io.github.zzpby.tickcount.domain.effectiveTarget
import io.github.zzpby.tickcount.ui.components.GlassPane
import io.github.zzpby.tickcount.ui.components.dialogWindowGuard
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.CountdownLineTextStyle
import io.github.zzpby.tickcount.ui.theme.eventAccent
import io.github.zzpby.tickcount.ui.theme.textHalo
import io.github.zzpby.tickcount.ui.theme.tintWash
import io.github.zzpby.tickcount.ui.theme.GlassAlpha
import io.github.zzpby.tickcount.ui.theme.glassPane
import io.github.zzpby.tickcount.ui.theme.hairline
import io.github.zzpby.tickcount.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
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
 * The page sits on the app's own washed background when one has been picked
 * in the appearance settings; nothing here draws one of its own.
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

    // The big count and the clock divide one distance between them: the gap to
    // the countdown's own moment — the saved time of day included — measured to
    // that moment while it is ahead and from it once it has passed. An all-day
    // countdown's moment is its date's midnight, so that reading keeps its old
    // shape (列表 4 天 → 大字 3 天 16:00:00；过去 8027 天 → 大字 8026 天 + 当天流逝).
    val bottomDate = when (countdown.phase) {
        CountdownPhase.PAST -> event.date
        else -> event.effectiveDate(now.toLocalDate())
    }
    // The anchored moment: the next occurrence while it is ahead, the stored
    // moment itself afterwards — so a past countdown is measured from the hour
    // it happened, never from its date's midnight.
    val anchor = when (countdown.phase) {
        CountdownPhase.PAST -> event.target(now.zone)
        else -> event.effectiveTarget(now.zone, now.toLocalDate())
    }
    val gapSeconds = remember(countdown, now) {
        if (countdown.phase == CountdownPhase.FUTURE) {
            Duration.between(now, anchor).seconds
        } else {
            Duration.between(anchor, now).seconds
        }
    }

    // Whole days of the gap in large type; the clock carries the incomplete day,
    // so the pair adds back to the true distance whichever way the days run.
    val totalDays = remember(countdown, now) { gapSeconds / 86_400 }
    val clockSeconds = remember(countdown, now) { gapSeconds - totalDays * 86_400 }

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
        // The chosen photo leads; only the names are prepared up front. The
        // pixels are decoded per page, at the size the page is shown at — a
        // sharp card costs one decode of one photo, not a pile of nine.
        val photoNames = remember(event.photoUris, event.photoCover, event.id) {
            event.photoUris.sortedByDescending { it == event.photoCover }
        }
        var viewerOpen by rememberSaveable { mutableStateOf(false) }
        var viewerPage by rememberSaveable { mutableIntStateOf(0) }
        val carouselState = rememberPagerState { photoNames.size }

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
                style = MaterialTheme.typography.headlineLarge.copy(shadow = textHalo()),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            // The countdown itself, in the iOS register: a small spaced label, a
            // huge light number — the one place the countdown's colour lives —
            // with its unit riding the baseline, and the clock quiet and grey.
            // On a pane of its own: the big figure is light and thin, and over
            // a busy photograph it would dissolve into the picture without a
            // pane to sit on.
            GlassPane(
                modifier = Modifier.fillMaxWidth(0.72f),
                alpha = 0.20f,
            ) {
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
            }

            // The associated gallery photos, the chosen one first, a tap away
            // from full-screen. The whole photo shows — nothing cropped to
            // fit the card — and the space it leaves is the photo's own blur
            // stretched wide. The card also takes the photo's shape: a
            // landscape stays low, a portrait grows tall, so a vertical
            // picture is never left a narrow strip.
            if (photoNames.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints {
                    val cardWidthPx = constraints.maxWidth
                    HorizontalPager(
                        state = carouselState,
                        pageSpacing = 12.dp,
                        beyondViewportPageCount = 1,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        CarouselPage(
                            name = photoNames[page],
                            maxCardWidthPx = cardWidthPx,
                            onClick = {
                                viewerPage = page
                                viewerOpen = true
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PageDots(
                    count = photoNames.size,
                    current = carouselState.currentPage,
                )
            }

            // The facts, as one grouped card: the day it counts to — with the
            // lunar day named when the date was entered that way — and how it
            // repeats, if it repeats.
            Spacer(Modifier.height(26.dp))
            GlassPane(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                // The day itself, named in full with nothing before it: the label it
                // used to carry was one more word ahead of the longest line on the
                // page, and the word was the only part that could be spared.
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (event.yearlyRepeat) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    // The repeat rides under the day the same way: no label, the
                    // words centred — 「每年 · 农历」 says it all by itself.
                    Text(
                        text = stringResource(
                            if (event.enteredAsLunar && event.lunarMonth != null) {
                                R.string.detail_repeat_lunar
                            } else {
                                R.string.detail_repeat_solar
                            }
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                photos = photoNames,
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
 * One photo of the detail page's carousel, shown whole on a card of its own
 * blur.
 *
 * [ContentScale.Fit] keeps every edge of the picture; the space it leaves on
 * the card is the same picture, blurred and stretched, so the card fills
 * without the photo losing its head to a crop. The card also leans towards
 * the photo's own shape — a landscape rides a taller card, a portrait's card
 * narrows until the blur is a frame rather than wings — and both dimensions
 * spring to their answer rather than stepping to it. The decode is a
 * background job keyed to the name, at the width the card is about to be.
 */
@Composable
private fun CarouselPage(name: String, maxCardWidthPx: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap by produceState<Bitmap?>(null, name, maxCardWidthPx) {
        value = withContext(Dispatchers.IO) {
            EventBackgrounds.thumbnail(context, name, maxCardWidthPx)
        }
    }

    // The shape the card settles into once the photo's own is known: full
    // width and as tall as the band allows for a landscape, and for a
    // portrait a card narrowed until the photo has no more than a frame of
    // blur around it.
    val fullWidth = with(density) { maxCardWidthPx.toDp() }
    val asked = bitmap?.let { pixels ->
        val aspect = pixels.width.toFloat() / pixels.height
        val height = with(density) { (maxCardWidthPx / aspect).toDp() }
            .coerceIn(280.dp, 360.dp)
        val width = if (aspect >= 1f) {
            fullWidth
        } else {
            minOf(fullWidth, height * aspect + 80.dp)
        }
        height to width
    } ?: (280.dp to fullWidth)
    val cardHeight by animateDpAsState(asked.first, spring(), label = "cardHeight")
    val cardWidth by animateDpAsState(asked.second, spring(), label = "cardWidth")

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(glassPane())
                .clickable(onClick = onClick),
        ) {
            bitmap?.let { pixels ->
                // The backdrop first: the photo itself, blurred and cropped to
                // the card — scaled up a touch so the blur's soft edge stays
                // outside — with a scrim to keep it behind the star.
                Image(
                    bitmap = pixels.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = 1.25f
                            scaleY = 1.25f
                        }
                        .blur(24.dp),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                )
                // And the photo, whole and centred.
                Image(
                    bitmap = pixels.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
                .dialogWindowGuard()
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

/**
 * A tag, drawn as a small pane of glass — the same chip the home card's tags
 * wear, cut to this page's air.
 *
 * There is nothing to press here, and a chip that rippled when tapped would
 * be an invitation that leads nowhere.
 */
@Composable
private fun TagPill(tag: String) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(PillShape)
            .background(glassPane())
            .hairline(PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
