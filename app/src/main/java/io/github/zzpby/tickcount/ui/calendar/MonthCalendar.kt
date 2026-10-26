package io.github.zzpby.tickcount.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.domain.CalendarCell
import io.github.zzpby.tickcount.domain.firstDayOfWeek
import io.github.zzpby.tickcount.domain.monthCells
import io.github.zzpby.tickcount.domain.weekDaysInOrder
import io.github.zzpby.tickcount.ui.components.ChevronIcon
import io.github.zzpby.tickcount.ui.components.currentLocale
import io.github.zzpby.tickcount.ui.theme.eventAccent
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How many countdown dots fit under a day number before they stop being legible. */
private const val MAX_DOTS = 3

/** The anniversary's mark, a warm rose that belongs to no theme. */
private val AnniversaryColor = Color(0xFFC25B4E)

/**
 * The month grid.
 *
 * Everything here is plain Compose layout: 42 cells compose eagerly and cheaply,
 * which avoids pulling in a calendar dependency for what is ultimately a
 * `ChronoUnit` loop and a `Row`.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    events: Map<LocalDate, List<CountdownEvent>>,
    /** The days a capsule opens on, marked apart from the countdown dots. */
    capsuleDates: Set<LocalDate> = emptySet(),
    /** The days a wish was ticked off, marked with the countdowns' own dots. */
    wishDates: Set<LocalDate> = emptySet(),
    /** The milestone days from the anniversary, ringed in the anniversary's rose. */
    milestoneDates: Set<LocalDate> = emptySet(),
    /** The anniversary, marked on its month and day in every year the grid shows. */
    anniversary: LocalDate? = null,
    onSelect: (LocalDate) -> Unit,
    onStepMonth: (Long) -> Unit,
    onMonthClick: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val calendarLocale = remember(locale) { if (locale.language.isEmpty()) Locale.getDefault() else locale }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            MonthHeader(
                month = month,
                locale = calendarLocale,
                onStepMonth = onStepMonth,
                onMonthClick = onMonthClick,
                onToday = onToday,
            )
            Spacer(Modifier.height(4.dp))
            WeekDayHeader(locale = calendarLocale)
            Spacer(Modifier.height(2.dp))
            MonthGrid(
                month = month,
                locale = calendarLocale,
                selectedDate = selectedDate,
                today = today,
                events = events,
                capsuleDates = capsuleDates,
                wishDates = wishDates,
                milestoneDates = milestoneDates,
                anniversary = anniversary,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    locale: Locale,
    onStepMonth: (Long) -> Unit,
    onMonthClick: () -> Unit,
    onToday: () -> Unit,
) {
    val pattern = stringResource(R.string.month_year_format)
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStepMonth(-1) }) {
            ChevronIcon(pointsLeft = true)
        }

        // Tapping the title opens the year/month chooser, which is far quicker
        // than stepping through months when the target is years away.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .clickable(onClick = onMonthClick),
        ) {
            Text(
                text = month.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        TextButton(onClick = onToday) {
            Text(
                text = stringResource(R.string.action_today),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        IconButton(onClick = { onStepMonth(1) }) {
            ChevronIcon(pointsLeft = false)
        }
    }
}

@Composable
private fun WeekDayHeader(locale: Locale) {
    val days = remember(locale) {
        weekDaysInOrder(locale).map { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    locale: Locale,
    selectedDate: LocalDate?,
    today: LocalDate,
    events: Map<LocalDate, List<CountdownEvent>>,
    capsuleDates: Set<LocalDate>,
    wishDates: Set<LocalDate>,
    milestoneDates: Set<LocalDate>,
    anniversary: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month, locale) { monthCells(month, firstDayOfWeek(locale)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isSelected = cell.date == selectedDate,
                        isToday = cell.date == today,
                        accents = events[cell.date].orEmpty().map { eventAccent(it.colorHue) },
                        hasCapsule = cell.date in capsuleDates,
                        hasWish = cell.date in wishDates,
                        hasMilestone = cell.date in milestoneDates,
                        // The anniversary is a day of the year, not a date: it
                        // marks its month and day in every year the grid shows.
                        isAnniversary = anniversary != null &&
                            cell.date.month == anniversary.month &&
                            cell.date.dayOfMonth == anniversary.dayOfMonth,
                        onClick = { onSelect(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarCell,
    isSelected: Boolean,
    isToday: Boolean,
    accents: List<Color>,
    hasCapsule: Boolean = false,
    hasWish: Boolean = false,
    hasMilestone: Boolean = false,
    isAnniversary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val numberColor = when {
        isSelected -> colorScheme.onPrimary
        isToday -> colorScheme.primary
        cell.inMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    val description = remember(cell.date) {
        cell.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    Box(
        modifier = modifier
            .aspectRatio(1.02f)
            .padding(3.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(colorScheme.primary, CircleShape)
            )
        } else if (isToday) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.5.dp, colorScheme.primary, CircleShape)
            )
        }

        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = numberColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )

        if (accents.isNotEmpty() || hasCapsule || hasWish || hasMilestone || isAnniversary) {
            // Up to three countdown dots, then the capsule's ring, the wish's
            // diamond, the milestone's rose ring and the anniversary's rose dot —
            // a day holding several things looks different from a day holding
            // one, each in its own shape.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                accents.take(MAX_DOTS).forEach { accent ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (isSelected) colorScheme.onPrimary else accent,
                                CircleShape,
                            )
                    )
                }
                if (hasCapsule) {
                    // A ring rather than a dot: a capsule opening is a container
                    // waiting, and it reads apart from the countdowns' solids.
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .border(
                                1.2.dp,
                                if (isSelected) colorScheme.onPrimary else colorScheme.tertiary,
                                CircleShape,
                            )
                    )
                }
                if (hasWish) {
                    // A diamond: a wish ticked off is a thing settled, not one
                    // waiting, and the turn sets it apart from the dots.
                    Box(
                        Modifier
                            .size(5.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(
                                if (isSelected) colorScheme.onPrimary else colorScheme.secondary,
                                RoundedCornerShape(1.dp),
                            )
                    )
                }
                if (hasMilestone) {
                    // The anniversary's own rose, hollow: a milestone is the day
                    // the count itself turns a number worth saying.
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, if (isSelected) colorScheme.onPrimary else AnniversaryColor, CircleShape)
                    )
                }
                if (isAnniversary) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (isSelected) colorScheme.onPrimary else AnniversaryColor,
                                CircleShape,
                            )
                    )
                }
            }
        }
    }
}
