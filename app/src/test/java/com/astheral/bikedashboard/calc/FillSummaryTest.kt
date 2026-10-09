package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FillSummaryTest {

    private fun sg(id: Long, date: String, odo: Int, litres: Double, cost: Double) = FillUp(
        id = id, date = LocalDate.parse(date), country = Country.SG, odometerKm = odo,
        sgLitres = litres, sgCostSgd = cost,
    )

    private fun my(id: Long, date: String, odo: Int, litres: Double, cost: Double, noTrip: Boolean = false) = FillUp(
        id = id, date = LocalDate.parse(date), country = Country.MY, odometerKm = odo,
        myLitres = litres, myCostSgd = cost, noSpecialTrip = noTrip,
    )

    private val fills = listOf(
        sg(1, "2026-09-01", 10000, 12.0, 30.0),
        sg(2, "2026-09-05", 10300, 12.0, 31.2), // 25 km/L, S$2.60/L
        my(3, "2026-09-09", 10660, 12.0, 15.6), // 30 km/L, S$1.30/L
    )

    @Test
    fun betterEconomyIsGoodAndCheaperFuelIsGood() {
        val s = summarizeFill(computeStats(fills), 3)!!
        assertEquals(30.0, s.kmPerLitre.current!!, 1e-9)
        assertEquals(25.0, s.kmPerLitre.previous!!, 1e-9)
        assertEquals(Direction.UP, s.kmPerLitre.direction)
        assertEquals(Verdict.GOOD, s.kmPerLitre.verdict)

        assertEquals(Direction.DOWN, s.pricePerLitre.direction)
        assertEquals(Verdict.GOOD, s.pricePerLitre.verdict)
        assertEquals(-0.5, s.pricePerLitre.fraction!!, 1e-9)

        assertEquals(Verdict.GOOD, s.costPerKm.verdict)
        assertEquals(Verdict.NEUTRAL, s.litres.verdict) // litres are neither good nor bad
        assertEquals(Direction.UP, s.distanceKm.direction)
        assertEquals(Verdict.NEUTRAL, s.distanceKm.verdict)
    }

    @Test
    fun worseEconomyIsBad() {
        val s = summarizeFill(computeStats(fills), 2)!!
        // Fill 1 is the first, so it has no km/L to compare with.
        assertNull(s.kmPerLitre.previous)
        assertNull(s.kmPerLitre.direction)
        assertEquals(Verdict.NEUTRAL, s.kmPerLitre.verdict)
        // Price went up from S$2.50 to S$2.60.
        assertEquals(Direction.UP, s.pricePerLitre.direction)
        assertEquals(Verdict.BAD, s.pricePerLitre.verdict)
    }

    @Test
    fun tinyChangesCountAsTheSame() {
        val c = MetricChange(25.05, 25.0, Better.HIGHER)
        assertEquals(Direction.SAME, c.direction)
        assertEquals(Verdict.NEUTRAL, c.verdict)
        assertEquals(Verdict.BAD, MetricChange(24.0, 25.0, Better.HIGHER).verdict)
        assertEquals(Verdict.BAD, MetricChange(0.12, 0.10, Better.LOWER).verdict)
    }

    @Test
    fun savingsMatchTheAppWideCalculation() {
        val stats = computeStats(fills, tripKm = 48.0)
        val s = summarizeFill(stats, 3)!!
        assertTrue(s.hasMalaysiaPart)
        // SG reference S$2.60/L × 12 L − S$15.60 = S$15.60, minus 48 km × (15.6 / 360) per km.
        assertEquals(15.6 - 48.0 * 15.6 / 360, s.savingsSgd!!, 1e-9)
        assertEquals(stats.last().savingsSgd, s.savingsSgd)

        val noTrip = computeStats(fills.dropLast(1) + my(3, "2026-09-09", 10660, 12.0, 15.6, noTrip = true))
        assertEquals(15.6, summarizeFill(noTrip, 3)!!.savingsSgd!!, 1e-9)
    }

    @Test
    fun singaporeFillHasNoMalaysiaSavings() {
        val s = summarizeFill(computeStats(fills), 2)!!
        assertFalse(s.hasMalaysiaPart)
        assertNull(s.savingsSgd)
    }

    @Test
    fun trendCoversRecentFillsEndingWithThisOne() {
        val stats = computeStats(fills)
        val s = summarizeFill(stats, 3, recent = 2)!!
        assertEquals(listOf(25.0, 30.0), s.recentKmPerLitre.map { Math.round(it * 10) / 10.0 })
        assertEquals(2, s.recentPricePerLitre.size)
        assertTrue(s.kmPerLitreIncludesThis)

        val first = summarizeFill(stats, 1)!!
        assertTrue(first.recentKmPerLitre.isEmpty())
        assertFalse(first.kmPerLitreIncludesThis)
        assertNull(first.previous)
    }

    @Test
    fun unknownIdGivesNothing() {
        assertNull(summarizeFill(computeStats(fills), 99))
    }
}
