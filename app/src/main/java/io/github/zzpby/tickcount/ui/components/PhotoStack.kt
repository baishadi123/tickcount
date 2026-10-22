package io.github.zzpby.tickcount.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * A pile of photos, the way WeChat stacks them: the top one square to the reader,
 * the ones behind it peeking out turned a little and slid a little, like prints
 * dropped on a table — a white frame and a soft shadow on each, so the pile reads
 * as objects rather than as one collage.
 *
 * At most four layers are drawn; a deeper pile shows nothing new for its extra
 * layers, and the count badge says how many are really underneath.
 */
@Composable
fun PhotoStack(
    photos: List<Bitmap>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (photos.isEmpty()) return

    // The back of the pile first, so the front lands on top in the Box.
    val visible = photos.takeLast(VISIBLE_LAYERS).asReversed()

    Box(
        modifier = modifier
            .size(width = 216.dp, height = 156.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        visible.forEachIndexed { index, photo ->
            // Depth zero is the print on top; the turns and slides grow with depth
            // in a fixed pattern, so the same photo always sits the same way.
            val depth = visible.size - 1 - index
            val (turn, slideX, slideY) = when (depth) {
                0 -> Triple(0f, 0f, 0f)
                1 -> Triple(-7f, 12f, -6f)
                2 -> Triple(5f, -14f, 8f)
                else -> Triple(-3f, 8f, 12f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = slideX.dp, y = slideY.dp)
                    .graphicsLayer {
                        rotationZ = turn
                        shadowElevation = (depth + 1) * 4.dp.toPx()
                        shape = RoundedCornerShape(3.dp)
                        clip = false
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .padding(4.dp),
            ) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        if (photos.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 12.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = photos.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/** How many layers of the pile are drawn; the rest only add to the badge. */
private const val VISIBLE_LAYERS = 4
