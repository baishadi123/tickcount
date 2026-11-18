package io.github.zzpby.tickcount.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.zzpby.tickcount.data.AppSettings
import io.github.zzpby.tickcount.data.ThemePreset

/**
 * The palettes. Each preset is a world of its own — its own paper, its own ink, its own
 * accent — rather than one recipe re-tinted around the wheel. What they share is the
 * structure: a page the colour is unashamed of, cards that lift off it, and ink that
 * belongs to the same world instead of a grey that wandered in.
 *
 * White is the exception on purpose: it is the plain one, a white page with near-black
 * ink, for the days that call for quiet. Night is the same idea in the other direction —
 * a coffee-dark table with gold on it.
 */
private class Surfaces(
    val page: Color,
    val panel: Color,
    val card: Color,
    val onPage: Color,
    val onVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

// ---------------------------------------------------------------- the worlds

/**
 * Paper (Paper Talk): a warm cream page — cream is the resting state, not white — with
 * clay for the big numbers, gold paper for selection, and espresso for ink. Every colour
 * is earth: mustard, clay, coffee.
 */
private val PaperSurfaces = Surfaces(
    page = Color(0xFFF7F1E8),
    panel = Color(0xFFEFE7DA),
    card = Color(0xFFFFFFFF),
    onPage = Color(0xFF39221E),
    onVariant = Color(0xFF8A7565),
    outline = Color(0xFFE1DCD3),
    outlineVariant = Color(0xFFEBE5D8),
)

/** Garden: café cream and ceramic, four greens deep, the page a warm napkin. */
private val GardenSurfaces = Surfaces(
    page = Color(0xFFF2F0EB),
    panel = Color(0xFFEBE8E2),
    card = Color(0xFFFFFFFF),
    onPage = Color(0xFF21261F),
    onVariant = Color(0xFF6E7269),
    outline = Color(0xFFDAD5CD),
    outlineVariant = Color(0xFFE6E2DA),
)

/** Rose: cherry-blossom paper, garnet ink — the theme the app grew up with. */
private val CoralSurfaces = Surfaces(
    page = Color(0xFFFEF0F2),
    panel = Color(0xFFF9E2E7),
    card = Color(0xFFFFFDFD),
    onPage = Color(0xFF3A2226),
    onVariant = Color(0xFF7E6570),
    outline = Color(0xFFF0D8DD),
    outlineVariant = Color(0xFFF6E4E8),
)

/** Still Sea: fog-blue paper, deep-sea ink — the one cool world, and it is still paper. */
private val SeaSurfaces = Surfaces(
    page = Color(0xFFE7EDF2),
    panel = Color(0xFFDCE5EB),
    card = Color(0xFFFDFEFF),
    onPage = Color(0xFF152430),
    onVariant = Color(0xFF54677A),
    outline = Color(0xFFC6D4DE),
    outlineVariant = Color(0xFFD4DFE7),
)

/** White: a white page, near-black ink, and no hue anywhere. */
private val WhiteSurfaces = Surfaces(
    page = Color(0xFFFFFFFF),
    panel = Color(0xFFF4F4F2),
    card = Color(0xFFFFFFFF),
    onPage = Color(0xFF17171A),
    onVariant = Color(0xFF6B6B70),
    outline = Color(0xFFE5E5E3),
    outlineVariant = Color(0xFFEFEFF0),
)

/** Night: a coffee-dark table — cards are dark wood, the text is cream, the gold is gold. */
private val NightSurfaces = Surfaces(
    page = Color(0xFF1D1712),
    panel = Color(0xFF2A211A),
    card = Color(0xFF362A21),
    onPage = Color(0xFFF3EAD9),
    onVariant = Color(0xFFA8987F),
    outline = Color(0xFF3B2E24),
    outlineVariant = Color(0xFF2D231C),
)

// ---------------------------------------------------------------- the schemes

private fun light(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surfaces: Surfaces,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    // The accent is the second and third colour too: three hues on one screen was one too
    // many, and the secondary/tertiary slots are only read by a few Material parts.
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = primaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = primary,
    onTertiary = onPrimary,
    tertiaryContainer = primaryContainer,
    onTertiaryContainer = onPrimaryContainer,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFCE7E5),
    onErrorContainer = Color(0xFF7A1D16),
    background = surfaces.page,
    onBackground = surfaces.onPage,
    surface = surfaces.page,
    onSurface = surfaces.onPage,
    surfaceVariant = surfaces.panel,
    onSurfaceVariant = surfaces.onVariant,
    surfaceContainerLowest = surfaces.card,
    surfaceContainerLow = surfaces.card,
    surfaceContainer = surfaces.panel,
    surfaceContainerHigh = surfaces.card,
    surfaceContainerHighest = surfaces.panel,
    outline = surfaces.outline,
    outlineVariant = surfaces.outlineVariant,
    inverseSurface = surfaces.onPage,
    inverseOnSurface = surfaces.page,
    scrim = Color.Black,
)

private fun dark(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surfaces: Surfaces = NightSurfaces,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = primaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = Color(0xFFE7C36B),
    onTertiary = Color(0xFF2A1F05),
    tertiaryContainer = Color(0xFF3A2E10),
    onTertiaryContainer = Color(0xFFF0DBA4),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF8A2018),
    onErrorContainer = Color(0xFFFCE7E5),
    background = surfaces.page,
    onBackground = surfaces.onPage,
    surface = surfaces.page,
    onSurface = surfaces.onPage,
    surfaceVariant = surfaces.panel,
    onSurfaceVariant = surfaces.onVariant,
    surfaceContainerLowest = surfaces.card,
    surfaceContainerLow = surfaces.card,
    surfaceContainer = surfaces.panel,
    surfaceContainerHigh = surfaces.card,
    surfaceContainerHighest = surfaces.panel,
    outline = surfaces.outline,
    outlineVariant = surfaces.outlineVariant,
    inverseSurface = surfaces.onPage,
    inverseOnSurface = surfaces.page,
    scrim = Color.Black,
)

/**
 * The schemes for the five fixed worlds. Each accent is chosen the way its world earns
 * its living: clay that reads as a number on cream, a green bright enough to act, one
 * voltage of coral, a sea blue on fog paper, near-black ink on white, gold on coffee.
 */
private val PaperScheme = light(
    primary = Color(0xFF6B443D),
    onPrimary = Color(0xFFFDF8F0),
    primaryContainer = Color(0xFFDDBE78),
    onPrimaryContainer = Color(0xFF4A3208),
    surfaces = PaperSurfaces,
)

private val GardenScheme = light(
    primary = Color(0xFF00754A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E9E2),
    onPrimaryContainer = Color(0xFF00382A),
    surfaces = GardenSurfaces,
)

private val CoralScheme = light(
    // The old rose, whole again: the deep low-saturation garnet the day
    // numbers were set in — a rose, not a pink — on the blossom paper the
    // theme has always been.
    primary = Color(0xFF8C2D44),
    onPrimary = Color(0xFFFFF4F6),
    primaryContainer = Color(0xFFF5DEE4),
    onPrimaryContainer = Color(0xFF451022),
    surfaces = CoralSurfaces,
)

private val SeaScheme = light(
    primary = Color(0xFF23689E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE1EE),
    onPrimaryContainer = Color(0xFF0E3453),
    surfaces = SeaSurfaces,
)

private val WhiteScheme = light(
    primary = Color(0xFF17171A),
    onPrimary = Color.White,
    // A grey a step away from the panel it sits on, so a selected row and a tinted pill are
    // still visible in the one theme that has no colour to tell them apart with.
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF17171A),
    surfaces = WhiteSurfaces,
)

private val NightScheme = dark(
    primary = Color(0xFFDDAA61),
    onPrimary = Color(0xFF3A2A05),
    primaryContainer = Color(0xFF684D07),
    onPrimaryContainer = Color(0xFFF0DBA4),
)

/**
 * The custom world dyes paper rather than tinting white: whatever hue the strip picks
 * soaks into the sheet, so even a mixed hue gets the warm-paper body the fixed worlds have.
 */
private fun dyedPaper(hue: Float): ColorScheme = light(
    primary = Color.hsl(hue, 0.50f, 0.36f),
    onPrimary = Color.White,
    primaryContainer = Color.hsl(hue, 0.42f, 0.86f),
    onPrimaryContainer = Color.hsl(hue, 0.50f, 0.16f),
    surfaces = Surfaces(
        page = Color.hsl(hue, 0.26f, 0.91f),
        panel = Color.hsl(hue, 0.22f, 0.87f),
        card = Color.hsl(hue, 0.04f, 0.99f),
        onPage = Color.hsl(hue, 0.20f, 0.13f),
        onVariant = Color.hsl(hue, 0.12f, 0.38f),
        outline = Color.hsl(hue, 0.18f, 0.80f),
        outlineVariant = Color.hsl(hue, 0.15f, 0.86f),
    ),
)

/**
 * The same hue steeped into the night table instead of the paper: the coffee-dark
 * surfaces of Night, carrying the mixed hue, with the accent read at the bright,
 * low-saturation weight the theme's gold is — so the one who chose the dark does
 * not have to blink through a white sheet to keep a colour of their own.
 */
private fun dyedNight(hue: Float): ColorScheme = dark(
    primary = Color.hsl(hue, 0.45f, 0.72f),
    onPrimary = Color.hsl(hue, 0.50f, 0.12f),
    primaryContainer = Color.hsl(hue, 0.35f, 0.26f),
    onPrimaryContainer = Color.hsl(hue, 0.40f, 0.85f),
    surfaces = Surfaces(
        page = Color.hsl(hue, 0.22f, 0.13f),
        panel = Color.hsl(hue, 0.20f, 0.18f),
        card = Color.hsl(hue, 0.16f, 0.24f),
        onPage = Color.hsl(hue, 0.14f, 0.92f),
        onVariant = Color.hsl(hue, 0.10f, 0.72f),
        outline = Color.hsl(hue, 0.18f, 0.28f),
        outlineVariant = Color.hsl(hue, 0.16f, 0.22f),
    ),
)

/** The palette [settings] asks for. */
fun schemeFor(settings: AppSettings): ColorScheme =
    schemeFor(settings.preset, settings.customHue, settings.customDark)

fun schemeFor(preset: ThemePreset, customHue: Float, customDark: Boolean = false): ColorScheme =
    when (preset) {
        ThemePreset.WHITE -> WhiteScheme
        ThemePreset.BLACK -> NightScheme
        ThemePreset.TEAL -> GardenScheme
        ThemePreset.MIST -> SeaScheme
        ThemePreset.AMBER -> PaperScheme
        ThemePreset.ROSE -> CoralScheme
        ThemePreset.CUSTOM ->
            if (customDark) dyedNight(((customHue % 360f) + 360f) % 360f)
            else dyedPaper(((customHue % 360f) + 360f) % 360f)
    }

/**
 * The colour a countdown paints itself with, given its own [hue].
 *
 * Null means it was not given one, and the caller falls back to the theme's accent — which is
 * what keeps an uncoloured countdown visible rather than leaving a hole where its dot is.
 */
fun eventHueColor(hue: Float?, dark: Boolean): Color? = hue?.let {
    Color.hsl(it, if (dark) 0.52f else 0.62f, if (dark) 0.66f else 0.40f)
}

/** How much of a countdown's own colour is laid over the card behind it. */
const val EVENT_WASH_ALPHA = 0.10f

/** The colour a countdown paints itself with, falling back to the theme's accent. */
@Composable
fun eventAccent(hue: Float?): Color =
    eventHueColor(hue, isDarkScheme()) ?: MaterialTheme.colorScheme.primary
