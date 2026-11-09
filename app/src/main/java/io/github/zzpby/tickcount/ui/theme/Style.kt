package io.github.zzpby.tickcount.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R

/**
 * The brush the two names are written with: 华文行楷, bundled with the app —
 * Android does not ship it, and a downloaded font would not follow the backup.
 */
val XingKai = FontFamily(Font(R.font.xingkai))

/**
 * 华文新魏, bundled the same way: the letter's own hand — the reading face of a
 * capsule, where XingKai is the page's name written large.
 */
val XinWei = FontFamily(Font(R.font.xinwei))

/**
 * The shapes the app is cut from, in one place.
 *
 * Generous radii and a hairline outline rather than a shadow: a card that is white on a warm
 * page already reads as raised, and a shadow on top of that reads as a decoration. The outline
 * is one pixel and the same colour everywhere, which is what makes a screen of cards look like
 * one system instead of several.
 */
val CardShape = RoundedCornerShape(26.dp)
val PanelShape = RoundedCornerShape(30.dp)
val FieldShape = RoundedCornerShape(18.dp)
val PillShape = RoundedCornerShape(50)

/** The horizontal margin every screen shares, so their contents line up down the page. */
val Gutter = 20.dp

/** The gap between two cards in a list. */
val CardGap = 10.dp

/**
 * How much of a card's own surface colour is actually drawn.
 *
 * The HyperOS reading of glass: a high-transparency white that lets the
 * picture behind show through clear and unblurred, held together by a fine
 * bright edge — not milk over the picture. Text stays readable because the
 * pane keeps the page light wherever it lands.
 */
const val GlassAlpha = 0.52f

/**
 * The edge a pane of glass carries: a line of light, not a grey outline.
 *
 * A grey hairline over a milky fill was what made the first glass read as
 * dirty; this is the pane catching the light instead, in both light and dark.
 */
@Composable
fun glassEdge() = if (isDarkScheme()) {
    Color.White.copy(alpha = 0.35f)
} else {
    Color.White.copy(alpha = 0.85f)
}

/**
 * The colour of a pane of glass itself: cool. A whisper of blue in the
 * white — not enough to read as blue, enough to stop a warm photograph
 * behind it from turning the pane yellow; cold glass is what reads as
 * premium, warm glass as plastic. Over a dark page the pane is near-black
 * and stays neutral, where the picture already does the darkening.
 */
@Composable
fun glassPane(alpha: Float = GlassAlpha): Color =
    if (isDarkScheme()) {
        // Dark panes can afford to be thin: the picture already darkens, and
        // thin dark glass over a bright photograph is where the look shines.
        Color(0xFF141414).copy(alpha = alpha)
    } else {
        // Light panes are bright paper, not thin glass: white over a
        // photograph smears the picture's colours through everything, and
        // the light reading of glass is crispness, not translucency. Pure
        // white — the cool cast belongs to the reflection layer alone, a
        // blue fill under seven layers of light reads as a blue card.
        Color.White.copy(alpha = (alpha + 0.36f).coerceAtMost(0.92f))
    }

/**
 * One theme's whole recipe for glass, in one place — the guide's own token
 * structure, so light and dark are two named sets of decisions rather than
 * dozens of scattered numbers. Light is the §8 reading: glass on a light
 * page is told apart by its shadows, not its brightness.
 */
private class GlassTokens(
    val bodyStops: List<Pair<Float, Color>>,
    val glowAlpha: Float,
    val glowRadius: Float,
    val specAlpha: Float,
    val specRadius: Float,
    val bottomReflect: (photoTint: Color) -> Color,
    val vignetteAlpha: Float,
    val vignetteStart: Float,
    val noiseDark: Boolean,
    val noiseAlpha: Int,
    val rimStops: List<Pair<Float, Color>>,
    val topLineAlpha: Float,
    val elevation: Dp,
    val ambient: Color,
    val spot: Color,
    val edgeLine: Color,
)

private val DarkGlass = GlassTokens(
    bodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.175f),
        0.52f to Color.White.copy(alpha = 0.045f),
        1.00f to Color.White.copy(alpha = 0.105f),
    ),
    glowAlpha = 0.30f, glowRadius = 0.88f,
    specAlpha = 0.34f, specRadius = 0.16f,
    bottomReflect = { tint -> tint.copy(alpha = 0.10f) },
    vignetteAlpha = 0.17f, vignetteStart = 0.58f,
    noiseDark = false, noiseAlpha = 0x10,
    rimStops = listOf(
        0.00f to Color.White.copy(alpha = 0.82f),
        0.34f to Color.White.copy(alpha = 0.10f),
        0.63f to Color.White.copy(alpha = 0.03f),
        1.00f to Color.White.copy(alpha = 0.52f),
    ),
    topLineAlpha = 0.55f,
    elevation = 26.dp,
    ambient = Color(0xFF241B4D).copy(alpha = 0.90f),
    spot = Color(0xFF0A0820),
    edgeLine = Color.Transparent,
)

private val LightGlass = GlassTokens(
    // Brighter than the page by a step, the middle stop carrying a touch of
    // cold so the pane never reads as dead white.
    bodyStops = listOf(
        0.00f to Color.White.copy(alpha = 0.80f),
        0.52f to Color(0xFFEFF3FA).copy(alpha = 0.55f),
        1.00f to Color.White.copy(alpha = 0.72f),
    ),
    glowAlpha = 0.55f, glowRadius = 0.72f,
    specAlpha = 0.85f, specRadius = 0.11f,
    bottomReflect = { _ -> Color.White.copy(alpha = 0.30f) },
    vignetteAlpha = 0.05f, vignetteStart = 0.74f,
    noiseDark = true, noiseAlpha = 0x0C,
    rimStops = listOf(
        0.00f to Color.White.copy(alpha = 1.00f),
        0.35f to Color.White.copy(alpha = 0.45f),
        0.65f to Color.White.copy(alpha = 0.55f),
        1.00f to Color.White.copy(alpha = 0.95f),
    ),
    topLineAlpha = 1.00f,
    elevation = 18.dp,
    ambient = Color(0xFF64748B).copy(alpha = 0.18f),
    spot = Color(0xFF0F172A).copy(alpha = 0.10f),
    // The light page's one new line: the dark hairline along the bottom,
    // the shadow side of the pane, where it stands on the page.
    edgeLine = Color(0xFF64748B).copy(alpha = 0.14f),
)

@Composable
private fun glassTokens(): GlassTokens = if (isDarkScheme()) DarkGlass else LightGlass

/** The grain, minted once per kind: white for dark panes, dark for light. */
private fun glassGrain(darkGrain: Boolean, alphaByte: Int): Brush {
    val n = 128
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = kotlin.random.Random(7)
    val pixels = IntArray(n * n) {
        val v = if (darkGrain) rnd.nextInt(0, 66) else rnd.nextInt(190, 256)
        (alphaByte shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(pixels, 0, n, 0, 0, n, n)
    return ShaderBrush(
        ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated),
    )
}

private val DarkGrain by lazy { glassGrain(darkGrain = false, alphaByte = 0x10) }
private val DarkGrainFaint by lazy { glassGrain(darkGrain = false, alphaByte = 0x08) }
private val LightGrain by lazy { glassGrain(darkGrain = true, alphaByte = 0x0C) }
private val LightGrainFaint by lazy { glassGrain(darkGrain = true, alphaByte = 0x06) }

/** Where the specular light sits before a finger has touched the pane. */
private val SpecDefault = Offset(0.22f, -0.02f)

/**
 * The hue of the light that bounces off the floor of a pane: the picture's
 * own dominant colour, sampled where the picture is set up — a warm picture
 * throws warm light back, a cold one cold. Null until a picture exists; the
 * fallback is the guide's own blue-violet.
 */
val LocalGlassTint = androidx.compose.runtime.compositionLocalOf<Color?> { null }

/**
 * The seven layers of light a pane of glass is made of — thickness, the
 * great soft light and the sharp glint it carries, the reflection along the
 * bottom in the picture's own colour, the vignette that grounds it, the
 * grain, and the directional rim where light enters and leaves the glass.
 *
 * The specular lights follow the finger: the position is written by a
 * pointer observer and read only inside the draw, so a moving light costs
 * a redraw and never a recomposition. The rim is drawn in its own pass,
 * after everything soft, because a rim under the lights is no rim at all.
 *
 * Over a frosted pane ([stackedWithBlur]) the fill alphas step down and the
 * grain thins to a whisper — the blur has already laid a floor of colour,
 * and doubling it reads as haze.
 */
fun Modifier.glassLayers(
    cornerRadius: Dp = 26.dp,
    trackSpecular: Boolean = true,
    stackedWithBlur: Boolean = false,
): Modifier = composed {
    val t = glassTokens()
    val dark = isDarkScheme()
    var spec by remember { mutableStateOf(SpecDefault) }
    val photoTint = LocalGlassTint.current ?: Color(0xFF96A0FF)
    val step = if (stackedWithBlur) 0.03f else 0f
    val grain = when {
        dark && stackedWithBlur -> DarkGrainFaint
        dark -> DarkGrain
        stackedWithBlur -> LightGrainFaint
        else -> LightGrain
    }

    this
        .then(
            if (trackSpecular) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            if (size.width > 0 && size.height > 0) {
                                spec = Offset(
                                    (pos.x / size.width).coerceIn(0f, 1f),
                                    (pos.y / size.height).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
            } else {
                Modifier
            }
        )
        .drawWithCache {
            val body = Brush.linearGradient(
                colorStops = t.bodyStops
                    .map { (at, c) -> at to c.copy(alpha = (c.alpha - step).coerceAtLeast(0f)) }
                    .toTypedArray(),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val reflection = Brush.verticalGradient(
                0.72f to Color.Transparent,
                1.00f to t.bottomReflect(photoTint).copy(
                    alpha = (t.bottomReflect(photoTint).alpha - step).coerceAtLeast(0f),
                ),
            )
            val vignette = Brush.verticalGradient(
                t.vignetteStart to Color.Transparent,
                1.00f to Color.Black.copy(alpha = t.vignetteAlpha - step / 2),
            )
            onDrawWithContent {
                val w = size.width
                val h = size.height

                // ① Thickness: the body itself, diagonal, three stops, the
                // middle one a valley — glass is thinnest where the most
                // light passes through.
                drawRect(body)

                // ②③ The great soft light, and ④ the glint: both ride the
                // finger, one the room's light, one the point of it.
                val light = Offset(w * spec.x, h * spec.y)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (t.glowAlpha - step).coerceAtLeast(0f)),
                            Color.Transparent,
                        ),
                        center = light,
                        radius = w * t.glowRadius,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = t.specAlpha),
                            Color.Transparent,
                        ),
                        center = light,
                        radius = w * t.specRadius,
                    ),
                    radius = w * t.specRadius,
                    center = light,
                )

                // The bounce along the bottom, and the vignette that keeps
                // the pane standing on the page.
                drawRect(reflection)
                drawRect(vignette)

                // ⑥ The grain.
                drawRect(grain)

                drawContent()
            }
        }
        // ⑦ The directional rim, drawn after everything soft so it stays
        // sharp: bright where light enters, dark across the far face, bright
        // again at the corner where it leaves — glass has two surfaces.
        .drawWithCache {
            val rimPx = 1.dp.toPx()
            val cornerPx = cornerRadius.toPx()
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colorStops = t.rimStops.toTypedArray(),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    topLeft = Offset(rimPx * 0.5f, rimPx * 0.5f),
                    size = Size(size.width - rimPx, size.height - rimPx),
                    cornerRadius = CornerRadius((cornerPx - rimPx * 0.5f).coerceAtLeast(0f)),
                    style = Stroke(width = rimPx),
                )
                // The top edge, where the light first lands.
                drawLine(
                    color = Color.White.copy(alpha = t.topLineAlpha),
                    start = Offset(cornerPx * 0.9f, rimPx * 1.6f),
                    end = Offset(size.width - cornerPx * 0.9f, rimPx * 1.6f),
                    strokeWidth = rimPx,
                )
                // The light page's bottom hairline: the shadow side of the
                // pane, the line it stands on.
                if (t.edgeLine.alpha > 0f) {
                    drawLine(
                        color = t.edgeLine,
                        start = Offset(cornerPx * 0.8f, size.height - rimPx),
                        end = Offset(size.width - cornerPx * 0.8f, size.height - rimPx),
                        strokeWidth = rimPx,
                    )
                }
            }
        }
}

/**
 * A pane of glass without the frost — the recipe for everything that lives in
 * a list, where per-pane blur would take the frame rate with it: the fill,
 * the seven layers of light, held in shape by clip, lifted by the coloured
 * shadow, and sprung under the finger — pressed, a pane gives a hair and
 * comes back, the way a thing that exists does.
 *
 * A pane that is not a card — an editor sheet, say — passes interactive =
 * false: no spring, no glint chasing the finger across a form. Glass, but at
 * rest.
 */
fun Modifier.glassSurface(
    shape: Shape = CardShape,
    alpha: Float = GlassAlpha,
    interactive: Boolean = true,
): Modifier = composed {
    val t = glassTokens()
    val dark = isDarkScheme()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassPress",
    )
    this
        .then(
            if (interactive) {
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                Modifier
            }
        )
        .shadow(
            elevation = t.elevation,
            shape = shape,
            ambientColor = t.ambient,
            spotColor = t.spot,
        )
        .clip(shape)
        // The dark pane keeps its readable floor under the thin glass body;
        // the light body is bright enough to be its own floor.
        .then(
            if (dark) {
                Modifier.background(glassPane(alpha))
            } else {
                Modifier
            }
        )
        .glassLayers(
            cornerRadius = shapeRadiusOf(shape),
            trackSpecular = interactive,
        )
        .then(
            if (interactive) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            pressed = event.changes.any { it.pressed }
                        }
                    }
                }
            } else {
                Modifier
            }
        )
}

/** The corner radius a pane's rim should follow, for the shapes the app cuts. */
private fun shapeRadiusOf(shape: Shape): Dp = when (shape) {
    CardShape -> 26.dp
    io.github.zzpby.tickcount.ui.theme.FieldShape -> 18.dp
    io.github.zzpby.tickcount.ui.theme.PillShape -> 50.dp
    else -> 20.dp
}

/**
 * The one-pixel outline a card carries.
 *
 * A function rather than a constant so it follows the scheme, and so a card can pass a
 * different colour when it wants to — the accent for something selected, say.
 */
@Composable
fun Modifier.hairline(
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    color: androidx.compose.ui.graphics.Color = glassEdge(),
): Modifier = border(width = 1.dp, color = color, shape = shape)

/**
 * The wash behind something that is highlighted rather than merely present — the batch
 * section in the editor, the picker's own preview.
 *
 * The accent's container at a partial alpha, so it reads as the accent's own tint on both a
 * light and a dark page rather than as a second colour.
 */
@Composable
fun tintWash() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)

/**
 * The halo every piece of naked text wears over the app's background picture:
 * a soft glow of the page's own colour, so dark ink stands off a dark
 * photograph and light ink off a light one — the page colour is already the
 * opposite tone of the page's own ink, which is exactly the pair needed.
 *
 * The voyage's star names have worn the fixed-colour original of this since
 * they were first drawn; this is that idea, following the theme.
 */
@Composable
fun textHalo(): Shadow = Shadow(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    blurRadius = 16f,
)
