package io.github.zzpby.tickcount.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The weight a brush carries along a stroke: nothing at either end, most of
 * it a little past the middle. The sine is clamped before the root because
 * sin(π·x), in floats, arrives a hair below zero — and a negative base under a
 * fractional power is NaN, which would void the whole path.
 */
internal fun brushWeight(t: Float): Float =
    sin(Math.PI.toFloat() * t).coerceAtLeast(0f).pow(0.55f)

/**
 * Lays a stroke the way a brush lays one: the centre line offset by half the
 * weight on either side, so the ribbon swells through the middle and dies at
 * the ends instead of stopping square.
 */
internal fun DrawScope.brushStroke(
    points: List<Offset>,
    widths: FloatArray,
    color: Color,
    blendMode: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
) {
    val count = points.size
    if (count < 2) return
    val normals = ArrayList<Offset>(count)
    for (index in 0 until count) {
        val before = points[(index - 1).coerceAtLeast(0)]
        val after = points[(index + 1).coerceAtMost(count - 1)]
        var dx = after.x - before.x
        var dy = after.y - before.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).takeIf { it > 0.0001f } ?: 1f
        dx /= length
        dy /= length
        normals += Offset(-dy, dx)
    }
    val path = Path()
    path.moveTo(
        points[0].x + normals[0].x * widths[0],
        points[0].y + normals[0].y * widths[0],
    )
    for (index in 1 until count) {
        path.lineTo(
            points[index].x + normals[index].x * widths[index],
            points[index].y + normals[index].y * widths[index],
        )
    }
    for (index in count - 1 downTo 0) {
        path.lineTo(
            points[index].x - normals[index].x * widths[index],
            points[index].y - normals[index].y * widths[index],
        )
    }
    path.close()
    drawPath(path, color, blendMode = blendMode)
}

/**
 * An album's mark, in the same hand as the tape wall's keys: one stroke of
 * hills — a high summit left of centre, a lower shoulder right of it, the
 * stroke leaving the ground thin and landing thin — with the sun a filled
 * dot above the pass. The photograph's own ancient symbol, written by brush.
 */
@Composable
fun AlbumMark(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val side = this.size.width

        // The hills: from the left ground to the right, through a tall peak
        // and a lower one — a quadratic through each leg, sampled as points
        // so the brush can weight the whole climb and fall as one stroke.
        val anchors = listOf(
            Offset(0.08f, 0.72f),
            Offset(0.34f, 0.30f),
            Offset(0.52f, 0.52f),
            Offset(0.70f, 0.40f),
            Offset(0.92f, 0.72f),
        )
        val points = ArrayList<Offset>(141)
        for (leg in 0 until anchors.size - 1) {
            val a = anchors[leg]
            val b = anchors[leg + 1]
            // The control point lifts the leg above the straight line, so
            // the ridge swells like a hill rather than bending like a wire.
            val control = Offset((a.x + b.x) / 2f, kotlin.math.min(a.y, b.y) - 0.10f)
            val steps = 35
            for (step in 0 until steps) {
                val t = step / steps.toFloat()
                val u = 1f - t
                points += Offset(
                    side * (u * u * a.x + 2f * u * t * control.x + t * t * b.x),
                    side * (u * u * a.y + 2f * u * t * control.y + t * t * b.y),
                )
            }
        }
        points += Offset(side * anchors.last().x, side * anchors.last().y)
        val widths = FloatArray(points.size) { index ->
            side * 0.075f * brushWeight(index / (points.size - 1f))
        }
        brushStroke(points, widths, color)

        // The sun: a filled dot above the pass, the same seed the record
        // mark's spiral winds down to.
        drawCircle(color, radius = side * 0.075f, center = Offset(side * 0.72f, side * 0.24f))
    }
}

/**
 * A padlock in the letters' own hand, drawn in the two postures a letter
 * knows. Closed, the shackle sits in the body and the seal is sound — the
 * mark of a capsule still waiting out its days. Open, the shackle swings up
 * off one foot and hangs in the air over the wax — unsealed, and one press
 * from sealing again. The body in both is a stamp of wax; the keyhole is
 * punched clean through, so whatever the page is made of shows inside it.
 */
@Composable
fun BrushLock(color: Color, size: Dp = 17.dp, open: Boolean = false) {
    Canvas(modifier = Modifier.size(size)) {
        val side = this.size.width

        // The body first, so the shackle's feet land inside it: a stamp of
        // wax, rounded like a seal rather than cut like a box.
        drawRoundRect(
            color = color,
            topLeft = Offset(side * 0.20f, side * 0.46f),
            size = androidx.compose.ui.geometry.Size(side * 0.60f, side * 0.40f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.12f),
        )

        // The shackle: one stroke over the top, thin where it leaves the
        // body, full through the crown, thin where it comes home. Closed, it
        // arches between the body's two shoulders. Open, the whole arch has
        // swung up off the right foot — pivoted about the left — and hangs,
        // waiting to be pressed back down.
        val cx = side * 0.5f
        val cy = side * 0.46f
        val r = side * 0.20f
        val count = 60
        val points = ArrayList<Offset>(count)
        val widths = FloatArray(count)
        val pivot = Offset(cx - r, cy)
        val swing = if (open) -Math.PI.toFloat() * 0.32f else 0f
        val cosS = kotlin.math.cos(swing)
        val sinS = kotlin.math.sin(swing)
        for (index in 0 until count) {
            val t = index / (count - 1f)
            val angle = Math.PI.toFloat() * (1f - t)
            val px = cx + r * kotlin.math.cos(angle)
            val py = cy - r * kotlin.math.sin(angle)
            val swung = if (open) {
                val dx = px - pivot.x
                val dy = py - pivot.y
                Offset(pivot.x + dx * cosS - dy * sinS, pivot.y + dx * sinS + dy * cosS)
            } else {
                Offset(px, py)
            }
            points += swung
            widths[index] = side * 0.075f * brushWeight(t)
        }
        brushStroke(points, widths, color)

        // The keyhole: a hole punched clean through the wax, so whatever the
        // page is made of shows inside it.
        drawCircle(
            color = Color.Black,
            radius = side * 0.065f,
            center = Offset(cx, side * 0.63f),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
        )
    }
}
/**
 * The wax seal a letter is closed with, in the states a letter knows — drawn
 * large, the way a seal on an envelope is read at a glance, and drawn as
 * glass allows: no plate of colour sitting on the pane, but the seal's own
 * rim and knot in the theme's ink, the wax itself all but transparent — a
 * seal pressed onto glass rather than poured over it.
 *
 * [notch] is how far the seal has been prized open, 0 to 1: at 0 the rim is
 * closed all the way round — nothing has been at this wax, and the letter
 * keeps its whole circle. As notch rises, the gap in the upper right widens,
 * its two lips tapering where the wax gives — the letter was read through
 * here. The knot the seal carries stays whole throughout: one of eight from
 * the Chinese repertoire, chosen by the letter's own id, so every letter on
 * the shelf has its own device the way real seals were personal.
 */
@Composable
fun WaxSeal(color: Color, variant: Int = 0, notch: Float = 0f, size: Dp = 96.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val side = this.size.width
        val cx = side / 2f
        val cy = side / 2f
        val r = side * 0.47f

        fun ringAt(center: Offset, radius: Float, width: Float, gapAngle: Float = 56f, phase: Float = 24f) {
            val count = 64
            val points = ArrayList<Offset>(count)
            val widths = FloatArray(count)
            for (index in 0 until count) {
                val t = index / (count - 1f)
                val angle = phase + gapAngle / 2f + t * (360f - gapAngle)
                val rad = Math.toRadians(angle.toDouble())
                points += Offset(
                    center.x + radius * kotlin.math.cos(rad).toFloat(),
                    center.y + radius * kotlin.math.sin(rad).toFloat(),
                )
                widths[index] = width * brushWeight(t)
            }
            brushStroke(points, widths, color)
        }

        /** A polygon in one stroke — a knot's cord, leaving one gap. */
        fun polyAt(corners: List<Offset>, width: Float) {
            val per = 14
            val points = ArrayList<Offset>(per * corners.size)
            val widths = FloatArray(per * corners.size)
            var written = 0
            for (edge in corners.indices) {
                val a = corners[edge]
                val b = corners[(edge + 1) % corners.size]
                for (step in 0 until per) {
                    val t = step / per.toFloat()
                    points += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    written++
                }
            }
            for (index in 0 until written) {
                widths[index] = width * brushWeight(index / (written - 1f))
            }
            brushStroke(points, widths, color)
        }

        /**
         * The knot itself. Eight devices from the Chinese book of knots and
         * seals: the double coin (two rings, one each), the panchang (nested
         * lozenges with an ear at every corner), the plum blossom (five
         * petals round a seed), the eight-horn star (two squares turned a
         * half-corner apart), the concentric (three rings on one centre),
         * the four-fold ruyi (a ring at each compass point round a seed),
         * the six-horn (two triangles, sixty degrees apart), and the trio
         * (three rings set as 品 — one above, two below).
         */
        fun knotAt(center: Offset) {
            val w = side * 0.026f
            when (((variant % 8) + 8) % 8) {
                0 -> {
                    // The double coin: two rings interlocked, one each.
                    ringAt(Offset(center.x - side * 0.115f, center.y), side * 0.175f, w, phase = 24f)
                    ringAt(Offset(center.x + side * 0.115f, center.y), side * 0.175f, w, phase = 204f)
                }
                1 -> {
                    // The panchang: three lozenges nested on one centre, an
                    // ear-ring at each of the four corners.
                    for ((i, reach) in listOf(0.30f, 0.21f, 0.115f).withIndex()) {
                        val pts = listOf(
                            Offset(center.x, center.y - side * reach),
                            Offset(center.x + side * reach, center.y),
                            Offset(center.x, center.y + side * reach),
                            Offset(center.x - side * reach, center.y),
                        )
                        polyAt(pts, w * (1f - i * 0.15f))
                    }
                    for (corner in listOf(45f, 135f, 225f, 315f)) {
                        val rad = Math.toRadians(corner.toDouble())
                        ringAt(
                            Offset(
                                center.x + side * 0.30f * kotlin.math.cos(rad).toFloat(),
                                center.y + side * 0.30f * kotlin.math.sin(rad).toFloat(),
                            ),
                            side * 0.055f,
                            w * 0.8f,
                            gapAngle = 70f,
                            phase = corner + 90f,
                        )
                    }
                }
                2 -> {
                    // The plum blossom: five petals round a seed.
                    for (petal in 0 until 5) {
                        val rad = Math.toRadians((90f + petal * 72f).toDouble())
                        ringAt(
                            Offset(
                                center.x + side * 0.155f * kotlin.math.cos(rad).toFloat(),
                                center.y - side * 0.155f * kotlin.math.sin(rad).toFloat(),
                            ),
                            side * 0.115f,
                            w * 0.85f,
                            gapAngle = 64f,
                            phase = petal * 72f + 40f,
                        )
                    }
                    drawCircle(color, radius = side * 0.04f, center = center)
                }
                3 -> {
                    // The eight-horn star: two squares, a half-corner apart.
                    for (turn in listOf(0f, 45f)) {
                        val pts = (0 until 4).map { i ->
                            val rad = Math.toRadians((45f + 90f * i + turn).toDouble())
                            Offset(
                                center.x + side * 0.235f * kotlin.math.cos(rad).toFloat(),
                                center.y + side * 0.235f * kotlin.math.sin(rad).toFloat(),
                            )
                        }
                        polyAt(pts, w)
                    }
                    drawCircle(color, radius = side * 0.035f, center = center)
                }
                4 -> {
                    // The concentric: three rings on one centre, the seal at
                    // its simplest — a drop of wax pressed with the plain die.
                    ringAt(center, side * 0.27f, w)
                    ringAt(center, side * 0.175f, w * 0.85f, phase = 150f)
                    ringAt(center, side * 0.085f, w * 0.7f, phase = 260f)
                }
                5 -> {
                    // The four-fold ruyi: a cloud-curl ring at each point of
                    // the compass, and a seed in the middle.
                    for (point in listOf(0f, 90f, 180f, 270f)) {
                        val rad = Math.toRadians(point.toDouble())
                        ringAt(
                            Offset(
                                center.x + side * 0.175f * kotlin.math.cos(rad).toFloat(),
                                center.y + side * 0.175f * kotlin.math.sin(rad).toFloat(),
                            ),
                            side * 0.125f,
                            w * 0.85f,
                            gapAngle = 66f,
                            phase = point + 50f,
                        )
                    }
                    drawCircle(color, radius = side * 0.035f, center = center)
                }
                6 -> {
                    // The six-horn: two triangles, sixty degrees apart.
                    for (turn in listOf(90f, 270f)) {
                        val pts = (0 until 3).map { i ->
                            val rad = Math.toRadians((turn + 120f * i).toDouble())
                            Offset(
                                center.x + side * 0.25f * kotlin.math.cos(rad).toFloat(),
                                center.y + side * 0.25f * kotlin.math.sin(rad).toFloat(),
                            )
                        }
                        polyAt(pts, w)
                    }
                    drawCircle(color, radius = side * 0.035f, center = center)
                }
                else -> {
                    // The trio: three rings set as 品 — one above, two below —
                    // three wishes, or three words, held in one seal.
                    ringAt(Offset(center.x, center.y - side * 0.13f), side * 0.125f, w * 0.9f, phase = 24f)
                    ringAt(Offset(center.x - side * 0.14f, center.y + side * 0.12f), side * 0.125f, w * 0.9f, phase = 150f)
                    ringAt(Offset(center.x + side * 0.14f, center.y + side * 0.12f), side * 0.125f, w * 0.9f, phase = 270f)
                }
            }
        }

        if (notch <= 0.01f) {
            // The sealed rim: closed all the way round, one even stroke whose
            // ends meet — nothing has been at this wax, and the letter keeps
            // its whole circle.
            val count = 96
            val points = ArrayList<Offset>(count + 1)
            val widths = FloatArray(count + 1)
            for (index in 0..count) {
                val t = index / count.toFloat()
                val rad = Math.toRadians((t * 360f).toDouble())
                points += Offset(
                    cx + r * kotlin.math.cos(rad).toFloat(),
                    cy + r * kotlin.math.sin(rad).toFloat(),
                )
                widths[index] = side * 0.030f
            }
            brushStroke(points, widths, color)
            knotAt(Offset(cx, cy))
        } else {
            // The prized rim: the gap in the upper right, widening with the
            // notch, its two lips tapering where the wax gave.
            ringAt(Offset(cx, cy), r, side * 0.030f, gapAngle = 70f * notch, phase = 315f)
            knotAt(Offset(cx, cy))
        }
    }
}
