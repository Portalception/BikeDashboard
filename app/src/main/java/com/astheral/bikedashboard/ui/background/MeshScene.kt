package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * A wireframe sheet of triangles seen at a slight angle, rolling in slow hills like a flag in
 * a breeze. Each point also wanders a little on its own, so the triangles never look stiff.
 */
internal class MeshScene : BackgroundScene {
    private val cols = 9
    private val rows = 13
    private val count = cols * rows

    // Per point: how far it wanders, how high its hill goes, and where in its swing it starts.
    private val wanderX = FloatArray(count)
    private val wanderY = FloatArray(count)
    private val lift = FloatArray(count)
    private val phase = FloatArray(count)

    // Where each point lands on screen this frame.
    private val px = FloatArray(count)
    private val py = FloatArray(count)

    init {
        val random = Random(0x3E5)
        for (i in 0 until count) {
            wanderX[i] = (random.nextFloat() - 0.5f) * 0.045f
            wanderY[i] = (random.nextFloat() - 0.5f) * 0.045f
            lift[i] = 0.4f + random.nextFloat() * 0.6f
            phase[i] = random.nextFloat() * TAU
        }
    }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val t = f.t
        val m = f.motion
        val w = size.width
        val h = size.height
        val camera = max(w, h) * 1.8f
        val hill = size.minDimension * 0.16f * m
        val tilt = 0.4f
        val cosTilt = cos(tilt)
        val sinTilt = sin(tilt)
        val roll = TAU * cycle(t, 20)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val i = row * cols + col
                // Spread a little past the edges so the sheet always fills the screen.
                val u = -0.15f + 1.3f * col / (cols - 1) + wanderX[i] * m * wave(t, 15, phase[i])
                val v = -0.2f + 1.4f * row / (rows - 1) + wanderY[i] * m * wave(t, 12, phase[i] * 1.3f)
                val x = (u - 0.5f) * w
                val y = (v - 0.5f) * h
                val z = hill * lift[i] * sin(roll + u * 4.2f + v * 2.6f + phase[i] * 0.35f)
                val y2 = y * cosTilt - z * sinTilt
                val z2 = y * sinTilt + z * cosTilt
                val p = camera / (camera - z2)
                px[i] = w / 2f + x * p
                py[i] = h / 2f + y2 * p
            }
        }
        val stroke = (size.minDimension * 0.0035f).coerceAtLeast(1f)
        val lineAlpha = ink.a(0.14f)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val i = row * cols + col
                val colour = if ((row + col) % 3 == 0) ink.accent else ink.primary
                val here = Offset(px[i], py[i])
                if (col < cols - 1) drawLine(colour, here, Offset(px[i + 1], py[i + 1]), stroke, alpha = lineAlpha)
                if (row < rows - 1) {
                    drawLine(colour, here, Offset(px[i + cols], py[i + cols]), stroke, alpha = lineAlpha)
                    if (col < cols - 1) drawLine(colour, here, Offset(px[i + cols + 1], py[i + cols + 1]), stroke, alpha = lineAlpha * 0.7f)
                }
                drawCircle(colour, stroke * 1.4f, here, alpha = ink.a(0.22f))
            }
        }
    }
}
