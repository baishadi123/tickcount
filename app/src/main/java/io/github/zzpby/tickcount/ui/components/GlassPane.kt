package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.ui.theme.CardShape
import io.github.zzpby.tickcount.ui.theme.GlassAlpha
import io.github.zzpby.tickcount.ui.theme.glassPane
import io.github.zzpby.tickcount.ui.theme.isDarkScheme
import io.github.zzpby.tickcount.ui.theme.hairline
import kotlin.math.max

/**
 * The app's background picture, as a pane of glass sees it: the picture and
 * its framing, plus the size of the stage it was drawn on, so any pane can
 * reproduce the exact piece of the picture that sits behind itself.
 *
 * Null where no picture has been picked — a pane then falls back to plain
 * translucency, the look the app had before frosted glass existed.
 */
class GlassBackdrop(
    val image: ImageBitmap,
    val zoom: Float,
    val focusX: Float,
    val focusY: Float,
    val stageWidth: Float,
    val stageHeight: Float,
) {
    /**
     * Draws the picture, framed exactly as the stage behind the app draws it,
     * shifted so the pane at [panePos] sees its own piece — blurred by
     * whatever modifier wraps this drawing, which is the whole point.
     */
    fun DrawScope.drawBehindPane(panePos: Offset) {
        if (stageWidth <= 0f || stageHeight <= 0f) return
        val iw = image.width
        val ih = image.height
        if (iw <= 0 || ih <= 0) return

        val cover = max(stageWidth / iw, stageHeight / ih)
        val scale = cover * zoom.coerceIn(
            CountdownEvent.MIN_BACKGROUND_ZOOM,
            CountdownEvent.MAX_BACKGROUND_ZOOM,
        )
        val drawnW = iw * scale
        val drawnH = ih * scale

        val left: Float = if (drawnW <= stageWidth) {
            (stageWidth - drawnW) / 2f
        } else {
            (stageWidth / 2f - focusX.coerceIn(0f, 1f) * drawnW)
                .coerceIn(stageWidth - drawnW, 0f)
        }
        val top: Float = if (drawnH <= stageHeight) {
            (stageHeight - drawnH) / 2f
        } else {
            (stageHeight / 2f - focusY.coerceIn(0f, 1f) * drawnH)
                .coerceIn(stageHeight - drawnH, 0f)
        }

        drawImage(
            image = image,
            dstOffset = IntOffset(
                (left - panePos.x).toInt(),
                (top - panePos.y).toInt(),
            ),
            dstSize = IntSize(drawnW.toInt() + 1, drawnH.toInt() + 1),
        )
    }
}

/** What the panes see behind themselves; null until a picture is picked. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * A pane of frosted glass: the app's own picture, drawn again behind the
 * pane's bounds and blurred there, under the same cool scrim every pane
 * wears — the iOS reading, with the stage's picture staying sharp outside
 * the pane and going soft only through it.
 *
 * Below Android 12 the blur is silently nothing, and the pane falls back to
 * plain translucency — the frosted look is a garnish, never a dependency.
 * Where no picture is behind, the pane is simply translucent, as before.
 */
@Composable
fun GlassPane(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    alpha: Float = GlassAlpha,
    wash: Color? = null,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val highlightStrength = if (isDarkScheme()) 0.10f else 0.22f

    Box(
        modifier = modifier
            .clip(shape)
            .hairline(shape),
    ) {
        if (backdrop != null) {
            var panePos by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { panePos = it.positionInRoot() }
                    // Slightly overdrawn, so the blur's soft edge never shows
                    // as a darker band inside the pane's own edge.
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }
                    .blur(12.dp)
                    .drawBehind { with(backdrop) { drawBehindPane(panePos) } },
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glassPane(alpha))
                // The 135° top highlight, the sheen that separates glass from
                // a grey brick — over the scrim, under the content.
                .drawWithCache {
                    val highlight = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = highlightStrength),
                            0.45f to Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(highlight) }
                },
        )
        if (wash != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(wash),
            )
        }
        content()
    }
}
