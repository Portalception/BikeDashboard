package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * The world streaking past at speed: thin horizontal motion lines of different lengths sweeping
 * across the screen, near ones longer, thicker and faster than far ones, each fading towards its
 * tail and wavering slightly as if in the wind.
 */
internal class SpeedLinesScene : BackgroundScene {
    override val layered: Boolean get() = true

    private val count = 46
    private val y = FloatArray(count)
    private val length = FloatArray(count)
    private val depth = FloatArray(count)
    private val passes = IntArray(count)
    private val start = FloatArray(count)
    private val sway = IntArray(count)
    private val phase = FloatArray(count)

    init {
        val random = Random(0x5EED)
        for (i in 0 until count) {
            depth[i] = random.nextFloat()
            y[i] = 0.03f + 0.94f * random.nextFloat()
            length[i] = 0.12f + (0.18f + 0.35f * depth[i]) * random.nextFloat()
            // Far lines cross the screen in about 6 seconds, near ones in under 2.
            passes[i] = (200 + 450 * depth[i] + random.nextInt(60)).toInt()
            start[i] = random.nextFloat()
            sway[i] = 40 + random.nextInt(80)
            phase[i] = random.nextFloat() * TAU
        }
    }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        for (i in 0 until count) {
            val d = depth[i]
            val len = length[i] * w
            val p = cycle(f.travel, passes[i], start[i])
            val head = w * 1.05f - p * (w * 1.1f + len)
            val lineY = y[i] * h + unit * 0.012f * f.motion * wave(f.t, sway[i], phase[i]) + f.shiftY * (0.2f + 0.8f * d)
            val x0 = head + f.shiftX * (0.2f + 0.8f * d)
            val width = (unit * (0.0015f + 0.005f * d)).coerceAtLeast(1f)
            val colour = when (i % 4) {
                0 -> ink.accent
                1 -> ink.primary
                2 -> ink.bright
                else -> ink.blend
            }
            val alpha = ink.a(0.12f + 0.3f * d)
            // Brightest at the head, fading in four steps to the tail.
            for (k in 0 until 4) {
                drawLine(
                    colour,
                    Offset(x0 + len * k / 4f, lineY),
                    Offset(x0 + len * (k + 1) / 4f, lineY),
                    width,
                    alpha = alpha * (1f - k * 0.24f),
                )
            }
            if (d > 0.75f) drawCircle(colour, width * 1.2f, Offset(x0, lineY), alpha = alpha)
        }
    }
}
