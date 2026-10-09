package com.astheral.bikedashboard.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astheral.bikedashboard.calc.ChartData
import com.astheral.bikedashboard.calc.DatedValue
import com.astheral.bikedashboard.calc.SpendSplit
import com.astheral.bikedashboard.ui.DisplayUnit
import com.astheral.bikedashboard.ui.EconomyUnit
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.theme.CountryColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
// The economy chart follows the colour theme; price and spend use the country colours.
private val EconomyColor @Composable get() = MaterialTheme.colorScheme.tertiary

/** Dates on the x axis are stored as day numbers; show them as "Mar 25", or "3 Oct" over short spans. */
internal fun dateAxis(short: Boolean) = CartesianValueFormatter { _, x, _ ->
    LocalDate.ofEpochDay(x.toLong()).format(if (short) DAY_MONTH else MONTH_YEAR)
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** How many days apart the date labels sit, so a few always fit. */
internal fun labelEvery(points: List<DatedValue>): Int {
    val span = if (points.size < 2) 1L else points.last().date.toEpochDay() - points.first().date.toEpochDay()
    return when {
        span <= 21 -> 7
        span <= 75 -> 14
        span <= 200 -> 45
        else -> 90
    }
}

internal fun yFormatter(digits: Int, prefix: String = "") =
    CartesianValueFormatter { _, y, _ -> prefix + String.format(Locale.ENGLISH, "%.${digits}f", y) }

@Composable
private fun ChartCard(
    title: String,
    period: String,
    legend: List<Pair<String, Color>> = emptyList(),
    note: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    GroupCard(contentPadding = 20.dp, onClick = onClick, onLongClick = onLongClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AccentChip(period)
            }
            if (legend.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    legend.forEach { (name, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color, CircleShape))
                            Text("  $name", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            chart()
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Axis labels and grid lines in the theme's quieter colours, so they read on light, dark and black. */
@Composable
private fun axisLabel() = rememberAxisLabelComponent(
    style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp),
)

@Composable
private fun guideline() = rememberAxisGuidelineComponent(fill = Fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))

@Composable
internal fun startAxis(formatter: CartesianValueFormatter) =
    VerticalAxis.rememberStart(label = axisLabel(), guideline = guideline(), valueFormatter = formatter)

@Composable
internal fun bottomAxis(formatter: CartesianValueFormatter, spacing: Int) = HorizontalAxis.rememberBottom(
    label = axisLabel(),
    guideline = guideline(),
    valueFormatter = formatter,
    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }),
)

/** The whole period fits on screen: no scrolling or pinch-zooming inside the chart. */
@Composable
internal fun FittedChart(
    chart: com.patrykandpatrick.vico.compose.cartesian.CartesianChart,
    producer: CartesianChartModelProducer,
) {
    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Below this many points a line can't show a real trend, so it's drawn dotted and straight. */
const val SMOOTH_MIN_POINTS = 6

/**
 * A line in [color] through [points] points. With enough points it's a smooth curve; with only a
 * few it's dotted, so it doesn't look like a trend. Short series also mark each fill-up, so a
 * period with one point still shows it.
 */
@Composable
internal fun line(color: Color, points: Int) = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(Fill(color)),
    stroke = if (points < SMOOTH_MIN_POINTS) {
        LineCartesianLayer.LineStroke.Dashed(thickness = 2.5.dp, dashLength = 4.dp, gapLength = 4.dp)
    } else {
        LineCartesianLayer.LineStroke.Continuous(thickness = 2.5.dp)
    },
    interpolator = if (points < SMOOTH_MIN_POINTS) LineCartesianLayer.Interpolator.Sharp else LineCartesianLayer.Interpolator.cubic(),
    pointProvider = if (points <= 20) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(color), shape = CircleShape), 7.dp),
        )
    } else {
        null
    },
)

/**
 * The y axis fits the data instead of starting at zero, with a little room above and below, so
 * small changes (like 2.30 vs 2.45 a litre) are easy to see.
 */
@Composable
internal fun fittedRange(values: List<Double>): CartesianLayerRangeProvider = remember(values) {
    if (values.isEmpty()) return@remember CartesianLayerRangeProvider.auto()
    val lo = values.min()
    val hi = values.max()
    val pad = if (hi > lo) (hi - lo) * 0.15 else maxOf(kotlin.math.abs(hi) * 0.05, 0.01)
    CartesianLayerRangeProvider.fixed(minY = lo - pad, maxY = hi + pad)
}

@Composable
fun PriceChart(data: ChartData, hasOldMixed: Boolean, period: String, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    if (data.sgPrice.isEmpty() && data.myPrice.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    // Each country only gets a line if it has points; Vico needs every series to be non-empty.
    val sgColor = CountryColors.sg
    val myColor = CountryColors.my
    val series = listOf(data.sgPrice to sgColor, data.myPrice to myColor).filter { it.first.isNotEmpty() }
    val all = (data.sgPrice + data.myPrice).sortedBy { it.date }
    val every = labelEvery(all)
    LaunchedEffect(data) {
        producer.runTransaction {
            lineSeries {
                series.forEach { (points, _) ->
                    series(points.map { it.date.toEpochDay() }, points.map { it.value })
                }
            }
        }
    }
    ChartCard(
        "Petrol Price per Litre",
        period,
        legend = listOf("Singapore" to sgColor, "Malaysia" to myColor).filter { (name, _) ->
            if (name == "Singapore") data.sgPrice.isNotEmpty() else data.myPrice.isNotEmpty()
        },
        note = if (hasOldMixed) "In SGD. Old mixed fill-ups aren't shown, because their price is a blend of both countries." else "In SGD.",
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(series.map { (points, color) -> line(color, points.size) }),
                    rangeProvider = fittedRange(all.map { it.value }),
                ),
                startAxis = startAxis(yFormatter(2, "S$")),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
    }
}

/** Fuel economy in the unit picked on the dashboard tile. */
@Composable
fun EconomyChart(data: ChartData, period: String, unit: EconomyUnit = EconomyUnit.KM_PER_L, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    if (data.kmPerLitre.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    val every = labelEvery(data.kmPerLitre)
    LaunchedEffect(data, unit) {
        producer.runTransaction {
            lineSeries { series(data.kmPerLitre.map { it.date.toEpochDay() }, data.kmPerLitre.map { unit.convert(it.value) }) }
        }
    }
    ChartCard("Fuel Economy (${unit.label})", period, onClick = onClick, onLongClick = onLongClick) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(line(EconomyColor, data.kmPerLitre.size)),
                    rangeProvider = fittedRange(data.kmPerLitre.map { unit.convert(it.value) }),
                ),
                startAxis = startAxis(yFormatter(if (unit == EconomyUnit.L_PER_100KM) 1 else 0)),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
    }
}

/**
 * Spending as bars: one per month over a lifetime, or one per fill-up over a short period
 * ([byMonth] false), where months would only be one or two bars. Each bar is coloured by where
 * the petrol was pumped, stacked when a fill-up or month has both.
 */
@Composable
fun SpendChart(data: ChartData, period: String, byMonth: Boolean, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    val labels: List<String>
    val splits: List<SpendSplit>
    if (byMonth) {
        labels = data.monthlySpend.map { it.month.format(MONTH_YEAR) }
        splits = data.monthlySpend.map { it.split }
    } else {
        labels = data.fillSpend.map { it.date.format(DAY_MONTH) }
        splits = data.fillSplit
    }
    if (splits.isEmpty()) return
    // Only countries that appear get a series and a legend entry.
    val parts = listOf<Triple<String, Color, (SpendSplit) -> Double>>(
        Triple("Singapore", CountryColors.sg, { it.sg }),
        Triple("Malaysia", CountryColors.my, { it.my }),
        Triple("Old Mixed", CountryColors.mixed, { it.unsplit }),
    ).filter { (_, _, part) -> splits.any { part(it) > 0 } }
    if (parts.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, splits) {
        producer.runTransaction {
            columnSeries { parts.forEach { (_, _, part) -> series(splits.indices.toList(), splits.map(part)) } }
        }
    }
    // While switching period the chart can briefly ask about bars from the old data, so stay in range:
    // the chart library doesn't allow a blank label.
    val axis = remember(labels) { CartesianValueFormatter { _, x, _ -> labels[x.toInt().coerceIn(0, labels.lastIndex)] } }
    val thickness = if (splits.size > 12) 8.dp else 14.dp
    ChartCard(
        if (byMonth) "Monthly Spend (SGD)" else "Spend per Fill-Up (SGD)",
        period,
        legend = parts.map { (name, color, _) -> name to color },
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        FittedChart(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(parts.map { (_, color, _) -> rememberLineComponent(fill = Fill(color), thickness = thickness) }),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = startAxis(yFormatter(0, "S$")),
                bottomAxis = bottomAxis(axis, if (splits.size > 12) 3 else 2),
            ),
            producer,
        )
    }
}

/** Decimal places for y axis labels, so values a few apart get none and tiny ones get enough. */
internal fun axisDigits(values: List<Double>): Int {
    if (values.isEmpty()) return 1
    val size = maxOf(values.max() - values.min(), values.maxOf { kotlin.math.abs(it) } * 0.1)
    return when {
        size >= 20 -> 0
        size >= 2 -> 1
        size >= 0.2 -> 2
        else -> 3
    }
}

/**
 * One number over time as a line, one point per fill-up that has it, shown in [unit]. Two
 * fill-ups on the same day keep the later one, so the line doesn't double back.
 */
@Composable
fun TrendChart(
    title: String,
    points: List<DatedValue>,
    unit: DisplayUnit,
    period: String,
    color: Color = EconomyColor,
    note: String? = "In ${unit.label}.",
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    if (points.isEmpty()) return
    val byDay = remember(points, unit) {
        points.associate { it.date.toEpochDay() to unit.convert(it.value) }.toList()
    }
    val producer = remember { CartesianChartModelProducer() }
    val every = labelEvery(points)
    LaunchedEffect(byDay) {
        producer.runTransaction { lineSeries { series(byDay.map { it.first }, byDay.map { it.second }) } }
    }
    ChartCard(title, period, note = note, onClick = onClick, onLongClick = onLongClick) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(line(color, byDay.size)),
                    rangeProvider = fittedRange(byDay.map { it.second }),
                ),
                startAxis = startAxis(yFormatter(axisDigits(byDay.map { it.second }))),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
    }
}

/**
 * What pumping in Malaysia saved, as bars: one per Malaysian fill-up over a short period, or one
 * per month over a lifetime ([byMonth]). Savings are green; a fill-up or month where Malaysia
 * cost more than Singapore would have goes below the line in red.
 */
@Composable
fun SavingsChart(data: ChartData, period: String, byMonth: Boolean, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null) {
    val labels: List<String>
    val values: List<Double>
    if (byMonth) {
        labels = data.monthlySavings.map { it.first.format(MONTH_YEAR) }
        values = data.monthlySavings.map { it.second }
    } else {
        labels = data.savings.map { it.date.format(DAY_MONTH) }
        values = data.savings.map { it.value }
    }
    if (values.isEmpty()) return
    val saved = CountryColors.my
    val lost = MaterialTheme.colorScheme.error
    // Gains and losses are two series so each gets its colour; at each bar one of them is zero.
    val parts = listOf<Triple<String, Color, (Double) -> Double>>(
        Triple("Saved", saved, { maxOf(it, 0.0) }),
        Triple("Cost More", lost, { minOf(it, 0.0) }),
    ).filter { (_, _, part) -> values.any { part(it) != 0.0 } }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, values) {
        producer.runTransaction {
            if (parts.isEmpty()) {
                columnSeries { series(values.indices.toList(), values) }
            } else {
                columnSeries { parts.forEach { (_, _, part) -> series(values.indices.toList(), values.map(part)) } }
            }
        }
    }
    val axis = remember(labels) { CartesianValueFormatter { _, x, _ -> labels[x.toInt().coerceIn(0, labels.lastIndex)] } }
    val thickness = if (values.size > 12) 8.dp else 14.dp
    val total = values.sum()
    ChartCard(
        if (byMonth) "Monthly Petrol Savings (SGD)" else "Petrol Savings per Fill-Up (SGD)",
        period,
        legend = if (parts.size > 1) parts.map { (name, color, _) -> name to color } else emptyList(),
        note = (if (total >= 0) "${sgd(total)} saved here in all" else "${sgd(-total)} more than Singapore here in all") +
            (if (byMonth) ", by month." else ", from fill-ups with Malaysian petrol."),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        FittedChart(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        (if (parts.isEmpty()) listOf(saved) else parts.map { it.second }).map { rememberLineComponent(fill = Fill(it), thickness = thickness) },
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = startAxis(yFormatter(0, "S$")),
                bottomAxis = bottomAxis(axis, if (values.size > 12) 3 else 2),
            ),
            producer,
        )
    }
}
