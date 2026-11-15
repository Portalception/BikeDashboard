package com.astheral.bikedashboard.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackShapesTest {
    @Test
    fun everyTrackFillsTheBoxAndIsAProperLoop() {
        for (track in RaceTrack.entries) {
            val (x, y) = TrackShapes.trace(track)
            assertEquals(track.name, x.size, y.size)
            assertTrue(track.name, x.size >= 100)
            assertEquals(track.name, -1f, x.min(), 1e-4f)
            assertEquals(track.name, 1f, x.max(), 1e-4f)
            assertEquals(track.name, -1f, y.min(), 1e-4f)
            assertEquals(track.name, 1f, y.max(), 1e-4f)
            // No jumps: neighbouring points (and the join back to the start) stay close.
            for (i in x.indices) {
                val j = (i + 1) % x.size
                val step = kotlin.math.hypot(x[j] - x[i], y[j] - y[i])
                assertTrue("${track.name} jumps at $i", step < 0.3f)
            }
        }
    }

    @Test
    fun unknownTrackFallsBackToClassic() {
        assertEquals(RaceTrack.CLASSIC, RaceTrack.fromName(null))
        assertEquals(RaceTrack.CLASSIC, RaceTrack.fromName("MONACO"))
        assertEquals(RaceTrack.STREET, RaceTrack.fromName("STREET"))
    }

    @Test
    fun roundingCornersDoublesThePointsEachTime() {
        val (x, _) = TrackShapes.smoothPolygon(floatArrayOf(0f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 1f), rounds = 3)
        assertEquals(32, x.size)
    }
}
