package com.astheral.bikedashboard.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMathTest {
    @Test
    fun unknownOrMissingStyleFallsBackToCircles() {
        assertEquals(BackgroundStyle.CIRCLES, BackgroundStyle.fromName(null))
        assertEquals(BackgroundStyle.CIRCLES, BackgroundStyle.fromName("LAVA"))
        assertEquals(BackgroundStyle.MATRIX, BackgroundStyle.fromName("MATRIX"))
        assertEquals(BackgroundStyle.NONE, BackgroundStyle.fromName("NONE"))
    }

    @Test
    fun movementsLineUpWhenTheClockWraps() {
        for (times in listOf(1, 7, 20, 300)) {
            assertEquals(wave(0f, times, 0.4f), wave(LOOP_SECONDS, times, 0.4f), 1e-3f)
            assertEquals(cycle(0f, times, 0.3f), cycle(LOOP_SECONDS, times, 0.3f), 1e-3f)
        }
        // A whole number of round trips ends where it started.
        assertEquals(bounce(0.35f), bounce(0.35f + 2f * 3), 1e-4f)
    }

    @Test
    fun bounceStaysBetweenTheWalls() {
        assertEquals(0.5f, bounce(1.5f), 1e-5f)
        assertEquals(0.25f, bounce(-0.25f), 1e-5f)
        var x = -3f
        while (x < 3f) {
            assertTrue(bounce(x) in 0f..1f)
            x += 0.07f
        }
    }

    @Test
    fun noiseIsFixedAndInRange() {
        assertEquals(noise(42), noise(42), 0f)
        for (seed in -500..500) assertTrue(noise(seed) >= 0f && noise(seed) < 1f)
    }
}
