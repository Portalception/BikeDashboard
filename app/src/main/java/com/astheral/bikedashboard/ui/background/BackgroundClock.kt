package com.astheral.bikedashboard.ui.background

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import android.view.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Background time runs from 0 to this many seconds, then starts again. Every movement repeats a
 * whole number of times per loop (see [wave] and [cycle]), so the restart can't be seen.
 */
internal const val LOOP_SECONDS = 1200f

/** The moment shown when the phone's animations are switched off, and in still previews. */
internal const val STILL_SECONDS = 47f

internal const val TAU = 6.2831855f

/**
 * One clock shared by every background, so moving between screens carries on the same picture
 * instead of jumping back to the start.
 */
private val sharedClock = LoopClock()

/** What a background reads while drawing, so nothing recomposes per frame. */
@Stable
internal class BackgroundFrameState {
    val time = mutableFloatStateOf(STILL_SECONDS)
    val travel = mutableFloatStateOf(STILL_SECONDS)
    /** The parallax shift, -1 to 1 each way. */
    val shiftX = mutableFloatStateOf(0f)
    val shiftY = mutableFloatStateOf(0f)

    /** Which way is down on screen, from the gravity sensor (straight down until it's read), and how steeply. */
    val downX = mutableFloatStateOf(0f)
    val downY = mutableFloatStateOf(1f)
    val downPull = mutableFloatStateOf(1f)

    fun showStill() {
        time.floatValue = STILL_SECONDS
        travel.floatValue = STILL_SECONDS
        shiftX.floatValue = 0f
        shiftY.floatValue = 0f
    }

    fun copyFrom(clock: LoopClock, ease: TiltEase?, down: DownEase? = null) {
        time.floatValue = clock.time.toFloat()
        travel.floatValue = clock.travel.toFloat()
        shiftX.floatValue = ease?.x ?: 0f
        shiftY.floatValue = ease?.y ?: 0f
        if (down != null) {
            downX.floatValue = down.x
            downY.floatValue = down.y
            downPull.floatValue = down.pull
        }
    }
}

/**
 * The background's clock and parallax shift. It ticks every [stepMillis] at most (no point
 * repainting a slow drift at 120 Hz), only while the screen is showing, and not at all if
 * [animate] is off or the phone's "Remove animations" (animator duration scale 0) is on; the
 * parallax stops then too. [look] sets the speed, movement and whether the phone's tilt counts.
 */
@Composable
internal fun rememberBackgroundFrame(animate: Boolean, stepMillis: Long, look: BackgroundLook): BackgroundFrameState {
    val context = LocalContext.current
    val running = animate && remember(context) { animationsOn(context) }
    val frame = remember { BackgroundFrameState().also { if (running) it.copyFrom(sharedClock, null) } }
    val tilt = rememberTilt(running && (look.parallax || look.followsGravity))
    val view = LocalView.current
    val gravity by rememberUpdatedState(look.followsGravity)
    val speed by rememberUpdatedState(look.speed)
    val movement by rememberUpdatedState(look.movement)
    val parallax by rememberUpdatedState(look.parallax)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(running, stepMillis, lifecycle) {
        if (!running) {
            frame.showStill()
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastStep = Long.MIN_VALUE
            val ease = TiltEase()
            val down = DownEase()
            val stepNanos = stepMillis * 1_000_000L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    sharedClock.advance(now, speed, movement)
                    ease.advance(now, if (parallax) tilt.x else 0f, if (parallax) tilt.y else 0f)
                    if (gravity && tilt.hasGravity) {
                        val (sx, sy) = screenDown(tilt.gx, tilt.gy, view.display?.rotation ?: Surface.ROTATION_0)
                        down.advance(sx, sy)
                    }
                    val step = now / stepNanos
                    if (step != lastStep) {
                        lastStep = step
                        frame.copyFrom(sharedClock, ease, down)
                    }
                }
            }
        }
    }
    return frame
}

/** False when the phone's "Remove animations" (animator duration scale 0) is on: backgrounds then stay still. */
fun animationsOn(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/** A sine wave that goes round [times] times per loop. */
internal fun wave(t: Float, times: Int, phase: Float = 0f): Float = sin(TAU * t / LOOP_SECONDS * times + phase)

/** 0 rising to 1, [times] times per loop, starting from [start]. */
internal fun cycle(t: Float, times: Int, start: Float = 0f): Float = frac(start + t / LOOP_SECONDS * times)

internal fun frac(x: Float): Float = x - floor(x)

/** Goes 0 → 1 → 0 as [x] goes 0 → 1 → 2, like something bouncing between two walls. */
internal fun bounce(x: Float): Float {
    val p = frac(x / 2f) * 2f
    return if (p <= 1f) p else 2f - p
}

/** A fixed "random" number from 0 to 1 for [seed], the same every time, without making a Random. */
internal fun noise(seed: Int): Float {
    var x = seed * -0x61c88647 + 0x165667b1
    x = (x xor (x ushr 15)) * 0x2c1b3c6d
    x = (x xor (x ushr 12)) * 0x297a2d39
    x = x xor (x ushr 15)
    return (x ushr 8) / 16777216f
}

/**
 * Which way is down on screen, from a gravity reading along the phone's own sideways ([gx]) and
 * up-down ([gy]) axes (as a share of full gravity), allowing for the screen being turned
 * ([rotation], a Surface.ROTATION_ value). Gives x to the right and y down the screen.
 */
internal fun screenDown(gx: Float, gy: Float, rotation: Int): Pair<Float, Float> {
    // The reading turned to line up with the screen.
    val (x, y) = when (rotation) {
        Surface.ROTATION_90 -> -gy to gx
        Surface.ROTATION_180 -> -gx to -gy
        Surface.ROTATION_270 -> gy to -gx
        else -> gx to gy
    }
    // The sensor feels the push holding the phone up, so down is the other way; and screen y
    // points down where the sensor's points up.
    return -x to y
}

/**
 * Down on screen, eased so rain swings round smoothly rather than snapping as the phone turns.
 * Lying flat there's no clear down, so it keeps the last one and [pull] drops, letting the
 * drops crawl rather than run.
 */
internal class DownEase {
    var x = 0f
        private set
    var y = 1f
        private set
    var pull = 1f
        private set

    /** One frame's reading: [sx], [sy] is down on screen, as long as full gravity when the phone is upright. */
    fun advance(sx: Float, sy: Float) {
        val m = sqrt(sx * sx + sy * sy)
        pull += ((m / 0.6f).coerceIn(0.25f, 1f) - pull) * 0.05f
        if (m < 0.12f) return
        x += (sx / m - x) * 0.06f
        y += (sy / m - y) * 0.06f
        val len = sqrt(x * x + y * y).coerceAtLeast(1e-4f)
        x /= len
        y /= len
    }
}
