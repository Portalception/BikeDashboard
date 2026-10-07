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
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeTonalSpot

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }

/**
 * Colour themes. Each is built from one seed colour into a full Material colour scheme,
 * in both light and dark. [vivid] keeps the main colour close to the seed.
 */
enum class ColorTheme(val label: String, val seed: Color, val vivid: Boolean) {
    YAMAHA_BLUE("Yamaha Blue", Color(0xFF1B3F9A), vivid = true),
    RACING_RED("Racing Red", Color(0xFFC62828), vivid = true),
    MIDNIGHT("Midnight", Color(0xFF3F4A7A), vivid = false),
    FOREST("Forest", Color(0xFF2E6B3A), vivid = false),
    WALLPAPER("Wallpaper", Color(0xFF7E57C2), vivid = false),
    ;

    companion object {
        /** Matching the phone's wallpaper needs Android 12 or newer. */
        val wallpaperSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val available get() = entries.filter { it != WALLPAPER || wallpaperSupported }
    }
}

/** True when the dark colours are showing, whichever way that was chosen. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Fixed colours for Singapore and Malaysia in the charts and cards, readable in light and dark. */
object CountryColors {
    val sg: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFFF8A80) else Color(0xFFD32F2F)
    val my: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF82B1FF) else Color(0xFF1565C0)
}

@Composable
fun BikeDashboardTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.YAMAHA_BLUE,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        colorTheme == ColorTheme.WALLPAPER && ColorTheme.wallpaperSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(colorTheme, dark) { schemeFor(colorTheme, dark) }
    }

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

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

private fun schemeFor(theme: ColorTheme, dark: Boolean): ColorScheme {
    val seed = Hct.fromInt(theme.seed.toArgb())
    val s: DynamicScheme = if (theme.vivid) SchemeFidelity(seed, dark, 0.0) else SchemeTonalSpot(seed, dark, 0.0)
    fun c(argb: Int) = Color(argb)
    return if (dark) {
        darkColorScheme(
            primary = c(s.primary), onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer), onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary), onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer), onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = c(s.tertiary), onTertiary = c(s.onTertiary),
            tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
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
            tertiary = c(s.tertiary), onTertiary = c(s.onTertiary),
            tertiaryContainer = c(s.tertiaryContainer), onTertiaryContainer = c(s.onTertiaryContainer),
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
