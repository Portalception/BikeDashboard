package com.astheral.bikedashboard.calc

import java.time.LocalDate

/**
 * Totals for a group of fill-ups. Rates are worked out from totals, not by averaging
 * each fill-up's own figure, so a short top-up doesn't count as much as a full tank.
 *
 * Km/L, cost/km and cost/day only use fill-ups that have a previous one to measure
 * from: the very first fill-up's fuel was used after it, so it has no distance yet.
 */
data class PeriodSummary(
    val fillUpCount: Int,
    val distanceKm: Int,
    val costSgd: Double,
    val kmPerLitre: Double?,
    val costPerKm: Double?,
    val costPerDay: Double?,
    val pricePerLitre: Double?,
    val savingsSgd: Double?,
)

data class DashboardSummary(
    val lastFillUp: PeriodSummary,
    val last30Days: PeriodSummary,
    val lifetime: PeriodSummary,
)

fun summarize(stats: List<FillUpStats>): PeriodSummary {
    val measured = stats.filter { it.distanceKm != null }
    val distance = measured.sumOf { it.distanceKm!! }
    val measuredLitres = measured.sumOf { it.fillUp.totalLitres }
    val measuredCost = measured.sumOf { it.fillUp.totalCostSgd }

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
        costPerDay = if (days > 0) withDays.sumOf { it.fillUp.totalCostSgd } / days else null,
        pricePerLitre = if (litres > 0) cost / litres else null,
        savingsSgd = if (savings.isEmpty()) null else savings.sum(),
    )
}

/**
 * [stats] must be oldest first, as returned by [computeStats].
 * "Last 30 days" covers fill-ups dated from 29 days before [today] up to [today].
 */
fun summarizeDashboard(stats: List<FillUpStats>, today: LocalDate): DashboardSummary {
    val windowStart = today.minusDays(29)
    return DashboardSummary(
        lastFillUp = summarize(stats.takeLast(1)),
        last30Days = summarize(stats.filter { !it.fillUp.date.isBefore(windowStart) && !it.fillUp.date.isAfter(today) }),
        lifetime = summarize(stats),
    )
}
