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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import com.astheral.bikedashboard.calc.partialsWaiting
import com.astheral.bikedashboard.calc.hasOldMixed
import com.astheral.bikedashboard.calc.inLastDays
import com.astheral.bikedashboard.calc.summarizeDashboard
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.DueLevel
import com.astheral.bikedashboard.data.IntervalStatus
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.data.withDefaults
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.service.DueRow
import com.astheral.bikedashboard.ui.service.dueList
import com.astheral.bikedashboard.ui.service.levelColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.astheral.bikedashboard.ui.DisplayUnit
import com.astheral.bikedashboard.ui.SgdUnit
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.astheral.bikedashboard.calc.CardLayout
import com.astheral.bikedashboard.calc.ChartLayout
import com.astheral.bikedashboard.calc.DashChart
import com.astheral.bikedashboard.calc.MetricChange
import com.astheral.bikedashboard.calc.compareLastFill
import com.astheral.bikedashboard.calc.comparePreviousDays
import com.astheral.bikedashboard.ui.components.CompactChange
import com.astheral.bikedashboard.calc.DashCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.draw.clip
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
    val kmPerDay: List<DatedValue>,
    val savings: List<DatedValue>,
)

data class DashboardData(
    val summary: DashboardSummary,
    /** Every fill-up worked out, oldest first, for the detail pages. */
    val stats: List<FillUpStats> = emptyList(),
    val lastFillUp: FillUp?,
    /** Chart data for each period: the last 10 fill-ups, the last 7 and 30 days, and everything. */
    val charts: Map<Period, ChartData>,
    val hasOldMixed: Boolean,
    val tiles: TileSeries,
    /** Days since the last fill-up, and the usual number of days between full tanks. */
    val daysSinceLast: Long,
    val usualDays: Long?,
    /** Days since the last full tank, for the progress bar (same as [daysSinceLast] without partial fills). */
    val daysSinceLastFull: Long = daysSinceLast,
    /** Partial fills since the last full tank, waiting for the next one to be counted. */
    val partialsWaiting: Int = 0,
    val today: LocalDate,
    /** The last exchange rate entered, for showing prices in ringgit. */
    val rmPerSgd: Double?,
    /** Service intervals that are due soon or overdue, most urgent first. */
    val due: List<IntervalStatus> = emptyList(),
    /** Each card's number next to the period before (none for Lifetime). */
    val comparisons: Map<Period, Map<DashCard, MetricChange>> = emptyMap(),
)

private fun recent(stats: List<FillUpStats>, value: (FillUpStats) -> Double?) =
    stats.mapNotNull { s -> value(s)?.let { DatedValue(s.fillUp.date, it) } }.takeLast(TILE_BARS)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    val units: StateFlow<Units> = settings.units

    fun updateUnits(change: (Units) -> Units) = settings.updateUnits(change)

    /** Which number cards show, and in what order. */
    val cards: StateFlow<CardLayout> = settings.dashboardCards
    val cardsTipSeen: StateFlow<Boolean> = settings.dashboardCardsTipSeen

    fun setCards(layout: CardLayout) = settings.setDashboardCards(layout)
    fun markCardsTipSeen() = settings.markDashboardCardsTipSeen()

    /** Which charts show, and in what order. */
    val charts: StateFlow<ChartLayout> = settings.dashboardCharts
    val chartsTipSeen: StateFlow<Boolean> = settings.dashboardChartsTipSeen

    fun setCharts(layout: ChartLayout) = settings.setDashboardCharts(layout)
    fun markChartsTipSeen() = settings.markDashboardChartsTipSeen()

    /** Whether the cards show arrows comparing with the period before. */
    val comparisons: StateFlow<Boolean> = settings.periodComparisons

    private val db = AppDatabase.get(application)

    /** Null while loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<DashboardData?> = Profiles.get(application).let { profiles ->
        profiles.activeFillUps.flatMapLatest { (profile, all) ->
            if (profile == null) {
                flowOf(Triple(profile, all, emptyList<IntervalStatus>()))
            } else {
                combine(db.serviceDao().observe(profile.id), db.intervalDao().observe(profile.id), db.categoryDao().observe()) { records, saved, cats ->
                    val due = dueList(withDefaults(profile.id, saved, cats), records, currentOdometer(all, records), cats)
                        .filter { it.level == DueLevel.SOON || it.level == DueLevel.OVERDUE }
                    Triple(profile, all, due)
                }
            }
        }.combine(profiles.tripKm) { (_, all, due), tripKm ->
            val today = LocalDate.now()
            val stats = computeStats(all, tripKm)
            val gaps = stats.mapNotNull { it.days }.filter { it > 0 }.sorted()
            DashboardData(
                summary = summarizeDashboard(stats, today),
                stats = stats,
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
                    kmPerDay = recent(stats) { it.kmPerDay },
                    savings = recent(stats) { it.savingsSgd },
                ),
                daysSinceLast = stats.lastOrNull()?.let { ChronoUnit.DAYS.between(it.fillUp.date, today) } ?: 0,
                // Gaps are between full tanks (only they have days), so partial top-ups don't shorten them.
                usualDays = gaps.getOrNull(gaps.size / 2), // median, so the odd long break doesn't skew it
                daysSinceLastFull = (stats.lastOrNull { it.fillUp.fullTank } ?: stats.lastOrNull())
                    ?.let { ChronoUnit.DAYS.between(it.fillUp.date, today) } ?: 0,
                partialsWaiting = partialsWaiting(stats),
                today = today,
                rmPerSgd = stats.lastOrNull { it.fillUp.rmPerSgd != null }?.fillUp?.rmPerSgd,
                due = due,
                comparisons = mapOf(
                    Period.LAST to compareLastFill(stats),
                    Period.WEEK to comparePreviousDays(stats, today, 7),
                    Period.MONTH to comparePreviousDays(stats, today, 30),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The dashboard's tabs. Week and month are rolling: the last 7 and 30 days up to today. */
enum class Period(val label: String, val chartLabel: String, val days: Long? = null) {
    LAST("Last Fill", "Last 10"),
    WEEK("Week", "7 Days", 7),
    MONTH("Month", "30 Days", 30),
    LIFETIME("Lifetime", "All Time"),
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
    onOpenServicing: () -> Unit,
    onOpenMetric: (Metric, Period) -> Unit,
    // One choice of period for the whole page: the tabs, a swipe on the tiles, and every chart follow it.
    periodPager: PagerState = rememberPagerState(pageCount = { Period.entries.size }),
    viewModel: DashboardViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    val units by viewModel.units.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val tipSeen by viewModel.cardsTipSeen.collectAsState()
    val charts by viewModel.charts.collectAsState()
    val chartsTipSeen by viewModel.chartsTipSeen.collectAsState()
    val comparisons by viewModel.comparisons.collectAsState()
    // The tab the arrange sheet opens on, or null while it's closed.
    var arranging by rememberSaveable { mutableStateOf<ArrangeTab?>(null) }
    val d = data ?: return
    val pager = periodPager
    val scope = rememberCoroutineScope()
    arranging?.let { tab ->
        ArrangeSheet(cards, viewModel::setCards, charts, viewModel::setCharts, tab, onDismiss = { arranging = null })
    }
    val tiles = TileSettings(
        cards, charts, units,
        showTip = !tipSeen,
        showChartsTip = !chartsTipSeen,
        comparisons = comparisons,
        onUnits = viewModel::updateUnits,
        onArrange = { tab ->
            arranging = tab
            if (tab == ArrangeTab.CARDS) viewModel.markCardsTipSeen() else viewModel.markChartsTipSeen()
        },
    )
    Column(
        Modifier
            .fillMaxSize()
            // A sideways swipe anywhere on the page (not just below the tabs) changes the period,
            // and past Lifetime it carries on to History.
            .then(
                if (d.lastFillUp == null) Modifier
                else Modifier.scrollable(pager, Orientation.Horizontal, reverseDirection = LocalLayoutDirection.current == LayoutDirection.Ltr, flingBehavior = PagerDefaults.flingBehavior(pager)),
            )
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
            if (d.due.isNotEmpty()) {
                GroupCard(edges, contentPadding = 16.dp, edge = d.due.first().levelColor().copy(alpha = 0.6f), onClick = onOpenServicing) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { d.due.forEach { DueRow(it) } }
                }
            }
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
            ) { page -> PeriodPage(Period.entries[page], d, tiles) { metric -> onOpenMetric(metric, Period.entries[page]) } }
        }
    }
}

/** Everything for one period: the tiles, then the charts. */
@Composable
private fun PeriodPage(period: Period, d: DashboardData, tiles: TileSettings, onOpen: (Metric) -> Unit) {
    val units = tiles.units
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupCard(contentPadding = 12.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)) {
            PeriodTiles(period, d.summary.of(period), d, tiles, onOpen)
        }
        val visible = tiles.charts.visible
        if (visible.isEmpty()) return@Column
        val charts = d.charts.getValue(period)
        if (period.days != null && d.summary.of(period).fillUpCount == 0) {
            Text("No fill-ups in the last ${period.days} days, so there's nothing to chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val haptic = LocalHapticFeedback.current
            val arrange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tiles.onArrange(ArrangeTab.CHARTS)
            }
            val label = period.chartLabel
            visible.forEach { chart ->
                key(chart) {
                    val open = { onOpen(chart.metric) }
                    when (chart) {
                        DashChart.ECONOMY -> EconomyChart(charts, label, units.economy, open, arrange)
                        DashChart.PRICE -> PriceChart(charts, d.hasOldMixed, label, open, arrange)
                        DashChart.SPEND -> SpendChart(charts, label, byMonth = period == Period.LIFETIME, open, arrange)
                        DashChart.KM_PER_DAY -> TrendChart(chart.title, charts.kmPerDay, units.distanceTime, label, onClick = open, onLongClick = arrange)
                        DashChart.COST_PER_DAY -> TrendChart(chart.title, charts.costPerDay, units.timeCost, label, onClick = open, onLongClick = arrange)
                        DashChart.COST_PER_KM -> TrendChart(chart.title, charts.costPerKm, units.distanceCost, label, onClick = open, onLongClick = arrange)
                        DashChart.SAVINGS -> SavingsChart(charts, label, byMonth = period == Period.LIFETIME, open, arrange)
                    }
                }
            }
        }
        if (tiles.showChartsTip) {
            Text(
                "Press and hold a chart to arrange charts or show more.",
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The title, a line about the last fill-up in the accent colour, a
 * couple of chips and the wavy bar.
 */
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
            AccentChip(d.summary.lifetime.fillUpCount.toDouble(), { count(it.toInt(), "fill") })
            Spacer(Modifier.size(8.dp))
            d.summary.lifetime.kmPerLitre?.let { AccentChip(units.economy.convert(it), units.economy::format) }
        }
        d.usualDays?.let { usual ->
            ProgressLine(d.daysSinceLastFull.toFloat() / usual)
            Text(
                fillGapText(d.daysSinceLastFull, usual, sinceFullTank = d.partialsWaiting > 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d.partialsWaiting > 0) {
            Text(
                if (d.partialsWaiting == 1) "1 partial fill-up waiting for a full tank" else "${d.partialsWaiting} partial fill-ups waiting for a full tank",
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
            Text("No Fill-Ups Yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add your first fill-up, or import your old spreadsheet to see your history straight away.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddFillUp) { Text("Add Fill-Up") }
            OutlinedButton(onClick = onImport) { Text("Import Spreadsheet") }
        }
    }
}

/** What the tiles and charts need besides the numbers: which to show, their units, and the arrange sheet. */
private class TileSettings(
    val cards: CardLayout,
    val charts: ChartLayout,
    val units: Units,
    val showTip: Boolean,
    val showChartsTip: Boolean,
    /** Period comparisons switched on in Settings. */
    val comparisons: Boolean,
    val onUnits: ((Units) -> Units) -> Unit,
    val onArrange: (ArrangeTab) -> Unit,
)

@Composable
private fun PeriodTiles(
    period: Period,
    summary: PeriodSummary,
    d: DashboardData,
    settings: TileSettings,
    onOpen: (Metric) -> Unit,
) {
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
        val error = MaterialTheme.colorScheme.error
        val haptic = LocalHapticFeedback.current
        val units = settings.units
        val onUnits = settings.onUnits
        val t = d.tiles
        val price = units.price.at(d.rmPerSgd)
        val onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            settings.onArrange(ArrangeTab.CARDS)
        }
        val changes = if (settings.comparisons) d.comparisons[period] else null
        val missing = when (period) {
            Period.LAST -> "No earlier fill"
            Period.WEEK -> "No previous week"
            else -> "No previous 30 days"
        }

        @Composable
        fun Tile(card: DashCard, modifier: Modifier) {
            val label = card.title
            val open = { onOpen(card.metric) }
            when (card) {
                DashCard.ECONOMY -> StatTile(
                    changes?.get(card), missing, label, summary.kmPerLitre, units.economy, t.kmPerLitre, highlight(t.kmPerLitre), accent, modifier, open,
                    onSwitchUnit = { onUnits { it.copy(economy = it.economy.next()) } },
                    onLongClick = onLongClick,
                )
                DashCard.KM_PER_DAY -> StatTile(
                    changes?.get(card), missing, label, summary.kmPerDay, units.distanceTime, t.kmPerDay, highlight(t.kmPerDay), accent, modifier, open,
                    onSwitchUnit = { onUnits { it.copy(distanceTime = it.distanceTime.next()) } },
                    onLongClick = onLongClick,
                )
                DashCard.COST_PER_DAY -> StatTile(
                    changes?.get(card), missing, label, summary.costPerDay, units.timeCost, t.costPerDay, highlight(t.costPerDay), accent, modifier, open,
                    onSwitchUnit = { onUnits { it.copy(timeCost = it.timeCost.next()) } },
                    onLongClick = onLongClick,
                )
                DashCard.COST_PER_KM -> StatTile(
                    changes?.get(card), missing, label, summary.costPerKm, units.distanceCost, t.costPerKm, highlight(t.costPerKm), accent, modifier, open,
                    onSwitchUnit = { onUnits { it.copy(distanceCost = it.distanceCost.next()) } },
                    onLongClick = onLongClick,
                )
                DashCard.PRICE -> StatTile(
                    changes?.get(card), missing, label, summary.pricePerLitre, price, t.pricePerLitre, highlight(t.pricePerLitre), accent, modifier, open,
                    // Ringgit needs a rate, so without one there's nothing to switch to.
                    onSwitchUnit = if (d.rmPerSgd == null) null else ({ onUnits { it.copy(price = it.price.next()) } }),
                    onLongClick = onLongClick,
                )
                DashCard.SAVINGS -> {
                    val saved = summary.savingsSgd
                    StatTile(
                        changes?.get(card), missing, label, saved, SgdUnit, t.savings, highlight(t.savings),
                        if (saved != null && saved < 0) error else CountryColors.my,
                        modifier, open,
                        onLongClick = onLongClick,
                    )
                }
            }
        }

        // Two to a row, in the chosen order; an odd one out at the end takes the whole row.
        settings.cards.visible.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card -> key(card) { Tile(card, Modifier.weight(1f)) } }
            }
        }
        if (changes != null) {
            Text(
                when (period) {
                    Period.LAST -> "Arrows compare with your previous fill-up."
                    Period.WEEK -> "Arrows compare with the 7 days before."
                    else -> "Arrows compare with the 30 days before."
                },
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (settings.showTip) {
            Text(
                "Press and hold a card to arrange or hide cards.",
                Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun caption(period: Period, s: PeriodSummary, last: FillUp): String = when (period) {
    Period.LAST -> "${last.date.short()} · ${last.country.label()} · ${sgd(s.costSgd)}" +
        (if (s.distanceKm > 0) " · ${km(s.distanceKm)}" else "")
    else -> "${count(s.fillUpCount, "fill-up")} · ${km(s.distanceKm)} · ${sgd(s.costSgd)} spent"
}

/**
 * A small outlined tile: label with a dot, the number in monospace (counting up when it
 * appears), and bars for recent fill-ups with the ones this number covers lit up. Tapping the tile
 * opens its detail page; tapping the number (marked with ⇄) switches to the next unit, e.g. km/L
 * to L/100 km. Pressing and holding anywhere on it calls [onLongClick] (arranging the cards).
 */
@Composable
private fun StatTile(
    /** This number next to the period before, or null to show no comparison line. */
    change: MetricChange?,
    /** What the comparison line says when there's nothing to compare with. */
    missing: String,
    label: String,
    value: Double?,
    unit: DisplayUnit,
    series: List<DatedValue>,
    highlight: Int,
    color: Color,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    onSwitchUnit: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DotLabel(label, color)
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onSwitchUnit != null && value != null) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onSwitchUnit) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value == null) {
                    Text("—", style = MaterialTheme.typography.titleMedium, fontFamily = NumberFont)
                } else {
                    CountUpText(unit.convert(value), unit::format, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                    if (onSwitchUnit != null) {
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Change unit",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            change?.let {
                // Without a number there's nothing to compare, but the line keeps tiles in a row level.
                if (value == null) Text(" ", style = MaterialTheme.typography.labelSmall)
                else CompactChange(it, unit, missing)
            }
            Box(Modifier.padding(top = 2.dp)) { MiniBars(series.map { unit.convert(it.value) }, highlight, color) }
        }
    }
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    val press = if (onClick == null && onLongClick == null) Modifier else {
        Modifier.clip(TileShape).combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
    }
    Surface(modifier.then(press), shape = TileShape, color = fill, border = outline(), content = body)
}

/**
 * Under the progress line: how far into the usual gap between fill-ups (or full tanks, when
 * partial fills are waiting) you are, e.g. "Day 3 of about 5 between fill-ups". Past the usual
 * gap it says how long it's been instead, since "Day 43 of about 5" reads oddly.
 */
internal fun fillGapText(days: Long, usual: Long, sinceFullTank: Boolean): String {
    val what = if (sinceFullTank) "full tanks" else "fill-ups"
    val last = if (sinceFullTank) "full tank" else "fill-up"
    val usually = if (usual == 1L) "about a day" else "about $usual days"
    return when {
        days <= 0L -> "Filled today · usually $usually between $what"
        days > usual -> "${if (days == 1L) "1 day" else "$days days"} since your last $last · usually $usually"
        else -> "Day $days of about $usual between $what"
    }
}
