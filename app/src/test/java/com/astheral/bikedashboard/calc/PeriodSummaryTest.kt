package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PeriodSummaryTest {

    private fun sg(date: String, odo: Int, litres: Double, cost: Double) = FillUp(
        date = LocalDate.parse(date), country = Country.SG, odometerKm = odo,
        sgLitres = litres, sgCostSgd = cost,
    )

    private fun my(date: String, odo: Int, litres: Double, cost: Double, savings: Double) = FillUp(
        date = LocalDate.parse(date), country = Country.MY, odometerKm = odo,
        myLitres = litres, myCostSgd = cost, importedSavingsSgd = savings,
    )

    // Rows 1–5 of the spreadsheet
    private val rows = listOf(
        sg("2025-03-02", 130883, 11.9, 26.90),
        sg("2025-03-05", 131117, 10.54, 24.70),
        sg("2025-03-08", 131308, 8.445, 19.30),
        my("2025-03-11", 131572, 12.302, 15.95, savings = 11.18),
        my("2025-03-17", 131796, 10.56, 10.39, savings = 13.16),
    )
    private val stats = computeStats(rows)

    // Fill-ups 2–5 have a previous one to measure from
    private val measuredKm = 131796 - 130883
    private val measuredLitres = 10.54 + 8.445 + 12.302 + 10.56
    private val measuredCost = 24.70 + 19.30 + 15.95 + 10.39

    @Test
    fun lifetimeUsesTotalsAndSkipsFirstFillUpForDistanceRates() {
        val life = summarize(stats)
        assertEquals(5, life.fillUpCount)
        assertEquals(measuredKm, life.distanceKm)
        assertEquals(measuredKm / measuredLitres, life.kmPerLitre!!, 1e-9)
        assertEquals(measuredCost / measuredKm, life.costPerKm!!, 1e-9)
        assertEquals(measuredCost / 15, life.costPerDay!!, 1e-9) // 2 Mar → 17 Mar
    }

    @Test
    fun lifetimePriceAndSpendIncludeEveryFillUp() {
        val life = summarize(stats)
        val allLitres = 11.9 + measuredLitres
        val allCost = 26.90 + measuredCost
        assertEquals(allCost, life.costSgd, 1e-9)
        assertEquals(allCost / allLitres, life.pricePerLitre!!, 1e-9)
    }

    @Test
    fun savingsAreAdded() {
        assertEquals(11.18 + 13.16, summarize(stats).savingsSgd!!, 1e-9)
    }

    @Test
    fun lastFillUpMatchesItsOwnStats() {
        val last = summarizeDashboard(stats, today = LocalDate.parse("2025-03-17")).lastFillUp
        val own = stats.last()
        assertEquals(1, last.fillUpCount)
        assertEquals(own.kmPerLitre!!, last.kmPerLitre!!, 1e-9)
        assertEquals(own.costPerKm!!, last.costPerKm!!, 1e-9)
        assertEquals(own.costPerDay!!, last.costPerDay!!, 1e-9)
        assertEquals(own.pricePerLitre!!, last.pricePerLitre!!, 1e-9)
        assertEquals(13.16, last.savingsSgd!!, 1e-9)
    }

    @Test
    fun last30DaysIncludesTodayAnd29DaysBefore() {
        // 3 Apr minus 29 days = 5 Mar, so 2 Mar drops out and 5 Mar is kept
        val window = summarizeDashboard(stats, today = LocalDate.parse("2025-04-03")).last30Days
        assertEquals(4, window.fillUpCount)
        assertEquals(measuredKm, window.distanceKm) // 5 Mar still measures from 2 Mar
        assertEquals(measuredCost / 15, window.costPerDay!!, 1e-9)
    }

    @Test
    fun last30DaysIsEmptyAfterLongBreak() {
        val window = summarizeDashboard(stats, today = LocalDate.parse("2025-06-01")).last30Days
        assertEquals(0, window.fillUpCount)
        assertEquals(0.0, window.costSgd, 0.0)
        assertNull(window.kmPerLitre)
        assertNull(window.costPerKm)
        assertNull(window.costPerDay)
        assertNull(window.pricePerLitre)
        assertNull(window.savingsSgd)
    }

    @Test
    fun singleFirstFillUpHasPriceButNoRates() {
        val only = summarize(computeStats(rows.take(1)))
        assertEquals(26.90 / 11.9, only.pricePerLitre!!, 1e-9)
        assertNull(only.kmPerLitre)
        assertNull(only.costPerDay)
        assertNull(only.savingsSgd)
    }

    @Test
    fun noFillUpsAtAll() {
        val dash = summarizeDashboard(emptyList(), today = LocalDate.parse("2026-10-07"))
        assertEquals(0, dash.lifetime.fillUpCount)
        assertEquals(0, dash.lastFillUp.fillUpCount)
        assertNull(dash.lifetime.kmPerLitre)
    }
}
