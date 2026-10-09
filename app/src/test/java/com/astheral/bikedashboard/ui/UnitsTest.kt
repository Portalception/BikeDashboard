package com.astheral.bikedashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun economyConversions() {
        assertEquals(4.0, EconomyUnit.L_PER_100KM.convert(25.0), 1e-9)
        assertEquals(58.8, EconomyUnit.MPG.convert(25.0), 0.05)
        assertEquals("4.00 L/100km", EconomyUnit.L_PER_100KM.format(4.0))
    }

    @Test
    fun costConversions() {
        assertEquals(5.0, DistanceCostUnit.PER_100KM.convert(0.05), 1e-9)
        assertEquals(21.0, TimeCostUnit.PER_WEEK.convert(3.0), 1e-9)
    }

    @Test
    fun priceInRinggitNeedsARate() {
        assertEquals(6.5, PriceUnit.RM_PER_L.at(3.25).convert(2.0), 1e-9)
        assertEquals("S$2.00/L", PriceUnit.RM_PER_L.at(null).format(2.0))
    }

    @Test
    fun nextWrapsAndSkips() {
        assertEquals(EconomyUnit.KM_PER_L, EconomyUnit.MPG.next())
        assertEquals(EconomyUnit.MPG, EconomyUnit.KM_PER_L.next { it != EconomyUnit.L_PER_100KM })
    }

    @Test
    fun distanceOverTime() {
        assertEquals(70.0, DistanceTimeUnit.PER_WEEK.convert(10.0), 1e-9)
        assertEquals(304.375, DistanceTimeUnit.PER_MONTH.convert(10.0), 1e-9)
        assertEquals("25.3 km/day", DistanceTimeUnit.PER_DAY.format(25.26))
        assertEquals("1,234 km/mo", DistanceTimeUnit.PER_MONTH.format(1234.4))
    }
}
