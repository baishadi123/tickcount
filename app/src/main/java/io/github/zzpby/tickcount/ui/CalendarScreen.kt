package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.TimeCapsule
import io.github.zzpby.tickcount.data.Wish
import io.github.zzpby.tickcount.domain.SearchScope
import io.github.zzpby.tickcount.domain.SortOrder
import io.github.zzpby.tickcount.domain.countdownList
import io.github.zzpby.tickcount.domain.dayNumberOf
import io.github.zzpby.tickcount.domain.effectiveDate
import io.github.zzpby.tickcount.domain.matchesQuery
import io.github.zzpby.tickcount.domain.searchText
import io.github.zzpby.tickcount.ui.calendar.MonthCalendar
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.hairline
import io.github.zzpby.tickcount.ui.theme.tintWash
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import io.github.zzpby.tickcount.ui.components.PlusIcon

/** The rose the anniversary is marked in, on the grid and in its row alike. */
private val AnniversaryMark = androidx.compose.ui.graphics.Color(0xFFC25B4E)

/**
 * The month, and underneath it whatever the chosen day holds.
 *
 * Choosing a day used to open a dialog, which made the calendar a way of asking a
 * question one day at a time. Showing the day's countdowns below it instead means the
 * grid and the answer to it are on screen together, and the list underneath is the
 * same list as the home screen's — same cards, same search, same order — narrowed to
 * one date.
 */
@Composable
fun CalendarScreen(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    eventsByDate: Map<LocalDate, List<CountdownEvent>>,
    /** The capsules by the day they open on — a day's list holds them beside the countdowns. */
    capsulesByDate: Map<LocalDate, List<TimeCapsule>>,
    /** The wishes by the day they were ticked off, which the calendar keeps as memories. */
    wishesByDate: Map<LocalDate, List<Wish>>,
    /** The anniversary the 「在一起第 N 天」 counts from, or none set. */
    anniversaryDate: LocalDate?,
    /** The milestone days from that anniversary, by the day number they land on. */
    milestonesByDate: Map<LocalDate, Long>,
    query: String,
    onQueryChange: (String) -> Unit,
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onStepMonth: (Long) -> Unit,
    onMonthClick: () -> Unit,
    onToday: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onLongPressEvent: (String) -> Unit,
    onOpenCapsule: () -> Unit,
    onOpenWishes: () -> Unit,
    onSetAnniversary: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_full)
    val timeFormatter = rememberDateFormatter(R.string.time_format)
    val allDay = stringResource(R.string.field_all_day)

    Column(modifier = modifier.fillMaxSize()) {
        MonthCalendar(
            month = month,
            selectedDate = selectedDate,
            today = today,
            events = eventsByDate,
            capsuleDates = capsulesByDate.keys,
            wishDates = wishesByDate.keys,
            milestoneDates = milestonesByDate.keys,
            anniversary = anniversaryDate,
            onSelect = onSelectDate,
            onStepMonth = onStepMonth,
            onMonthClick = onMonthClick,
            onToday = onToday,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (selectedDate == null) {
            CentredNotice(stringResource(R.string.calendar_pick_a_day))
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                scope = scope,
                onScopeChange = onScopeChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SortButton(current = sortOrder, onPick = onSortChange)
        }

        val entries = countdownList(eventsByDate[selectedDate].orEmpty(), today, sortOrder)
        val visible = entries.filter { entry ->
            matchesQuery(
                haystack = searchText(
                    event = entry.event,
                    scope = scope,
                    // Every card here is the same date, so the line under the name
                    // carries the time instead — the date is the grid above. The date
                    // a repeating countdown is searched by is its next occurrence, the
                    // same day its card counts to.
                    dateText = entry.event.effectiveDate(today).format(dateFormatter),
                    timeText = entry.event.time?.format(timeFormatter) ?: allDay,
                ),
                query = query,
            )
        }

        val dayCapsules = capsulesByDate[selectedDate].orEmpty()
        val dayWishes = wishesByDate[selectedDate].orEmpty()
        val dayMilestone = milestonesByDate[selectedDate]
        // The anniversary is a day of the year rather than a date: it belongs to
        // its month and day in whatever year the grid is showing.
        val anniversaryDay = anniversaryDate != null &&
            selectedDate.month == anniversaryDate.month &&
            selectedDate.dayOfMonth == anniversaryDate.dayOfMonth
        val dayHasMore = dayCapsules.isNotEmpty() || dayWishes.isNotEmpty() ||
            anniversaryDay || dayMilestone != null

        Box(modifier = Modifier.weight(1f)) {
            when {
                entries.isEmpty() && !dayHasMore ->
                    CentredNotice(stringResource(R.string.calendar_day_empty))

                visible.isEmpty() && !dayHasMore ->
                    CentredNotice(
                        stringResource(R.string.home_no_matches, query.trim())
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    if (anniversaryDate == null) {
                        item(key = "set-anniversary") {
                            SetAnniversaryRow(onSet = { onSetAnniversary(selectedDate) })
                        }
                    } else if (anniversaryDay) {
                        item(key = "anniversary") {
                            AnniversaryRow(anniversary = anniversaryDate, today = today)
                        }
                    }
                    if (dayMilestone != null) {
                        item(key = "milestone") {
                            MilestoneRow(day = dayMilestone)
                        }
                    }
                    items(dayCapsules, key = { "capsule-${it.id}" }) { capsule ->
                        CapsuleDayRow(capsule = capsule, onClick = onOpenCapsule)
                    }
                    items(dayWishes, key = { "wish-${it.id}" }) { wish ->
                        WishDayRow(wish = wish, onClick = onOpenWishes)
                    }
                    items(visible, key = { it.event.id }) { entry ->
                        EventCard(
                            entry = entry,
                            today = today,
                            detailText = withLunar(
                                entry.event,
                                entry.event.time?.format(timeFormatter) ?: allDay,
                            ),
                            onClick = { onOpenEvent(entry.event.id) },
                            onLongClick = { onLongPressEvent(entry.event.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One capsule opening on this day.
 *
 * Built on the event card's own geometry — the seventy-dp column on the left,
 * the name beside it — with the ring the grid carries standing in for the day
 * count. The ring says the state without a word: hollow while the capsule is
 * still sealed, filled once it has been opened.
 */
@Composable
private fun CapsuleDayRow(capsule: TimeCapsule, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .hairline(CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (capsule.openedEpochDay != null) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(scheme.tertiary, CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .border(1.8.dp, scheme.tertiary, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capsule.title.ifBlank { capsule.message.take(20) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (capsule.openedEpochDay == null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.calendar_capsule_open),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One wish ticked off on this day: a check where the count lives, the name beside it. */
@Composable
private fun WishDayRow(wish: Wish, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .hairline(CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.55f)
                    lineTo(size.width * 0.40f, size.height * 0.85f)
                    lineTo(size.width * 0.92f, size.height * 0.18f)
                }
                drawPath(
                    path = path,
                    color = scheme.secondary,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = wish.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * This day is the anniversary: the rose dot the grid carries, the name, and
 * how many days it has been — on the banner wash a reminder reads from.
 */
@Composable
private fun AnniversaryRow(anniversary: LocalDate, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(tintWash())
            .hairline(CardShape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(AnniversaryMark, CircleShape)
            )
        }

        Text(
            text = stringResource(R.string.calendar_anniversary),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(
                R.string.anniversary_together,
                dayNumberOf(anniversary, today),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * A milestone of the days together: the rose ring the grid carries, and the
 * number it is — the banner wash an anniversary reads from, its own day said.
 */
@Composable
private fun MilestoneRow(day: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(tintWash())
            .hairline(CardShape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AnniversaryMark, CircleShape)
            )
        }

        Text(
            text = stringResource(R.string.anniversary_together, day),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** No anniversary yet: the card the others are, with a plus where the mark goes. */
@Composable
private fun SetAnniversaryRow(onSet: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CardGap / 2)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .hairline(CardShape)
            .clickable(onClick = onSet)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlusIcon(color = scheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.calendar_set_anniversary),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}
