package com.astheral.bikedashboard.ui.background

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The centre line of each [RaceTrack], traced densely as one closed loop, scaled to fill -1 to 1
 * each way (y pointing down the screen). Pure maths, so it can be tested.
 */
internal object TrackShapes {
    /** The loop's points, as x and y arrays of the same length (the last point joins back to the first). */
    fun trace(track: RaceTrack): Pair<FloatArray, FloatArray> {
        val (x, y) = when (track) {
            RaceTrack.CLASSIC -> curve(900) { a ->
                // A rounded rectangle with a hairpin pushed in from the right.
                val c = cos(a)
                val s = sin(a)
                val fromRight = if (a > TAU / 2) a - TAU else a
                (sign(c) * abs(c).pow(0.55f) - 0.75f * exp(-(fromRight / 0.32f).let { it * it })) to
                    (sign(s) * abs(s).pow(0.55f) + 0.1f * sin(3 * a + 0.6f))
            }
            // A long oval: two straights and two sweeping banked turns.
            RaceTrack.SPEEDWAY -> curve(900) { a ->
                val c = cos(a)
                val s = sin(a)
                (sign(c) * abs(c).pow(0.8f) * 0.7f) to (sign(s) * abs(s).pow(0.35f))
            }
            // Right-angle corners round city blocks.
            RaceTrack.STREET -> smoothPolygon(
                floatArrayOf(-1f, 0.15f, 0.15f, 1f, 1f, 0.35f, 0.35f, -0.35f, -0.35f, -1f),
                floatArrayOf(-1f, -1f, -0.35f, -0.35f, 1f, 1f, 0.45f, 0.45f, 0.1f, 0.1f),
                rounds = 4,
            )
            // Esses, a chicane and a tight hairpin.
            RaceTrack.TWISTY -> smoothPolygon(
                floatArrayOf(-0.85f, 0.55f, 1f, 0.35f, 0.95f, 0.7f, -0.1f, -0.45f, -1f, -0.55f, -1f),
                floatArrayOf(-1f, -0.95f, -0.45f, -0.15f, 0.35f, 1f, 0.65f, 1f, 0.6f, 0f, -0.45f),
                rounds = 4,
            )
            // Crosses over itself in the middle; the start line sits on a lobe, away from the crossing.
            RaceTrack.FIGURE_EIGHT -> curve(900) { a -> (0.8f * sin(2 * a + 2f)) to sin(a + 1f) }
            // Switchbacks climbing one side and a fast run back down.
            RaceTrack.MOUNTAIN -> smoothPolygon(
                floatArrayOf(-0.9f, 0.9f, 0.9f, -0.2f, 0.9f, 0.9f, -0.2f, 0.9f, 0.9f, -0.9f),
                floatArrayOf(-1f, -1f, -0.6f, -0.45f, -0.25f, 0.1f, 0.3f, 0.5f, 1f, 1f),
                rounds = 4,
            )
        }
        fit(x, y)
        return x to y
    }

    private inline fun curve(points: Int, at: (Float) -> Pair<Float, Float>): Pair<FloatArray, FloatArray> {
        val x = FloatArray(points)
        val y = FloatArray(points)
        for (k in 0 until points) {
            val (px, py) = at(TAU * k / points)
            x[k] = px
            y[k] = py
        }
        return x to y
    }

    /**
     * A closed polygon with its corners rounded off by cutting each one ([rounds] times, Chaikin's
     * way): the more rounds, the smoother. Straight sides stay straight.
     */
    fun smoothPolygon(px: FloatArray, py: FloatArray, rounds: Int): Pair<FloatArray, FloatArray> {
        var x = px
        var y = py
        repeat(rounds) {
            val n = x.size
            val nx = FloatArray(n * 2)
            val ny = FloatArray(n * 2)
            for (i in 0 until n) {
                val j = (i + 1) % n
                nx[2 * i] = 0.75f * x[i] + 0.25f * x[j]
                ny[2 * i] = 0.75f * y[i] + 0.25f * y[j]
                nx[2 * i + 1] = 0.25f * x[i] + 0.75f * x[j]
                ny[2 * i + 1] = 0.25f * y[i] + 0.75f * y[j]
            }
            x = nx
            y = ny
        }
        return x to y
    }

    /** Stretches the loop to fill -1 to 1 each way. */
    private fun fit(x: FloatArray, y: FloatArray) {
        val minX = x.min()
        val maxX = x.max()
        val minY = y.min()
        val maxY = y.max()
        for (i in x.indices) {
            x[i] = (x[i] - minX) / (maxX - minX) * 2 - 1
            y[i] = (y[i] - minY) / (maxY - minY) * 2 - 1
        }
    }
}
