package com.astheral.bikedashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun countIsSingularOnlyForOne() {
        assertEquals("0 fill-ups", count(0, "fill-up"))
        assertEquals("1 fill-up", count(1, "fill-up"))
        assertEquals("2 fills", count(2, "fill"))
        assertEquals("1,885 services", count(1885, "service"))
    }
}
