package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Partial fills: their fuel is counted at the next full tank. Worked example:
 *
 * A full  1 Jan  1000 km  SG 12 L  S$30.00 (S$2.50/L)
 * B part  4 Jan  1150 km  MY  5 L  S$6.00
 * C part  6 Jan  1250 km  SG  4 L  S$10.40 (S$2.60/L)
 * D full 10 Jan  1400 km  MY  6 L  S$7.20
 * E part 12 Jan  1500 km  MY  3 L  S$3.60
 *
 * D closes A→D: 400 km over 9 days on 5 + 4 + 6 = 15 L costing 6 + 10.40 + 7.20 = S$23.60.
 */
class PartialFillTest {
    private fun sg(id: Long, date: String, odo: Int, litres: Double, cost: Double, full: Boolean = true) = FillUp(
        id = id, date = LocalDate.parse(date), country = Country.SG, odometerKm = odo,
        sgLitres = litres, sgCostSgd = cost, fullTank = full,
    )

    private fun my(id: Long, date: String, odo: Int, litres: Double, cost: Double, full: Boolean = true, noTrip: Boolean = false) = FillUp(
        id = id, date = LocalDate.parse(date), country = Country.MY, odometerKm = odo,
        myLitres = litres, myCostSgd = cost, fullTank = full, noSpecialTrip = noTrip,
    )

    private val a = sg(1, "2026-01-01", 1000, 12.0, 30.0)
    private val b = my(2, "2026-01-04", 1150, 5.0, 6.0, full = false)
    private val c = sg(3, "2026-01-06", 1250, 4.0, 10.4, full = false)
    private val d = my(4, "2026-01-10", 1400, 6.0, 7.2)
    private val e = my(5, "2026-01-12", 1500, 3.0, 3.6, full = false)
    private val fills = listOf(a, b, c, d, e)
    private val stats = computeStats(fills)
    private val segKm = 400
    private val segLitres = 15.0
    private val segCost = 23.6
    private val segCostPerKm = segCost / segKm

    @Test
    fun fullTankUsesTheWholeSegment() {
        val s = stats[3]
        assertEquals(segKm, s.distanceKm)
        assertEquals(9L, s.days)
        assertEquals(segLitres, s.segmentLitres!!, 1e-9)
        assertEquals(segCost, s.segmentCostSgd!!, 1e-9)
        assertEquals(2, s.partialCount)
        assertEquals(segKm / segLitres, s.kmPerLitre!!, 1e-9)
        assertEquals(segCostPerKm, s.costPerKm!!, 1e-9)
        assertEquals(segCost / 9, s.costPerDay!!, 1e-9)
        assertEquals(segKm / 9.0, s.kmPerDay!!, 1e-9)
        assertEquals(7.2 / 6.0, s.pricePerLitre!!, 1e-9) // its own price, not the segment's
    }

    @Test
    fun partialFillsOnlyKnowTheirOwnPrice() {
        listOf(stats[1], stats[2], stats[4]).forEach { s ->
            assertNull(s.distanceKm)
            assertNull(s.days)
            assertNull(s.kmPerLitre)
            assertNull(s.costPerKm)
            assertNull(s.costPerDay)
            assertNull(s.kmPerDay)
            assertNull(s.segmentLitres)
            assertEquals(0, s.partialCount)
        }
        assertEquals(1.2, stats[1].pricePerLitre!!, 1e-9)
        assertEquals(2.6, stats[2].pricePerLitre!!, 1e-9)
    }

    @Test
    fun firstFullTankStillHasNothingToMeasureFrom() {
        assertNull(stats[0].distanceKm)
        assertNull(stats[0].kmPerLitre)
        // Partials before the first full tank don't give it a distance either.
        val s = computeStats(listOf(b.copy(date = LocalDate.parse("2025-12-30"), odometerKm = 900), a))
        assertNull(s[1].distanceKm)
        assertNull(s[1].kmPerLitre)
        assertEquals(1, s[1].partialCount)
    }

    @Test
    fun partialSingaporeFillUpdatesReferencePrice() {
        assertEquals(2.5, stats[1].sgReferencePricePerLitre!!, 1e-9)
        assertEquals(2.6, stats[2].sgReferencePricePerLitre!!, 1e-9)
        assertEquals(2.6, stats[3].sgReferencePricePerLitre!!, 1e-9)
    }

    @Test
    fun malaysiaFillsSaveUsingTheirSegmentsCostPerKm() {
        assertEquals(2.5 * 5 - 6.0 - 48 * segCostPerKm, stats[1].savingsSgd!!, 1e-9)
        assertEquals(2.6 * 6 - 7.2 - 48 * segCostPerKm, stats[3].savingsSgd!!, 1e-9)
    }

    @Test
    fun openSegmentUsesMostRecentKnownCostPerKm() {
        assertEquals(2.6 * 3 - 3.6 - 48 * segCostPerKm, stats[4].savingsSgd!!, 1e-9)
    }

    @Test
    fun noKnownCostPerKmMeansNoSavingsUnlessNoSpecialTrip() {
        val early = computeStats(listOf(a, b))
        assertNull(early[1].savingsSgd)
        val noTrip = computeStats(listOf(a, b.copy(noSpecialTrip = true)))
        assertEquals(2.5 * 5 - 6.0, noTrip[1].savingsSgd!!, 1e-9)
    }

    @Test
    fun importedSavingsAreKeptForPartials() {
        val s = computeStats(listOf(a, b.copy(importedSavingsSgd = 4.2)))
        assertEquals(4.2, s[1].savingsSgd!!, 0.0)
    }

    @Test
    fun partialsWaitingCountsTrailingPartials() {
        assertEquals(1, partialsWaiting(stats))
        assertEquals(0, partialsWaiting(stats.take(4)))
        assertEquals(2, partialsWaiting(stats.take(3)))
        assertEquals(0, partialsWaiting(emptyList()))
    }

    @Test
    fun allFullTanksWorkAsBefore() {
        val s = computeStats(fills.map { it.copy(fullTank = true) })
        assertEquals(150, s[1].distanceKm)
        assertEquals(150 / 5.0, s[1].kmPerLitre!!, 1e-9)
        assertEquals(6.0 / 150, s[1].costPerKm!!, 1e-9)
        assertEquals(5.0, s[1].segmentLitres!!, 1e-9)
        assertTrue(s.all { it.partialCount == 0 })
    }

    @Test
    fun periodRatesUseClosedSegmentsAndSpendUsesEverything() {
        val life = summarize(stats)
        assertEquals(5, life.fillUpCount)
        assertEquals(segKm, life.distanceKm)
        assertEquals(segKm / segLitres, life.kmPerLitre!!, 1e-9)
        assertEquals(segCostPerKm, life.costPerKm!!, 1e-9)
        assertEquals(segCost / 9, life.costPerDay!!, 1e-9)
        assertEquals(segKm / 9.0, life.kmPerDay!!, 1e-9)
        val allCost = 30.0 + 6.0 + 10.4 + 7.2 + 3.6
        val allLitres = 12.0 + 5 + 4 + 6 + 3
        assertEquals(allCost, life.costSgd, 1e-9)
        assertEquals(allCost / allLitres, life.pricePerLitre!!, 1e-9)
    }

    @Test
    fun segmentCountsInThePeriodOfItsClosingFullTank() {
        // 8–14 Jan holds D (closing A→D, though B and C are earlier) and the open partial E.
        val week = summarizeDashboard(stats, today = LocalDate.parse("2026-01-14")).last7Days
        assertEquals(2, week.fillUpCount)
        assertEquals(segKm / segLitres, week.kmPerLitre!!, 1e-9)
        assertEquals(segCostPerKm, week.costPerKm!!, 1e-9)
        assertEquals(7.2 + 3.6, week.costSgd, 1e-9)
        // 4–6 Jan has only partials: spend and price, but no economy.
        val partialsOnly = summarize(inLastDays(stats, LocalDate.parse("2026-01-06"), 3))
        assertEquals(2, partialsOnly.fillUpCount)
        assertNull(partialsOnly.kmPerLitre)
        assertNull(partialsOnly.costPerKm)
        assertNull(partialsOnly.costPerDay)
        assertNull(partialsOnly.kmPerDay)
        assertEquals((6.0 + 10.4) / 9.0, partialsOnly.pricePerLitre!!, 1e-9)
    }

    @Test
    fun lastFillCanBePartial() {
        val last = summarizeDashboard(stats, today = LocalDate.parse("2026-01-12")).lastFillUp
        assertEquals(1, last.fillUpCount)
        assertNull(last.kmPerLitre)
        assertNull(last.costPerKm)
        assertEquals(1.2, last.pricePerLitre!!, 1e-9)
        assertEquals(3.6, last.costSgd, 1e-9)
        assertNotNull(last.savingsSgd)
    }

    @Test
    fun chartsTakeEconomyFromFullTanksAndPricesFromEveryFill() {
        val chart = chartData(stats)
        assertEquals(listOf(LocalDate.parse("2026-01-10")), chart.kmPerLitre.map { it.date })
        assertEquals(2, chart.sgPrice.size) // A and partial C
        assertEquals(3, chart.myPrice.size) // B, D and E
        assertEquals(5, chart.fillSpend.size)
    }

    @Test
    fun summaryOfPartialSkipsEconomyAndComparesOnlyPrice() {
        val s = summarizeFill(stats, 3)!!
        assertTrue(s.isPartial)
        assertNull(s.kmPerLitre.current)
        assertNull(s.totalCost.previous)
        assertNull(s.litres.previous)
        assertEquals(1.2, s.pricePerLitre.previous!!, 1e-9) // B, the fill-up just before
        assertEquals(0, s.partialsIncluded)
    }

    @Test
    fun summaryOfFullTankComparesWithPreviousFullTank() {
        val withNext = fills + my(6, "2026-01-15", 1600, 4.0, 4.8)
        val s = summarizeFill(computeStats(withNext), 6)!!
        assertFalse(s.isPartial)
        assertEquals(1, s.partialsIncluded)
        assertEquals(200 / 7.0, s.kmPerLitre.current!!, 1e-9) // 1400 → 1600 on 3 + 4 L
        assertEquals(segKm / segLitres, s.kmPerLitre.previous!!, 1e-9) // D, the previous full tank
        assertEquals(7.2, s.totalCost.previous!!, 1e-9)
    }
}
