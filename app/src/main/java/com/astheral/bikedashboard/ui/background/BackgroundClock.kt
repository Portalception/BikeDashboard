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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.floor
import kotlin.math.sin

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

    fun showStill() {
        time.floatValue = STILL_SECONDS
        travel.floatValue = STILL_SECONDS
        shiftX.floatValue = 0f
        shiftY.floatValue = 0f
    }

    fun copyFrom(clock: LoopClock, ease: TiltEase?) {
        time.floatValue = clock.time.toFloat()
        travel.floatValue = clock.travel.toFloat()
        shiftX.floatValue = ease?.x ?: 0f
        shiftY.floatValue = ease?.y ?: 0f
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
    val tilt = rememberTilt(running && look.parallax)
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
            val stepNanos = stepMillis * 1_000_000L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    sharedClock.advance(now, speed, movement)
                    ease.advance(now, if (parallax) tilt.x else 0f, if (parallax) tilt.y else 0f)
                    val step = now / stepNanos
                    if (step != lastStep) {
                        lastStep = step
                        frame.copyFrom(sharedClock, ease)
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
