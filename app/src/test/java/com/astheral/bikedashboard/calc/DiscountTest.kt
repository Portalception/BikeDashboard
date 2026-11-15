package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DiscountTest {
    @Test
    fun savedAndPercentComeFromThePumpPrice() {
        val d = discountOf(paidSgd = 24.0, beforeSgd = 32.0)!!
        assertEquals(8.0, d.savedSgd, 1e-9)
        assertEquals(25.0, d.percent, 1e-9)
    }

    @Test
    fun noDiscountWhenMissingOrNothingTakenOff() {
        assertNull(discountOf(24.0, null))
        assertNull(discountOf(null, 32.0))
        assertNull(discountOf(32.0, 32.0))
        assertNull(discountOf(33.0, 32.0))
    }

    @Test
    fun fillUpDiscountUsesWhatWasActuallyPaid() {
        val fill = FillUp(date = LocalDate.of(2026, 11, 15), country = Country.SG, odometerKm = 1, sgLitres = 12.0, sgCostSgd = 24.6, sgBeforeDiscountSgd = 32.8)
        assertEquals(8.2, fill.sgDiscount!!.savedSgd, 1e-9)
        assertEquals(24.6, fill.totalCostSgd, 1e-9)
    }
}
