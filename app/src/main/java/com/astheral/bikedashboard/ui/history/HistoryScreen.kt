package com.astheral.bikedashboard.ui.history

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.FillUpStats
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.NoInsets
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.litres
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerLitre
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    /** Newest first. Null while loading. */
    val fillUps: StateFlow<List<FillUpStats>?> =
        combine(
            AppDatabase.get(application).fillUpDao().observeAll(),
            Settings.get(application).tripKm,
        ) { all, tripKm -> computeStats(all, tripKm).reversed() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onAddFillUp: () -> Unit,
    onOpenFillUp: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val fillUps by viewModel.fillUps.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFillUp) { Icon(Icons.Filled.Add, contentDescription = "Add fill-up") }
        },
        contentWindowInsets = NoInsets,
    ) { padding ->
        val list = fillUps
        when {
            list == null -> {}
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "No fill-ups yet.\nTap + to add one, or import your spreadsheet in Settings.",
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp), // room for the + button
            ) {
                items(list, key = { it.fillUp.id }) { stats ->
                    FillUpRow(stats, onClick = { onOpenFillUp(stats.fillUp.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FillUpRow(stats: FillUpStats, onClick: () -> Unit) {
    val fill = stats.fillUp
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fill.date.long(), style = MaterialTheme.typography.titleSmall)
            Text(fill.country.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${litres(fill.totalLitres)} · ${sgd(fill.totalCostSgd)}")
            stats.pricePerLitre?.let { Text(sgdPerLitre(it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                km(fill.odometerKm) + (stats.distanceKm?.let { " · +${km(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats.kmPerLitre?.let {
                Text(kmPerLitre(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
