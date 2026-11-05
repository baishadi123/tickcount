package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.data.FootprintMedia
import io.github.zzpby.tickcount.data.GalleryMedia
import io.github.zzpby.tickcount.domain.MediaRef
import io.github.zzpby.tickcount.ui.components.dialogWindowGuard
import io.github.zzpby.tickcount.ui.components.rememberGalleryReadRequest
import io.github.zzpby.tickcount.ui.theme.GlassAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One record's one picture, as the health check carries it: whose it is. */
data class GalleryHealthItem(val owner: String, val entry: String)

/**
 * 相册体检 — every picture the records reference, checked against where it
 * lives: a gallery reference against the album it points into, a private
 * copy against the app's own directory. In place, found again by its
 * fingerprint, or gone. The same walk a phone migration will take, done
 * today on this phone, so the real day holds a number instead of a hope.
 */
@Composable
fun GalleryHealthDialog(
    items: List<GalleryHealthItem>,
    onRelink: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    onGalleryReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val requestRead = rememberGalleryReadRequest()

    var state by remember { mutableStateOf<HealthState>(HealthState.Working) }

    LaunchedEffect(Unit) {
        requestRead { granted ->
            if (granted) onGalleryReady()
            state = if (!granted) {
                HealthState.Denied
            } else if (items.isEmpty()) {
                HealthState.Done(emptyList(), 0)
            } else {
                HealthState.Working
            }
        }
    }

    if (state is HealthState.Working) {
        LaunchedEffect(items) {
            // First pass: where does every reference stand?
            val first = withContext(Dispatchers.IO) {
                items.map { item -> item to healthOf(context, item) }
            }
            // Found-again entries are repaired in the same breath: the
            // fingerprint's answer becomes the record's new truth — 换机的
            // 最后一步就发生在这次体检里.
            val mapping = first.mapNotNull { (item, status) ->
                if (status != GalleryMedia.Health.RELOCATED) {
                    null
                } else {
                    GalleryMedia.relocatedId(context, item.entry)?.let { newId ->
                        MediaRef.parse(item.entry)?.let { ref ->
                            item.entry to MediaRef.encode(ref.copy(mediaStoreId = newId))
                        }
                    }
                }
            }.toMap()
            var repaired = 0
            var second = emptyMap<String, GalleryMedia.Health>()
            if (mapping.isNotEmpty()) {
                onRelink(mapping)
                second = withContext(Dispatchers.IO) {
                    mapping.keys.associateWith { entry ->
                        healthOf(context, GalleryHealthItem("", entry))
                    }
                }
                repaired = second.count { it.value == GalleryMedia.Health.IN_PLACE }
            }
            val report = first.map { (item, status) ->
                if (item.entry in mapping) {
                    item to (second[item.entry] ?: GalleryMedia.Health.RELOCATED)
                } else {
                    item to status
                }
            }
            state = HealthState.Done(report, repaired)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .dialogWindowGuard()
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gallery_health_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.gallery_health_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }

            Spacer(Modifier.height(12.dp))

            when (val current = state) {
                HealthState.Denied -> HealthNotice(stringResource(R.string.gallery_health_permission))
                HealthState.Working -> HealthNotice(stringResource(R.string.gallery_health_scanning))
                is HealthState.Done -> HealthReport(current.report, current.repaired)
            }
        }
    }
}

private sealed interface HealthState {
    data object Working : HealthState
    data object Denied : HealthState
    data class Done(
        val report: List<Pair<GalleryHealthItem, GalleryMedia.Health>>,
        /** How many found-again references were written back into the records. */
        val repaired: Int,
    ) : HealthState
}

/** One entry's verdict: references ask the album, private copies ask the file. */
private fun healthOf(context: android.content.Context, item: GalleryHealthItem): GalleryMedia.Health =
    when {
        MediaRef.isRef(item.entry) -> GalleryMedia.health(context, item.entry) ?: GalleryMedia.Health.MISSING
        item.entry.startsWith("fp-") ->
            if (FootprintMedia.file(context, item.entry) != null) GalleryMedia.Health.IN_PLACE else GalleryMedia.Health.MISSING
        else ->
            if (EventBackgrounds.exists(context, item.entry)) GalleryMedia.Health.IN_PLACE else GalleryMedia.Health.MISSING
    }

@Composable
private fun HealthNotice(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthReport(
    report: List<Pair<GalleryHealthItem, GalleryMedia.Health>>,
    repaired: Int,
) {
    val inPlace = report.count { it.second == GalleryMedia.Health.IN_PLACE }
    val relocated = report.count { it.second == GalleryMedia.Health.RELOCATED }
    val missing = report.count { it.second == GalleryMedia.Health.MISSING }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.gallery_health_count, report.size, inPlace, relocated, missing,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (repaired > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.gallery_health_relinked_count, repaired),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        report.forEach { (item, status) ->
            if (status == GalleryMedia.Health.IN_PLACE) return@forEach
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = GlassAlpha))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.owner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        text = stringResource(
                            if (status == GalleryMedia.Health.RELOCATED) {
                                R.string.gallery_health_relocated
                            } else {
                                R.string.gallery_health_missing
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status == GalleryMedia.Health.MISSING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        if (missing == 0 && relocated == 0) {
            item {
                Text(
                    text = stringResource(R.string.gallery_health_all_well),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.gallery_health_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
