package com.astheral.bikedashboard.calc

import java.time.LocalDate

/**
 * Totals for a group of fill-ups. Rates are worked out from totals, not by averaging
 * each fill-up's own figure, so a short top-up doesn't count as much as a full tank.
 *
 * Km/L, cost/km, cost/day and km/day come from the stretches between full tanks whose closing
 * full tank falls in the group, using everything pumped over each stretch (partial fills
 * included). The very first full tank has nothing to measure from, and partial fills wait for
 * the next full tank. Spend, price per litre and the fill-up count include every fill-up.
 */
data class PeriodSummary(
    val fillUpCount: Int,
    val distanceKm: Int,
    val costSgd: Double,
    val kmPerLitre: Double?,
    val costPerKm: Double?,
    val costPerDay: Double?,
    /** Kilometres ridden per day: total distance ÷ total days, over measured stretches. */
    val kmPerDay: Double?,
    val pricePerLitre: Double?,
    val savingsSgd: Double?,
)

data class DashboardSummary(
    val lastFillUp: PeriodSummary,
    val last7Days: PeriodSummary,
    val last30Days: PeriodSummary,
    val lifetime: PeriodSummary,
)

fun summarize(stats: List<FillUpStats>): PeriodSummary {
    val measured = stats.filter { it.distanceKm != null }
    val distance = measured.sumOf { it.distanceKm!! }
    val measuredLitres = measured.sumOf { it.segmentLitres ?: it.fillUp.totalLitres }
    val measuredCost = measured.sumOf { it.segmentCost }

    val withDays = stats.filter { it.days != null }
    val days = withDays.sumOf { it.days!! }

    val litres = stats.sumOf { it.fillUp.totalLitres }
    val cost = stats.sumOf { it.fillUp.totalCostSgd }
    val savings = stats.mapNotNull { it.savingsSgd }

    return PeriodSummary(
        fillUpCount = stats.size,
        distanceKm = distance,
        costSgd = cost,
        kmPerLitre = if (distance > 0 && measuredLitres > 0) distance / measuredLitres else null,
        costPerKm = if (distance > 0) measuredCost / distance else null,
        costPerDay = if (days > 0) withDays.sumOf { it.segmentCost } / days else null,
        kmPerDay = if (days > 0) withDays.sumOf { it.distanceKm ?: 0 }.toDouble() / days else null,
        pricePerLitre = if (litres > 0) cost / litres else null,
        savingsSgd = if (savings.isEmpty()) null else savings.sum(),
    )
}

/** What the stretch measured by this fill-up cost: its segment if it closes one, otherwise just itself. */
private val FillUpStats.segmentCost: Double get() = segmentCostSgd ?: fillUp.totalCostSgd

/** Fill-ups in the last [days] days: from [days] − 1 days before [today] up to [today]. */
fun inLastDays(stats: List<FillUpStats>, today: LocalDate, days: Long): List<FillUpStats> {
    val windowStart = today.minusDays(days - 1)
    return stats.filter { !it.fillUp.date.isBefore(windowStart) && !it.fillUp.date.isAfter(today) }
}

/**
 * [stats] must be oldest first, as returned by [computeStats].
 * "Last 30 days" covers fill-ups dated from 29 days before [today] up to [today]; the week likewise.
 */
fun summarizeDashboard(stats: List<FillUpStats>, today: LocalDate): DashboardSummary =
    DashboardSummary(
        lastFillUp = summarize(stats.takeLast(1)),
        last7Days = summarize(inLastDays(stats, today, 7)),
        last30Days = summarize(inLastDays(stats, today, 30)),
        lifetime = summarize(stats),
    )
