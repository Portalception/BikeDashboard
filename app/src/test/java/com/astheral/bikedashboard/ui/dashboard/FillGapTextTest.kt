package com.astheral.bikedashboard.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class FillGapTextTest {
    @Test
    fun withinTheUsualGapCountsTheDays() {
        assertEquals("Day 3 of about 5 between fill-ups", fillGapText(3, 5, sinceFullTank = false))
        assertEquals("Day 5 of about 5 between full tanks", fillGapText(5, 5, sinceFullTank = true))
    }

    @Test
    fun pastTheUsualGapSaysHowLongItsBeen() {
        assertEquals("43 days since your last fill-up · usually about 5 days", fillGapText(43, 5, sinceFullTank = false))
        assertEquals("2 days since your last full tank · usually about a day", fillGapText(2, 1, sinceFullTank = true))
    }

    @Test
    fun filledToday() {
        assertEquals("Filled today · usually about 5 days between fill-ups", fillGapText(0, 5, sinceFullTank = false))
    }
}

class NiceRangeTest {
    private fun onStep(x: Double, step: Double) = kotlin.math.abs(x / step - Math.rint(x / step)) < 1e-6

    @Test
    fun endsLandOnRoundStepsAroundTheData() {
        val (lo, hi) = niceRange(0.0453, 0.1113)
        org.junit.Assert.assertTrue(lo <= 0.0453 && hi >= 0.1113)
        org.junit.Assert.assertTrue(onStep(lo, 0.02) && onStep(hi, 0.02))
    }

    @Test
    fun staysAboveZeroForPositiveData() {
        val (lo, _) = niceRange(0.5, 40.0)
        assertEquals(0.0, lo, 0.0)
    }

    @Test
    fun aSingleValueStillGetsARange() {
        val (lo, hi) = niceRange(24.9, 24.9)
        org.junit.Assert.assertTrue(lo < 24.9 && hi > 24.9)
    }
}
