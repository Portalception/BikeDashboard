package com.astheral.bikedashboard.ui.background

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
private const val LOOP_NANOS = 1_200_000_000_000L

/** The moment shown when the phone's animations are switched off, and in still previews. */
internal const val STILL_SECONDS = 47f

internal const val TAU = 6.2831855f

/**
 * One shared starting point for every background, so moving between screens carries on the
 * same picture instead of jumping back to the start.
 */
private object BackgroundClock {
    var originNanos = -1L
}

/**
 * The background's time in seconds, read only while drawing so nothing recomposes per frame.
 * It ticks every [stepMillis] at most (no point repainting a slow drift at 120 Hz), only while
 * the screen is showing, and not at all if [animate] is off or the phone's "Remove animations"
 * (animator duration scale 0) is on.
 */
@Composable
internal fun rememberBackgroundTime(animate: Boolean, stepMillis: Long): State<Float> {
    val context = LocalContext.current
    val running = animate && remember(context) { animationsOn(context) }
    val time = remember {
        val origin = BackgroundClock.originNanos
        mutableFloatStateOf(if (running && origin >= 0) secondsAt(System.nanoTime() - origin, stepMillis) else STILL_SECONDS)
    }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(running, stepMillis, lifecycle) {
        if (!running) {
            time.floatValue = STILL_SECONDS
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastStep = Long.MIN_VALUE
            val stepNanos = stepMillis * 1_000_000L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (BackgroundClock.originNanos < 0) BackgroundClock.originNanos = now
                    val step = (now - BackgroundClock.originNanos) / stepNanos
                    if (step != lastStep) {
                        lastStep = step
                        time.floatValue = ((step * stepNanos) % LOOP_NANOS) / 1e9f
                    }
                }
            }
        }
    }
    return time
}

private fun secondsAt(elapsedNanos: Long, stepMillis: Long): Float {
    val stepNanos = stepMillis * 1_000_000L
    return ((elapsedNanos / stepNanos * stepNanos) % LOOP_NANOS) / 1e9f
}

private fun animationsOn(context: Context): Boolean =
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
