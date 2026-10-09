package com.astheral.bikedashboard.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The app's original look, brought to life: a few large, faint discs in the same places as
 * before, each drifting on its own slow loop and gently swelling and shrinking.
 */
internal class CirclesScene : BackgroundScene {
    private class Blob(
        val x: Float, val y: Float,
        /** Radius as a share of the screen width. */
        val r: Float,
        val driftX: Float, val driftY: Float,
        val timesX: Int, val timesY: Int, val timesR: Int,
        val phase: Float,
        val accent: Boolean,
        val alpha: Float,
    )

    private val blobs = arrayOf(
        Blob(0.05f, 0.22f, 0.55f, 0.06f, 0.03f, 20, 16, 24, 0.0f, false, 0.075f),
        Blob(1.00f, 0.12f, 0.45f, 0.05f, 0.04f, 15, 18, 20, 1.3f, true, 0.065f),
        Blob(0.85f, 0.62f, 0.50f, 0.05f, 0.04f, 16, 20, 15, 2.1f, false, 0.065f),
        Blob(0.10f, 0.90f, 0.35f, 0.06f, 0.03f, 18, 15, 25, 3.7f, true, 0.07f),
        Blob(0.50f, 0.42f, 0.16f, 0.08f, 0.06f, 12, 10, 30, 4.4f, true, 0.05f),
    )

    override fun DrawScope.paint(t: Float, ink: BackgroundInk) {
        val w = size.width
        val h = size.height
        for (b in blobs) {
            val cx = (b.x + b.driftX * wave(t, b.timesX, b.phase)) * w
            val cy = (b.y + b.driftY * wave(t, b.timesY, b.phase * 1.7f)) * h
            val r = b.r * w * (1f + 0.05f * wave(t, b.timesR, b.phase))
            drawCircle(if (b.accent) ink.accent else ink.primary, r, Offset(cx, cy), alpha = ink.a(b.alpha))
        }
    }
}
