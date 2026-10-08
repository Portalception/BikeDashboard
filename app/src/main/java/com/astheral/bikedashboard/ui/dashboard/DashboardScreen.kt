package com.astheral.bikedashboard.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.astheral.bikedashboard.calc.inLastDays
import com.astheral.bikedashboard.calc.summarizeDashboard
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.DisplayUnit
import com.astheral.bikedashboard.ui.Units
import com.astheral.bikedashboard.ui.next
import com.astheral.bikedashboard.ui.profile.ProfileTitle
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.DotLabel
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.MiniBars
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillTabs
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.ProgressLine
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.CountryColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    /** Chart data for each period: the last 10 fill-ups, the last 7 and 30 days, and everything. */
    val charts: Map<Period, ChartData>,
    val hasOldMixed: Boolean,
    val tiles: TileSeries,
    /** Days since the last fill-up, and the usual number of days between fill-ups. */
    val daysSinceLast: Long,
    val usualDays: Long?,
    val today: LocalDate,
    /** The last exchange rate entered, for showing prices in ringgit. */
    val rmPerSgd: Double?,
)

private fun recent(stats: List<FillUpStats>, value: (FillUpStats) -> Double?) =
    stats.mapNotNull { s -> value(s)?.let { DatedValue(s.fillUp.date, it) } }.takeLast(TILE_BARS)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    val units: StateFlow<Units> = settings.units

    fun updateUnits(change: (Units) -> Units) = settings.updateUnits(change)

    /** Null while loading. */
    val data: StateFlow<DashboardData?> =
        Profiles.get(application).activeFillUps.map { (profile, all) ->
            val today = LocalDate.now()
            val stats = computeStats(all, profile?.tripKm ?: DEFAULT_TRIP_KM)
            val gaps = stats.mapNotNull { it.days }.filter { it > 0 }.sorted()
            DashboardData(
                summary = summarizeDashboard(stats, today),
                lastFillUp = stats.lastOrNull()?.fillUp,
                charts = mapOf(
                    // One fill-up makes an empty chart, so "last fill-up" shows it with the ones before it.
                    Period.LAST to chartData(stats.takeLast(RECENT_FILL_UPS)),
                    Period.WEEK to chartData(inLastDays(stats, today, 7)),
                    Period.MONTH to chartData(inLastDays(stats, today, 30)),
                    Period.LIFETIME to chartData(stats),
                ),
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
                rmPerSgd = stats.lastOrNull { it.fillUp.rmPerSgd != null }?.fillUp?.rmPerSgd,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The dashboard's tabs. Week and month are rolling: the last 7 and 30 days up to today. */
enum class Period(val label: String, val chartLabel: String, val days: Long? = null) {
    LAST("Last fill", "Last 10"),
    WEEK("Week", "7 days", 7),
    MONTH("Month", "30 days", 30),
    LIFETIME("Lifetime", "All time"),
}

fun DashboardSummary.of(period: Period): PeriodSummary = when (period) {
    Period.LAST -> lastFillUp
    Period.WEEK -> last7Days
    Period.MONTH -> last30Days
    Period.LIFETIME -> lifetime
}

/** How many fill-ups the charts show on the "Last fill-up" tab. */
private const val RECENT_FILL_UPS = 10

@Composable
fun DashboardScreen(
    profile: Profile?,
    onProfileClick: () -> Unit,
    onAddFillUp: () -> Unit,
    onImport: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    val units by viewModel.units.collectAsState()
    val d = data ?: return
    // One choice of period for the whole page: the tabs, a swipe on the tiles, and every chart follow it.
    val pager = rememberPagerState(pageCount = { Period.entries.size })
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val edges = Modifier.padding(horizontal = 16.dp)
        if (d.lastFillUp == null) {
            Header(null, units, profile, onProfileClick, edges)
            Box(edges) { Empty(onAddFillUp, onImport) }
        } else {
            Header(d, units, profile, onProfileClick, edges)
            PillTabs(
                Period.entries.map { null to it.label },
                selected = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                modifier = edges,
            )
            // The whole page below the tabs slides sideways when the period changes, and can be swiped.
            HorizontalPager(
                state = pager,
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 16.dp,
            ) { page -> PeriodPage(Period.entries[page], d, units, viewModel::updateUnits) }
        }
    }
}

/** Everything for one period: the tiles, then the charts. */
@Composable
private fun PeriodPage(period: Period, d: DashboardData, units: Units, onUnits: ((Units) -> Units) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupCard(contentPadding = 12.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)) {
            PeriodTiles(period, d.summary.of(period), d, units, onUnits)
        }
        val charts = d.charts.getValue(period)
        if (period.days != null && d.summary.of(period).fillUpCount == 0) {
            Text("No fill-ups in the last ${period.days} days, so there's nothing to chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            PriceChart(charts, d.hasOldMixed, period.chartLabel)
            EconomyChart(charts, period.chartLabel, units.economy)
            SpendChart(charts, period.chartLabel, byMonth = period == Period.LIFETIME)
        }
    }
}

/** Title, a line about the last fill-up in the accent colour, a couple of chips and the wavy bar. */
@Composable
private fun Header(d: DashboardData?, units: Units, profile: Profile?, onProfileClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProfileTitle(profile, onProfileClick)
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
            AccentChip(d.summary.lifetime.fillUpCount.toDouble(), { "${it.toInt()} fills" })
            Spacer(Modifier.size(8.dp))
            d.summary.lifetime.kmPerLitre?.let { AccentChip(units.economy.convert(it), units.economy::format) }
        }
        d.usualDays?.let { usual ->
            ProgressLine(d.daysSinceLast.toFloat() / usual)
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

@Composable
private fun PeriodTiles(period: Period, summary: PeriodSummary, d: DashboardData, units: Units, onUnits: ((Units) -> Units) -> Unit) {
    fun highlight(series: List<DatedValue>) = when (period) {
        Period.LAST -> if (series.lastOrNull()?.date == d.lastFillUp?.date) 1 else 0
        Period.LIFETIME -> series.size
        else -> series.count { !it.date.isBefore(d.today.minusDays(period.days!! - 1)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            caption(period, summary, d.lastFillUp!!),
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.fillUpCount == 0) {
            Text("No fill-ups in the last ${period.days} days.", Modifier.padding(6.dp))
            return
        }
        val accent = MaterialTheme.colorScheme.tertiary
        val t = d.tiles
        val price = units.price.at(d.rmPerSgd)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Fuel economy", summary.kmPerLitre, units.economy, t.kmPerLitre, highlight(t.kmPerLitre), accent, Modifier.weight(1f),
                onClick = { onUnits { it.copy(economy = it.economy.next()) } },
            )
            StatTile(
                "Petrol price", summary.pricePerLitre, price, t.pricePerLitre, highlight(t.pricePerLitre), accent, Modifier.weight(1f),
                // Ringgit needs a rate, so without one there's nothing to switch to.
                onClick = if (d.rmPerSgd == null) null else ({ onUnits { it.copy(price = it.price.next()) } }),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Cost per km", summary.costPerKm, units.distanceCost, t.costPerKm, highlight(t.costPerKm), accent, Modifier.weight(1f),
                onClick = { onUnits { it.copy(distanceCost = it.distanceCost.next()) } },
            )
            StatTile(
                "Cost per day", summary.costPerDay, units.timeCost, t.costPerDay, highlight(t.costPerDay), accent, Modifier.weight(1f),
                onClick = { onUnits { it.copy(timeCost = it.timeCost.next()) } },
            )
        }
        val saved = summary.savingsSgd
        StatTile(
            "Saved by pumping in Malaysia",
            saved,
            SgdUnit,
            t.savings,
            highlight(t.savings),
            if (saved != null && saved < 0) MaterialTheme.colorScheme.error else CountryColors.my,
            Modifier.fillMaxWidth(),
        )
    }
}

/** Plain SGD amounts, which have no other unit. */
private object SgdUnit : DisplayUnit {
    override val label = "S$"
    override fun convert(base: Double) = base
    override fun format(value: Double) = sgd(value)
}

private fun caption(period: Period, s: PeriodSummary, last: FillUp): String = when (period) {
    Period.LAST -> "${last.date.short()} · ${last.country.label()} · ${sgd(s.costSgd)}" +
        (if (s.distanceKm > 0) " · ${km(s.distanceKm)}" else "")
    else -> "${s.fillUpCount} fill-ups · ${km(s.distanceKm)} · ${sgd(s.costSgd)} spent"
}

/**
 * A small outlined tile: label with a dot, the number in monospace (counting up when it
 * appears), and bars for recent fill-ups with the ones this number covers lit up. Tapping a tile
 * with [onClick] switches it to its next unit, e.g. km/L to L/100 km.
 */
@Composable
private fun StatTile(
    label: String,
    value: Double?,
    unit: DisplayUnit,
    series: List<DatedValue>,
    highlight: Int,
    color: Color,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DotLabel(label, color)
            if (value == null) {
                Text("—", style = MaterialTheme.typography.titleMedium, fontFamily = NumberFont)
            } else {
                CountUpText(unit.convert(value), unit::format, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.padding(top = 2.dp)) { MiniBars(series.map { unit.convert(it.value) }, highlight, color) }
        }
    }
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    if (onClick == null) {
        Surface(modifier, shape = TileShape, color = fill, border = outline(), content = body)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = TileShape, color = fill, border = outline(), content = body)
    }
}
