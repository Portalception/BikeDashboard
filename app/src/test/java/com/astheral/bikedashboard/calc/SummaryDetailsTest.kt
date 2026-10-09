package com.astheral.bikedashboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryDetailsTest {
    private val defaults = SummaryDetail.entries.filter { it.shownByDefault }.toSet()

    @Test
    fun nothingSavedGivesTheDefaults() {
        assertEquals(defaults, SummaryDetails.parse(null).shown)
        assertEquals(defaults, SummaryDetails.parse("  ").shown)
        assertEquals(4, defaults.size)
    }

    @Test
    fun formatRoundTrips() {
        val picked = SummaryDetails().setShown(SummaryDetail.PRICE, false).setShown(SummaryDetail.ODOMETER, true)
        assertEquals(picked, SummaryDetails.parse(picked.format()))
        val none = SummaryDetail.entries.fold(SummaryDetails()) { d, it -> d.setShown(it, false) }
        assertTrue(SummaryDetails.parse(none.format()).shown.isEmpty())
    }

    @Test
    fun unknownNamesAreSkippedAndMissingOnesTakeTheirDefault() {
        val parsed = SummaryDetails.parse("!COST_LITRES, ODOMETER ,BOGUS,!ODOMETER")
        assertFalse(SummaryDetail.COST_LITRES in parsed.shown)
        assertTrue(SummaryDetail.ODOMETER in parsed.shown) // the first mention wins
        // Not mentioned: shown or hidden as by default.
        assertTrue(SummaryDetail.PRICE in parsed.shown)
        assertFalse(SummaryDetail.DAYS in parsed.shown)
    }

    @Test
    fun visibleKeepsTheFixedOrder() {
        val d = SummaryDetails(setOf(SummaryDetail.ODOMETER, SummaryDetail.COST_LITRES, SummaryDetail.DAYS))
        assertEquals(listOf(SummaryDetail.COST_LITRES, SummaryDetail.DAYS, SummaryDetail.ODOMETER), d.visible)
    }
}
