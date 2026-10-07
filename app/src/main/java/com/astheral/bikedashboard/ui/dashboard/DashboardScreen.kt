package com.astheral.bikedashboard.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.ChartData
import com.astheral.bikedashboard.calc.DashboardSummary
import com.astheral.bikedashboard.calc.PeriodSummary
import com.astheral.bikedashboard.calc.chartData
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.hasOldMixed
import com.astheral.bikedashboard.calc.summarizeDashboard
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.NoInsets
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerKm
import com.astheral.bikedashboard.ui.sgdPerLitre
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DashboardData(
    val summary: DashboardSummary,
    val lastFillUp: FillUp?,
    val charts: ChartData,
    val hasOldMixed: Boolean,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    /** Null while loading. */
    val data: StateFlow<DashboardData?> =
        combine(
            AppDatabase.get(application).fillUpDao().observeAll(),
            Settings.get(application).tripKm,
        ) { all, tripKm ->
            val stats = computeStats(all, tripKm)
            DashboardData(
                summary = summarizeDashboard(stats, LocalDate.now()),
                lastFillUp = stats.lastOrNull()?.fillUp,
                charts = chartData(stats),
                hasOldMixed = hasOldMixed(stats),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Colours for the two countries, used by both the cards and the charts. */
val SgColor = Color(0xFFD32F2F)
val MyColor = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddFillUp: () -> Unit,
    onImport: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val data by viewModel.data.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Bike Dashboard") }) },
        floatingActionButton = {
            if (data?.lastFillUp != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddFillUp,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add fill-up") },
                )
            }
        },
        contentWindowInsets = NoInsets,
    ) { padding ->
        val d = data ?: return@Scaffold
        if (d.lastFillUp == null) {
            Empty(onAddFillUp, onImport, Modifier.padding(padding))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp),
            ) {
                Summary(d)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PriceChart(d.charts, d.hasOldMixed)
                    EconomyChart(d.charts)
                    MonthlySpendChart(d.charts)
                }
            }
        }
    }
}

@Composable
private fun Empty(onAddFillUp: () -> Unit, onImport: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("No fill-ups yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add your first fill-up, or import your old spreadsheet to see your history straight away.",
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAddFillUp) { Text("Add fill-up") }
            OutlinedButton(onClick = onImport) { Text("Import spreadsheet") }
        }
    }
}

private enum class Period(val label: String) { LAST("Last fill-up"), DAYS_30("Last 30 days"), LIFETIME("Lifetime") }

@Composable
private fun Summary(d: DashboardData) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[selected]
    PrimaryTabRow(selectedTabIndex = selected) {
        Period.entries.forEachIndexed { i, p ->
            Tab(selected = selected == i, onClick = { selected = i }, text = { Text(p.label) })
        }
    }
    val summary = when (period) {
        Period.LAST -> d.summary.lastFillUp
        Period.DAYS_30 -> d.summary.last30Days
        Period.LIFETIME -> d.summary.lifetime
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(caption(period, summary, d.lastFillUp!!), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (summary.fillUpCount == 0) {
            Text("No fill-ups in the last 30 days.")
            return
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Fuel economy", summary.kmPerLitre?.let(::kmPerLitre), Modifier.weight(1f))
            Metric("Petrol price", summary.pricePerLitre?.let(::sgdPerLitre), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Cost per km", summary.costPerKm?.let(::sgdPerKm), Modifier.weight(1f))
            Metric("Cost per day", summary.costPerDay?.let { "${sgd(it)}/day" }, Modifier.weight(1f))
        }
        Metric(
            "Saved by pumping in Malaysia",
            summary.savingsSgd?.let(::sgd),
            Modifier.fillMaxWidth(),
            valueColor = summary.savingsSgd?.let { if (it < 0) MaterialTheme.colorScheme.error else MyColor },
        )
    }
}

private fun caption(period: Period, s: PeriodSummary, last: FillUp): String = when (period) {
    Period.LAST -> "${last.date.short()} · ${last.country.label()} · ${sgd(s.costSgd)}" +
        (if (s.distanceKm > 0) " · ${km(s.distanceKm)}" else "")
    else -> "${s.fillUpCount} fill-ups · ${km(s.distanceKm)} · ${sgd(s.costSgd)} spent"
}

@Composable
private fun Metric(label: String, value: String?, modifier: Modifier, valueColor: Color? = null) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
