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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    val progressStyle: ProgressStyle = ProgressStyle.CHAIN,
)

enum class ProgressStyle(val label: String) {
    CHAIN("Chain"), KERB("Kerb"), CHECKERED("Chequered"), TREAD("Tyre tread"), REV("Rev counter"), WAVE("Wave"),
}

/** The progress line shape picked in Settings. */
val LocalProgressStyle = staticCompositionLocalOf { ProgressStyle.WAVE }

/**
 * Ready-made primary + accent pairs. Apart from Monochrome, each takes its colours from a
 * maker's flagship sports bike and its racing colours.
 */
enum class ColorPreset(val brand: String, val model: String, val primary: Color, val accent: Color) {
    MONOCHROME("Monochrome", "Greys", Color(0xFF8E8E8E), Color(0xFFE0E0E0)),
    YAMAHA("Yamaha", "YZF-R1", Color(0xFF1B3F9A), Color(0xFF00B0FF)),
    HONDA("Honda", "Fireblade", Color(0xFFCC0000), Color(0xFF1E4DB7)),
    KAWASAKI("Kawasaki", "ZX-10R", Color(0xFF5FB52A), Color(0xFF3C3C3C)),
    SUZUKI("Suzuki", "GSX-R1000", Color(0xFF1C5AA8), Color(0xFFB0BEC5)),
    DUCATI("Ducati", "Panigale V4", Color(0xFFC8102E), Color(0xFFE6E6E6)),
    BMW("BMW", "M 1000 RR", Color(0xFF1C69D4), Color(0xFFE2231A)),
    KTM("KTM", "RC 8C", Color(0xFFFF6600), Color(0xFF263238)),
    APRILIA("Aprilia", "RSV4", Color(0xFFD7191C), Color(0xFF7B2D8E)),
    MV_AGUSTA("MV Agusta", "F3 RR", Color(0xFFB71C1C), Color(0xFFB0B0B0)),
    TRIUMPH("Triumph", "Daytona", Color(0xFF2B2B2B), Color(0xFFFFC72C)),
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
fun BikeDashboardTheme(appearance: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
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

    CompositionLocalProvider(LocalDarkTheme provides dark, LocalProgressStyle provides appearance.progressStyle) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * Builds a full Material colour scheme from two colours: [primary] for the main colour
 * and [accent] for highlights (Material calls this "tertiary"). Both are kept close to the
 * colours picked rather than toned down.
 */
fun schemeFor(primary: Color, accent: Color, dark: Boolean): ColorScheme {
    val s = SchemeFidelity(Hct.fromInt(primary.toArgb()), dark, 0.0)
    val a = TonalPalette.fromHct(Hct.fromInt(accent.toArgb()))
    fun c(argb: Int) = Color(argb)
    val tertiary = c(a.tone(if (dark) 80 else 40))
    val onTertiary = c(a.tone(if (dark) 20 else 100))
    val tertiaryContainer = c(a.tone(if (dark) 30 else 90))
    val onTertiaryContainer = c(a.tone(if (dark) 90 else 10))
    return if (dark) {
        darkColorScheme(
            primary = c(s.primary), onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.surfaceTint),
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
            primary = c(s.primary), onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = c(s.background), onBackground = c(s.onBackground),
            surface = c(s.surface), onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant), onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.surfaceTint),
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
