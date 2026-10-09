package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Riding at night: a three-lane road running to a vanishing point, its lane dashes and street
 * lights rushing past, each lamp throwing a faint pool of light on the road. The road bends
 * gently left and right, and a low city skyline sits on the horizon.
 */
internal class NightRideScene : BackgroundScene {
    override val layered: Boolean get() = true

    private val edgeSamples = 28
    private val edgePath = Path()
    private var edge = Stroke(1f)

    private val dashes = 12
    private val lamps = 7
    private val buildings = 26
    private val buildingHeight = FloatArray(buildings) { 0.25f + 0.75f * noise(it * 5 + 3) }
    private val buildingWidth = FloatArray(buildings) { 0.6f + 0.6f * noise(it * 5 + 4) }

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        // The far scenery barely moves with the tilt; the road and lamps move more.
        val vpX = w / 2f + f.shiftX * 0.7f
        val vpY = h * 0.36f + f.shiftY * 0.7f
        val focal = h - vpY
        val curve = 0.9f * f.motion * wave(f.t, 16)

        skyline(w, h * 0.36f + f.shiftY * 0.2f, f.shiftX * 0.2f, unit, ink)
        drawLine(ink.blend, Offset(0f, vpY), Offset(w, vpY), (unit * 0.002f).coerceAtLeast(1f), alpha = ink.a(0.12f))

        // Road edges, from right by you to the horizon.
        val ew = (unit * 0.004f).coerceAtLeast(1f)
        if (edge.width != ew) edge = Stroke(ew)
        for (side in -1..1 step 2) {
            edgePath.reset()
            for (k in 0..edgeSamples) {
                val z = Z_NEAR * pow(Z_FAR / Z_NEAR, k.toFloat() / edgeSamples)
                val x = vpX + focal * (side * ROAD_HALF + bendAt(z, curve)) / z
                val y = vpY + focal / z
                if (k == 0) edgePath.moveTo(x, y) else edgePath.lineTo(x, y)
            }
            drawPath(edgePath, ink.primary, alpha = ink.a(0.22f), style = edge)
        }

        // Lane dashes, coming towards you at a steady pace.
        for (lane in -1..1 step 2) {
            val laneX = lane * ROAD_HALF / 3f
            for (k in 0 until dashes) {
                val s = cycle(f.travel, 500, k.toFloat() / dashes)
                val z = Z_FAR - (Z_FAR - Z_NEAR) * s
                val z2 = z + DASH
                val fade = (s / 0.2f).coerceAtMost(1f)
                drawLine(
                    ink.bright,
                    Offset(vpX + focal * (laneX + bendAt(z, curve)) / z, vpY + focal / z),
                    Offset(vpX + focal * (laneX + bendAt(z2, curve)) / z2, vpY + focal / z2),
                    (focal * 0.025f / z).coerceIn(1f, unit * 0.02f),
                    alpha = ink.a(0.28f * fade),
                )
            }
        }

        // Street lights on both sides, staggered.
        for (side in -1..1 step 2) {
            for (k in 0 until lamps) {
                val s = cycle(f.travel, 260, (k + if (side > 0) 0.5f else 0f) / lamps)
                val z = Z_FAR - (Z_FAR - Z_LAMP_NEAR) * s
                val fade = (s / 0.25f).coerceAtMost(1f) * ink.a(1f)
                val near = Z_NEAR / z
                // Nearer lamps move a little more with the tilt.
                val nudgeX = f.shiftX * 0.3f * near
                val poleX = vpX + nudgeX + focal * (side * POLE_X + bendAt(z, curve)) / z
                val armX = vpX + nudgeX + focal * (side * (POLE_X - 0.7f) + bendAt(z, curve)) / z
                val groundY = vpY + focal / z
                val topY = vpY - focal * (POLE_HEIGHT - 1f) / z
                val pole = (focal * 0.02f / z).coerceIn(1f, unit * 0.012f)
                drawLine(ink.primary, Offset(poleX, groundY), Offset(poleX, topY), pole, alpha = 0.16f * fade)
                drawLine(ink.primary, Offset(poleX, topY), Offset(armX, topY), pole, alpha = 0.16f * fade)
                val lamp = Offset(armX, topY)
                drawCircle(ink.accent, (focal * 0.16f / z).coerceAtMost(unit * 0.2f), lamp, alpha = 0.07f * fade)
                drawCircle(ink.bright, (focal * 0.035f / z).coerceAtMost(unit * 0.05f), lamp, alpha = 0.45f * fade)
                // A pool of light on the road below.
                val poolX = vpX + nudgeX + focal * (side * (POLE_X - 0.9f) + bendAt(z, curve)) / z
                val poolW = focal * 1.8f / z
                val poolH = focal * 0.3f / z
                drawOval(ink.accent, Offset(poolX - poolW / 2f, groundY - poolH / 2f), Size(poolW, poolH), alpha = 0.05f * fade)
            }
        }
    }

    /** A row of faint blocks along the horizon, with a few lit windows. */
    private fun DrawScope.skyline(w: Float, horizon: Float, nudge: Float, unit: Float, ink: BackgroundInk) {
        val slot = w * 1.1f / buildings
        var x = -w * 0.05f + nudge
        for (b in 0 until buildings) {
            val bw = slot * buildingWidth[b]
            val bh = unit * 0.11f * buildingHeight[b]
            drawRect(ink.primary, Offset(x, horizon - bh), Size(bw, bh), alpha = ink.a(0.06f))
            if (b % 3 == 0) {
                val win = (unit * 0.006f).coerceAtLeast(1.5f)
                drawRect(ink.accent, Offset(x + bw * 0.3f, horizon - bh * 0.7f), Size(win, win), alpha = ink.a(0.22f))
            }
            x += slot
        }
    }

    /** How far sideways the road has bent by distance [z]. */
    private fun bendAt(z: Float, curve: Float): Float {
        val d = z - Z_NEAR
        return curve * d * d * 0.012f
    }

    private fun pow(base: Float, exp: Float): Float = Math.pow(base.toDouble(), exp.toDouble()).toFloat()

    private companion object {
        const val Z_NEAR = 1f
        const val Z_LAMP_NEAR = 1.6f
        const val Z_FAR = 34f
        const val DASH = 0.9f
        const val ROAD_HALF = 1.4f
        const val POLE_X = 2.4f
        const val POLE_HEIGHT = 3.2f
    }
}
