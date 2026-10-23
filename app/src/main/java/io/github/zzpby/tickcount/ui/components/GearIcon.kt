package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A gear, drawn rather than taken from a library: the app's icons are its own
 * lines, and a gear is eight strokes and two circles.
 */
@Composable
fun GearIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF5A554B),
) {
    Canvas(modifier = modifier.size(22.dp, 22.dp)) {
        val c = center
        val r = size.minDimension * 0.30f
        val stroke = size.minDimension * 0.11f

        // The body and the hub, as rings rather than fills: lighter than a solid
        // gear at the corner of a panel.
        drawCircle(tint, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(
            tint,
            radius = r * 0.42f,
            center = c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 0.75f),
        )

        // Eight teeth, radiating: short strokes with rounded ends, long enough
        // to read as teeth and short enough to stay quiet.
        repeat(8) { i ->
            val angle = Math.PI * 2 * i / 8 - Math.PI / 2
            val from = Offset(
                (c.x + cos(angle) * r * 0.95f).toFloat(),
                (c.y + sin(angle) * r * 0.95f).toFloat(),
            )
            val to = Offset(
                (c.x + cos(angle) * r * 1.38f).toFloat(),
                (c.y + sin(angle) * r * 1.38f).toFloat(),
            )
            drawLine(tint, from, to, stroke, StrokeCap.Round)
        }
    }
}
