package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * A chequered flag filling the screen, rippling softly as if held up in a breeze: waves roll
 * across from the left (the pole side, where it barely moves) and grow towards the free edge.
 * Squares facing the light are a little brighter than those turned away.
 */
internal class ChequeredFlagScene : BackgroundScene {
    private val cols = 7
    private val rowsMax = 24
    private val px = FloatArray((cols + 1) * (rowsMax + 1))
    private val py = FloatArray((cols + 1) * (rowsMax + 1))
    private val light = FloatArray((cols + 1) * (rowsMax + 1))
    private val square = Path()

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val w = size.width
        val h = size.height
        val side = w * 1.1f / cols
        val rows = minOf(rowsMax, (h * 1.1f / side).toInt() + 1)
        val left = -w * 0.05f
        val top = (h - rows * side) / 2f
        val roll = TAU * cycle(f.t, 220)
        val roll2 = TAU * cycle(f.t, 130)
        for (r in 0..rows) {
            for (c in 0..cols) {
                val i = r * (cols + 1) + c
                val u = c.toFloat() / cols
                val v = r.toFloat() / rows
                val a = TAU * (u * 1.3f + v * 0.3f) - roll
                val b = TAU * (u * 0.7f - v * 0.45f) - roll2 + 1f
                val amp = side * 0.5f * f.motion * (0.25f + 0.75f * u)
                px[i] = left + c * side + amp * 0.3f * cos(a)
                py[i] = top + r * side + amp * (sin(a) + 0.45f * sin(b))
                light[i] = 0.7f + 0.3f * cos(a) * f.motion.coerceAtMost(1f)
            }
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = r * (cols + 1) + c
                val j = i + 1
                val k = i + cols + 1
                val l = k + 1
                val shade = (light[i] + light[j] + light[k] + light[l]) / 4f
                square.reset()
                square.moveTo(px[i], py[i])
                square.lineTo(px[j], py[j])
                square.lineTo(px[l], py[l])
                square.lineTo(px[k], py[k])
                square.close()
                if ((r + c) % 2 == 0) {
                    drawPath(square, ink.primary, alpha = ink.a(0.13f * shade))
                } else {
                    drawPath(square, ink.bright, alpha = ink.a(0.035f * shade))
                }
            }
        }
    }
}
