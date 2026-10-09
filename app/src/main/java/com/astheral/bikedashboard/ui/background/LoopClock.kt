package com.astheral.bikedashboard.ui.background

import kotlin.math.exp
import kotlin.math.min

/**
 * The background's running time. Instead of working it out from the moment it started (which
 * would make the picture jump whenever the speed changed), each frame adds the time since the
 * last one, times the speed. Two times are kept:
 *  - [time] runs at the chosen speed, for things that sway or pulse in place;
 *  - [travel] also scales with the movement setting, for things that travel (falling snow,
 *    streaming stars), so movement 0 holds them still.
 * Both wrap round at [LOOP_SECONDS], which every scene repeats over, so the wrap can't be seen.
 */
internal class LoopClock(start: Float = STILL_SECONDS) {
    var time: Double = start.toDouble()
        private set
    var travel: Double = start.toDouble()
        private set
    private var lastNanos = -1L

    /**
     * Moves the clock on to [nowNanos]. Calling it again with the same moment does nothing, so
     * several backgrounds sharing the clock on one frame don't speed it up. A long gap (the app
     * was in the background) only counts as one short step, so it carries on where it left off.
     */
    fun advance(nowNanos: Long, speed: Float, movement: Float) {
        if (lastNanos >= 0 && nowNanos <= lastNanos) return
        if (lastNanos >= 0) {
            val dt = min(nowNanos - lastNanos, MAX_STEP_NANOS) / 1e9
            time = (time + dt * speed) % LOOP
            travel = (travel + dt * speed * movement) % LOOP
        }
        lastNanos = nowNanos
    }

    private companion object {
        val LOOP = LOOP_SECONDS.toDouble()
        const val MAX_STEP_NANOS = 100_000_000L
    }
}

/**
 * The parallax shift gliding towards the tilt: about two thirds of the way every 0.15 s,
 * whatever the frame rate, so sensor readings arriving a few times a second still move smoothly.
 */
internal class TiltEase {
    var x = 0f
        private set
    var y = 0f
        private set
    private var lastNanos = -1L

    fun advance(nowNanos: Long, targetX: Float, targetY: Float) {
        if (lastNanos >= 0 && nowNanos > lastNanos) {
            val dt = min(nowNanos - lastNanos, 100_000_000L) / 1e9
            val ease = (1.0 - exp(-dt / 0.15)).toFloat()
            x += (targetX - x) * ease
            y += (targetY - y) * ease
        }
        lastNanos = nowNanos
    }
}
