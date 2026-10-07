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
// Economy and spend charts follow the colour theme.
private val EconomyColor @Composable get() = MaterialTheme.colorScheme.tertiary
private val SpendColor @Composable get() = MaterialTheme.colorScheme.primary

/** Dates on the x axis are stored as day numbers; show them as "Mar 25", or "3 Oct" over short spans. */
private fun dateAxis(short: Boolean) = CartesianValueFormatter { _, x, _ ->
    LocalDate.ofEpochDay(x.toLong()).format(if (short) DAY_MONTH else MONTH_YEAR)
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** How many days apart the date labels sit, so a few always fit. */
private fun labelEvery(points: List<DatedValue>): Int {
    val span = if (points.size < 2) 1L else points.last().date.toEpochDay() - points.first().date.toEpochDay()
    return when {
        span <= 21 -> 7
        span <= 75 -> 14
        span <= 200 -> 45
        else -> 90
    }
}

private fun yFormatter(digits: Int, prefix: String = "") =
    CartesianValueFormatter { _, y, _ -> prefix + String.format(Locale.ENGLISH, "%.${digits}f", y) }

@Composable
private fun ChartCard(
    title: String,
    period: String,
    legend: List<Pair<String, Color>> = emptyList(),
    note: String? = null,
    chart: @Composable () -> Unit,
) {
    GroupCard(contentPadding = 20.dp) {
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
private fun startAxis(formatter: CartesianValueFormatter) =
    VerticalAxis.rememberStart(label = axisLabel(), guideline = guideline(), valueFormatter = formatter)

@Composable
private fun bottomAxis(formatter: CartesianValueFormatter, spacing: Int) = HorizontalAxis.rememberBottom(
    label = axisLabel(),
    guideline = guideline(),
    valueFormatter = formatter,
    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }),
)

/** The whole period fits on screen: no scrolling or pinch-zooming inside the chart. */
@Composable
private fun FittedChart(
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

/** A line in [color]; with [dots], each fill-up is also marked, so a period with one point still shows it. */
@Composable
private fun line(color: Color, dots: Boolean) = LineCartesianLayer.rememberLine(
    fill = LineCartesianLayer.LineFill.single(Fill(color)),
    pointProvider = if (dots) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(fill = Fill(color), shape = CircleShape), 7.dp),
        )
    } else {
        null
    },
)

@Composable
fun PriceChart(data: ChartData, hasOldMixed: Boolean, period: String) {
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
        "Petrol price per litre",
        period,
        legend = listOf("Singapore" to sgColor, "Malaysia" to myColor).filter { (name, _) ->
            if (name == "Singapore") data.sgPrice.isNotEmpty() else data.myPrice.isNotEmpty()
        },
        note = if (hasOldMixed) "In SGD. Old mixed fill-ups aren't shown, because their price is a blend of both countries." else "In SGD.",
    ) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(series.map { line(it.second, dots = all.size <= 20) })),
                startAxis = startAxis(yFormatter(2, "S$")),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
    }
}

@Composable
fun EconomyChart(data: ChartData, period: String) {
    if (data.kmPerLitre.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    val every = labelEvery(data.kmPerLitre)
    LaunchedEffect(data) {
        producer.runTransaction {
            lineSeries { series(data.kmPerLitre.map { it.date.toEpochDay() }, data.kmPerLitre.map { it.value }) }
        }
    }
    ChartCard("Fuel economy (km/L)", period) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line(EconomyColor, dots = data.kmPerLitre.size <= 20))),
                startAxis = startAxis(yFormatter(0)),
                bottomAxis = bottomAxis(dateAxis(every < 45), every),
            ),
            producer,
        )
    }
}

/**
 * Spending as bars: one per month over a lifetime, or one per fill-up over a short period
 * ([byMonth] false), where months would only be one or two bars.
 */
@Composable
fun SpendChart(data: ChartData, period: String, byMonth: Boolean) {
    val labels: List<String>
    val values: List<Double>
    if (byMonth) {
        labels = data.monthlySpend.map { it.month.format(MONTH_YEAR) }
        values = data.monthlySpend.map { it.costSgd }
    } else {
        labels = data.fillSpend.map { it.date.format(DAY_MONTH) }
        values = data.fillSpend.map { it.value }
    }
    if (values.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, values) {
        producer.runTransaction { columnSeries { series(values.indices.toList(), values) } }
    }
    // While switching period the chart can briefly ask about bars from the old data, so stay in range:
    // the chart library doesn't allow a blank label.
    val axis = remember(labels) { CartesianValueFormatter { _, x, _ -> labels[x.toInt().coerceIn(0, labels.lastIndex)] } }
    ChartCard(if (byMonth) "Monthly spend (SGD)" else "Spend per fill-up (SGD)", period) {
        FittedChart(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(SpendColor), thickness = if (values.size > 12) 8.dp else 14.dp),
                    ),
                ),
                startAxis = startAxis(yFormatter(0, "S$")),
                bottomAxis = bottomAxis(axis, if (values.size > 12) 3 else 2),
            ),
            producer,
        )
    }
}
