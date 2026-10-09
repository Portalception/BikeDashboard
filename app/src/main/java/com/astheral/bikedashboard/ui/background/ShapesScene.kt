package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wireframe solids (a cube, a hex nut, a pyramid and friends) floating around the screen,
 * each turning slowly about all three axes. Edges at the back are fainter than the ones at the
 * front, so they read as 3D.
 */
internal class ShapesScene : BackgroundScene {
    /** A solid's corners (x, y, z in turn, scaled to fit a unit ball) and the corners each edge joins. */
    private class Solid(corners: FloatArray) {
        val points: FloatArray
        val edges: IntArray
        val size: Int get() = points.size / 3

        init {
            val n = corners.size / 3
            var far = 0f
            for (i in 0 until n) far = maxOf(far, length(corners, i, null, 0))
            points = FloatArray(corners.size) { corners[it] / far }
            // Every solid here has edges all the same length, the shortest gap between corners.
            var shortest = Float.MAX_VALUE
            for (i in 0 until n) for (j in i + 1 until n) shortest = minOf(shortest, length(points, i, points, j))
            val pairs = ArrayList<Int>()
            for (i in 0 until n) for (j in i + 1 until n) {
                if (length(points, i, points, j) < shortest * 1.01f) { pairs += i; pairs += j }
            }
            edges = pairs.toIntArray()
        }

        private fun length(a: FloatArray, i: Int, b: FloatArray?, j: Int): Float {
            val dx = a[i * 3] - (b?.get(j * 3) ?: 0f)
            val dy = a[i * 3 + 1] - (b?.get(j * 3 + 1) ?: 0f)
            val dz = a[i * 3 + 2] - (b?.get(j * 3 + 2) ?: 0f)
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    private class Floater(
        val solid: Solid,
        val x: Float, val y: Float,
        /** Size as a share of the screen's shorter side. */
        val scale: Float,
        val spinX: Int, val spinY: Int, val spinZ: Int,
        val driftX: Float, val driftY: Float, val timesX: Int, val timesY: Int,
        val phase: Float,
        val accent: Boolean,
    )

    private val cube = Solid(floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, -1f, 1f, 1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, 1f))
    private val octahedron = Solid(floatArrayOf(1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f))
    private val pyramid = Solid(floatArrayOf(1f, 1f, 1f, 1f, -1f, -1f, -1f, 1f, -1f, -1f, -1f, 1f))
    private val icosahedron = run {
        val g = 1.618034f
        Solid(
            floatArrayOf(
                0f, 1f, g, 0f, -1f, g, 0f, 1f, -g, 0f, -1f, -g,
                1f, g, 0f, -1f, g, 0f, 1f, -g, 0f, -1f, -g, 0f,
                g, 0f, 1f, -g, 0f, 1f, g, 0f, -1f, -g, 0f, -1f,
            ),
        )
    }

    /** A six-sided prism with sides as long as it is thick: a nut, near enough. */
    private val nut = prism(6)
    private val wedge = prism(3)

    private val floaters = arrayOf(
        Floater(cube, 0.20f, 0.14f, 0.11f, 30, 20, 0, 0.06f, 0.03f, 12, 15, 0.0f, false),
        Floater(nut, 0.80f, 0.26f, 0.10f, 24, -15, 10, 0.05f, 0.04f, 15, 12, 1.2f, true),
        Floater(icosahedron, 0.42f, 0.44f, 0.09f, -20, 24, 12, 0.07f, 0.04f, 10, 16, 2.4f, false),
        Floater(octahedron, 0.16f, 0.62f, 0.09f, 15, 30, -12, 0.05f, 0.04f, 16, 10, 3.1f, true),
        Floater(pyramid, 0.84f, 0.60f, 0.08f, 24, -20, 15, 0.04f, 0.05f, 12, 20, 4.0f, false),
        Floater(wedge, 0.30f, 0.88f, 0.09f, -15, 24, 20, 0.06f, 0.03f, 15, 12, 5.2f, false),
        Floater(nut, 0.74f, 0.90f, 0.07f, 20, 15, -24, 0.05f, 0.03f, 10, 15, 0.7f, true),
    )

    // One solid's turned corners at a time.
    private val rx = FloatArray(12)
    private val ry = FloatArray(12)
    private val rz = FloatArray(12)

    override fun DrawScope.paint(t: Float, ink: BackgroundInk) {
        val unit = size.minDimension
        val stroke = (unit * 0.0045f).coerceAtLeast(1f)
        for (f in floaters) {
            val cx = (f.x + f.driftX * wave(t, f.timesX, f.phase)) * size.width
            val cy = (f.y + f.driftY * wave(t, f.timesY, f.phase * 1.4f)) * size.height
            val r = f.scale * unit
            val ax = TAU * cycle(t, f.spinX, f.phase)
            val ay = TAU * cycle(t, f.spinY, f.phase * 0.5f)
            val az = TAU * cycle(t, f.spinZ, f.phase * 0.3f)
            val cosX = cos(ax); val sinX = sin(ax)
            val cosY = cos(ay); val sinY = sin(ay)
            val cosZ = cos(az); val sinZ = sin(az)
            val points = f.solid.points
            for (i in 0 until f.solid.size) {
                val x0 = points[i * 3]
                val y0 = points[i * 3 + 1]
                val z0 = points[i * 3 + 2]
                // Turn about x, then y, then z.
                val y1 = y0 * cosX - z0 * sinX
                val z1 = y0 * sinX + z0 * cosX
                val x2 = x0 * cosY + z1 * sinY
                val z2 = -x0 * sinY + z1 * cosY
                val x3 = x2 * cosZ - y1 * sinZ
                val y3 = x2 * sinZ + y1 * cosZ
                val near = 4f / (4f - z2)
                rx[i] = cx + x3 * near * r
                ry[i] = cy + y3 * near * r
                rz[i] = z2
            }
            val colour = if (f.accent) ink.accent else ink.primary
            val edges = f.solid.edges
            var e = 0
            while (e < edges.size) {
                val a = edges[e]
                val b = edges[e + 1]
                // 0 at the back, 1 at the front.
                val depth = ((rz[a] + rz[b]) / 2f + 1f) / 2f
                drawLine(colour, Offset(rx[a], ry[a]), Offset(rx[b], ry[b]), stroke, alpha = ink.a(0.32f * (0.25f + 0.75f * depth)))
                e += 2
            }
        }
    }

    private fun prism(sides: Int): Solid {
        // Corners on a circle whose side length is 1, top and bottom half a unit apart.
        val radius = 0.5f / sin(TAU / sides / 2f)
        val corners = FloatArray(sides * 6)
        for (k in 0 until sides) {
            val angle = TAU * k / sides
            for (level in 0..1) {
                val i = (k * 2 + level) * 3
                corners[i] = radius * cos(angle)
                corners[i + 1] = radius * sin(angle)
                corners[i + 2] = if (level == 0) -0.5f else 0.5f
            }
        }
        return Solid(corners)
    }
}
