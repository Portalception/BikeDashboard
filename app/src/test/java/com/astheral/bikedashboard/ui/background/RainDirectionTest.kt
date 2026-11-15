package com.astheral.bikedashboard.ui.background

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainDirectionTest {
    private fun assertDown(expectX: Float, expectY: Float, got: Pair<Float, Float>) {
        assertEquals(expectX, got.first, 1e-4f)
        assertEquals(expectY, got.second, 1e-4f)
    }

    @Test
    fun uprightPhoneRainsDownTheScreen() {
        assertDown(0f, 1f, screenDown(0f, 1f, Surface.ROTATION_0))
    }

    @Test
    fun tippingTheRightEdgeDownRunsRight() {
        // The sensor then feels the push from the left edge.
        assertDown(1f, 0f, screenDown(-1f, 0f, Surface.ROTATION_0))
    }

    @Test
    fun turnedScreensStillRainDownwards() {
        // Held in landscape, whichever way round, down is still down the screen.
        assertDown(0f, 1f, screenDown(1f, 0f, Surface.ROTATION_90))
        assertDown(0f, 1f, screenDown(-1f, 0f, Surface.ROTATION_270))
        assertDown(0f, 1f, screenDown(0f, -1f, Surface.ROTATION_180))
    }

    @Test
    fun downEasesRoundAndKeepsItsWayWhenFlat() {
        val ease = DownEase()
        repeat(300) { ease.advance(1f, 0f) }
        assertEquals(1f, ease.x, 1e-3f)
        assertEquals(0f, ease.y, 1e-3f)
        assertEquals(1f, ease.pull, 1e-3f)
        // Lying flat: same way, but the pull weakens.
        repeat(300) { ease.advance(0.01f, 0f) }
        assertEquals(1f, ease.x, 1e-3f)
        assertEquals(0.25f, ease.pull, 1e-3f)
    }

    @Test
    fun savedDirectionsLoadAndUnknownOnesFallBackToLeft() {
        assertEquals(RainDirection.LEFT, RainDirection.fromName(null))
        assertEquals(RainDirection.LEFT, RainDirection.fromName("SIDEWAYS"))
        assertEquals(RainDirection.GRAVITY, RainDirection.fromName("GRAVITY"))
        // A 3 by 3 compass with Gravity in the middle.
        assertEquals(9, RainDirection.entries.size)
        assertEquals(RainDirection.GRAVITY, RainDirection.entries[4])
    }

    @Test
    fun onlyRainFollowingGravityNeedsTheSensor() {
        assertTrue(BackgroundLook(style = BackgroundStyle.RAIN, rain = RainDirection.GRAVITY).followsGravity)
        assertFalse(BackgroundLook(style = BackgroundStyle.RAIN, rain = RainDirection.DOWN).followsGravity)
        assertFalse(BackgroundLook(style = BackgroundStyle.SNOW, rain = RainDirection.GRAVITY).followsGravity)
    }
}
