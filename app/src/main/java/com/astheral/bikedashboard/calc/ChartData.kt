package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import java.time.LocalDate
import java.time.YearMonth

data class DatedValue(val date: LocalDate, val value: Double)

data class MonthlySpend(val month: YearMonth, val costSgd: Double)

data class ChartData(
    /** Price per litre paid in Singapore, one point per fill-up with an SG part. */
    val sgPrice: List<DatedValue>,
    /** Price per litre paid in Malaysia (in SGD). Old mixed fill-ups are left out of both lines. */
    val myPrice: List<DatedValue>,
    val kmPerLitre: List<DatedValue>,
    /** Every calendar month from the first fill-up to the last, including months with no spending. */
    val monthlySpend: List<MonthlySpend>,
)

fun chartData(stats: List<FillUpStats>): ChartData {
    val months = if (stats.isEmpty()) {
        emptyList()
    } else {
        val spend = stats.groupBy { YearMonth.from(it.fillUp.date) }
            .mapValues { (_, inMonth) -> inMonth.sumOf { it.fillUp.totalCostSgd } }
        val first = YearMonth.from(stats.first().fillUp.date)
        val last = YearMonth.from(stats.last().fillUp.date)
        generateSequence(first) { it.plusMonths(1) }
            .takeWhile { it <= last }
            .map { MonthlySpend(it, spend[it] ?: 0.0) }
            .toList()
    }
    return ChartData(
        sgPrice = stats.mapNotNull { s -> s.fillUp.sgPricePerLitre?.let { DatedValue(s.fillUp.date, it) } },
        myPrice = stats.mapNotNull { s -> s.fillUp.myPricePerLitre?.let { DatedValue(s.fillUp.date, it) } },
        kmPerLitre = stats.mapNotNull { s -> s.kmPerLitre?.let { DatedValue(s.fillUp.date, it) } },
        monthlySpend = months,
    )
}

/** Whether the old mixed fill-ups exist, so the chart can say they're left out. */
fun hasOldMixed(stats: List<FillUpStats>) =
    stats.any { it.fillUp.country == Country.MIXED && it.fillUp.unsplitLitres != null }
