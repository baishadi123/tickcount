package io.github.zzpby.tickcount.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Shadow
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
 * The rest of the pane lets the app's background picture through, which is
 * what makes the cards read as glass laid on the picture rather than paint
 * laid over it — frosted by translucency, since the toolkit cannot blur what
 * sits behind a card. The hairline each card already carries is the pane's
 * edge.
 */
const val GlassAlpha = 0.80f

/**
 * The one-pixel outline a card carries.
 *
 * A function rather than a constant so it follows the scheme, and so a card can pass a
 * different colour when it wants to — the accent for something selected, say.
 */
@Composable
fun Modifier.hairline(
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outlineVariant,
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
