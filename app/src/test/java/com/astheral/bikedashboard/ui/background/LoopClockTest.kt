package com.astheral.bikedashboard.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopClockTest {
    private val second = 1_000_000_000L

    /** Advances [clock] from [from] seconds in 50 ms frames for [seconds] seconds. */
    private fun run(clock: LoopClock, from: Double, seconds: Double, speed: Float, movement: Float = 1f): Double {
        var t = from
        val end = from + seconds
        while (t < end - 1e-9) {
            t += 0.05
            clock.advance((t * second).toLong(), speed, movement)
        }
        return t
    }

    @Test
    fun timeRunsAtTheChosenSpeed() {
        val clock = LoopClock(start = 0f)
        clock.advance(0L, 1f, 1f)
        run(clock, 0.0, 10.0, speed = 2f)
        assertEquals(20.0, clock.time, 1e-3)
    }

    @Test
    fun changingSpeedDoesNotJump() {
        val clock = LoopClock(start = 0f)
        clock.advance(0L, 1f, 1f)
        val now = run(clock, 0.0, 10.0, speed = 1f)
        val before = clock.time
        // One frame later at triple speed only moves on by one frame's worth.
        clock.advance(((now + 0.05) * second).toLong(), 3f, 1f)
        assertEquals(before + 0.15, clock.time, 1e-3)
    }

    @Test
    fun movementOnlyScalesTravel() {
        val clock = LoopClock(start = 0f)
        clock.advance(0L, 1f, 0f)
        run(clock, 0.0, 5.0, speed = 1f, movement = 0f)
        assertEquals(5.0, clock.time, 1e-3)
        assertEquals(0.0, clock.travel, 1e-6)
    }

    @Test
    fun sameFrameTwiceCountsOnce() {
        val clock = LoopClock(start = 0f)
        clock.advance(0L, 1f, 1f)
        clock.advance(second / 20, 1f, 1f)
        clock.advance(second / 20, 1f, 1f)
        assertEquals(0.05, clock.time, 1e-6)
    }

    @Test
    fun longPauseIsOneShortStep() {
        val clock = LoopClock(start = 0f)
        clock.advance(0L, 1f, 1f)
        clock.advance(60 * second, 1f, 1f)
        assertTrue(clock.time <= 0.1 + 1e-9)
    }

    @Test
    fun wrapsRoundTheLoop() {
        val clock = LoopClock(start = LOOP_SECONDS - 1f)
        clock.advance(0L, 1f, 1f)
        run(clock, 0.0, 2.0, speed = 1f)
        assertEquals(1.0, clock.time, 1e-3)
    }

    @Test
    fun tiltGlidesTowardsTheTarget() {
        val ease = TiltEase()
        ease.advance(0L, 1f, -1f)
        var t = 0L
        repeat(20) { t += second / 60; ease.advance(t, 1f, -1f) }
        assertTrue(ease.x in 0.8f..1f)
        assertTrue(ease.y in -1f..-0.8f)
    }
}
