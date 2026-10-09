package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Groups of thin concentric rings, like ripples, drifting slowly around the screen. Each ring
 * breathes a little out of step with its neighbours, and every so often a faint extra ring
 * spreads out from the middle of a group and fades away.
 */
internal class RingsScene : BackgroundScene {
    private class Group(
        val x: Float, val y: Float, val driftX: Float, val driftY: Float,
        val timesX: Int, val timesY: Int,
        /** Innermost radius as a share of the screen's shorter side. */
        val r: Float,
        val rings: Int,
        val colour: Int,
        val phase: Float,
    )

    private val groups = arrayOf(
        Group(0.18f, 0.20f, 0.08f, 0.05f, 15, 12, 0.18f, 3, 0, 0.0f),
        Group(0.86f, 0.16f, 0.05f, 0.05f, 12, 16, 0.14f, 2, 1, 1.1f),
        Group(0.55f, 0.48f, 0.07f, 0.06f, 16, 15, 0.12f, 3, 2, 2.3f),
        Group(0.14f, 0.74f, 0.05f, 0.05f, 20, 12, 0.16f, 2, 1, 3.2f),
        Group(0.84f, 0.84f, 0.05f, 0.04f, 12, 15, 0.13f, 3, 0, 4.0f),
    )

    private val ringAlpha = floatArrayOf(0.16f, 0.11f, 0.075f, 0.05f)
    private var stroke = Stroke(1f)

    override val layered: Boolean get() = true

    override fun DrawScope.paint(f: SceneTime, ink: BackgroundInk) {
        val t = f.t
        val m = f.motion
        val unit = size.minDimension
        val width = (unit * 0.006f).coerceAtLeast(1f)
        if (stroke.width != width) stroke = Stroke(width)
        groups.forEachIndexed { index, g ->
            val colour = when (g.colour) {
                0 -> ink.primary
                1 -> ink.accent
                else -> ink.blend
            }
            // Smaller groups sit nearer and move more with the tilt.
            val depth = (0.18f / g.r).coerceAtMost(1f) * 0.8f
            val center = Offset(
                (g.x + g.driftX * m * wave(t, g.timesX, g.phase)) * size.width + f.shiftX * depth,
                (g.y + g.driftY * m * wave(t, g.timesY, g.phase * 1.3f)) * size.height + f.shiftY * depth,
            )
            val base = g.r * unit
            for (i in 0 until g.rings) {
                val r = base * (1f + i * 0.34f) + unit * 0.012f * m * wave(t, 24, g.phase + i * 0.9f)
                drawCircle(colour, r, center, alpha = ink.a(ringAlpha[i]), style = stroke)
            }
            // A ripple leaving the group every 30 seconds, groups taking turns.
            val p = cycle(t, 40, index / groups.size.toFloat())
            val ripple = base * (0.6f + p * 1.9f)
            drawCircle(colour, ripple, center, alpha = ink.a(0.12f * (1f - p) * (1f - p)), style = stroke)
        }
    }
}
