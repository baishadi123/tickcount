package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A pencil: the body is a rounded rectangle turned forty-five degrees, the tip a
 * small triangle below it — the plain edit mark every icon set draws, hand-drawn
 * like the other icons here for the same reason: two shapes do not justify the
 * icon artifact.
 */
@Composable
fun EditIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()

        rotate(degrees = 45f, pivot = Offset(w / 2f, h / 2f)) {
            // The body: a rounded rectangle standing upright before the turn.
            val bodyWidth = w * 0.32f
            val bodyHeight = h * 0.46f
            val left = w / 2f - bodyWidth / 2f
            val top = h / 2f - bodyHeight / 2f - h * 0.08f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(stroke * 0.6f),
                style = Stroke(stroke),
            )

            // The tip: a triangle whose shoulders meet the body's bottom edge.
            val shoulder = top + bodyHeight + stroke * 0.9f
            val apex = shoulder + h * 0.13f
            val tip = Path().apply {
                moveTo(left - bodyWidth * 0.10f, shoulder)
                lineTo(left + bodyWidth * 1.10f, shoulder)
                lineTo(w / 2f, apex)
                close()
            }
            drawPath(tip, color, style = Stroke(stroke))
        }
    }
}
