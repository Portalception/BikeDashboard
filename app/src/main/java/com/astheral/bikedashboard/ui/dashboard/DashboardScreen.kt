package com.astheral.bikedashboard.ui.dashboard

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.ChartData
import com.astheral.bikedashboard.calc.DashboardSummary
import com.astheral.bikedashboard.calc.DatedValue
import com.astheral.bikedashboard.calc.FillUpStats
import com.astheral.bikedashboard.calc.PeriodSummary
import com.astheral.bikedashboard.calc.chartData
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.hasOldMixed
import com.astheral.bikedashboard.calc.summarizeDashboard
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.DotLabel
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.MiniBars
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillTabs
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.WavyProgress
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerKm
import com.astheral.bikedashboard.ui.sgdPerLitre
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.CountryColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How many recent fill-ups the little bar charts in the tiles show. */
private const val TILE_BARS = 16

/** Per-fill-up values for the tile bar charts, oldest first. */
data class TileSeries(
    val kmPerLitre: List<DatedValue>,
    val pricePerLitre: List<DatedValue>,
    val costPerKm: List<DatedValue>,
    val costPerDay: List<DatedValue>,
    val savings: List<DatedValue>,
)

data class DashboardData(
    val summary: DashboardSummary,
    val lastFillUp: FillUp?,
    val charts: ChartData,
    val hasOldMixed: Boolean,
    val tiles: TileSeries,
    /** Days since the last fill-up, and the usual number of days between fill-ups. */
    val daysSinceLast: Long,
    val usualDays: Long?,
    val today: LocalDate,
)

private fun recent(stats: List<FillUpStats>, value: (FillUpStats) -> Double?) =
    stats.mapNotNull { s -> value(s)?.let { DatedValue(s.fillUp.date, it) } }.takeLast(TILE_BARS)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    /** Null while loading. */
    val data: StateFlow<DashboardData?> =
        combine(
            AppDatabase.get(application).fillUpDao().observeAll(),
            Settings.get(application).tripKm,
        ) { all, tripKm ->
            val today = LocalDate.now()
            val stats = computeStats(all, tripKm)
            val gaps = stats.mapNotNull { it.days }.filter { it > 0 }.sorted()
            DashboardData(
                summary = summarizeDashboard(stats, today),
                lastFillUp = stats.lastOrNull()?.fillUp,
                charts = chartData(stats),
                hasOldMixed = hasOldMixed(stats),
                tiles = TileSeries(
                    kmPerLitre = recent(stats) { it.kmPerLitre },
                    pricePerLitre = recent(stats) { it.pricePerLitre },
                    costPerKm = recent(stats) { it.costPerKm },
                    costPerDay = recent(stats) { it.costPerDay },
                    savings = recent(stats) { it.savingsSgd },
                ),
                daysSinceLast = stats.lastOrNull()?.let { ChronoUnit.DAYS.between(it.fillUp.date, today) } ?: 0,
                usualDays = gaps.getOrNull(gaps.size / 2), // median, so the odd long break doesn't skew it
                today = today,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private enum class Period(val label: String) { LAST("Last fill-up"), DAYS_30("30 days"), LIFETIME("Lifetime") }

@Composable
fun DashboardScreen(
    onAddFillUp: () -> Unit,
    onImport: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    val d = data ?: return
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (d.lastFillUp == null) {
            Header(null)
            Empty(onAddFillUp, onImport)
        } else {
            Header(d)
            Summary(d)
            PriceChart(d.charts, d.hasOldMixed)
            EconomyChart(d.charts)
            MonthlySpendChart(d.charts)
        }
    }
}

/** Title, a line about the last fill-up in the accent colour, a couple of chips and the wavy bar. */
@Composable
private fun Header(d: DashboardData?) {
    Column(Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Bike Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val last = d?.lastFillUp ?: return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ago = when (d.daysSinceLast) {
                0L -> "today"
                1L -> "yesterday"
                else -> "${d.daysSinceLast} days ago"
            }
            Text(
                "Filled $ago · ${last.country.label()}",
                Modifier.weight(1f),
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
            )
            AccentChip("${d.summary.lifetime.fillUpCount} fills")
            Spacer(Modifier.size(8.dp))
            d.summary.lifetime.kmPerLitre?.let { AccentChip(String.format(Locale.ENGLISH, "%.1f km/L", it)) }
        }
        d.usualDays?.let { usual ->
            WavyProgress(d.daysSinceLast.toFloat() / usual)
            Text(
                "Day ${d.daysSinceLast} of about $usual between fill-ups",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Empty(onAddFillUp: () -> Unit, onImport: () -> Unit) {
    GroupCard(contentPadding = 28.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.TwoWheeler, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
            Text("No fill-ups yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add your first fill-up, or import your old spreadsheet to see your history straight away.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddFillUp) { Text("Add fill-up") }
            OutlinedButton(onClick = onImport) { Text("Import spreadsheet") }
        }
    }
}

/** One card holding the period tabs and the tiles; swipe sideways or tap a tab to switch. */
@Composable
private fun Summary(d: DashboardData) {
    val pager = rememberPagerState(pageCount = { Period.entries.size })
    val scope = rememberCoroutineScope()
    GroupCard(contentPadding = 12.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)) {
        PillTabs(
            listOf(
                Icons.Filled.LocalGasStation to Period.LAST.label,
                Icons.Filled.CalendarMonth to Period.DAYS_30.label,
                Icons.Filled.AllInclusive to Period.LIFETIME.label,
            ),
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalPager(state = pager, verticalAlignment = Alignment.Top, pageSpacing = 12.dp) { page ->
            val period = Period.entries[page]
            val summary = when (period) {
                Period.LAST -> d.summary.lastFillUp
                Period.DAYS_30 -> d.summary.last30Days
                Period.LIFETIME -> d.summary.lifetime
            }
            PeriodTiles(period, summary, d)
        }
    }
}

@Composable
private fun PeriodTiles(period: Period, summary: PeriodSummary, d: DashboardData) {
    val windowStart = d.today.minusDays(29)
    fun highlight(series: List<DatedValue>) = when (period) {
        Period.LAST -> if (series.lastOrNull()?.date == d.lastFillUp?.date) 1 else 0
        Period.DAYS_30 -> series.count { !it.date.isBefore(windowStart) }
        Period.LIFETIME -> series.size
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            caption(period, summary, d.lastFillUp!!),
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.fillUpCount == 0) {
            Text("No fill-ups in the last 30 days.", Modifier.padding(6.dp))
            return
        }
        val accent = MaterialTheme.colorScheme.tertiary
        val t = d.tiles
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Fuel economy", summary.kmPerLitre, ::kmPerLitre, t.kmPerLitre, highlight(t.kmPerLitre), accent, Modifier.weight(1f))
            StatTile("Petrol price", summary.pricePerLitre, ::sgdPerLitre, t.pricePerLitre, highlight(t.pricePerLitre), accent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Cost per km", summary.costPerKm, ::sgdPerKm, t.costPerKm, highlight(t.costPerKm), accent, Modifier.weight(1f))
            StatTile("Cost per day", summary.costPerDay, { "${sgd(it)}/d" }, t.costPerDay, highlight(t.costPerDay), accent, Modifier.weight(1f))
        }
        val saved = summary.savingsSgd
        StatTile(
            "Saved by pumping in Malaysia",
            saved,
            ::sgd,
            t.savings,
            highlight(t.savings),
            if (saved != null && saved < 0) MaterialTheme.colorScheme.error else CountryColors.my,
            Modifier.fillMaxWidth(),
        )
    }
}

private fun caption(period: Period, s: PeriodSummary, last: FillUp): String = when (period) {
    Period.LAST -> "${last.date.short()} · ${last.country.label()} · ${sgd(s.costSgd)}" +
        (if (s.distanceKm > 0) " · ${km(s.distanceKm)}" else "")
    else -> "${s.fillUpCount} fill-ups · ${km(s.distanceKm)} · ${sgd(s.costSgd)} spent"
}

/**
 * A small outlined tile: label with a dot, the number in monospace (counting up when it
 * appears), and bars for recent fill-ups with the ones this number covers lit up.
 */
@Composable
private fun StatTile(
    label: String,
    value: Double?,
    format: (Double) -> String,
    series: List<DatedValue>,
    highlight: Int,
    color: Color,
    modifier: Modifier,
) {
    val shown = remember { Animatable(0f) }
    // Once the count-up finishes, show the exact value (the animation runs on rounded floats).
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        settled = false
        if (value != null) shown.animateTo(value.toFloat(), tween(durationMillis = 700, easing = FastOutSlowInEasing))
        settled = true
    }
    Surface(
        modifier,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = outline(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DotLabel(label, color)
            Text(
                if (value == null) "—" else format(if (settled) value else shown.value.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NumberFont,
                maxLines = 1,
            )
            Box(Modifier.padding(top = 2.dp)) { MiniBars(series.map { it.value }, highlight, color) }
        }
    }
}
