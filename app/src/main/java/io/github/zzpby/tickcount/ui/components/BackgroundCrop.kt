package io.github.zzpby.tickcount.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.zzpby.tickcount.data.CountdownEvent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** The black wash over a background, so content reads on any picture. */
const val BackgroundScrimAlpha = 0.35f

/**
 * A background of the detail-screen kind: the picture drawn as a cover crop that
 * the editor's manual adjustment can move and enlarge.
 *
 * With [zoom] at its default and the focus at its centre this is exactly the crop
 * the plain cover used to give. Otherwise [zoom] multiplies that cover, and the
 * point at [focusX] [focusY] of the picture is what the box centres on — clamped
 * so the picture never pulls away from an edge, which is what lets the editor's
 * wide preview strip and the tall screen agree: both put the chosen point in
 * their own middle, and both keep every corner of the box covered.
 */
@Composable
fun BackgroundCrop(
    bitmap: Bitmap,
    zoom: Float,
    focusX: Float,
    focusY: Float,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Canvas(modifier) {
        // The draw can run a frame before the layout that sizes it — a canvas
        // measured at nothing, on the frame a replacement bitmap lands. Drawing
        // nothing for that one frame beats crashing on a reversed clamp.
        if (size.width <= 0f || size.height <= 0f || image.width <= 0 || image.height <= 0) {
            return@Canvas
        }

        val cover = max(size.width / image.width, size.height / image.height)
        val scale = cover * zoom.coerceAtLeast(CountdownEvent.DEFAULT_BACKGROUND_ZOOM)
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale

        var left = size.width / 2f - focusX.coerceIn(0f, 1f) * drawnWidth
        var top = size.height / 2f - focusY.coerceIn(0f, 1f) * drawnHeight
        // The upper ends are taken back to zero when the drawn picture measures
        // smaller than the box — a sub-pixel pass can see that — so the range
        // never turns empty and the picture is pinned to the edge instead.
        left = left.coerceIn((size.width - drawnWidth).coerceAtMost(0f), 0f)
        top = top.coerceIn((size.height - drawnHeight).coerceAtMost(0f), 0f)

        drawImage(
            image = image,
            dstOffset = IntOffset(floor(left).toInt(), floor(top).toInt()),
            dstSize = IntSize(ceil(drawnWidth).toInt(), ceil(drawnHeight).toInt()),
        )
    }
}
