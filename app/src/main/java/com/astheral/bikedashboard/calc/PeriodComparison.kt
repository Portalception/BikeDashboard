package com.astheral.bikedashboard.calc

import java.time.LocalDate

/**
 * Fill-ups in the [days] days just before the last [days] days: from 2 × [days] − 1 days before
 * [today] up to [days] days before it. With [days] = 7 and today the 15th, that's the 2nd to the 8th.
 */
fun inPreviousDays(stats: List<FillUpStats>, today: LocalDate, days: Long): List<FillUpStats> {
    val start = today.minusDays(2 * days - 1)
    val end = today.minusDays(days)
    return stats.filter { !it.fillUp.date.isBefore(start) && !it.fillUp.date.isAfter(end) }
}

/**
 * Each dashboard card's number for the last fill-up next to the same number earlier on: the latest
 * earlier fill-up that has it. Fuel economy, cost per km, cost per day and km per day only exist on
 * full tanks (they come from the stretch a full tank closes), so those compare with the previous
 * full tank; price compares with the fill-up just before; savings with the last Malaysian fill-up.
 * [stats] must be oldest first, as returned by [computeStats]. Empty when there are no fill-ups.
 */
fun compareLastFill(stats: List<FillUpStats>): Map<DashCard, MetricChange> {
    val last = stats.lastOrNull() ?: return emptyMap()
    val earlier = stats.dropLast(1).asReversed()
    return DashCard.entries.associateWith { card ->
        MetricChange(card.of(last), earlier.firstNotNullOfOrNull { card.of(it) }, card.better)
    }
}

/**
 * Each dashboard card's number for the last [days] days next to the [days] days before that, both
 * worked out from totals like the dashboard ([summarize]). A number missing on either side (no
 * fill-ups then, or none closing a full tank) has no change.
 */
fun comparePreviousDays(stats: List<FillUpStats>, today: LocalDate, days: Long): Map<DashCard, MetricChange> {
    val now = summarize(inLastDays(stats, today, days))
    val before = inPreviousDays(stats, today, days).takeIf { it.isNotEmpty() }?.let(::summarize)
    return DashCard.entries.associateWith { card -> MetricChange(card.of(now), before?.let(card::of), card.better) }
}

/** The change in a number once shown in some other unit (like L/100 km), so the arrow and difference match what's on screen. */
fun MetricChange.displayDelta(convert: (Double) -> Double): Double? =
    if (current != null && previous != null) convert(current) - convert(previous) else null
