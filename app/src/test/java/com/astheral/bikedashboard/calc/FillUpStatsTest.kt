package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Rows below are copied from "R3 Bike Dashboard - Fueling Record.csv"; expected values are the sheet's. */
class FillUpStatsTest {

    private fun sg(date: String, odo: Int, litres: Double, cost: Double) = FillUp(
        date = LocalDate.parse(date), country = Country.SG, odometerKm = odo,
        sgLitres = litres, sgCostSgd = cost,
    )

    private fun my(date: String, odo: Int, litres: Double, cost: Double) = FillUp(
        date = LocalDate.parse(date), country = Country.MY, odometerKm = odo,
        myLitres = litres, myCostSgd = cost,
    )

    // Rows 1–5
    private val firstRows = listOf(
        sg("2025-03-02", 130883, 11.9, 26.90),
        sg("2025-03-05", 131117, 10.54, 24.70),
        sg("2025-03-08", 131308, 8.445, 19.30),
        my("2025-03-11", 131572, 12.302, 15.95),
        my("2025-03-17", 131796, 10.56, 10.39),
    )

    // Rows 114–117 (row 116 is an old mixed fill-up with one total)
    private val lastRows = listOf(
        sg("2026-09-23", 162999, 9.713, 26.44),
        my("2026-09-26", 163305, 12.82, 20.36),
        FillUp(
            date = LocalDate.parse("2026-10-02"), country = Country.MIXED, odometerKm = 163740,
            unsplitLitres = 17.144, unsplitCostSgd = 31.67,
        ),
        my("2026-10-03", 163903, 6.54, 10.27),
    )

    @Test
    fun firstFillUpHasNothingToCompareWith() {
        val first = computeStats(firstRows).first()
        assertNull(first.distanceKm)
        assertNull(first.days)
        assertNull(first.kmPerLitre)
        assertNull(first.costPerKm)
        assertNull(first.costPerDay)
        assertEquals(2.26, first.pricePerLitre!!, 0.005)
    }

    @Test
    fun matchesSpreadsheetForRow2() {
        val row2 = computeStats(firstRows)[1]
        assertEquals(234, row2.distanceKm)
        assertEquals(3L, row2.days)
        assertEquals(22.20, row2.kmPerLitre!!, 0.005)
        assertEquals(0.1056, row2.costPerKm!!, 0.00005)
        assertEquals(8.2333, row2.costPerDay!!, 0.00005)
        assertEquals(2.34, row2.pricePerLitre!!, 0.005)
    }

    @Test
    fun matchesSpreadsheetForMalaysiaRow4() {
        val row4 = computeStats(firstRows)[3]
        assertEquals(264, row4.distanceKm)
        assertEquals(21.46, row4.kmPerLitre!!, 0.005)
        assertEquals(0.0604, row4.costPerKm!!, 0.00005)
        assertEquals(5.3167, row4.costPerDay!!, 0.00005)
        assertEquals(1.30, row4.pricePerLitre!!, 0.005)
    }

    @Test
    fun matchesSpreadsheetForLastRow() {
        val row117 = computeStats(lastRows).last()
        assertEquals(163, row117.distanceKm)
        assertEquals(1L, row117.days)
        assertEquals(24.92, row117.kmPerLitre!!, 0.005)
        assertEquals(0.0630, row117.costPerKm!!, 0.00005)
        assertEquals(10.27, row117.costPerDay!!, 0.005)
    }

    @Test
    fun savingsUseLatestSingaporePriceAndSubtractTripCost() {
        val row117 = computeStats(lastRows).last()
        val sgPrice = 26.44 / 9.713 // row 114, the last SG fill-up
        assertEquals(sgPrice, row117.sgReferencePricePerLitre!!, 1e-9)
        val expected = sgPrice * 6.54 - 10.27 - 48 * (10.27 / 163)
        assertEquals(expected, row117.savingsSgd!!, 1e-9)
    }

    @Test
    fun oldMixedRowDoesNotChangeSingaporePrice() {
        val stats = computeStats(lastRows)
        assertEquals(stats[0].sgReferencePricePerLitre!!, stats[2].sgReferencePricePerLitre!!, 0.0)
        assertNull(stats[2].savingsSgd) // no MY part to compare, and no imported figure in this test
    }

    @Test
    fun tripDistanceSettingIsUsed() {
        val with48 = computeStats(lastRows).last().savingsSgd!!
        val with60 = computeStats(lastRows, tripKm = 60.0).last().savingsSgd!!
        assertEquals(12 * (10.27 / 163), with48 - with60, 1e-9)
    }

    @Test
    fun noSpecialTripSkipsTripCost() {
        val rows = lastRows.toMutableList()
        rows[3] = rows[3].copy(noSpecialTrip = true)
        val row117 = computeStats(rows).last()
        assertEquals(26.44 / 9.713 * 6.54 - 10.27, row117.savingsSgd!!, 1e-9)
    }

    @Test
    fun importedSavingsAreKept() {
        val rows = firstRows.toMutableList()
        rows[3] = rows[3].copy(importedSavingsSgd = 11.18) // row 4's sheet value
        assertEquals(11.18, computeStats(rows)[3].savingsSgd!!, 0.0)
    }

    @Test
    fun noSavingsBeforeAnySingaporeFillUp() {
        val rows = listOf(my("2025-03-11", 131572, 12.302, 15.95), my("2025-03-17", 131796, 10.56, 10.39))
        assertNull(computeStats(rows).last().savingsSgd)
    }

    @Test
    fun noSavingsForSingaporeFillUp() {
        assertNull(computeStats(firstRows)[2].savingsSgd)
    }

    @Test
    fun newMixedFillUpUsesItsOwnSingaporePartAndSavesOnMalaysiaPartOnly() {
        val mixed = FillUp(
            date = LocalDate.parse("2026-10-10"), country = Country.MIXED, odometerKm = 164300,
            sgLitres = 4.0, sgCostSgd = 11.00, myLitres = 10.0, myCostSgd = 15.00,
        )
        val stats = computeStats(lastRows + mixed).last()
        assertEquals(2.75, stats.sgReferencePricePerLitre!!, 1e-9)
        assertEquals(14.0, mixed.totalLitres, 0.0)
        assertEquals(26.0, mixed.totalCostSgd, 0.0)
        val costPerKm = 26.0 / (164300 - 163903)
        assertEquals(2.75 * 10.0 - 15.00 - 48 * costPerKm, stats.savingsSgd!!, 1e-9)
    }

    @Test
    fun sameDayFillUpHasNoCostPerDay() {
        val rows = listOf(sg("2026-08-07", 160548, 10.984, 27.90), sg("2026-08-07", 160662, 4.416, 10.58))
        val second = computeStats(rows).last()
        assertEquals(0L, second.days)
        assertNull(second.costPerDay)
        assertEquals(114, second.distanceKm)
    }

    @Test
    fun inputOrderDoesNotMatter() {
        assertEquals(computeStats(firstRows), computeStats(firstRows.reversed()))
    }
}
