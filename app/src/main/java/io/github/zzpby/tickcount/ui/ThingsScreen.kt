package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.Wish
import io.github.zzpby.tickcount.domain.daysFromToday
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.CardGap
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.Gutter
import io.github.zzpby.tickcount.ui.theme.XingKai
import io.github.zzpby.tickcount.ui.theme.hairline
import java.time.LocalDate

/**
 * The wish list: things the two of you want to do together, one page of its own —
 * a wish is not a countdown, so it does not live with the dates.
 *
 * What is still to do leads, what has been done is kept and dimmed under its own
 * heading, together with the day it was done. A tap on the ring ticks it; a long
 * press edits or removes it. The count under the title is the only summary: how
 * many of the things are done.
 */
@Composable
fun ThingsScreen(
    wishes: List<Wish>,
    today: LocalDate,
    /** Bumped each time the title bar's plus is tapped, which mints a new wish. */
    newRequest: Int,
    onToggle: (String) -> Unit,
    onSave: (Wish) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_short)

    val (open, done) = wishes.partition { !it.done }
    val orderedOpen = open.sortedByDescending { it.createdAt }
    val orderedDone = done.sortedByDescending { it.doneEpochDay ?: 0L }

    var editorWish by rememberSaveable { mutableStateOf<Wish?>(null) }
    var editorIsNew by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(newRequest) {
        if (newRequest > 0) {
            editorWish = Wish(id = Wish.newId(), title = "", createdAt = System.currentTimeMillis())
            editorIsNew = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 2.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.page_things),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = XingKai,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (wishes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.things_summary, done.size, wishes.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            wishes.isEmpty() -> CentredNotice(stringResource(R.string.things_empty))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 4.dp),
            ) {
                items(orderedOpen, key = { it.id }) { wish ->
                    WishCard(
                        wish = wish,
                        today = today,
                        dateFormatter = dateFormatter,
                        onToggle = { onToggle(wish.id) },
                        onLongClick = {
                            editorWish = wish
                            editorIsNew = false
                        },
                    )
                }

                if (orderedDone.isNotEmpty()) {
                    item(key = "things-done-heading") {
                        Text(
                            text = stringResource(R.string.things_done_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                items(orderedDone, key = { it.id }) { wish ->
                    WishCard(
                        wish = wish,
                        today = today,
                        dateFormatter = dateFormatter,
                        onToggle = { onToggle(wish.id) },
                        onLongClick = {
                            editorWish = wish
                            editorIsNew = false
                        },
                    )
                }
            }
        }
    }

    if (editorWish != null) {
        WishEditorDialog(
            initial = editorWish!!,
            isNew = editorIsNew,
            onSave = { saved ->
                onSave(saved)
                editorWish = null
            },
            onDelete = if (!editorIsNew) {
                {
                    pendingDeleteId = editorWish!!.id
                    editorWish = null
                }
            } else {
                null
            },
            onDismiss = { editorWish = null },
        )
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_delete_message,
                        wishes.firstOrNull { it.id == pendingDeleteId }?.title ?: "",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pendingDeleteId!!)
                    pendingDeleteId = null
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One wish as a card: an ink box that ticks it, the name, and — done — the day.
 *
 * The ink box is the whole interaction on the face of the card, because ticking is
 * what a wish exists for; editing and removing live on the long press, the same way
 * the countdowns keep them.
 */
@Composable
private fun WishCard(
    wish: Wish,
    today: LocalDate,
    dateFormatter: java.time.format.DateTimeFormatter,
    onToggle: () -> Unit,
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
            .combinedClickable(onClick = onToggle, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (wish.done) scheme.primary else scheme.surface)
                .hairline(CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (wish.done) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wish.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (wish.done) scheme.onSurfaceVariant else scheme.onSurface,
            )
            if (wish.done && wish.doneEpochDay != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.things_done_on,
                        LocalDate.ofEpochDay(wish.doneEpochDay).format(dateFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The light dialog that adds or renames a wish: a name, and the way out of it. */
@Composable
private fun WishEditorDialog(
    initial: Wish,
    isNew: Boolean,
    onSave: (Wish) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.things_new_title else R.string.things_edit_title
                )
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(Wish.MAX_TITLE_LENGTH) },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(stringResource(R.string.things_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            createdAt = if (isNew) System.currentTimeMillis() else initial.createdAt,
                        )
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (isNew) R.string.action_add else R.string.action_save
                    )
                )
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
