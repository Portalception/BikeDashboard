package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OdometerEstimateTest {
    private fun fill(date: String, km: Int) = FillUp(date = LocalDate.parse(date), country = Country.SG, odometerKm = km, sgLitres = 13.0, sgCostSgd = 35.0)

    private val fills = listOf(fill("2026-03-17", 12820), fill("2026-03-03", 12340), fill("2026-02-20", 12000))

    @Test
    fun goesInAStraightLineBetweenTheFillUpsAround() {
        val e = estimateOdometer(LocalDate.parse("2026-03-10"), fills)!!
        assertEquals(EstimateKind.BETWEEN, e.kind)
        assertEquals(12580, e.km) // 7 of 14 days: halfway from 12,340 to 12,820
        assertEquals(OdometerReading(LocalDate.parse("2026-03-03"), 12340), e.before)
        assertEquals(OdometerReading(LocalDate.parse("2026-03-17"), 12820), e.after)
    }

    @Test
    fun roundsToWholeKm() {
        val e = estimateOdometer(LocalDate.parse("2026-03-04"), fills)!!
        assertEquals(12374, e.km) // 12,340 + 480 × 1/14 = 12,374.3
    }

    @Test
    fun usesAFillUpOnTheSameDay() {
        val e = estimateOdometer(LocalDate.parse("2026-03-03"), fills + fill("2026-03-03", 12500))!!
        assertEquals(EstimateKind.SAME_DAY, e.kind)
        assertEquals(12500, e.km) // the later of the two that day
    }

    @Test
    fun usesTheNearestReadingWithFillUpsOnOneSideOnly() {
        val later = estimateOdometer(LocalDate.parse("2026-05-01"), fills)!!
        assertEquals(EstimateKind.BEFORE_ONLY, later.kind)
        assertEquals(12820, later.km)
        val earlier = estimateOdometer(LocalDate.parse("2026-01-01"), fills)!!
        assertEquals(EstimateKind.AFTER_ONLY, earlier.kind)
        assertEquals(12000, earlier.km)
    }

    @Test
    fun nothingWithoutFillUps() {
        assertNull(estimateOdometer(LocalDate.parse("2026-03-10"), emptyList()))
    }
}
