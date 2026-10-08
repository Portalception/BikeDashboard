package com.astheral.bikedashboard.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.astheral.bikedashboard.ui.theme.schemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun hexRoundTripsEveryKindOfColour() {
        listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF1B3F9A, 0xFFFDF5E6, 0xFF0A0005).forEach { argb ->
            val color = Color(argb)
            val back = colorFromHex(hexOf(color))
            assertEquals(color.toArgb(), back?.toArgb())
        }
        assertEquals("1B3F9A", hexOf(Color(0xFF1B3F9A)))
        assertEquals(Color(0xFF1B3F9A).toArgb(), colorFromHex("#1b3f9a")?.toArgb())
    }

    @Test
    fun badHexIsRejected() {
        listOf("", "12345", "1234567", "GGGGGG", "#12 456").forEach { assertNull(it, colorFromHex(it)) }
    }

    @Test
    fun pickedColoursAreUsedAsIs() {
        val primary = Color(0xFF1B3F9A)
        val accent = Color(0xFFFF6600)
        val light = schemeFor(primary, accent, dark = false)
        assertEquals(primary.toArgb(), light.primary.toArgb())
        assertEquals(accent.toArgb(), light.tertiary.toArgb())
        val dark = schemeFor(Color(0xFF00B0FF), Color(0xFFFFC72C), dark = true)
        assertEquals(Color(0xFF00B0FF).toArgb(), dark.primary.toArgb())
        assertEquals(Color(0xFFFFC72C).toArgb(), dark.tertiary.toArgb())
    }

    @Test
    fun blackAndWhiteWorkWhereTheyStandOut() {
        assertEquals(Color.Black.toArgb(), schemeFor(Color.Black, Color.Black, dark = false).primary.toArgb())
        assertEquals(Color.White.toArgb(), schemeFor(Color.White, Color.White, dark = true).primary.toArgb())
    }

    @Test
    fun coloursThatWouldVanishAreLiftedJustEnough() {
        val dark = schemeFor(Color.Black, Color.Black, dark = true)
        assertNotEquals(Color.Black.toArgb(), dark.primary.toArgb())
        assertTrue(contrast(dark.primary, dark.surface) >= 1.95f)
        val light = schemeFor(Color.White, Color.White, dark = false)
        assertNotEquals(Color.White.toArgb(), light.tertiary.toArgb())
        assertTrue(contrast(light.tertiary, light.surface) >= 1.95f)
    }

    @Test
    fun textOnTopIsReadable() {
        listOf(Color.Black, Color.White, Color(0xFFFFEB3B), Color(0xFF1B3F9A), Color(0xFF808080)).forEach { c ->
            listOf(false, true).forEach { isDark ->
                val s = schemeFor(c, c, isDark)
                assertTrue(contrast(s.primary, s.onPrimary) >= 4.5f)
                assertTrue(contrast(s.tertiary, s.onTertiary) >= 4.5f)
            }
        }
    }
}
