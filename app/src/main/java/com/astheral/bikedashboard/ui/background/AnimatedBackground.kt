package com.astheral.bikedashboard.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * One background picture. It's a pure function of the time [t] (seconds, 0 until
 * [LOOP_SECONDS]), so it can be drawn at any moment without keeping state between frames, and
 * everything random about it comes from a fixed seed, so it looks the same every launch.
 * Scenes keep their own scratch arrays, so drawing a frame doesn't allocate.
 */
internal interface BackgroundScene {
    /** How often the picture needs to change, in milliseconds. */
    val stepMillis: Long get() = 16L

    fun DrawScope.paint(t: Float, ink: BackgroundInk)
}

/**
 * Draws [style] filling its space, behind whatever comes after it. With [animate] off (or the
 * phone's animations switched off) it shows a still frame.
 */
@Composable
fun AnimatedBackground(style: BackgroundStyle, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (style == BackgroundStyle.NONE) return
    val scene = remember(style) { sceneFor(style) }
    val ink = rememberBackgroundInk()
    val time = rememberBackgroundTime(animate, scene.stepMillis)
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        // Reading the time here, in the draw phase, repaints without recomposing.
        with(scene) { paint(time.value, ink) }
    }
}

private fun sceneFor(style: BackgroundStyle): BackgroundScene = when (style) {
    BackgroundStyle.NONE, BackgroundStyle.CIRCLES -> CirclesScene()
    BackgroundStyle.RINGS -> RingsScene()
    BackgroundStyle.GRID -> GridScene()
    BackgroundStyle.MESH -> MeshScene()
    BackgroundStyle.PARTICLES -> ParticlesScene()
    BackgroundStyle.SHAPES -> ShapesScene()
    BackgroundStyle.SNOW -> SnowScene()
    BackgroundStyle.SPACE -> SpaceScene()
    BackgroundStyle.MATRIX -> MatrixScene()
}
