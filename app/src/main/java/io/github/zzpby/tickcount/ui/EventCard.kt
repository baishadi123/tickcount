package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.domain.CountdownListEntry
import io.github.zzpby.tickcount.domain.daysFromToday
import io.github.zzpby.tickcount.domain.effectiveDate
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.EVENT_WASH_ALPHA
import io.github.zzpby.tickcount.ui.theme.GlassAlpha
import io.github.zzpby.tickcount.ui.theme.glassSurface
import io.github.zzpby.tickcount.ui.theme.glassPane
import io.github.zzpby.tickcount.ui.theme.PillShape
import io.github.zzpby.tickcount.ui.theme.eventAccent
import io.github.zzpby.tickcount.ui.theme.hairline
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One countdown as a card.
 *
 * The day count is the largest thing on it, in a column of its own on the left, because the
 * number is what the card exists to say: the name only says which countdown it is. Everything
 * else — the date, the time, the tags — is quiet underneath, on one line each, so a list of
 * these can be read by scanning the left column alone.
 *
 * Shared between the home screen and the calendar's day list, which are the same list of the
 * same things seen through two different filters.
 */
@Composable
fun EventCard(
    entry: CountdownListEntry,
    today: LocalDate,
    /**
     * The quiet line under the name. The date on the home list, the time on a calendar day,
     * where every card already shares one date.
     */
    detailText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** The tags on their own line; off where the card is already crowded. */
    showTags: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val event = entry.event
    val scheme = MaterialTheme.colorScheme
    // Null hue means the countdown has no colour of its own, and the fallback is the theme's
    // accent — which is what keeps the default card looking deliberate rather than like a
    // swatch that failed to load.
    val accent = eventAccent(event.colorHue)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            // The list's pane, without the frost: a pane per card in a
            // scrolling list would blur the frame rate away with it, so a
            // card wears the fill, the 135° highlight and the bright edge —
            // the recipe that reads as glass at list speed.
            .glassSurface(CardShape)
            // Only a countdown that was given a colour tints its card. Washing the default
            // would wash it in the theme's own accent, making every card look chosen when
            // none of them are.
            .then(
                if (event.colorHue != null) {
                    Modifier.background(accent.copy(alpha = EVENT_WASH_ALPHA))
                } else {
                    Modifier
                }
            )
            // A long press is the way to the three things a countdown's own screen does
            // without the trip into it; a tap still opens that screen.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // So the edge bar below can span the card's full height.
            .height(IntrinsicSize.Min),
    ) {
        // The bookmark: a sliver of the countdown's own colour against the card's
        // left edge. The old dot sat in the far corner, too small and too far from
        // anything to be read; a column of these down the list says who is who.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(accent),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayCount(
                days = daysFromToday(event.effectiveDate(today), today),
                isPast = entry.isPast,
                accent = accent,
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pinned and yearly are said with small drawn marks rather
                    // than coloured pills: the title row belongs to the title,
                    // and the marks are there for the second look, not the first.
                    if (event.pinned) {
                        PinnedMark()
                        Spacer(Modifier.width(5.dp))
                    }
                    if (event.yearlyRepeat) {
                        YearlyMark()
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (entry.isPast) scheme.onSurfaceVariant else scheme.onSurface,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                )

                // The tags as small glass chips: bare accent-coloured letters
                // were the first thing to drown in a background photograph, and
                // a chip is what everything else in the app already sits on.
                if (showTags && event.tags.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        event.tags.take(3).forEach { tag -> TagChip(tag, accent) }
                        if (event.tags.size > 3) {
                            Text(
                                text = "+${event.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How far off the countdown is, in the three-line form the left column uses: a caption, the
 * number, and what it counts.
 *
 * Today, tomorrow and yesterday stay words. A "0 days" would be arithmetic where a word is
 * shorter and says more, and it would be the one card in the list that reads as a bug.
 * Four digits and more step one size down, so a thousand days never crowds its column.
 */
@Composable
private fun DayCount(days: Long, isPast: Boolean, accent: Color) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.width(78.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (days) {
            0L -> CountWord(stringResource(R.string.widget_today), accent)
            1L -> CountWord(stringResource(R.string.widget_tomorrow), accent)
            -1L -> CountWord(stringResource(R.string.list_yesterday), scheme.onSurfaceVariant)
            else -> {
                Text(
                    text = stringResource(
                        if (isPast) R.string.card_caption_past else R.string.card_caption_future
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = abs(days).toString(),
                    style = if (abs(days) >= 1000) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = if (isPast) scheme.onSurfaceVariant else accent,
                )
                Text(
                    text = stringResource(
                        if (isPast) R.string.card_unit_past else R.string.card_unit_future
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One of the three short words that stands in for a number: today, tomorrow, yesterday. */
@Composable
private fun CountWord(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = color,
    )
}

/**
 * The mark of a pinned countdown: a small drawn pin, quiet in the title row —
 * the coloured pill it replaces shouted over the name it sat beside.
 */
@Composable
private fun PinnedMark() {
    Canvas(modifier = Modifier.size(11.dp)) {
        val head = Size(width = size.width * 0.62f, height = size.height * 0.42f)
        drawRoundRect(
            color = Color(0xFF6F6A60),
            topLeft = Offset((size.width - head.width) / 2f, 0f),
            size = head,
            cornerRadius = CornerRadius(head.width / 2f),
        )
        drawLine(
            color = Color(0xFF6F6A60),
            start = Offset(size.width / 2f, head.height * 0.9f),
            end = Offset(size.width / 2f, size.height * 0.95f),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

/**
 * The mark of a yearly countdown: a small circle that comes round again, with
 * the arrowhead that says it keeps going.
 */
@Composable
private fun YearlyMark() {
    Canvas(modifier = Modifier.size(12.dp)) {
        val inset = 1.5.dp.toPx()
        drawArc(
            color = Color(0xFF6F6A60),
            startAngle = -30f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
        // The arrowhead at the arc's leading end.
        val r = (size.width - inset * 2) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val tip = Offset(cx + r * cos(Math.toRadians(-30.0)).toFloat(), cy + r * sin(Math.toRadians(-30.0)).toFloat())
        drawLine(
            color = Color(0xFF6F6A60),
            start = tip,
            end = Offset(tip.x - 3.2.dp.toPx(), tip.y - 1.2.dp.toPx()),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF6F6A60),
            start = tip,
            end = Offset(tip.x - 1.2.dp.toPx(), tip.y - 3.2.dp.toPx()),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * One tag, as a little pane of glass: the app's own card recipe, cut small.
 */
@Composable
private fun TagChip(tag: String, accent: Color) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
        modifier = Modifier
            .clip(PillShape)
            .background(glassPane())
            .hairline(PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
