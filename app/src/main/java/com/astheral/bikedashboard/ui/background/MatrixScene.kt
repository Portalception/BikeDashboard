package com.astheral.bikedashboard.ui.background

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Digital rain: columns of dashboard digits and units trickling down the screen, each run led by
 * a brighter character in the accent colour with a tail fading out behind it. Characters flicker
 * to new ones now and then. Columns sit at different depths, so some are fainter than others.
 */
internal class MatrixScene : BackgroundScene {
    // The rain moves a whole character at a time, so 20 steps a second is plenty.
    override val stepMillis: Long get() = 50L

    private val glyphs = "0123456789KMLRSG\$%/.+-=".toCharArray()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private var inkSeen: BackgroundInk? = null
    private var primaryRgb = 0
    private var accentRgb = 0

    override fun DrawScope.paint(t: Float, ink: BackgroundInk) {
        if (ink !== inkSeen) {
            inkSeen = ink
            primaryRgb = ink.primary.toArgb() and 0xFFFFFF
            accentRgb = ink.accent.toArgb() and 0xFFFFFF
        }
        val cell = size.minDimension / 26f
        val rowHeight = cell * 1.05f
        val cols = ceil(size.width / cell).toInt()
        val rows = ceil(size.height / rowHeight).toInt() + 1
        textPaint.textSize = cell * 0.95f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (c in 0 until cols) {
                val x = (c + 0.5f) * cell
                val depth = 0.5f + 0.5f * noise(c * 11 + 7)
                for (s in 0 until 2) {
                    val seed = c * 11 + s * 4
                    // Each run falls the full height every 10 to 24 seconds.
                    val falls = 50 + (noise(seed) * 70).toInt()
                    val length = 7 + (noise(seed + 1) * 11).toInt()
                    val head = floor(cycle(t, falls, noise(seed + 2)) * (rows + length)).toInt()
                    for (k in 0 until length) {
                        val row = head - k
                        if (row < 0 || row >= rows) continue
                        val alpha = if (k == 0) 0.55f else 0.38f * (1f - k.toFloat() / length)
                        val a = (ink.a(alpha * depth) * 255).toInt().coerceIn(0, 255)
                        if (a < 4) continue
                        textPaint.color = (a shl 24) or (if (k == 0) accentRgb else primaryRgb)
                        val glyph = glyphAt(c, row, t)
                        native.drawText(glyphs, glyph, 1, x, (row + 0.85f) * rowHeight, textPaint)
                    }
                }
            }
        }
    }

    /** The character in a cell, which changes one to three times a second, each cell at its own pace. */
    private fun glyphAt(col: Int, row: Int, t: Float): Int {
        val cellSeed = col * 977 + row * 131
        val tick = floor(t * (1f + 2f * noise(cellSeed)) + noise(cellSeed + 1)).toInt()
        return (noise(cellSeed + tick * 7919) * glyphs.size).toInt().coerceIn(0, glyphs.size - 1)
    }
}
