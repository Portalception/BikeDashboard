package com.astheral.bikedashboard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.background.BackgroundLook
import com.astheral.bikedashboard.ui.background.BackgroundStyle
import com.astheral.bikedashboard.ui.background.RandomBackground
import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.SchemeFidelity

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Everything that decides how the app looks. */
data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** True black backgrounds in dark mode, for AMOLED screens. */
    val pureBlack: Boolean = false,
    /** Use the phone's wallpaper colours instead of [primary] and [accent] (Android 12+). */
    val wallpaper: Boolean = false,
    val primary: Color = ColorPreset.MONOCHROME.primary,
    val accent: Color = ColorPreset.MONOCHROME.accent,
    /** Shape of the progress line on the dashboard and while adding a fill-up. */
    val progressStyle: ProgressStyle = ProgressStyle.STANDARD,
    /** The moving picture behind every screen, or Random for a different one each launch. */
    val backgroundStyle: BackgroundStyle = BackgroundStyle.CIRCLES,
    /** The background shifts a little as the phone tilts. */
    val backgroundParallax: Boolean = true,
    /** How far the background moves, 0 to 2 (normal is 1). */
    val backgroundMovement: Float = 1f,
    /** How fast the background runs, 0.25 to 3 (normal is 1). */
    val backgroundSpeed: Float = 1f,
)

enum class ProgressStyle(val label: String) {
    STANDARD("Standard"), CHAIN("Chain"), TREAD("Tyre Tread"), REV("Rev Counter"), STRIPE("Race Stripe"), ROAD("On the Road");

    companion object {
        /** The style saved under [name], or Standard for nothing saved or a removed style (Kerb, Chequered, Wave). */
        fun fromName(name: String?): ProgressStyle = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** The progress line shape picked in Settings. */
val LocalProgressStyle = staticCompositionLocalOf { ProgressStyle.STANDARD }

/** The background picked in Settings (with Random already resolved), drawn by AppBackground. */
val LocalBackgroundLook = staticCompositionLocalOf { BackgroundLook() }

/**
 * Ready-made primary + accent pairs. Apart from Monochrome, each uses a motorcycle maker's
 * racing colours, and is shown by the brand name only.
 */
enum class ColorPreset(val brand: String, val primary: Color, val accent: Color) {
    MONOCHROME("Monochrome", Color(0xFF8E8E8E), Color(0xFFE0E0E0)),
    YAMAHA("Yamaha", Color(0xFF1B3F9A), Color(0xFF00B0FF)),
    HONDA("Honda", Color(0xFFCC0000), Color(0xFF1E4DB7)),
    KAWASAKI("Kawasaki", Color(0xFF5FB52A), Color(0xFF3C3C3C)),
    TRIUMPH("Triumph", Color(0xFF2B2B2B), Color(0xFFFFC72C)),
}

/** Colours offered for primary and accent; any other colour can be picked with "Custom". */
val SwatchColors = listOf(
    Color(0xFFE53935), Color(0xFFE0103A), Color(0xFFEC407A), Color(0xFF8E24AA),
    Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1B3F9A), Color(0xFF1E88E5),
    Color(0xFF00B0FF), Color(0xFF00ACC1), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFF9E9D24), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFF6D4C41),
    Color(0xFF546E7A),
)

val WallpaperSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** True when the dark colours are showing, whichever way that was chosen. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Fixed colours for Singapore and Malaysia in the charts and cards, readable in light and dark. */
object CountryColors {
    val sg: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFFF8A80) else Color(0xFFD32F2F)
    val my: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF82B1FF) else Color(0xFF1565C0)
    val mixed: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
}

@Composable
fun BikeDashboardTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    /** The bike being shown, so Random keeps one pick per bike for the session. */
    bikeId: Long? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val base = if (appearance.wallpaper && WallpaperSupported) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(appearance.primary, appearance.accent, dark) { schemeFor(appearance.primary, appearance.accent, dark) }
    }
    val colorScheme = if (dark && appearance.pureBlack) base.pureBlack() else base

    // Status bar and navigation bar icons: dark icons on light colours, light icons on dark.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalProgressStyle provides appearance.progressStyle,
        LocalBackgroundLook provides rememberBackgroundLook(appearance, bikeId),
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/** The background settings in [appearance], with Random turned into this session's pick for [bikeId]. */
@Composable
private fun rememberBackgroundLook(appearance: AppearanceSettings, bikeId: Long?): BackgroundLook {
    val context = LocalContext.current
    val style = remember(appearance.backgroundStyle, bikeId) {
        if (appearance.backgroundStyle != BackgroundStyle.RANDOM) appearance.backgroundStyle
        else {
            val settings = Settings.get(context)
            // Before the bikes have loaded, go by the last bike picked, so the pick doesn't change a moment later.
            RandomBackground.resolve(bikeId ?: settings.activeProfileId.value, settings::lastRandomBackground, settings::setLastRandomBackground)
        }
    }
    return remember(style, appearance.backgroundParallax, appearance.backgroundMovement, appearance.backgroundSpeed) {
        BackgroundLook(style, appearance.backgroundParallax, appearance.backgroundMovement, appearance.backgroundSpeed)
    }
}

/**
 * Builds a full Material colour scheme from two colours: [primary] for the main colour
 * and [accent] for highlights (Material calls this "tertiary").
 *
 * Material on its own would swap the picked colours for a fixed lighter or darker shade of
 * them, so here `primary` and `tertiary` are the picked colours themselves (see [readableOn]).
 * The text on top of them is black or white, whichever is easier to read. Backgrounds and
 * containers still come from Material, tinted by the picked colours.
 */
fun schemeFor(primary: Color, accent: Color, dark: Boolean): ColorScheme {
    val s = SchemeFidelity(Hct.fromInt(primary.toArgb()), dark, 0.0)
    val a = TonalPalette.fromHct(Hct.fromInt(accent.toArgb()))
    fun c(argb: Int) = Color(argb)
    val surfaceTone = Hct.fromInt(s.surface).tone
    val shownPrimary = primary.readableOn(surfaceTone, dark)
    val onPrimary = contentOn(shownPrimary)
    val tertiary = accent.readableOn(surfaceTone, dark)
    val onTertiary = contentOn(tertiary)
    val tertiaryContainer = c(a.tone(if (dark) 30 else 90))
    val onTertiaryContainer = c(a.tone(if (dark) 90 else 10))
    return if (dark) {
        darkColorScheme(
            primary = shownPrimary, onPrimary = onPrimary,
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = shownPrimary,
            inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error), onError = c(s.onError),
            errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline), outlineVariant = c(s.outlineVariant), scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright), surfaceDim = c(s.surfaceDim),
            surfaceContainer = c(s.surfaceContainer), surfaceContainerHigh = c(s.surfaceContainerHigh),
            surfaceContainerHighest = c(s.surfaceContainerHighest), surfaceContainerLow = c(s.surfaceContainerLow),
            surfaceContainerLowest = c(s.surfaceContainerLowest),
        )
    } else {
        lightColorScheme(
            primary = shownPrimary, onPrimary = onPrimary,
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = shownPrimary,
            inverseSurface = c(s.inverseSurface), inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error), onError = c(s.onError),
            errorContainer = c(s.errorContainer), onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline), outlineVariant = c(s.outlineVariant), scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright), surfaceDim = c(s.surfaceDim),
            surfaceContainer = c(s.surfaceContainer), surfaceContainerHigh = c(s.surfaceContainerHigh),
            surfaceContainerHighest = c(s.surfaceContainerHighest), surfaceContainerLow = c(s.surfaceContainerLow),
            surfaceContainerLowest = c(s.surfaceContainerLowest),
        )
    }
}

/**
 * This colour, unchanged, as long as it stands out at least 2:1 from a background of
 * [backgroundTone], which keeps bright oranges, yellows and sky blues exactly as picked.
 * Otherwise (black in dark mode, white or a pale colour in light mode) it would vanish as
 * coloured text or an outline, so it's made just light or dark enough, keeping its hue.
 */
private fun Color.readableOn(backgroundTone: Double, dark: Boolean): Color {
    val hct = Hct.fromInt(toArgb())
    if (Contrast.ratioOfTones(hct.tone, backgroundTone) >= MIN_CONTRAST) return this
    val tone = if (dark) Contrast.lighterUnsafe(backgroundTone, MIN_CONTRAST) else Contrast.darkerUnsafe(backgroundTone, MIN_CONTRAST)
    return Color(Hct.from(hct.hue, hct.chroma, tone).toInt())
}

private const val MIN_CONTRAST = 2.0

/** Black or white, whichever reads better on top of [color]. */
private fun contentOn(color: Color): Color {
    val l = color.luminance()
    // Contrast ratio against white is 1.05 / (l + 0.05), against black (l + 0.05) / 0.05.
    return if (1.05f / (l + 0.05f) >= (l + 0.05f) / 0.05f) Color.White else Color.Black
}

/** True black background with near-black cards, for AMOLED screens. */
private fun ColorScheme.pureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF232323),
    surfaceBright = Color(0xFF2A2A2A),
)
