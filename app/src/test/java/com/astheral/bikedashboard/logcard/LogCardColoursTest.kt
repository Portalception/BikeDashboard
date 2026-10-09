package com.astheral.bikedashboard.logcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LogCardColoursTest {
    @Test
    fun namesMapToColours() {
        assertEquals(0xFF1B3F9A.toInt(), logCardColour("Blue"))
        assertEquals(0xFF1B3F9A.toInt(), logCardColour("BLUE"))
        assertEquals(0xFF64B5F6.toInt(), logCardColour("Light Blue"))
        assertEquals(0xFFB0BEC5.toInt(), logCardColour("Metallic Silver"))
        assertNotNull(logCardColour("Pearl White"))
    }

    @Test
    fun nonColoursAreSkipped() {
        assertNull(logCardColour(null))
        assertNull(logCardColour("-"))
        assertNull(logCardColour("Multi-colour"))
    }

    @Test
    fun themeUsesPrimaryAndSecondary() {
        assertEquals(0xFF1B3F9A.toInt() to 0xFFE6E6E6.toInt(), logCardTheme("Blue", "White"))
        assertEquals(0xFFD32F2F.toInt() to 0xFFD32F2F.toInt(), logCardTheme("Red", null))
        assertEquals(0xFFD32F2F.toInt() to 0xFFD32F2F.toInt(), logCardTheme(null, "Red"))
        assertNull(logCardTheme(null, "-"))
    }
}
