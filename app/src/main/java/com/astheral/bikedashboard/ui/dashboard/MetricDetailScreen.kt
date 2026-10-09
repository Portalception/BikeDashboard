package com.astheral.bikedashboard.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.FillUpStats
import com.astheral.bikedashboard.calc.PeriodSummary
import com.astheral.bikedashboard.calc.chartData
import com.astheral.bikedashboard.calc.inLastDays
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.DisplayUnit
import com.astheral.bikedashboard.ui.DistanceCostUnit
import com.astheral.bikedashboard.ui.DistanceTimeUnit
import com.astheral.bikedashboard.ui.EconomyUnit
import com.astheral.bikedashboard.ui.PriceUnit
import com.astheral.bikedashboard.ui.SgdUnit
import com.astheral.bikedashboard.ui.TimeCostUnit
import com.astheral.bikedashboard.ui.Units
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillTabs
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.service.FilterPill
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.CountryColors
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import kotlinx.coroutines.launch

/**
 * The numbers on the dashboard that open a detailed overview. [higherIsBetter] is null for numbers
 * that are neither good nor bad, like how far you ride.
 */
enum class Metric(val title: String, val higherIsBetter: Boolean?) {
    ECONOMY("Fuel Economy", true),
    KM_PER_DAY("KM Daily", null),
    PRICE("Petrol Price", false),
    COST_PER_KM("Cost per km", false),
    COST_PER_DAY("Cost per Day", false),
    SAVINGS("Petrol Savings", true),
    SPEND("Spending", false),
    ;

    /** This metric for one fill-up, in the app's base unit. */
    fun of(s: FillUpStats): Double? = when (this) {
        ECONOMY -> s.kmPerLitre
        PRICE -> s.pricePerLitre
        COST_PER_KM -> s.costPerKm
        COST_PER_DAY -> s.costPerDay
        KM_PER_DAY -> s.kmPerDay
        SAVINGS -> s.savingsSgd
        SPEND -> s.fillUp.totalCostSgd
    }

    /** This metric over a period, worked out from totals. */
    fun of(p: PeriodSummary): Double? = when (this) {
        ECONOMY -> p.kmPerLitre
        PRICE -> p.pricePerLitre
        COST_PER_KM -> p.costPerKm
        COST_PER_DAY -> p.costPerDay
        KM_PER_DAY -> p.kmPerDay
        SAVINGS -> p.savingsSgd
        SPEND -> p.costSgd.takeIf { p.fillUpCount > 0 }
    }

    fun unit(units: Units, rmPerSgd: Double?): DisplayUnit = when (this) {
        ECONOMY -> units.economy
        PRICE -> units.price.at(rmPerSgd)
        COST_PER_KM -> units.distanceCost
        COST_PER_DAY -> units.timeCost
        KM_PER_DAY -> units.distanceTime
        SAVINGS, SPEND -> SgdUnit
    }
}

/** The fill-ups a period covers; "Last fill" shows the last 10 so there's something to compare. */
private fun statsFor(period: Period, d: DashboardData): List<FillUpStats> = when (period) {
    Period.LAST -> d.stats.takeLast(10)
    Period.LIFETIME -> d.stats
    else -> inLastDays(d.stats, d.today, period.days!!)
}

/**
 * A closer look at one dashboard number: its value for the period, how it compares with the other
 * periods, best and worst fill-ups, a chart, and the fill-ups behind it. Swipe or use the tabs to
 * change period, and the unit chips to change unit.
 */
@Composable
fun MetricDetailScreen(
    metric: Metric,
    startPeriod: Int,
    profile: Profile?,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    val units by viewModel.units.collectAsState()
    val d = data ?: return
    val pager = rememberPagerState(initialPage = startPeriod.coerceIn(0, Period.entries.lastIndex), pageCount = { Period.entries.size })
    val scope = rememberCoroutineScope()
    val unit = metric.unit(units, d.rmPerSgd)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val edges = Modifier.padding(horizontal = 16.dp)
        Row(edges.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(metric.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                profile?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
        UnitChips(metric, units, d.rmPerSgd, edges, viewModel::updateUnits)
        PillTabs(
            Period.entries.map { null to it.label },
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = edges,
        )
        HorizontalPager(
            state = pager,
            verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
        ) { page -> DetailPage(metric, Period.entries[page], d, unit, units) }
    }
}

/** The units this metric can be shown in, as pills. */
@Composable
private fun UnitChips(metric: Metric, units: Units, rmPerSgd: Double?, modifier: Modifier, onUnits: ((Units) -> Units) -> Unit) {
    val options: List<Pair<String, Pair<Boolean, (Units) -> Units>>> = when (metric) {
        Metric.ECONOMY -> EconomyUnit.entries.map { u -> u.label to ((units.economy == u) to { x: Units -> x.copy(economy = u) }) }
        Metric.PRICE -> if (rmPerSgd == null) emptyList() else PriceUnit.entries.map { u -> u.label to ((units.price == u) to { x: Units -> x.copy(price = u) }) }
        Metric.COST_PER_KM -> DistanceCostUnit.entries.map { u -> u.label to ((units.distanceCost == u) to { x: Units -> x.copy(distanceCost = u) }) }
        Metric.COST_PER_DAY -> TimeCostUnit.entries.map { u -> u.label to ((units.timeCost == u) to { x: Units -> x.copy(timeCost = u) }) }
        Metric.KM_PER_DAY -> DistanceTimeUnit.entries.map { u -> u.label to ((units.distanceTime == u) to { x: Units -> x.copy(distanceTime = u) }) }
        Metric.SAVINGS, Metric.SPEND -> emptyList()
    }
    if (options.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, choice) -> FilterPill(label, null, choice.first) { onUnits(choice.second) } }
    }
}

@Composable
private fun DetailPage(metric: Metric, period: Period, d: DashboardData, unit: DisplayUnit, units: Units) {
    val summary = d.summary.of(period)
    val stats = statsFor(period, d)
    val perFill = stats.mapNotNull { s -> metric.of(s)?.let { s to it } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The headline number for the period.
        GroupCard(contentPadding = 20.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)) {
            Text(
                when (period) {
                    Period.LAST -> "Last Fill-Up"
                    Period.LIFETIME -> "All Time"
                    else -> "Last ${period.days} Days"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val value = metric.of(summary)
            if (value == null) {
                Text("—", style = MaterialTheme.typography.displaySmall, fontFamily = NumberFont)
            } else {
                CountUpText(unit.convert(value), unit::format, style = MaterialTheme.typography.displaySmall)
            }
            Text(
                "${count(summary.fillUpCount, "fill-up")} · ${km(summary.distanceKm)} · ${sgd(summary.costSgd)} spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(Icons.AutoMirrored.Filled.CompareArrows, "Compared With")
        GroupCard {
            Period.entries.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                val v = metric.of(d.summary.of(p))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.label,
                        Modifier.weight(1f),
                        fontWeight = if (p == period) FontWeight.Bold else FontWeight.Normal,
                        color = if (p == period) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(v?.let { unit.format(unit.convert(it)) } ?: "—", fontFamily = NumberFont, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (perFill.size >= 2) {
            SectionHeader(Icons.Filled.Analytics, if (period == Period.LAST) "Last ${count(perFill.size, "Fill-Up")}" else "Per Fill-Up")
            StatsCard(metric, perFill, unit)
        }

        if (perFill.isNotEmpty()) {
            SectionHeader(Icons.AutoMirrored.Filled.ShowChart, "Over Time")
            if (metric == Metric.SPEND) {
                SpendChart(chartData(stats), period.chartLabel, byMonth = period == Period.LIFETIME)
            } else {
                MetricChart(metric, perFill, unit, period.chartLabel)
            }

            SectionHeader(Icons.AutoMirrored.Filled.FormatListBulleted, "Fill-Ups")
            GroupCard {
                perFill.reversed().take(30).forEachIndexed { i, (s, v) ->
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.fillUp.date.short(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(s.fillUp.country.label(), s.distanceKm?.let { km(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(unit.format(unit.convert(v)), fontFamily = NumberFont, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (perFill.size > 30) {
                    Text(
                        "…and ${perFill.size - 30} earlier",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text("No fill-ups with this figure in this period yet.", Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Best, worst, average and median of the period's fill-ups, best judged on the metric itself. */
@Composable
private fun StatsCard(metric: Metric, perFill: List<Pair<FillUpStats, Double>>, unit: DisplayUnit) {
    val sorted = perFill.sortedBy { it.second }
    val best = if (metric.higherIsBetter == true) sorted.last() else sorted.first()
    val worst = if (metric.higherIsBetter == true) sorted.first() else sorted.last()
    val values = sorted.map { it.second }
    val median = if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    val rows = buildList {
        if (metric == Metric.SPEND) {
            add(Triple("Biggest", sorted.last().second, sorted.last().first))
            add(Triple("Smallest", sorted.first().second, sorted.first().first))
            add(Triple("Total", values.sum(), null))
        } else if (metric.higherIsBetter == null) {
            // Neither good nor bad, so no "best" and "worst".
            add(Triple("Most", sorted.last().second, sorted.last().first))
            add(Triple("Least", sorted.first().second, sorted.first().first))
        } else {
            add(Triple("Best", best.second, best.first))
            add(Triple("Worst", worst.second, worst.first))
        }
        add(Triple("Average", values.average(), null))
        add(Triple("Median", median, null))
    }
    GroupCard {
        rows.forEachIndexed { i, (label, v, s) ->
            if (i > 0) GroupDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label)
                    s?.let {
                        Text(
                            "${it.fillUp.date.short()} · ${it.fillUp.country.label()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(unit.format(unit.convert(v)), fontFamily = NumberFont, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** One point per fill-up, coloured like the dashboard tile, on a y axis fitted to the data. */
@Composable
private fun MetricChart(metric: Metric, perFill: List<Pair<FillUpStats, Double>>, unit: DisplayUnit, period: String) {
    val points = perFill.map { (s, v) -> s.fillUp.date.toEpochDay() to unit.convert(v) }
        // Two fill-ups on one day would stack on one x; keep the later.
        .associate { it }.toList()
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        producer.runTransaction { lineSeries { series(points.map { it.first }, points.map { it.second }) } }
    }
    val color: Color = if (metric == Metric.SAVINGS) CountryColors.my else MaterialTheme.colorScheme.tertiary
    val dated = perFill.map { (s, v) -> com.astheral.bikedashboard.calc.DatedValue(s.fillUp.date, v) }
    val every = labelEvery(dated)
    val digits = when (metric) {
        Metric.COST_PER_KM -> if (unit == DistanceCostUnit.PER_KM) 3 else 1
        Metric.ECONOMY, Metric.KM_PER_DAY -> 1
        else -> 2
    }
    GroupCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(metric.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            com.astheral.bikedashboard.ui.components.AccentChip(period)
        }
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(line(color, points.size)),
                    rangeProvider = fittedRange(points.map { it.second }),
                ),
                startAxis = startAxis(yFormatter(digits)),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
        if (points.size < SMOOTH_MIN_POINTS) {
            Text(
                "Dotted: only ${points.size} ${if (points.size == 1) "fill-up" else "fill-ups"} here, too few for a trend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
