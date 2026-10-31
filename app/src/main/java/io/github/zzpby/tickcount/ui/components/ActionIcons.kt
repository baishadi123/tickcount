package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A check mark drawn as one stroke: the short arm rising into the long one,
 * joined round at the elbow — the affirmative every icon set draws, drawn the
 * way a pen draws it, in one move.
 */
@Composable
fun CheckIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 2.2.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()

        val mark = Path().apply {
            moveTo(w * 0.16f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.78f)
            lineTo(w * 0.86f, h * 0.20f)
        }
        drawPath(
            path = mark,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * A cross drawn as two bowed strokes, each curving gently inward the way a
 * hand crosses something out — softer than two planks corner to corner, and
 * of a piece with the handwriting the app sets its words in.
 */
@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 2.2.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()
        val inset = w * 0.20f

        // Down-right, bowing a little above the diagonal.
        val downRight = Path().apply {
            moveTo(inset, inset)
            quadraticTo(w * 0.58f, h * 0.42f, w - inset, h - inset)
        }
        drawPath(downRight, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Down-left, bowing a little above its own diagonal.
        val downLeft = Path().apply {
            moveTo(w - inset, inset)
            quadraticTo(w * 0.42f, h * 0.42f, inset, h - inset)
        }
        drawPath(downLeft, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/**
 * An undo arrow: most of a circle with an arrowhead where it begins — the
 * taking-back of a tick, hand-drawn like the other icons here.
 */
@Composable
fun ReopenIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()
        val inset = w * 0.16f

        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(w - inset * 2f, h - inset * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )

        // The arrowhead, where the arc begins: two short strokes fanned back
        // from the point, the way every undo arrow wears it.
        val cx = w / 2f
        val cy = h / 2f
        val r = (w - inset * 2f) / 2f
        val rad = Math.toRadians(140.0)
        val px = cx + r * kotlin.math.cos(rad).toFloat()
        val py = cy + r * kotlin.math.sin(rad).toFloat()
        val tangent = (140f + 90f) * (Math.PI.toFloat() / 180f)
        for (spread in listOf(-32f, 32f)) {
            val a = tangent + spread * (Math.PI.toFloat() / 180f)
            drawLine(
                color = color,
                start = Offset(px, py),
                end = Offset(px + kotlin.math.cos(a) * w * 0.20f, py + kotlin.math.sin(a) * w * 0.20f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A waste bin: the body a rounded rectangle, the lid a line with a small
 * handle above it — the delete mark every icon set draws, hand-drawn like the
 * other icons here.
 */
@Composable
fun TrashIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 1.8.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()

        // The lid: a line across, with the handle riding above it.
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.30f),
            end = Offset(w * 0.78f, h * 0.30f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.30f),
            end = Offset(w * 0.42f, h * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.58f, h * 0.30f),
            end = Offset(w * 0.58f, h * 0.18f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // The body: a rectangle open at the top, slightly tapered.
        val body = Path().apply {
            moveTo(w * 0.28f, h * 0.30f)
            lineTo(w * 0.32f, h * 0.86f)
            lineTo(w * 0.68f, h * 0.86f)
            lineTo(w * 0.72f, h * 0.30f)
        }
        drawPath(body, color, style = Stroke(stroke))

        // Two ribs down the body, the bin's own lines.
        drawLine(
            color = color,
            start = Offset(w * 0.44f, h * 0.42f),
            end = Offset(w * 0.455f, h * 0.74f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.56f, h * 0.42f),
            end = Offset(w * 0.545f, h * 0.74f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A folded map drawn as one outline: three panels in a gentle zigzag, the
 * fold lines running through them — the map as a paper object rather than a
 * globe, the way a journey is folded into a pocket.
 */
@Composable
fun MapIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 2.0.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()

        // Three panels zigzagging down: left up, middle down, right up.
        val outline = Path().apply {
            moveTo(w * 0.10f, h * 0.14f)
            lineTo(w * 0.38f, h * 0.05f)
            lineTo(w * 0.62f, h * 0.14f)
            lineTo(w * 0.90f, h * 0.05f)
            lineTo(w * 0.90f, h * 0.86f)
            lineTo(w * 0.62f, h * 0.95f)
            lineTo(w * 0.38f, h * 0.86f)
            lineTo(w * 0.10f, h * 0.95f)
            close()
        }
        drawPath(
            outline,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // The two folds.
        drawLine(
            color,
            Offset(w * 0.38f, h * 0.05f),
            Offset(w * 0.38f, h * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * 0.62f, h * 0.14f),
            Offset(w * 0.62f, h * 0.95f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A little globe: a round world with its meridian and equator drawn as a
 * hand would sketch it, for the control that carries the map back out to
 * the whole country.
 */
@Composable
fun GlobeIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 1.7.dp,
) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = strokeWidth.toPx()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.38f

        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )
        // The meridian: an ellipse a little narrower than the world.
        drawOval(
            color = color,
            topLeft = Offset(cx - radius * 0.46f, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 0.92f, radius * 2f),
            style = Stroke(width = stroke),
        )
        // The equator, bowed very slightly so it reads as a drawn line.
        val equator = Path().apply {
            moveTo(cx - radius, cy + radius * 0.06f)
            quadraticTo(cx, cy + radius * 0.20f, cx + radius, cy + radius * 0.06f)
        }
        drawPath(equator, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
