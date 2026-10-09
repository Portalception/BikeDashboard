package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Small glowing specks drifting about and bouncing softly off the edges. Specks that come close
 * to each other are joined by a faint line, which brightens as they get nearer, so loose
 * constellations form and break apart.
 */
internal class ParticlesScene : BackgroundScene {
    private val count = 44
    private val startX = FloatArray(count)
    private val startY = FloatArray(count)

    /** Round trips across the screen per loop; the sign is the direction. */
    private val tripsX = IntArray(count)
    private val tripsY = IntArray(count)
    private val wobble = FloatArray(count)
    private val radius = FloatArray(count)

    private val px = FloatArray(count)
    private val py = FloatArray(count)

    init {
        val random = Random(0x9A7)
        for (i in 0 until count) {
            startX[i] = random.nextFloat() * 2f
            startY[i] = random.nextFloat() * 2f
            tripsX[i] = (1 + random.nextInt(3)) * (if (random.nextBoolean()) 1 else -1)
            tripsY[i] = (1 + random.nextInt(3)) * (if (random.nextBoolean()) 1 else -1)
            wobble[i] = random.nextFloat() * TAU
            // Mostly small, a few bigger ones for depth.
            radius[i] = when (random.nextInt(10)) {
                in 0..5 -> 0.005f + random.nextFloat() * 0.003f
                in 6..8 -> 0.008f + random.nextFloat() * 0.004f
                else -> 0.012f + random.nextFloat() * 0.004f
            }
        }
    }

    override val layered: Boolean get() = true

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val t = f.t
        val m = f.motion
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val margin = unit * 0.03f
        for (i in 0 until count) {
            // Each trip there and back is 2 on the bounce scale, so the loop lines up.
            val bx = bounce(startX[i] + 2f * tripsX[i] * f.travel / LOOP_SECONDS) + 0.012f * m * wave(t, 40, wobble[i])
            val by = bounce(startY[i] + 2f * tripsY[i] * f.travel / LOOP_SECONDS) + 0.012f * m * wave(t, 30, wobble[i] * 1.7f)
            // Bigger specks are nearer, so they move more with the tilt.
            val depth = 0.3f + radius[i] / 0.016f * 0.7f
            px[i] = margin + bx * (w - 2 * margin) + f.shiftX * depth
            py[i] = margin + by * (h - 2 * margin) + f.shiftY * depth
        }
        val reach = unit * 0.26f
        val stroke = (unit * 0.0028f).coerceAtLeast(1f)
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val dx = px[i] - px[j]
                val dy = py[i] - py[j]
                val d2 = dx * dx + dy * dy
                if (d2 >= reach * reach) continue
                val near = 1f - sqrt(d2) / reach
                drawLine(ink.blend, Offset(px[i], py[i]), Offset(px[j], py[j]), stroke, alpha = ink.a(0.2f * near * near))
            }
        }
        for (i in 0 until count) {
            val colour = if (i % 3 == 0) ink.accent else ink.primary
            val r = radius[i] * unit
            val center = Offset(px[i], py[i])
            drawCircle(colour, r * 2.3f, center, alpha = ink.a(0.08f))
            drawCircle(colour, r, center, alpha = ink.a(0.4f))
        }
    }
}
