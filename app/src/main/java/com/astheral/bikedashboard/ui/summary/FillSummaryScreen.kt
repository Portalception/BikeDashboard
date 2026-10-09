package com.astheral.bikedashboard.ui.summary

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.FillSummary
import com.astheral.bikedashboard.calc.MetricChange
import com.astheral.bikedashboard.calc.Direction
import com.astheral.bikedashboard.calc.Verdict
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.calc.summarizeFill
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.DotLabel
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.MiniBars
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.edit.PillButton
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.litres
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerKm
import com.astheral.bikedashboard.ui.sgdPerLitre
import com.astheral.bikedashboard.ui.theme.CountryColors
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

data class FillSummaryState(val loading: Boolean = true, val summary: FillSummary? = null, val tripKm: Double = 0.0)

class FillSummaryViewModel(application: Application, private val id: Long) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FillSummaryState())
    val state: StateFlow<FillSummaryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val dao = AppDatabase.get(application).fillUpDao()
            val fill = dao.getById(id)
            if (fill == null) {
                _state.value = FillSummaryState(loading = false)
                return@launch
            }
            // Savings are worked out exactly as on the dashboard and in History.
            val tripKm = Profiles.get(application).tripKm.first()
            val stats = computeStats(dao.getAll(fill.profileId), tripKm)
            _state.value = FillSummaryState(loading = false, summary = summarizeFill(stats, id), tripKm = tripKm)
        }
    }
}

/**
 * Shown straight after adding a fill-up: what it worked out to, how that compares with the
 * previous fill-up, a small trend, what Malaysia saved, and a quick way back to navigation.
 */
@Composable
fun FillSummaryScreen(id: Long, onDone: () -> Unit) {
    val vm: FillSummaryViewModel = viewModel(key = "summary-$id") { FillSummaryViewModel(this[APPLICATION_KEY]!!, id) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val navApp = remember { findNavigationApp(context) }
    if (state.loading) return
    val s = state.summary

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text("Fill-Up Saved", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            s?.let { AccentChip(it.stats.fillUp.date.long()) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s == null) {
                GroupCard(contentPadding = 20.dp) { Text("This fill-up couldn't be found.") }
            } else {
                Hero(s)
                Tiles(s)
                if (s.hasMalaysiaPart) Savings(s, state.tripKm)
                Trend(s)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Done", Icons.Filled.Check, filled = navApp == null, modifier = Modifier.weight(1f), onClick = onDone)
            navApp?.let { app ->
                PillButton(app.label, Icons.Filled.Navigation, filled = true, modifier = Modifier.weight(1f)) {
                    if (!openNavigationApp(context, app)) Toast.makeText(context, "Couldn't open it", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** The headline: fuel economy, big, with its change since last time. */
@Composable
private fun Hero(s: FillSummary) {
    val fill = s.stats.fillUp
    val tint = when (fill.country) { Country.SG -> CountryColors.sg; Country.MY -> CountryColors.my; Country.MIXED -> CountryColors.mixed }
    GroupCard(contentPadding = 22.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)) {
        DotLabel("${fill.country.label()} · ${litres(fill.totalLitres)} · ${km(fill.odometerKm)}", tint)
        Spacer(Modifier.height(12.dp))
        Text("Fuel Economy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val economy = s.kmPerLitre.current
        if (economy == null) {
            Text("—", style = MaterialTheme.typography.displaySmall, fontFamily = NumberFont)
            Text(
                "Your first fill-up on this bike. Economy shows from the next one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CountUpText(economy, ::kmPerLitre, style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp))
            Spacer(Modifier.height(6.dp))
            ChangeLine(s.kmPerLitre, ::kmPerLitre)
        }
        s.stats.distanceKm?.let { d ->
            Spacer(Modifier.height(10.dp))
            val days = s.stats.days?.let { if (it == 1L) " in 1 day" else " in $it days" }.orEmpty()
            Text("${km(d)} since your last fill-up$days", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The other figures as small tiles, two per row, each with its change since last time. */
@Composable
private fun Tiles(s: FillSummary) {
    val tiles = listOfNotNull(
        Triple("Cost", s.totalCost, ::sgd),
        Triple("Price", s.pricePerLitre, ::sgdPerLitre),
        Triple("Cost per km", s.costPerKm, ::sgdPerKm),
        Triple("Litres", s.litres, ::litres),
        Triple("Distance", s.distanceKm) { v: Double -> km(v.toInt()) },
        s.costPerDay.takeIf { it.current != null }?.let { Triple("Cost per Day", it, ::sgd) },
    ).filter { it.second.current != null }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, change, format) -> Tile(label, change, format, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(label: String, change: MetricChange, format: (Double) -> String, modifier: Modifier) {
    Column(
        modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(outline(), TileShape)
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CountUpText(change.current ?: 0.0, format, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        ChangeLine(change, format, small = true)
    }
}

/** An arrow (green when better, red when worse) with the difference and the previous value. */
@Composable
private fun ChangeLine(change: MetricChange, format: (Double) -> String, small: Boolean = false) {
    val style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val direction = change.direction
    val delta = change.delta
    if (direction == null || delta == null) {
        Text("Nothing to compare with yet", style = style, color = muted)
        return
    }
    val color = verdictColor(change.verdict)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (direction) { Direction.UP -> Icons.Filled.ArrowUpward; Direction.DOWN -> Icons.Filled.ArrowDownward; Direction.SAME -> Icons.Filled.Remove },
            contentDescription = when (change.verdict) { Verdict.GOOD -> "Better"; Verdict.BAD -> "Worse"; Verdict.NEUTRAL -> null },
            tint = color,
            modifier = Modifier.size(if (small) 14.dp else 18.dp),
        )
        Spacer(Modifier.width(4.dp))
        val text = if (direction == Direction.SAME) "Same as last time" else signed(delta, format)
        Text(text, style = style, color = color, fontFamily = NumberFont, maxLines = 1)
        if (!small && direction != Direction.SAME) {
            Text("  vs ${format(change.previous!!)}", style = style, color = muted, fontFamily = NumberFont, maxLines = 1)
        }
    }
}

private fun signed(delta: Double, format: (Double) -> String): String =
    if (delta >= 0) "+${format(delta)}" else "−${format(abs(delta))}"

@Composable
private fun verdictColor(v: Verdict): Color {
    val dark = LocalDarkTheme.current
    return when (v) {
        Verdict.GOOD -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Verdict.BAD -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        Verdict.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** What pumping in Malaysia saved, worked out the same way as everywhere else in the app. */
@Composable
private fun Savings(s: FillSummary, tripKm: Double) {
    val saved = s.savingsSgd
    GroupCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Savings, contentDescription = null, tint = CountryColors.my)
            Spacer(Modifier.width(12.dp))
            Text(
                if (saved == null || saved >= 0) "Saved by Pumping in Malaysia" else "Malaysia Cost More This Time",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (saved == null) {
            Text(
                "Add a Singapore fill-up to compare prices with, and savings will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CountUpText(abs(saved), ::sgd, style = MaterialTheme.typography.headlineMedium, color = verdictColor(if (saved >= 0) Verdict.GOOD else Verdict.BAD))
            val fill = s.stats.fillUp
            val reference = s.stats.sgReferencePricePerLitre
            val note = when {
                fill.importedSavingsSgd != null -> "From your spreadsheet."
                reference == null -> null
                fill.noSpecialTrip -> "Compared with ${sgdPerLitre(reference)} in Singapore. No special trip, so no trip cost taken off."
                else -> "Compared with ${sgdPerLitre(reference)} in Singapore, minus the ${tripKm.toInt()} km trip."
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Recent km/L and price per litre as small bars, this fill-up highlighted at the right. */
@Composable
private fun Trend(s: FillSummary) {
    if (s.recentKmPerLitre.size < 2 && s.recentPricePerLitre.size < 2) return
    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Recent Fill-Ups", Modifier.padding(top = 0.dp))
    GroupCard(contentPadding = 18.dp) {
        if (s.recentKmPerLitre.size >= 2) {
            TrendRow("Fuel Economy", s.recentKmPerLitre, if (s.kmPerLitreIncludesThis) 1 else 0, MaterialTheme.colorScheme.tertiary)
        }
        if (s.recentKmPerLitre.size >= 2 && s.recentPricePerLitre.size >= 2) Spacer(Modifier.height(16.dp))
        if (s.recentPricePerLitre.size >= 2) {
            TrendRow("Price per Litre", s.recentPricePerLitre, if (s.pricePerLitreIncludesThis) 1 else 0, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TrendRow(label: String, values: List<Double>, highlight: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            "Last ${values.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = NumberFont,
        )
    }
    Spacer(Modifier.height(8.dp))
    MiniBars(values, highlight, color)
}
