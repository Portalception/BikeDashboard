package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A circuit seen from above ([track] picks which one): a faint run-off area, the tarmac with
 * white edge lines, red-and-white style kerbs on both sides of the tight corners, grid boxes
 * and a chequered start line. Three glowing bikes lap it on a racing line, cutting to the
 * inside of each corner, braking into the bends and pulling away on the straights, each with
 * a short light trail.
 *
 * The track's outline only changes with the screen size, so it's worked out once per size
 * rather than every frame.
 */
internal class RacetrackScene(track: RaceTrack = RaceTrack.CLASSIC) : BackgroundScene {
    private val n = 240

    // The track's centre line in its own space (-1 to 1 each way), evenly spaced along it.
    private val ux = FloatArray(n)
    private val uy = FloatArray(n)

    /** The bend at each point, -1 to 1, positive turning left (as seen on screen). */
    private val bend = FloatArray(n)

    /** Share of a lap taken to reach each point: corners take longer than straights. */
    private val lapAt = FloatArray(n + 1)

    // On screen, for the current size: the centre line and its left-hand normal.
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val nx = FloatArray(n)
    private val ny = FloatArray(n)
    private var half = 0f
    private var builtW = -1f
    private var builtH = -1f

    private val centre = Path()
    private val left = Path()
    private val right = Path()
    private var runOff = Stroke(1f)
    private var tarmac = Stroke(1f)
    private var edge = Stroke(1f)

    private val cars = 3
    private val laps = intArrayOf(38, 37, 36)
    private val carStart = floatArrayOf(0f, 0.31f, 0.64f)

    init {
        val (dx, dy) = TrackShapes.trace(track)
        val dense = dx.size
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
            val ax = ux[i] - ux[prev]
            val ay = uy[i] - uy[prev]
            val bx = ux[next] - ux[i]
            val by = uy[next] - uy[i]
            // Screen y points down, so a left turn has a negative cross product.
            raw[i] = -atan2(ax * by - ay * bx, ax * bx + ay * by)
        }
        // Smooth the bends so the racing line eases into each apex.
        var most = 0f
        for (i in 0 until n) {
            var sum = 0f
            for (o in -7..7) sum += raw[(i + o + n) % n]
            bend[i] = sum / 15f
            most = maxOf(most, abs(bend[i]))
        }
        for (i in 0 until n) bend[i] /= most
        for (i in 0 until n) lapAt[i + 1] = lapAt[i] + 1f / (1f - 0.55f * abs(bend[i]))
        for (i in 0..n) lapAt[i] /= lapAt[n]
    }

    /** Works out the track on screen for a [w] by [h] picture. */
    private fun build(w: Float, h: Float) {
        builtW = w
        builtH = h
        val sx = w * 0.40f
        val sy = h * 0.40f
        val unit = minOf(w, h)
        half = 0.075f * minOf(sx, sy)
        for (i in 0 until n) {
            px[i] = w / 2f + ux[i] * sx
            py[i] = h / 2f + uy[i] * sy
        }
        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            val tx = px[next] - px[prev]
            val ty = py[next] - py[prev]
            val len = hypot(tx, ty).coerceAtLeast(1e-3f)
            // Left of the direction of travel, on a screen whose y points down.
            nx[i] = ty / len
            ny[i] = -tx / len
        }
        centre.reset(); left.reset(); right.reset()
        for (i in 0..n) {
            val p = i % n
            val lx = px[p] + nx[p] * half
            val ly = py[p] + ny[p] * half
            val rx = px[p] - nx[p] * half
            val ry = py[p] - ny[p] * half
            if (i == 0) {
                centre.moveTo(px[p], py[p]); left.moveTo(lx, ly); right.moveTo(rx, ry)
            } else {
                centre.lineTo(px[p], py[p]); left.lineTo(lx, ly); right.lineTo(rx, ry)
            }
        }
        centre.close(); left.close(); right.close()
        runOff = Stroke(half * 3.4f, join = StrokeJoin.Round)
        tarmac = Stroke(half * 2f, join = StrokeJoin.Round)
        edge = Stroke((unit * 0.0035f).coerceAtLeast(1f), join = StrokeJoin.Round)
    }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        if (size.width != builtW || size.height != builtH) build(size.width, size.height)
        val unit = size.minDimension

        drawPath(centre, ink.blend, alpha = ink.a(0.025f), style = runOff)
        drawPath(centre, ink.blend, alpha = ink.a(0.06f), style = tarmac)
        drawPath(left, ink.bright, alpha = ink.a(0.18f), style = edge)
        drawPath(right, ink.bright, alpha = ink.a(0.18f), style = edge)

        kerbs(ink)
        gridBoxes(ink)
        startLine(ink)

        // The bikes, with trails fading out behind them.
        val dot = unit * 0.009f
        for (c in 0 until cars) {
            val q = cycle(f.travel, laps[c], carStart[c])
            val colour = when (c) {
                0 -> ink.accent
                1 -> ink.primary
                else -> ink.bright
            }
            var lx = 0f
            var ly = 0f
            for (k in 0..14) {
                val u = indexAt(frac(q - k * 0.003f))
                val x = carX(u)
                val y = carY(u)
                if (k > 0) {
                    drawLine(
                        colour, Offset(lx, ly), Offset(x, y), dot * (1.3f - k * 0.075f),
                        alpha = ink.a(0.3f * (1f - k / 15f)), cap = StrokeCap.Round,
                    )
                }
                lx = x
                ly = y
            }
            val u = indexAt(q)
            val hx = carX(u)
            val hy = carY(u)
            // Facing the way it's going: a short capsule pointing at a spot just ahead.
            val ahead = indexAt(frac(q + 0.002f))
            var fx = carX(ahead) - hx
            var fy = carY(ahead) - hy
            val flen = hypot(fx, fy).coerceAtLeast(1e-3f)
            fx /= flen
            fy /= flen
            val head = Offset(hx, hy)
            drawCircle(colour, dot * 3.4f, head, alpha = ink.a(0.09f))
            drawLine(
                colour,
                Offset(hx - fx * dot * 1.1f, hy - fy * dot * 1.1f),
                Offset(hx + fx * dot * 1.1f, hy + fy * dot * 1.1f),
                dot * 1.5f,
                alpha = ink.a(0.65f),
                cap = StrokeCap.Round,
            )
            // A faint headlight beam ahead.
            drawCircle(ink.bright, dot * 0.9f, Offset(hx + fx * dot * 3.2f, hy + fy * dot * 3.2f), alpha = ink.a(0.08f))
        }
    }

    /** Alternating kerb blocks just inside both edges wherever the track bends sharply. */
    private fun DrawScope.kerbs(ink: BackgroundInk) {
        val width = edge.width * 2.4f
        val inset = half - width * 0.6f
        for (i in 0 until n) {
            if (abs(bend[i]) < 0.4f) continue
            val j = (i + 1) % n
            val colour = if ((i / 2) % 2 == 0) ink.accent else ink.bright
            for (side in floatArrayOf(inset, -inset)) {
                drawLine(
                    colour,
                    Offset(px[i] + nx[i] * side, py[i] + ny[i] * side),
                    Offset(px[j] + nx[j] * side, py[j] + ny[j] * side),
                    width,
                    alpha = ink.a(0.3f),
                )
            }
        }
    }

    /** Staggered grid boxes behind the start line, like on race day. */
    private fun DrawScope.gridBoxes(ink: BackgroundInk) {
        for (slot in 0 until 4) {
            val i = (n - 3 - slot * 3) % n
            val side = if (slot % 2 == 0) half * 0.45f else -half * 0.45f
            val cx = px[i] + nx[i] * side
            val cy = py[i] + ny[i] * side
            val w = half * 0.35f
            drawLine(
                ink.bright,
                Offset(cx - nx[i] * w, cy - ny[i] * w),
                Offset(cx + nx[i] * w, cy + ny[i] * w),
                edge.width,
                alpha = ink.a(0.22f),
            )
        }
    }

    /** A two-row chequer across the track at the first point, square to the track. */
    private fun DrawScope.startLine(ink: BackgroundInk) {
        val squares = 6
        val side = 2 * half / squares
        // Along the track.
        val tx = -ny[0]
        val ty = nx[0]
        for (row in 0..1) for (s in 0 until squares) {
            if ((row + s) % 2 == 1) continue
            val across = -half + side * (s + 0.5f)
            val along = side * (row - 0.5f)
            val cx = px[0] + nx[0] * across + tx * along
            val cy = py[0] + ny[0] * across + ty * along
            // A square turned with the track: drawn as a thick, short line.
            drawLine(
                ink.bright,
                Offset(cx - tx * side / 2, cy - ty * side / 2),
                Offset(cx + tx * side / 2, cy + ty * side / 2),
                side,
                alpha = ink.a(0.35f),
            )
        }
    }

    /** Where along the track (a point number with a fraction) a bike is, [q] of the way round a lap. */
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
    private fun carX(u: Float): Float {
        val i = u.toInt() % n
        val j = (i + 1) % n
        val p = u - u.toInt()
        val a = px[i] + nx[i] * bend[i] * half * 0.65f
        val b = px[j] + nx[j] * bend[j] * half * 0.65f
        return a + (b - a) * p
    }

    private fun carY(u: Float): Float {
        val i = u.toInt() % n
        val j = (i + 1) % n
        val p = u - u.toInt()
        val a = py[i] + ny[i] * bend[i] * half * 0.65f
        val b = py[j] + ny[j] * bend[j] * half * 0.65f
        return a + (b - a) * p
    }

    private fun hypot(x: Float, y: Float) = sqrt(x * x + y * y)
}
