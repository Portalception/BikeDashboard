package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import java.time.LocalDate
import java.time.YearMonth

data class DatedValue(val date: LocalDate, val value: Double)

/**
 * Spending split by where it was pumped, in SGD. [unsplit] is old mixed fill-ups from the
 * spreadsheet, which only recorded one total for both countries.
 */
data class SpendSplit(val sg: Double = 0.0, val my: Double = 0.0, val unsplit: Double = 0.0) {
    val total: Double get() = sg + my + unsplit

    operator fun plus(other: SpendSplit) = SpendSplit(sg + other.sg, my + other.my, unsplit + other.unsplit)
}

val FillUp.spendSplit: SpendSplit
    get() = SpendSplit(sgCostSgd ?: 0.0, myCostSgd ?: 0.0, unsplitCostSgd ?: 0.0)

data class MonthlySpend(val month: YearMonth, val costSgd: Double, val split: SpendSplit = SpendSplit(unsplit = costSgd))

data class ChartData(
    /** Price per litre paid in Singapore, one point per fill-up with an SG part. */
    val sgPrice: List<DatedValue>,
    /** Price per litre paid in Malaysia (in SGD). Old mixed fill-ups are left out of both lines. */
    val myPrice: List<DatedValue>,
    val kmPerLitre: List<DatedValue>,
    /** Every calendar month from the first fill-up to the last, including months with no spending. */
    val monthlySpend: List<MonthlySpend>,
    /** What each fill-up cost, for short periods where months would be just one or two bars. */
    val fillSpend: List<DatedValue> = emptyList(),
    /** [fillSpend] split by country, in the same order. */
    val fillSplit: List<SpendSplit> = emptyList(),
    /** Km ridden per day, cost per day and cost per km: one point per full tank that closes a measured stretch. */
    val kmPerDay: List<DatedValue> = emptyList(),
    val costPerDay: List<DatedValue> = emptyList(),
    val costPerKm: List<DatedValue> = emptyList(),
    /** What each fill-up with a Malaysian part saved (negative when it cost more). */
    val savings: List<DatedValue> = emptyList(),
    /** [savings] added up per calendar month, every month from the first fill-up to the last. */
    val monthlySavings: List<Pair<YearMonth, Double>> = emptyList(),
)

fun chartData(stats: List<FillUpStats>): ChartData {
    val months = if (stats.isEmpty()) {
        emptyList()
    } else {
        val spend = stats.groupBy { YearMonth.from(it.fillUp.date) }
            .mapValues { (_, inMonth) -> inMonth.fold(SpendSplit()) { sum, s -> sum + s.fillUp.spendSplit } }
        val first = YearMonth.from(stats.first().fillUp.date)
        val last = YearMonth.from(stats.last().fillUp.date)
        generateSequence(first) { it.plusMonths(1) }
            .takeWhile { it <= last }
            .map { month -> spend[month].let { MonthlySpend(month, it?.total ?: 0.0, it ?: SpendSplit()) } }
            .toList()
    }
    val savingsByMonth = stats.mapNotNull { s -> s.savingsSgd?.let { YearMonth.from(s.fillUp.date) to it } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    fun series(value: (FillUpStats) -> Double?) = stats.mapNotNull { s -> value(s)?.let { DatedValue(s.fillUp.date, it) } }
    return ChartData(
        sgPrice = stats.mapNotNull { s -> s.fillUp.sgPricePerLitre?.let { DatedValue(s.fillUp.date, it) } },
        myPrice = stats.mapNotNull { s -> s.fillUp.myPricePerLitre?.let { DatedValue(s.fillUp.date, it) } },
        kmPerLitre = stats.mapNotNull { s -> s.kmPerLitre?.let { DatedValue(s.fillUp.date, it) } },
        monthlySpend = months,
        fillSpend = stats.map { DatedValue(it.fillUp.date, it.fillUp.totalCostSgd) },
        fillSplit = stats.map { it.fillUp.spendSplit },
        kmPerDay = series { it.kmPerDay },
        costPerDay = series { it.costPerDay },
        costPerKm = series { it.costPerKm },
        savings = series { it.savingsSgd },
        monthlySavings = if (savingsByMonth.isEmpty()) emptyList() else months.map { it.month to (savingsByMonth[it.month] ?: 0.0) },
    )
}

/** Whether the old mixed fill-ups exist, so the chart can say they're left out. */
fun hasOldMixed(stats: List<FillUpStats>) =
    stats.any { it.fillUp.country == Country.MIXED && it.fillUp.unsplitLitres != null }
