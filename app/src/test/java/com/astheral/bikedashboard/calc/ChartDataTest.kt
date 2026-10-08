package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ChartDataTest {
    private val rows = listOf(
        FillUp(date = LocalDate.of(2025, 3, 2), country = Country.SG, odometerKm = 1000, sgLitres = 10.0, sgCostSgd = 25.0),
        FillUp(date = LocalDate.of(2025, 3, 20), country = Country.MY, odometerKm = 1250, myLitres = 10.0, myCostSgd = 10.0),
        FillUp(date = LocalDate.of(2025, 4, 5), country = Country.MIXED, odometerKm = 1500, unsplitLitres = 12.0, unsplitCostSgd = 20.0),
        FillUp(
            date = LocalDate.of(2025, 6, 1), country = Country.MIXED, odometerKm = 1800,
            sgLitres = 4.0, sgCostSgd = 10.4, myLitres = 8.0, myCostSgd = 9.6,
        ),
    )
    private val data = chartData(computeStats(rows))

    @Test
    fun priceLinesUseEachCountrysPartAndSkipOldMixed() {
        assertEquals(listOf(2.5, 2.6), data.sgPrice.map { it.value })
        assertEquals(listOf(1.0, 1.2), data.myPrice.map { it.value })
        assertEquals(listOf(LocalDate.of(2025, 3, 2), LocalDate.of(2025, 6, 1)), data.sgPrice.map { it.date })
    }

    @Test
    fun economySkipsFirstFillUp() {
        assertEquals(listOf(25.0, 250.0 / 12, 300.0 / 12), data.kmPerLitre.map { it.value })
    }

    @Test
    fun monthlySpendIncludesEmptyMonths() {
        assertEquals(
            listOf(YearMonth.of(2025, 3), YearMonth.of(2025, 4), YearMonth.of(2025, 5), YearMonth.of(2025, 6)),
            data.monthlySpend.map { it.month },
        )
        assertEquals(listOf(35.0, 20.0, 0.0, 20.0), data.monthlySpend.map { it.costSgd })
    }

    @Test
    fun spendIsSplitByCountry() {
        assertEquals(SpendSplit(sg = 25.0, my = 10.0), data.monthlySpend[0].split)
        assertEquals(SpendSplit(unsplit = 20.0), data.monthlySpend[1].split)
        assertEquals(SpendSplit(), data.monthlySpend[2].split)
        assertEquals(SpendSplit(sg = 10.4, my = 9.6), data.fillSplit.last())
        assertEquals(data.fillSpend.map { it.value }, data.fillSplit.map { it.total })
    }

    @Test
    fun emptyApp() {
        val empty = chartData(emptyList())
        assertTrue(empty.monthlySpend.isEmpty() && empty.sgPrice.isEmpty())
    }
}
