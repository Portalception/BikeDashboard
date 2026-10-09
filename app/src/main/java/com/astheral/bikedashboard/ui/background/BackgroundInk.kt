package com.astheral.bikedashboard.ui.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme
import com.materialkolor.hct.Hct

/**
 * The colours a background draws with: the theme's primary and accent, kept at their hue but
 * set to an even brightness (light enough to show on black, dark enough to show on white), so
 * a navy or a pale grey shows as clearly as a bright orange. Drawn at low alpha, times
 * [strength], which is a little higher in dark mode and on pure black.
 */
internal class BackgroundInk(
    val primary: Color,
    val accent: Color,
    /** Halfway between the two. */
    val blend: Color,
    /** Near white in dark mode, for snow and stars; the primary in light mode. */
    val bright: Color,
    val dark: Boolean,
    val strength: Float,
) {
    /** [alpha] scaled for the current theme. */
    fun a(alpha: Float): Float = (alpha * strength).coerceAtMost(1f)
}

@Composable
internal fun rememberBackgroundInk(): BackgroundInk {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val pureBlack = dark && scheme.background == Color.Black
    return remember(scheme.primary, scheme.tertiary, dark, pureBlack) {
        val tone = if (dark) 72.0 else 45.0
        val primary = scheme.primary.atTone(tone)
        val accent = scheme.tertiary.atTone(tone)
        BackgroundInk(
            primary = primary,
            accent = accent,
            blend = lerp(primary, accent, 0.5f),
            bright = if (dark) lerp(primary, Color.White, 0.6f) else primary,
            dark = dark,
            strength = when {
                pureBlack -> 1.3f
                dark -> 1.1f
                else -> 1f
            },
        )
    }
}

private fun Color.atTone(tone: Double): Color {
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, tone).toInt())
}
