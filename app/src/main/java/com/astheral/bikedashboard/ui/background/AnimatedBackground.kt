package com.astheral.bikedashboard.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * What a scene draws from on one frame. Filled in afresh each frame (never reallocated).
 */
internal class SceneTime {
    /** Seconds, 0 until [LOOP_SECONDS], at the chosen speed: for swaying, pulsing and turning. */
    var t = 0f

    /** Like [t] but slowed by the movement setting: for things that travel across the screen. */
    var travel = 0f

    /** How far swaying things swing: 0 still, 1 normal, 2 twice as far. */
    var motion = 1f

    /** The parallax shift in pixels for the nearest layer; further layers take a share of it. */
    var shiftX = 0f
    var shiftY = 0f
}

/**
 * One background picture. It's a pure function of the times in [SceneTime] (so it can be drawn
 * at any moment without keeping state between frames), and everything random about it comes
 * from a fixed seed, so it looks the same every launch. Scenes keep their own scratch arrays,
 * so drawing a frame doesn't allocate.
 */
internal interface BackgroundScene {
    /** How often the picture needs to change, in milliseconds. */
    val stepMillis: Long get() = 16L

    /**
     * True when the scene applies the parallax shift itself, layer by layer (far things moving
     * less than near ones). Otherwise the whole picture is shifted as one.
     */
    val layered: Boolean get() = false

    fun DrawScope.paint(f: SceneTime, ink: BackgroundInk)
}

/** The furthest the picture moves with the phone's tilt. */
private val MAX_SHIFT = 20.dp

/** Share of the shift a whole-picture (not layered) scene takes, as if it sat halfway back. */
private const val FLAT_DEPTH = 0.6f

/**
 * Draws [look]'s style filling its space, behind whatever comes after it. With [animate] off (or
 * the phone's animations switched off) it shows a still frame. Random must be resolved first;
 * if it isn't, Circles shows.
 */
@Composable
fun AnimatedBackground(look: BackgroundLook, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (look.style == BackgroundStyle.NONE) return
    val scene = remember(look.style) { sceneFor(look.style) }
    val ink = rememberBackgroundInk()
    val frame = rememberBackgroundFrame(animate, scene.stepMillis, look)
    val sceneTime = remember { SceneTime() }
    val motion = look.movement
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        // Reading the frame here, in the draw phase, repaints without recomposing.
        val shift = MAX_SHIFT.toPx()
        sceneTime.t = frame.time.floatValue
        sceneTime.travel = frame.travel.floatValue
        sceneTime.motion = motion
        sceneTime.shiftX = frame.shiftX.floatValue * shift
        sceneTime.shiftY = frame.shiftY.floatValue * shift
        if (scene.layered) {
            with(scene) { paint(sceneTime, ink) }
        } else {
            translate(sceneTime.shiftX * FLAT_DEPTH, sceneTime.shiftY * FLAT_DEPTH) {
                with(scene) { paint(sceneTime, ink) }
            }
        }
    }
}

/** A small still (or, with [animate], moving) preview of [style] at normal settings. */
@Composable
fun BackgroundPreview(style: BackgroundStyle, modifier: Modifier = Modifier, animate: Boolean = false, look: BackgroundLook? = null) {
    AnimatedBackground(
        (look ?: BackgroundLook()).copy(style = style, parallax = false),
        modifier,
        animate,
    )
}

private fun sceneFor(style: BackgroundStyle): BackgroundScene = when (style) {
    BackgroundStyle.NONE, BackgroundStyle.RANDOM, BackgroundStyle.CIRCLES -> CirclesScene()
    BackgroundStyle.RINGS -> RingsScene()
    BackgroundStyle.GRID -> GridScene()
    BackgroundStyle.MESH -> MeshScene()
    BackgroundStyle.PARTICLES -> ParticlesScene()
    BackgroundStyle.SHAPES -> ShapesScene()
    BackgroundStyle.SNOW -> SnowScene()
    BackgroundStyle.SPACE -> SpaceScene()
    BackgroundStyle.MATRIX -> MatrixScene()
}
