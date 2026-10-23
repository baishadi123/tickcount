package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.AppLanguage
import io.github.zzpby.tickcount.ui.components.GearIcon

/**
 * The destinations the app has.
 *
 * The drawer offers the three pages you browse and the settings; everything from
 * the calendar down lives behind the settings gear, because it is set-up rather
 * than reading, and a drawer of nine rows said nothing about which was which.
 *
 * A single countdown's own screen is not a destination at all but a thing opened from
 * a list, so it is not in here.
 */
enum class AppScreen(val labelRes: Int) {
    HOME(R.string.drawer_home),
    THINGS(R.string.drawer_things),
    CAPSULE(R.string.drawer_capsule),
    CALENDAR(R.string.drawer_calendar),
    VOYAGE(R.string.drawer_voyage),
    SETTINGS(R.string.drawer_settings),
    REMINDERS(R.string.settings_notifications),
    APPEARANCE(R.string.settings_appearance),
    // The widgets have a page of their own rather than a section of Appearance: they are
    // drawn on the wallpaper, not in this window, and the choice is not this theme's.
    WIDGETS(R.string.settings_widgets),
    DATA(R.string.drawer_data),
    CHANGELOG(R.string.drawer_changelog),
    INTRO(R.string.settings_project_intro),
}

/** The pages the drawer itself offers: the ones you browse, and the gear behind them. */
internal val AppScreen.inDrawer: Boolean
    get() = this == AppScreen.HOME || this == AppScreen.THINGS ||
        this == AppScreen.CAPSULE || this == AppScreen.CALENDAR || this == AppScreen.VOYAGE

/** The pages the settings screen lists: everything below it in the enum. */
internal val AppScreen.inSettings: Boolean
    get() = ordinal > AppScreen.SETTINGS.ordinal

/**
 * Whether this destination opens a group, and so is drawn with a gap above it.
 *
 * Which entries start a group is a fact about [AppScreen] rather than about the drawer, so
 * it is stated here and the drawing code just asks. Reminders opens the settings, after the
 * calendar you browse; the changelog opens the two you read, after the one that writes.
 */
internal val AppScreen.startsGroup: Boolean
    get() = this == AppScreen.REMINDERS || this == AppScreen.CHANGELOG

/**
 * The gap between two groups.
 *
 * Sized against the 4dp between two rows inside a group — four times that, and equal to the
 * 12dp the column already leaves at its top and bottom, so the ends and the seams read as
 * the same kind of pause.
 */
internal val GroupSpacing = 12.dp

/**
 * The panel that slides in from the left.
 *
 * Hand-rolled rather than built on `ModalNavigationDrawer`, which clamps its sheet
 * to between 240dp and 360dp wide: on a phone that is nowhere near the half-screen
 * width this one is specified to take. Laying it out directly also keeps it a plain
 * child of the screen rather than a window of its own.
 *
 * The language is not here: it moved to the settings, where the rest of the
 * set-up lives.
 */
@Composable
fun AppDrawerContent(
    current: AppScreen?,
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        // The card colour, which is pure white in the white theme. What separates the panel
        // from the page there is the shadow below and the rounded edge, not a different tone:
        // a grey drawer on a white page reads as a stray piece of the old palette.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        // A real shadow rather than a tonal elevation: tonal elevation tints the surface
        // towards the primary colour, which on a white theme is a grey — the one thing this
        // panel should not be.
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                // The panel is drawn edge to edge, and as a sibling of the Scaffold rather
                // than a child of it, it is handed none of the insets the Scaffold applies
                // to its own content. Without this the top row is drawn twelve dp below the
                // top of the window and sits under the status bar.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(vertical = 12.dp),
        ) {
            Column(
                // Only the destinations scroll; the gear is outside this, so it
                // is never among the rows that can vanish.
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppScreen.entries.filter { it.inDrawer }.forEach { screen ->
                    DrawerRow(
                        label = stringResource(screen.labelRes),
                        selected = screen == current,
                        onClick = { onSelect(screen) },
                    )
                }
            }

            // The settings gear, bottom left: the language and everything below
            // the pages live behind it, and the corner is where a thumb looks
            // for a way out of a panel.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(AppScreen.SETTINGS) },
                    contentAlignment = Alignment.Center,
                ) {
                    GearIcon(
                        tint = if (current == AppScreen.SETTINGS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** The name each language is shown under, in that language rather than the current one. */
internal val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.CHINESE -> R.string.language_chinese
        AppLanguage.ENGLISH -> R.string.language_english
    }

@Composable
internal fun DrawerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                // Transparent rather than a card colour, so an ordinary row is the drawer's
                // own panel showing through and only the current destination is lifted.
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            // A floor rather than a fixed height: at a large system font the row has to
            // grow with its text instead of clipping it.
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
