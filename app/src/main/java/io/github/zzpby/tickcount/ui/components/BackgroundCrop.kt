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
 * the editor's manual adjustment can move, enlarge and shrink.
 *
 * With [zoom] at its default and the focus at its centre this is exactly the crop
 * the plain cover used to give. Above it [zoom] multiplies that cover; below it
 * the picture shrinks until it sits centred on the box, a print on the wall
 * rather than the wall itself. In both ranges the point at [focusX] [focusY] of
 * the picture is what the box centres on — clamped so the picture never pulls
 * away from an edge while it covers it, and held centred on the axis where it
 * no longer does.
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
        val scale = cover * zoom.coerceIn(
            CountdownEvent.MIN_BACKGROUND_ZOOM,
            CountdownEvent.MAX_BACKGROUND_ZOOM,
        )
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale

        // An axis the picture still spans pans to keep the chosen point
        // centred, clamped to its edges; an axis it no longer spans centres
        // the picture outright, because there is no edge left to hold.
        val left: Float
        val top: Float
        if (drawnWidth <= size.width) {
            left = (size.width - drawnWidth) / 2f
        } else {
            left = (size.width / 2f - focusX.coerceIn(0f, 1f) * drawnWidth)
                .coerceIn(size.width - drawnWidth, 0f)
        }
        if (drawnHeight <= size.height) {
            top = (size.height - drawnHeight) / 2f
        } else {
            top = (size.height / 2f - focusY.coerceIn(0f, 1f) * drawnHeight)
                .coerceIn(size.height - drawnHeight, 0f)
        }

        drawImage(
            image = image,
            dstOffset = IntOffset(floor(left).toInt(), floor(top).toInt()),
            dstSize = IntSize(ceil(drawnWidth).toInt(), ceil(drawnHeight).toInt()),
        )
    }
}
