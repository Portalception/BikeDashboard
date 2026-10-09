package com.astheral.bikedashboard.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressStyleTest {
    @Test
    fun savedStyleIsKept() {
        assertEquals(ProgressStyle.CHAIN, ProgressStyle.fromName("CHAIN"))
        assertEquals(ProgressStyle.ROAD, ProgressStyle.fromName("ROAD"))
    }

    @Test
    fun removedOrMissingStylesFallBackToStandard() {
        listOf("KERB", "CHECKERED", "WAVE", "", null).forEach {
            assertEquals(ProgressStyle.STANDARD, ProgressStyle.fromName(it))
        }
    }

    @Test
    fun standardIsTheDefault() {
        assertEquals(ProgressStyle.STANDARD, AppearanceSettings().progressStyle)
    }
}
