package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flying through a starfield: stars start as faint points near the middle and stream outwards,
 * growing and brightening, stretching into short light trails as they rush past. Every 40
 * seconds a shooting star crosses the screen.
 */
internal class SpaceScene : BackgroundScene {
    private val count = 130
    private val dirX = FloatArray(count)
    private val dirY = FloatArray(count)
    private val spread = FloatArray(count)
    private val startZ = FloatArray(count)
    private val trips = IntArray(count)

    init {
        val random = Random(0x57A)
        for (i in 0 until count) {
            // Golden-angle spacing spreads the stars evenly round the middle.
            val angle = i * 2.3999631f + random.nextFloat() * 0.3f
            dirX[i] = cos(angle)
            dirY[i] = sin(angle)
            spread[i] = 0.15f + random.nextFloat() * 0.85f
            startZ[i] = random.nextFloat()
            // Each star reaches you every 25 to 40 seconds.
            trips[i] = 30 + random.nextInt(19)
        }
    }

    override val layered: Boolean get() = true

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val t = f.travel
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val unit = size.minDimension
        val reach = max(w, h) * 0.1f
        for (i in 0 until count) {
            // Distance away: 1 far off, near 0 right by you.
            val z = 1f - cycle(t, trips[i], startZ[i])
            val depth = 0.06f + 0.94f * z
            val r = spread[i] * reach / depth
            val close = 1f - z
            // Near stars move more with the tilt than far ones.
            val px = f.shiftX * (0.2f + 0.8f * close)
            val py = f.shiftY * (0.2f + 0.8f * close)
            val x = cx + px + dirX[i] * r
            val y = cy + py + dirY[i] * r
            if (x < -20f || x > w + 20f || y < -20f || y > h + 20f) continue
            val alpha = ink.a(0.08f + 0.5f * close * close)
            val dot = (unit * 0.0022f / depth).coerceAtMost(unit * 0.01f)
            val colour = if (i % 5 == 0) ink.accent else ink.bright
            if (depth < 0.35f) {
                // A trail back to where it was a moment ago.
                val back = spread[i] * reach / (depth + 0.06f)
                drawLine(colour, Offset(cx + px + dirX[i] * back, cy + py + dirY[i] * back), Offset(x, y), dot, alpha = alpha * 0.6f)
            }
            drawCircle(colour, dot, Offset(x, y), alpha = alpha)
        }
        shootingStar(t, ink)
    }

    private fun DrawScope.shootingStar(t: Float, ink: BackgroundInk) {
        val loop = t / LOOP_SECONDS * 30f
        val p = frac(loop)
        if (p > 0.06f) return
        // Each pass takes its own path, picked from the pass number.
        val pass = floor(loop).toInt()
        val sx = noise(pass * 3) * 0.6f + 0.2f
        val sy = noise(pass * 3 + 1) * 0.35f
        val left = noise(pass * 3 + 2) < 0.5f
        val run = p / 0.06f
        val travel = size.minDimension * 0.9f
        val dx = (if (left) -1f else 1f) * 0.8f
        val dy = 0.6f
        val head = Offset(sx * size.width + dx * travel * run, sy * size.height + dy * travel * run)
        val tail = size.minDimension * 0.18f
        val fade = if (run < 0.8f) 1f else (1f - run) / 0.2f
        val stroke = (size.minDimension * 0.004f).coerceAtLeast(1f)
        for (k in 0 until 5) {
            val from = tail * k / 5f
            val to = tail * (k + 1) / 5f
            drawLine(
                ink.bright,
                Offset(head.x - dx * from, head.y - dy * from),
                Offset(head.x - dx * to, head.y - dy * to),
                stroke * (1f - k * 0.15f),
                alpha = ink.a(0.55f * fade * (1f - k / 5f)),
            )
        }
    }
}
