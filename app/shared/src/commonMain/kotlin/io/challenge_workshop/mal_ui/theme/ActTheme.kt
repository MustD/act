package io.challenge_workshop.mal_ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mal_ui.app.shared.generated.resources.Res
import mal_ui.app.shared.generated.resources.ibm_plex_mono_medium
import mal_ui.app.shared.generated.resources.ibm_plex_mono_regular
import mal_ui.app.shared.generated.resources.ibm_plex_mono_semibold
import mal_ui.app.shared.generated.resources.ibm_plex_sans_medium
import mal_ui.app.shared.generated.resources.ibm_plex_sans_regular
import mal_ui.app.shared.generated.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

/** The three accents the palette defines. Nothing chooses one yet: [ActTheme] defaults to [Phosphor]. */
enum class ActAccent { Phosphor, Amber, Cyan }

/** The design tokens of one Theme. `acc` and `pend` come from the [ActAccent]; the rest from dark or light. */
@Immutable
data class ActColors(
    val bg: Color,
    val sf: Color,
    val sf2: Color,
    val ln: Color,
    val ink: Color,
    val dim: Color,
    val onAcc: Color,
    val acc: Color,
    val pend: Color,
    val error: Color,
    val isDark: Boolean,
)

private val DarkBase = ActColors(
    bg = Color(0xFF0D0E0C), sf = Color(0xFF151713), sf2 = Color(0xFF1C1F1A), ln = Color(0xFF2B2E28),
    ink = Color(0xFFE8EAE2), dim = Color(0xFF8D9287), onAcc = Color(0xFF0D0E0C),
    acc = Color(0xFF8CEB7B), pend = Color(0xFFF2C057), error = Color(0xFFFF8A7A), isDark = true,
)

private val LightBase = ActColors(
    bg = Color(0xFFF1F0E9), sf = Color(0xFFFAF9F3), sf2 = Color(0xFFE6E5DC), ln = Color(0xFFD3D1C5),
    ink = Color(0xFF161713), dim = Color(0xFF5F6157), onAcc = Color(0xFFFAF9F3),
    acc = Color(0xFF2E7B33), pend = Color(0xFFA86A12), error = Color(0xFFB3261E), isDark = false,
)

/** The accent and its `pend` partner, which has to differ from it. Hexes approximate the design's oklch values. */
private fun accentFor(accent: ActAccent, dark: Boolean): Pair<Color, Color> {
    val phosphor = if (dark) Color(0xFF8CEB7B) else Color(0xFF2E7B33)
    val amber = if (dark) Color(0xFFF2C057) else Color(0xFFA86A12)
    val cyan = if (dark) Color(0xFF6FDBE8) else Color(0xFF1C6E8C)
    return when (accent) {
        ActAccent.Phosphor -> phosphor to amber
        ActAccent.Amber -> amber to cyan
        ActAccent.Cyan -> cyan to amber
    }
}

/** The palette for one Theme, without a composition: what [ActTheme] provides, and what a test can ask for. */
fun actColors(dark: Boolean, accent: ActAccent = ActAccent.Phosphor): ActColors {
    val (acc, pend) = accentFor(accent, dark)
    return (if (dark) DarkBase else LightBase).copy(acc = acc, pend = pend)
}

/** The type scale from the handoff, as named styles. */
@Immutable
data class ActType(
    val screenTitle: TextStyle,
    val pageTitle: TextStyle,
    val cardTitle: TextStyle,
    val rowTitle: TextStyle,
    val synopsis: TextStyle,
    val sheetTitle: TextStyle,
    val scoreText: TextStyle,
    val bigNumber: TextStyle,
    val cardNumber: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val sectionLabel: TextStyle,
    val tag: TextStyle,
    val tiny: TextStyle,
)

@Composable
private fun plexMono() = FontFamily(
    Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

@Composable
private fun plexSans() = FontFamily(
    Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

@Composable
private fun actType(): ActType {
    val mono = plexMono()
    val sans = plexSans()
    return ActType(
        screenTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em),
        pageTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.01).em),
        cardTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        rowTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
        synopsis = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        sheetTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
        scoreText = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        bigNumber = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.03).em),
        cardNumber = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 22.sp, letterSpacing = (-0.03).em),
        body = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        meta = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
        sectionLabel = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
        tag = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
        tiny = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp),
    )
}

/** The roles of M3's scale that an [ActType] style fits, so stock components (dialogs, menus, pickers) match. */
private fun ActType.toMaterial() = Typography(
    headlineLarge = screenTitle,
    headlineSmall = pageTitle,
    titleLarge = sheetTitle,
    titleMedium = cardTitle,
    titleSmall = rowTitle,
    bodyLarge = synopsis,
    bodyMedium = body,
    bodySmall = meta,
    labelLarge = body,
    labelMedium = meta,
    labelSmall = sectionLabel,
)

/** 2dp for buttons, covers and cells; 4dp for cards, boxes and dialogs. Nothing is pill-shaped. */
val ActSmall = RoundedCornerShape(2.dp)
val ActMedium = RoundedCornerShape(4.dp)

private val ActShapes = Shapes(
    extraSmall = ActSmall,
    small = ActSmall,
    medium = ActMedium,
    large = ActMedium,
    extraLarge = ActMedium,
)

/** The one easing curve every transition uses: `cubic-bezier(.2, .8, .2, 1)`. */
val ActEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private val LocalActColors = staticCompositionLocalOf { DarkBase }
private val LocalActType = staticCompositionLocalOf<ActType> { error("ActTheme not applied") }

/** `Act.colors` and `Act.type`: the tokens of the enclosing [ActTheme]. */
object Act {
    val colors: ActColors @Composable @ReadOnlyComposable get() = LocalActColors.current
    val type: ActType @Composable @ReadOnlyComposable get() = LocalActType.current
}

/**
 * Replaces the bare `MaterialTheme {}`: provides [Act], and maps the same tokens onto M3's `ColorScheme`,
 * `Typography` and `Shapes` so a stock component inherits them.
 */
@Composable
fun ActTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: ActAccent = ActAccent.Phosphor,
    content: @Composable () -> Unit,
) {
    val c = actColors(dark, accent)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.acc, onPrimary = c.onAcc,
        secondaryContainer = c.sf2, onSecondaryContainer = c.ink,
        background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink, onSurfaceVariant = c.dim,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.sf, surfaceContainer = c.sf,
        surfaceContainerHigh = c.sf2, surfaceContainerHighest = c.sf2,
        outline = c.ln, outlineVariant = c.ln,
        error = c.error, errorContainer = c.sf2, onErrorContainer = c.error,
    )
    val type = actType()
    CompositionLocalProvider(LocalActColors provides c, LocalActType provides type) {
        MaterialTheme(colorScheme = scheme, typography = type.toMaterial(), shapes = ActShapes, content = content)
    }
}
