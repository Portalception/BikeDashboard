package com.astheral.bikedashboard.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import com.astheral.bikedashboard.ui.theme.ThemeMode
import com.astheral.bikedashboard.ui.theme.WallpaperSupported
import com.astheral.bikedashboard.ui.theme.schemeFor
import kotlin.math.hypot
import kotlin.math.max

/** A bike switch in progress: the bike picked, where it was tapped (in window pixels) and its look. */
data class BikeSwitch(val profile: Profile, val origin: Offset, val appearance: AppearanceSettings)

private const val TOTAL_MS = 820
/** The share of the animation by which the screen is covered and the bike actually switches. */
private const val COVERED_AT = 0.38f
/** When the cover starts wiping away to show the new bike. */
private const val WIPE_FROM = 0.56f

/** Quick in, lingers in the middle, quick out: the name blasts past like a bike on a straight. */
private val FlyBy = CubicBezierEasing(0.12f, 0.85f, 0.88f, 0.15f)

/**
 * The racing-style switch between bikes. A circle in the new bike's colour bursts out from where
 * you tapped, led by a ring of its accent; once the screen is covered the bike switches
 * underneath ([onCovered]), its name flies across with speed lines, and the cover wipes off
 * to the left on a slant with accent stripes, showing the new bike's colours. About 0.8 s;
 * taps are held back only while it plays.
 */
@Composable
fun BikeSwitchOverlay(switch: BikeSwitch, onCovered: () -> Unit, onFinished: () -> Unit) {
    val colors = previewColors(switch.appearance)
    val progress = remember(switch) { Animatable(0f) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val covered by rememberUpdatedState(onCovered)
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(switch) {
        var switched = false
        try {
            progress.animateTo(COVERED_AT, tween((TOTAL_MS * COVERED_AT).toInt(), easing = LinearEasing))
            covered(); switched = true
            progress.animateTo(1f, tween((TOTAL_MS * (1 - COVERED_AT)).toInt(), easing = LinearEasing))
        } finally {
            if (!switched) covered()
            finished()
        }
    }

    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontSize = 44.sp,
        color = colors.onCover,
    )
    val kickerStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 4.sp,
        color = colors.onCover.copy(alpha = 0.75f),
    )

    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords = it }
            // Holds back taps while the switch plays.
            .pointerInput(Unit) { awaitEachGesture { awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        val t = progress.value
        val w = size.width
        val h = size.height
        val origin = coords?.takeIf { it.isAttached && switch.origin.isSpecified }?.windowToLocal(switch.origin)
            ?: Offset(w / 2, h * 0.8f)
        val reach = maxOf(
            hypot(origin.x, origin.y), hypot(w - origin.x, origin.y),
            hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y),
        )

        // The cover: a burst out of the tap, then a slanted wipe off to the left.
        val cover = Path()
        val slant = h * 0.22f
        if (t < WIPE_FROM) {
            val ring = FastOutSlowInEasing.transform((t / (COVERED_AT - 0.04f)).coerceIn(0f, 1f))
            val fill = FastOutSlowInEasing.transform(((t - 0.05f) / (COVERED_AT - 0.04f)).coerceIn(0f, 1f))
            if (fill < 1f) drawCircle(colors.accent, radius = ring * reach, center = origin)
            cover.addOval(Rect(origin, fill * reach))
        } else {
            val wipe = FastOutSlowInEasing.transform(((t - WIPE_FROM) / (1f - WIPE_FROM)).coerceIn(0f, 1f))
            val top = (1f - wipe) * (w + slant + 60.dp.toPx())
            cover.moveTo(0f, 0f); cover.lineTo(top, 0f); cover.lineTo(top - slant, h); cover.lineTo(0f, h); cover.close()
            // Racing stripes on the trailing edge.
            stripe(top + 6.dp.toPx(), 16.dp.toPx(), slant, colors.accent)
            stripe(top + 30.dp.toPx(), 6.dp.toPx(), slant, colors.accent.copy(alpha = 0.6f))
        }
        drawPath(cover, colors.cover)

        // The bike's name flies across, only showing on the cover.
        clipPath(cover) {
            val name = measurer.measure(
                switch.profile.name, nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (w * 1.6f).toInt()),
            )
            val kicker = measurer.measure("NOW RIDING", kickerStyle)
            val fly = FlyBy.transform(((t - 0.08f) / 0.86f).coerceIn(0f, 1f))
            val textW = max(name.size.width, kicker.size.width).toFloat()
            val x = w + 24.dp.toPx() - fly * (w + textW + 48.dp.toPx())
            val y = h / 2 - name.size.height / 2f
            // Speed lines trailing behind the name.
            val lineColor = colors.onCover.copy(alpha = 0.35f)
            val gap = name.size.height / 5f
            listOf(0.6f, 1f, 0.45f, 0.8f, 0.5f).forEachIndexed { i, length ->
                val ly = y + gap * (i + 0.5f)
                val start = x + name.size.width + 14.dp.toPx() + (i % 2) * 18.dp.toPx()
                drawLine(lineColor, Offset(start, ly), Offset(start + w * 0.35f * length, ly), strokeWidth = 3.dp.toPx())
            }
            translate(x + 6.dp.toPx(), y - kicker.size.height) { drawText(kicker) }
            translate(x, y) { drawText(name) }
        }
    }
}

/** A slanted band [width] wide whose top-left corner is at [x]. */
private fun DrawScope.stripe(x: Float, width: Float, slant: Float, color: Color) {
    val p = Path()
    p.moveTo(x, 0f); p.lineTo(x + width, 0f); p.lineTo(x + width - slant, size.height); p.lineTo(x - slant, size.height); p.close()
    drawPath(p, color)
}

private data class PreviewColors(val cover: Color, val onCover: Color, val accent: Color)

/** The new bike's main colour, its text colour and accent, as its theme will show them. */
@Composable
private fun previewColors(a: AppearanceSettings): PreviewColors {
    val dark = when (a.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = remember(a, dark) {
        if (a.wallpaper && WallpaperSupported) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            schemeFor(a.primary, a.accent, dark)
        }
    }
    return PreviewColors(scheme.primary, scheme.onPrimary, scheme.tertiary)
}
