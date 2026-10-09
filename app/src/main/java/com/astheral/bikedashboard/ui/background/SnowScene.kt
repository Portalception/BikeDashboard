package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gentle snowfall in three layers. Far flakes are tiny dots falling slowly, nearer ones are
 * six-armed crystals that fall faster, sway from side to side and turn as they go.
 */
internal class SnowScene : BackgroundScene {
    private val count = 70
    private val layer = IntArray(count)
    private val startX = FloatArray(count)
    private val startY = FloatArray(count)
    private val falls = IntArray(count)
    private val sway = FloatArray(count)
    private val swayTimes = IntArray(count)
    private val spin = IntArray(count)
    private val phase = FloatArray(count)

    init {
        val random = Random(0x5A0)
        for (i in 0 until count) {
            // Draw the far layer first, so nearer flakes land on top.
            layer[i] = when {
                i < 38 -> 0
                i < 60 -> 1
                else -> 2
            }
            startX[i] = random.nextFloat()
            startY[i] = random.nextFloat()
            // Screens fallen per loop: about 2½ minutes per fall far away, under a minute close up.
            falls[i] = when (layer[i]) {
                0 -> 7 + random.nextInt(3)
                1 -> 12 + random.nextInt(4)
                else -> 20 + random.nextInt(6)
            }
            sway[i] = (0.01f + random.nextFloat() * 0.02f) * (layer[i] + 1)
            swayTimes[i] = 60 + random.nextInt(60)
            spin[i] = (8 + random.nextInt(10)) * (if (random.nextBoolean()) 1 else -1)
            phase[i] = random.nextFloat() * TAU
        }
    }

    override fun DrawScope.paint(t: Float, ink: BackgroundInk) {
        val unit = size.minDimension
        val stroke = (unit * 0.003f).coerceAtLeast(1f)
        val margin = 0.05f
        for (i in 0 until count) {
            val fall = cycle(t, falls[i], startY[i])
            val y = (fall * (1f + 2 * margin) - margin) * size.height
            val x = frac(startX[i] + sway[i] * wave(t, swayTimes[i], phase[i])) * size.width
            val center = Offset(x, y)
            when (layer[i]) {
                0 -> drawCircle(ink.bright, unit * 0.0045f, center, alpha = ink.a(0.28f))
                else -> {
                    val near = layer[i] == 2
                    val arm = unit * (if (near) 0.022f else 0.012f)
                    val alpha = ink.a(if (near) 0.42f else 0.34f)
                    val turn = TAU * cycle(t, spin[i], phase[i])
                    flake(center, arm, turn, stroke, alpha, near, ink)
                }
            }
        }
    }

    /** Three lines crossing make the six arms; close flakes get a small "V" on each arm too. */
    private fun DrawScope.flake(c: Offset, arm: Float, turn: Float, stroke: Float, alpha: Float, branches: Boolean, ink: BackgroundInk) {
        for (k in 0 until 3) {
            val a = turn + k * TAU / 6f
            val dx = cos(a) * arm
            val dy = sin(a) * arm
            drawLine(ink.bright, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), stroke, alpha = alpha)
        }
        if (!branches) return
        for (k in 0 until 6) {
            val a = turn + k * TAU / 6f
            val bx = c.x + cos(a) * arm * 0.55f
            val by = c.y + sin(a) * arm * 0.55f
            for (side in -1..1 step 2) {
                val b = a + side * 0.75f
                drawLine(ink.bright, Offset(bx, by), Offset(bx + cos(b) * arm * 0.35f, by + sin(b) * arm * 0.35f), stroke, alpha = alpha)
            }
        }
    }
}
