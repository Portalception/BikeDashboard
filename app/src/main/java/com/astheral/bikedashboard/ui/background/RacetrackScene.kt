package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A circuit seen from above: a rounded track with a hairpin bitten into one side, red-and-white
 * style kerbs on the tight corners and a chequered start line. Three glowing dots lap it on a
 * racing line, cutting to the inside of each corner, braking into the bends and pulling away
 * on the straights, each with a short light trail.
 */
internal class RacetrackScene : BackgroundScene {
    private val n = 200

    // The track's centre line in its own space (about -1 to 1 each way), evenly spaced along it.
    private val ux = FloatArray(n)
    private val uy = FloatArray(n)

    // The left-hand normal at each point, and the bend there (-1 to 1, positive turning left).
    private val nx = FloatArray(n)
    private val ny = FloatArray(n)
    private val bend = FloatArray(n)

    /** Share of a lap taken to reach each point: corners take longer than straights. */
    private val lapAt = FloatArray(n + 1)

    private val centre = Path()
    private val left = Path()
    private val right = Path()
    private var band = Stroke(1f)
    private var edge = Stroke(1f)

    private val cars = 3
    private val laps = intArrayOf(38, 37, 36)
    private val carStart = floatArrayOf(0f, 0.31f, 0.64f)

    init {
        // Trace a rounded rectangle with a hairpin pushed in from the right, densely.
        val dense = 900
        val dx = FloatArray(dense)
        val dy = FloatArray(dense)
        for (k in 0 until dense) {
            val a = TAU * k / dense
            val c = cos(a)
            val s = sin(a)
            var x = sign(c) * abs(c).pow(0.55f)
            var y = sign(s) * abs(s).pow(0.55f)
            val fromRight = if (a > TAU / 2) a - TAU else a
            x -= 0.75f * exp(-(fromRight / 0.32f) * (fromRight / 0.32f))
            y += 0.1f * sin(3 * a + 0.6f)
            dx[k] = x
            dy[k] = y
        }
        // Even spacing along the track.
        val along = FloatArray(dense + 1)
        for (k in 0 until dense) {
            val j = (k + 1) % dense
            along[k + 1] = along[k] + hypot(dx[j] - dx[k], dy[j] - dy[k])
        }
        var k = 0
        for (i in 0 until n) {
            val target = along[dense] * i / n
            while (along[k + 1] < target) k++
            val p = (target - along[k]) / (along[k + 1] - along[k])
            val j = (k + 1) % dense
            ux[i] = dx[k] + (dx[j] - dx[k]) * p
            uy[i] = dy[k] + (dy[j] - dy[k]) * p
        }
        val raw = FloatArray(n)
        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            val tx = ux[next] - ux[prev]
            val ty = uy[next] - uy[prev]
            val len = hypot(tx, ty)
            nx[i] = -ty / len
            ny[i] = tx / len
            val ax = ux[i] - ux[prev]
            val ay = uy[i] - uy[prev]
            val bx = ux[next] - ux[i]
            val by = uy[next] - uy[i]
            raw[i] = atan2(ax * by - ay * bx, ax * bx + ay * by)
        }
        // Smooth the bends so the racing line eases into each apex.
        var most = 0f
        for (i in 0 until n) {
            var sum = 0f
            for (o in -6..6) sum += raw[(i + o + n) % n]
            bend[i] = sum / 13f
            most = maxOf(most, abs(bend[i]))
        }
        for (i in 0 until n) bend[i] /= most
        for (i in 0 until n) lapAt[i + 1] = lapAt[i] + 1f / (1f - 0.55f * abs(bend[i]))
        for (i in 0..n) lapAt[i] /= lapAt[n]
    }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val sx = size.width * 0.40f
        val sy = size.height * 0.40f
        val unit = size.minDimension
        val half = 0.075f
        val bandWidth = 2 * half * minOf(sx, sy)
        if (band.width != bandWidth) band = Stroke(bandWidth)
        val edgeWidth = (unit * 0.004f).coerceAtLeast(1f)
        if (edge.width != edgeWidth) edge = Stroke(edgeWidth)

        centre.reset(); left.reset(); right.reset()
        for (i in 0..n) {
            val p = i % n
            val x = cx + ux[p] * sx
            val y = cy + uy[p] * sy
            val lx = cx + (ux[p] + nx[p] * half) * sx
            val ly = cy + (uy[p] + ny[p] * half) * sy
            val rx = cx + (ux[p] - nx[p] * half) * sx
            val ry = cy + (uy[p] - ny[p] * half) * sy
            if (i == 0) {
                centre.moveTo(x, y); left.moveTo(lx, ly); right.moveTo(rx, ry)
            } else {
                centre.lineTo(x, y); left.lineTo(lx, ly); right.lineTo(rx, ry)
            }
        }
        drawPath(centre, ink.blend, alpha = ink.a(0.05f), style = band)
        drawPath(left, ink.primary, alpha = ink.a(0.2f), style = edge)
        drawPath(right, ink.primary, alpha = ink.a(0.2f), style = edge)

        // Kerbs on the outside of the tighter corners, in alternating colours.
        val kerb = edgeWidth * 2.2f
        for (i in 0 until n) {
            if (abs(bend[i]) < 0.35f) continue
            val side = -sign(bend[i]) * half
            val j = (i + 1) % n
            drawLine(
                if (i % 2 == 0) ink.accent else ink.bright,
                Offset(cx + (ux[i] + nx[i] * side) * sx, cy + (uy[i] + ny[i] * side) * sy),
                Offset(cx + (ux[j] + nx[j] * side) * sx, cy + (uy[j] + ny[j] * side) * sy),
                kerb,
                alpha = ink.a(0.3f),
            )
        }
        startLine(cx, cy, sx, sy, half, ink)

        // The cars, with trails fading out behind them.
        val dot = unit * 0.009f
        for (c in 0 until cars) {
            val q = cycle(f.travel, laps[c], carStart[c])
            val colour = when (c) {
                0 -> ink.accent
                1 -> ink.primary
                else -> ink.bright
            }
            var px = 0f
            var py = 0f
            for (k in 0..14) {
                val u = indexAt(frac(q - k * 0.0035f))
                val x = carX(u, cx, sx, half)
                val y = carY(u, cy, sy, half)
                if (k > 0) drawLine(colour, Offset(px, py), Offset(x, y), dot * (1.4f - k * 0.08f), alpha = ink.a(0.32f * (1f - k / 15f)))
                px = x
                py = y
            }
            val u = indexAt(q)
            val head = Offset(carX(u, cx, sx, half), carY(u, cy, sy, half))
            drawCircle(colour, dot * 3.2f, head, alpha = ink.a(0.1f))
            drawCircle(colour, dot, head, alpha = ink.a(0.6f))
        }
    }

    /** A two-row chequer across the track at the first point. */
    private fun DrawScope.startLine(cx: Float, cy: Float, sx: Float, sy: Float, half: Float, ink: BackgroundInk) {
        val squares = 6
        val tx = ny[0]
        val ty = -nx[0]
        val step = 2 * half / squares
        for (row in 0..1) for (s in 0 until squares) {
            if ((row + s) % 2 == 1) continue
            val across = -half + step * s
            val x = cx + (ux[0] + nx[0] * across + tx * step * row) * sx
            val y = cy + (uy[0] + ny[0] * across + ty * step * row) * sy
            val side = step * minOf(sx, sy)
            drawRect(ink.bright, Offset(x, y), Size(side, side), alpha = ink.a(0.35f))
        }
    }

    /** Where along the track (a point number with a fraction) a car is, [q] of the way round a lap. */
    private fun indexAt(q: Float): Float {
        var lo = 0
        var hi = n
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (lapAt[mid] <= q) lo = mid else hi = mid
        }
        return lo + (q - lapAt[lo]) / (lapAt[lo + 1] - lapAt[lo])
    }

    /** The racing line: towards the inside of each bend. */
    private fun carX(u: Float, cx: Float, sx: Float, half: Float): Float {
        val i = u.toInt() % n
        val j = (i + 1) % n
        val p = u - u.toInt()
        val offI = bend[i] * half * 0.7f
        val offJ = bend[j] * half * 0.7f
        val a = ux[i] + nx[i] * offI
        val b = ux[j] + nx[j] * offJ
        return cx + (a + (b - a) * p) * sx
    }

    private fun carY(u: Float, cy: Float, sy: Float, half: Float): Float {
        val i = u.toInt() % n
        val j = (i + 1) % n
        val p = u - u.toInt()
        val a = uy[i] + ny[i] * bend[i] * half * 0.7f
        val b = uy[j] + ny[j] * bend[j] * half * 0.7f
        return cy + (a + (b - a) * p) * sy
    }

    private fun hypot(x: Float, y: Float) = sqrt(x * x + y * y)
}
