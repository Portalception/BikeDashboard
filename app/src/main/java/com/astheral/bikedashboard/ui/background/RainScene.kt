package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Rain on your visor: beads of water land, sit for a while, then the wind catches them and
 * they streak sideways across the screen, leaving a thin wet trail with a few tiny beads along
 * it. Fine mist glints in between. Each drop lands somewhere new every time round.
 */
internal class RainScene : BackgroundScene {
    private val drops = 34
    private val lives = IntArray(drops)
    private val start = FloatArray(drops)

    private val mist = 70
    private val mistX = FloatArray(mist)
    private val mistY = FloatArray(mist)
    private val mistTimes = IntArray(mist)
    private val mistPhase = FloatArray(mist)

    private var ring = Stroke(1f)

    init {
        val random = Random(0x4A1)
        for (i in 0 until drops) {
            // Each drop lives 20 to 40 seconds before landing again somewhere else.
            lives[i] = 30 + random.nextInt(30)
            start[i] = random.nextFloat()
        }
        for (i in 0 until mist) {
            mistX[i] = random.nextFloat()
            mistY[i] = random.nextFloat()
            mistTimes[i] = 60 + random.nextInt(120)
            mistPhase[i] = random.nextFloat() * TAU
        }
    }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val ringWidth = (unit * 0.0025f).coerceAtLeast(1f)
        if (ring.width != ringWidth) ring = Stroke(ringWidth)

        for (i in 0 until mist) {
            val glint = 0.5f + 0.5f * wave(f.t, mistTimes[i], mistPhase[i])
            drawCircle(ink.bright, unit * 0.0025f, Offset(mistX[i] * w, mistY[i] * h), alpha = ink.a(0.05f + 0.12f * glint))
        }

        for (i in 0 until drops) {
            val life = f.travel / LOOP_SECONDS * lives[i] + start[i]
            val p = life - floor(life)
            // A new place each time round, from the round's number.
            val seed = i * 131 + floor(life).toInt() * 7
            val x0 = noise(seed) * w * 1.1f
            val y0 = noise(seed + 1) * h
            val r = unit * (0.008f + 0.014f * noise(seed + 2))
            val letGo = 0.45f + 0.3f * noise(seed + 3)
            // Wind pushes left and a little down.
            var dx = -1f
            var dy = 0.25f + 0.4f * noise(seed + 4)
            val len = sqrt(dx * dx + dy * dy)
            dx /= len
            dy /= len

            val appear = (p / 0.06f).coerceAtMost(1f)
            val q = if (p < letGo) 0f else (p - letGo) / (1f - letGo)
            val gone = if (q < 0.75f) 1f else (1f - q) / 0.25f
            val fade = appear * gone
            val travel = q * q * unit * (0.7f + 0.6f * noise(seed + 5))
            val wobble = r * 0.4f * f.motion * wave(f.t, 300, i.toFloat())
            val x = x0 + dx * travel - dy * wobble * q
            val y = y0 + dy * travel + dx * wobble * q
            val bead = r * (1f + 0.15f * (1f - q) * appear) * (1f - 0.45f * q)

            if (q > 0f) {
                drawLine(ink.bright, Offset(x0, y0), Offset(x, y), bead * 0.6f, alpha = ink.a(0.07f * fade))
                for (b in 1..2) {
                    val t = b / 3f
                    drawCircle(ink.bright, bead * 0.3f, Offset(x0 + (x - x0) * t, y0 + (y - y0) * t), alpha = ink.a(0.16f * fade))
                }
            }
            val c = Offset(x, y)
            drawCircle(ink.bright, bead, c, alpha = ink.a(0.07f * fade))
            drawCircle(ink.primary, bead, c, alpha = ink.a(0.26f * fade), style = ring)
            drawCircle(ink.bright, bead * 0.28f, Offset(x - bead * 0.35f, y - bead * 0.35f), alpha = ink.a(0.4f * fade))
        }
    }
}
