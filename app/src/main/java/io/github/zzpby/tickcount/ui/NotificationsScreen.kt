package io.github.zzpby.tickcount.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.AppSettings
import io.github.zzpby.tickcount.notify.Reminders
import io.github.zzpby.tickcount.ui.components.ChoicePill
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.FieldShape
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.PillShape
import io.github.zzpby.tickcount.ui.theme.hairline
import io.github.zzpby.tickcount.ui.theme.tintWash
import io.github.zzpby.tickcount.ui.theme.GlassAlpha
import io.github.zzpby.tickcount.ui.theme.textHalo
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The reminder, and whether it will actually arrive.
 *
 * There is one rule and no knobs beyond the time of day an all-day countdown
 * is reminded at: a countdown with a time of day is reminded at that time, an
 * all-day one at the time picked below, a letter at that time on the day it
 * opens. Laid out as a statement first and a list of facts second: the top
 * strip says when the next reminder is due, the three tiles under it say
 * whether anything is standing in its way, and the settings only appear once
 * the reminder is on.
 */
@Composable
fun NotificationsScreen(
    settings: AppSettings,
    nextAt: Instant?,
    onRemind: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Bumped by the refresh action: the permission is read from the system, and Compose has
    // no reason to re-read it when the user comes back from the system settings.
    var refreshKey by remember { mutableIntStateOf(0) }
    var timeDialogOpen by remember { mutableStateOf(false) }

    val granted = remember(refreshKey, settings.remind) { Reminders.canUseCalendar(context) }
    val scheduled = settings.remind && nextAt != null
    val nextText = when {
        !settings.remind -> stringResource(R.string.notify_off_summary)
        nextAt == null -> stringResource(R.string.notify_none)
        else -> rememberDateFormatter(R.string.date_format_full)
            .format(nextAt.atZone(ZoneId.systemDefault())) + " " +
            timeText(minutesOf(nextAt))
    }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) onRemind(true)
        refreshKey++
    }

    // The upgrade path: a reminder that was already on when the calendar
    // began asking has never been asked for — the old alarm needed no
    // permission, so it never came up. One ask on the way into this page,
    // and one full sync whenever the permission stands — whatever door it
    // came through, the toggle, the test, or the system's own settings —
    // keeps the calendar in step with the list that was there all along.
    LaunchedEffect(Unit) {
        if (settings.remind && !granted) calendarPermission.launch(CALENDAR_PERMISSIONS)
    }
    LaunchedEffect(settings.remind, granted) {
        if (settings.remind && granted) onRemind(true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_notifications),
            style = MaterialTheme.typography.displaySmall.copy(shadow = textHalo()),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- what will happen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(tintWash())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notify_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = nextText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = settings.remind,
                onCheckedChange = { want ->
                    when {
                        !want -> onRemind(false)
                        granted -> onRemind(true)
                        else -> calendarPermission.launch(CALENDAR_PERMISSIONS)
                    }
                },
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- anything in the way
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                value = stringResource(
                    if (granted) R.string.notify_granted else R.string.notify_not_granted
                ),
                label = stringResource(R.string.notify_permission),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = if (scheduled) "1" else "0",
                label = stringResource(R.string.notify_pending),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = stringResource(
                    if (settings.remind) R.string.notify_on else R.string.notify_off
                ),
                label = stringResource(R.string.notify_switch),
                modifier = Modifier.weight(1f),
            )
        }

        if (settings.remind) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = GlassAlpha))
                    .hairline(CardShape)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FieldShape)
                        .clickable { timeDialogOpen = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notify_time_all_day),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = timeText(settings.remindMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // The reminder time is only consulted for countdowns that do not name a
                // time of their own, and a row that looks universal without saying so reads
                // as a setting that has been ignored.
                Text(
                    text = stringResource(R.string.notify_time_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- prove it works
        Button(
            onClick = {
                if (granted) {
                    onTest()
                } else {
                    calendarPermission.launch(CALENDAR_PERMISSIONS)
                }
            },
            shape = PillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.notify_test))
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { refreshKey++ }) {
                Text(stringResource(R.string.notify_refresh))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (!granted) calendarPermission.launch(CALENDAR_PERMISSIONS)
                },
                enabled = !granted,
            ) {
                Text(stringResource(R.string.notify_grant))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.notify_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        Spacer(Modifier.height(28.dp))
    }

    if (timeDialogOpen) {
        TimeDialog(
            current = settings.remindMinutes,
            onPick = {
                onMinutes(it)
                timeDialogOpen = false
            },
            onDismiss = { timeDialogOpen = false },
        )
    }
}

/** Minutes after midnight for the instant a reminder was planned at, in local time. */
private fun minutesOf(at: Instant): Int {
    val local = at.atZone(ZoneId.systemDefault()).toLocalTime()
    return local.hour * 60 + local.minute
}

/** 780 becomes `13:00`. */
private fun timeText(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * One fact about the reminder, in a small square.
 *
 * A number first and a word under it, so the three tiles read as a row of readings rather than
 * as three sentences to parse.
 */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(FieldShape)
            .hairline(FieldShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Picks the time an all-day countdown's reminder arrives.
 *
 * Half-hour steps and four presets rather than a wheel: a reminder for a countdown is set
 * once and then left alone, and a wheel is three gestures to change something most people
 * will never change twice.
 */
@Composable
private fun TimeDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notify_time)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { minutes = (minutes + 1440 - 30) % 1440 }) {
                        Text("\u2212")
                    }
                    Text(
                        text = timeText(minutes),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(onClick = { minutes = (minutes + 30) % 1440 }) {
                        Text("+")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8 * 60, 9 * 60, 12 * 60, 21 * 60).forEach { preset ->
                        ChoicePill(
                            label = timeText(preset),
                            selected = minutes == preset,
                            onClick = { minutes = preset },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(minutes) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The pair the reminder runs on: reading the rows back is half of keeping them true. */
private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)
