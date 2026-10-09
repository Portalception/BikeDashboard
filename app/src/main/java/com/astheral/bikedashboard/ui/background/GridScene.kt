package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * An even grid of dots that "breathes": waves roll out from the middle of the screen like
 * ripples on a puddle, swelling and brightening the dots as they pass. Dots fade towards the
 * edges, and every third one takes the accent colour.
 */
internal class GridScene : BackgroundScene {
    override fun DrawScope.paint(t: Float, ink: BackgroundInk) {
        val spacing = size.minDimension / 9f
        val cols = ceil(size.width / spacing).toInt() + 1
        val rows = ceil(size.height / spacing).toInt() + 1
        // Centre the grid, so the margins match on both sides.
        val left = (size.width - (cols - 1) * spacing) / 2f
        val top = (size.height - (rows - 1) * spacing) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val reach = sqrt(cx * cx + cy * cy)
        // A full ripple every 4 seconds, about two of them on screen at once.
        val clock = TAU * cycle(t, 300)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = left + col * spacing
                val y = top + row * spacing
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                val s = kotlin.math.sin(clock - dist / spacing * 0.6f)
                val fade = 1f - 0.45f * (dist / reach)
                val colour = if ((row + col) % 3 == 0) ink.accent else ink.primary
                drawCircle(
                    colour,
                    radius = spacing * (0.055f + 0.03f * s),
                    center = Offset(x, y),
                    alpha = ink.a((0.24f + 0.12f * s) * fade),
                )
            }
        }
    }
}
