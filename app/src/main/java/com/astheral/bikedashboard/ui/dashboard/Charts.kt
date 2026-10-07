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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
// Economy and spend charts follow the colour theme.
private val EconomyColor @Composable get() = MaterialTheme.colorScheme.tertiary
private val SpendColor @Composable get() = MaterialTheme.colorScheme.primary

/** Dates on the x axis are stored as day numbers; show them as "Mar 25". */
private val DateAxis = CartesianValueFormatter { _, x, _ -> LocalDate.ofEpochDay(x.toLong()).format(MONTH_YEAR) }

private fun yFormatter(digits: Int, prefix: String = "") =
    CartesianValueFormatter { _, y, _ -> prefix + String.format(Locale.ENGLISH, "%.${digits}f", y) }

@Composable
private fun ChartCard(title: String, legend: List<Pair<String, Color>> = emptyList(), note: String? = null, chart: @Composable () -> Unit) {
    GroupCard(contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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

/** The whole history fits on screen: no scrolling or pinch-zooming inside the chart. */
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

@Composable
private fun line(color: Color) = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(color)))

@Composable
fun PriceChart(data: ChartData, hasOldMixed: Boolean) {
    if (data.sgPrice.isEmpty() && data.myPrice.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    // Each country only gets a line if it has points; Vico needs every series to be non-empty.
    val sgColor = CountryColors.sg
    val myColor = CountryColors.my
    val series = listOf(data.sgPrice to sgColor, data.myPrice to myColor).filter { it.first.isNotEmpty() }
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
        legend = listOf("Singapore" to sgColor, "Malaysia" to myColor).filter { (name, _) ->
            if (name == "Singapore") data.sgPrice.isNotEmpty() else data.myPrice.isNotEmpty()
        },
        note = if (hasOldMixed) "In SGD. Old mixed fill-ups aren't shown, because their price is a blend of both countries." else "In SGD.",
    ) {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(series.map { line(it.second) })),
                startAxis = startAxis(yFormatter(2, "S$")),
                bottomAxis = bottomAxis(DateAxis, 90),
            ),
            producer,
        )
    }
}

@Composable
fun EconomyChart(data: ChartData) {
    if (data.kmPerLitre.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            lineSeries { series(data.kmPerLitre.map { it.date.toEpochDay() }, data.kmPerLitre.map { it.value }) }
        }
    }
    ChartCard("Fuel economy (km/L)") {
        FittedChart(
            rememberCartesianChart(
                rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line(EconomyColor))),
                startAxis = startAxis(yFormatter(0)),
                bottomAxis = bottomAxis(DateAxis, 90),
            ),
            producer,
        )
    }
}

@Composable
fun MonthlySpendChart(data: ChartData) {
    if (data.monthlySpend.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    val months = data.monthlySpend.map { it.month }
    LaunchedEffect(data) {
        producer.runTransaction {
            columnSeries { series(data.monthlySpend.indices.toList(), data.monthlySpend.map { it.costSgd }) }
        }
    }
    val monthAxis = remember(months) {
        CartesianValueFormatter { _, x, _ -> months.getOrNull(x.toInt())?.format(MONTH_YEAR) ?: "" }
    }
    ChartCard("Monthly spend (SGD)") {
        FittedChart(
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = Fill(SpendColor), thickness = 8.dp),
                    ),
                ),
                startAxis = startAxis(yFormatter(0, "S$")),
                bottomAxis = bottomAxis(monthAxis, 3),
            ),
            producer,
        )
    }
}
