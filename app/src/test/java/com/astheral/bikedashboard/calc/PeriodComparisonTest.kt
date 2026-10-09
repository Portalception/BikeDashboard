package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodComparisonTest {

    private fun sg(date: String, odo: Int, litres: Double, cost: Double, full: Boolean = true) = FillUp(
        date = LocalDate.parse(date), country = Country.SG, odometerKm = odo,
        sgLitres = litres, sgCostSgd = cost, fullTank = full,
    )

    private fun my(date: String, odo: Int, litres: Double, cost: Double) = FillUp(
        date = LocalDate.parse(date), country = Country.MY, odometerKm = odo,
        myLitres = litres, myCostSgd = cost, noSpecialTrip = true,
    )

    private val today = LocalDate.parse("2026-10-15")

    private val fills = listOf(
        sg("2026-09-20", 10000, 12.0, 30.0),
        sg("2026-10-02", 10300, 12.0, 31.2), // 25 km/L over 12 days, S$2.60/L
        sg("2026-10-05", 10400, 4.0, 10.0, full = false), // partial, S$2.50/L
        my("2026-10-10", 10660, 8.0, 10.4), // closes 360 km on 12 L: 30 km/L over 8 days, saves 2.5 × 8 − 10.4 (latest SG price is the 5 Oct partial)
        sg("2026-10-14", 10900, 5.0, 13.0, full = false), // partial, S$2.60/L
    )
    private val stats = computeStats(fills)

    @Test
    fun previousDaysIsTheSameLengthJustBefore() {
        // Last 7 days: 9–15 Oct. The 7 before: 2–8 Oct.
        val dates = inPreviousDays(stats, today, 7).map { it.fillUp.date.toString() }
        assertEquals(listOf("2026-10-02", "2026-10-05"), dates)
        assertEquals(listOf("2026-10-10", "2026-10-14"), inLastDays(stats, today, 7).map { it.fillUp.date.toString() })
    }

    @Test
    fun weekComparesTotalsWithTheWeekBefore() {
        val c = comparePreviousDays(stats, today, 7)
        val economy = c.getValue(DashCard.ECONOMY)
        assertEquals(30.0, economy.current!!, 1e-9)
        assertEquals(25.0, economy.previous!!, 1e-9)
        assertEquals(Verdict.GOOD, economy.verdict)

        val ridden = c.getValue(DashCard.KM_PER_DAY)
        assertEquals(45.0, ridden.current!!, 1e-9)
        assertEquals(25.0, ridden.previous!!, 1e-9)
        assertEquals(Direction.UP, ridden.direction)
        assertEquals(Verdict.NEUTRAL, ridden.verdict) // neither good nor bad

        val price = c.getValue(DashCard.PRICE)
        assertEquals((10.4 + 13.0) / 13.0, price.current!!, 1e-9)
        assertEquals((31.2 + 10.0) / 16.0, price.previous!!, 1e-9)
        assertEquals(Verdict.GOOD, price.verdict) // cheaper

        // Nothing was saved the week before, so savings have nothing to compare with.
        assertEquals(2.5 * 8 - 10.4, c.getValue(DashCard.SAVINGS).current!!, 1e-9)
        assertNull(c.getValue(DashCard.SAVINGS).delta)
    }

    @Test
    fun emptyPreviousPeriodMeansNoComparison() {
        // 30 days before the last 30 (17 Aug – 15 Sep) has no fill-ups.
        val c = comparePreviousDays(stats, today, 30)
        DashCard.entries.forEach { card ->
            assertNull(card.name, c.getValue(card).previous)
            assertNull(card.name, c.getValue(card).direction)
        }
        assertTrue(comparePreviousDays(emptyList(), today, 7).values.all { it.delta == null })
    }

    @Test
    fun lastFillComparesEconomyWithThePreviousFullTankAndPriceWithTheFillBefore() {
        val upToMy = computeStats(fills.dropLast(1))
        val c = compareLastFill(upToMy)
        // The partial on 5 Oct has no economy, so the full tank on 2 Oct is the one compared.
        assertEquals(30.0, c.getValue(DashCard.ECONOMY).current!!, 1e-9)
        assertEquals(25.0, c.getValue(DashCard.ECONOMY).previous!!, 1e-9)
        assertEquals(1.3, c.getValue(DashCard.PRICE).current!!, 1e-9)
        assertEquals(2.5, c.getValue(DashCard.PRICE).previous!!, 1e-9)
        assertEquals(Verdict.GOOD, c.getValue(DashCard.PRICE).verdict)
        // The first Malaysian fill-up has no earlier savings to compare with.
        assertNull(c.getValue(DashCard.SAVINGS).delta)
    }

    @Test
    fun partialLastFillOnlyComparesPrice() {
        val c = compareLastFill(stats)
        assertNull(c.getValue(DashCard.ECONOMY).delta)
        assertNull(c.getValue(DashCard.COST_PER_KM).delta)
        val price = c.getValue(DashCard.PRICE)
        assertEquals(2.6, price.current!!, 1e-9)
        assertEquals(1.3, price.previous!!, 1e-9)
        assertEquals(Verdict.BAD, price.verdict)
        assertTrue(compareLastFill(emptyList()).isEmpty())
    }

    @Test
    fun moreSavedIsBetter() {
        val withTwo = computeStats(fills + my("2026-10-15", 11000, 13.0, 13.0))
        val savings = compareLastFill(withTwo).getValue(DashCard.SAVINGS)
        assertEquals(2.5 * 8 - 10.4, savings.previous!!, 1e-9)
        assertEquals(2.6 * 13 - 13.0, savings.current!!, 1e-9)
        assertEquals(Verdict.GOOD, savings.verdict)
    }

    @Test
    fun displayDeltaFollowsTheUnit() {
        val change = MetricChange(30.0, 25.0, Better.HIGHER)
        assertEquals(5.0, change.displayDelta { it }!!, 1e-9)
        // In L/100 km better economy is a smaller number.
        assertEquals(100.0 / 30 - 100.0 / 25, change.displayDelta { 100.0 / it }!!, 1e-9)
        assertNull(MetricChange(30.0, null, Better.HIGHER).displayDelta { it })
    }
}
