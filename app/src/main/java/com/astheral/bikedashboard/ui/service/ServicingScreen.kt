package com.astheral.bikedashboard.ui.service

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.data.ServiceType
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.DotLabel
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillShape
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A colour per kind of work, readable on light and dark backgrounds. */
@Composable
fun ServiceType.color(): Color {
    val dark = LocalDarkTheme.current
    return when (this) {
        ServiceType.MAJOR_SERVICING -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        ServiceType.MINOR_SERVICING -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        ServiceType.ENGINE_OIL -> if (dark) Color(0xFFFFD54F) else Color(0xFF9A7400)
        ServiceType.FLUID_CHANGE -> if (dark) Color(0xFF4DD0E1) else Color(0xFF00838F)
        ServiceType.REPAIR -> if (dark) Color(0xFF82B1FF) else Color(0xFF1565C0)
        ServiceType.PERFORMANCE_MOD -> if (dark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
        ServiceType.COSMETIC_MOD -> if (dark) Color(0xFFF48FB1) else Color(0xFFAD1457)
        ServiceType.BIKE_WASH -> if (dark) Color(0xFF80CBC4) else Color(0xFF00695C)
        ServiceType.OTHER -> if (dark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
    }
}

fun ServiceRecord.costText(): String? = cost?.let {
    when (currency) {
        Currency.SGD -> sgd(it)
        Currency.MYR -> "RM" + String.format(Locale.ENGLISH, "%,.2f", it)
    }
}

/** The kinds of work the "since last" tiles follow. */
private val TRACKED = listOf(ServiceType.MAJOR_SERVICING, ServiceType.MINOR_SERVICING, ServiceType.ENGINE_OIL, ServiceType.BIKE_WASH)

/**
 * Servicing for the bike being shown: how long since the last service, oil change and wash,
 * what's been spent, and every record, newest first. Tap a record to change it.
 */
@Composable
fun ServicingScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenIntervals: () -> Unit,
    viewModel: ServicingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val message by viewModel.message.collectAsState()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    val importSheet = { pickFile.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "text/*")) }
    val s = state ?: return
    var filter by remember { mutableStateOf<ServiceType?>(null) }
    val today = LocalDate.now()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Servicing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(s.profile.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            IconButton(onClick = importSheet) { Icon(Icons.Filled.Download, contentDescription = "Import from spreadsheet") }
            Box(
                Modifier.size(48.dp).clip(PillShape).background(MaterialTheme.colorScheme.tertiary).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = "Add a service", tint = MaterialTheme.colorScheme.onTertiary) }
        }
        message?.let {
            GroupCard(contentPadding = 16.dp, onClick = viewModel::clearMessage) { Text(it) }
        }

        if (s.records.isEmpty()) {
            GroupCard(contentPadding = 28.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Filled.Build, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("No services yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Add your services, oil changes, repairs and washes, or import the Servicing Record sheet from your spreadsheet (saved as CSV).",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onAdd) { Text("Add a service") }
                    OutlinedButton(onClick = importSheet) { Text("Import spreadsheet") }
                }
            }
        } else {
            if (s.due.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(Icons.Filled.NotificationsActive, "Coming up", Modifier.weight(1f).padding(top = 0.dp))
                    TextButton(onClick = onOpenIntervals) { Text("Intervals") }
                }
                GroupCard(contentPadding = 16.dp, onClick = onOpenIntervals) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { s.due.forEach { DueRow(it) } }
                }
            }
            SectionHeader(Icons.Filled.History, "Since last", Modifier.padding(top = 0.dp))
            TRACKED.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { type -> SinceTile(type, s.last(type), s.odometerKm, today, Modifier.weight(1f)) }
                }
            }

            SectionHeader(Icons.Filled.Payments, "Spent")
            SpendCard(s, today)

            SectionHeader(Icons.Filled.Build, "Records")
            TypeFilter(s.records, filter) { filter = it }
            val shown = s.records.filter { filter == null || it.type == filter }
            shown.groupBy { it.date.year }.forEach { (year, inYear) ->
                Row(Modifier.padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$year", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        sgd(inYear.sumOf { s.sgd(it) }),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = NumberFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupCard {
                    inYear.forEachIndexed { i, record ->
                        if (i > 0) GroupDivider()
                        RecordRow(record) { onOpen(record.id) }
                    }
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Import ${count(p.records.size, "service")}?") },
            text = {
                Text(
                    buildString {
                        append("From ${p.fileName}, for ${s.profile.name}.")
                        if (p.existing > 0) append(" This replaces the ${count(p.existing, "service")} already there.")
                        if (p.problems.isNotEmpty()) {
                            append("\n\n${p.problems.size} rows couldn't be read and will be left out:\n")
                            p.problems.take(10).forEach { append("#${it.rowNumber}: ${it.message}\n") }
                        }
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Import") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
    }
}

/** Days and km since the last time this kind of work was done. */
@Composable
private fun SinceTile(type: ServiceType, last: ServiceRecord?, odometer: Int?, today: LocalDate, modifier: Modifier) {
    Surface(modifier, shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f), border = outline()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DotLabel(type.label, type.color())
            if (last == null) {
                Text("Not yet", style = MaterialTheme.typography.titleMedium)
            } else {
                val days = ChronoUnit.DAYS.between(last.date, today)
                CountUpText(days.toDouble(), { "${it.toInt()} days" }, style = MaterialTheme.typography.titleMedium)
                val kmSince = if (odometer != null && last.odometerKm != null) odometer - last.odometerKm else null
                Text(
                    listOfNotNull(kmSince?.let { km(it) }, last.date.short()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SpendCard(s: ServicingState, today: LocalDate) {
    val thisYear = s.records.filter { it.date.year == today.year }
    val hasMyr = s.records.any { it.currency == Currency.MYR && it.cost != null }
    GroupCard(contentPadding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("All time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountUpText(s.records.sumOf { s.sgd(it) }, ::sgd, style = MaterialTheme.typography.titleLarge)
                Text(count(s.records.size, "record"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text("${today.year}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountUpText(thisYear.sumOf { s.sgd(it) }, ::sgd, style = MaterialTheme.typography.titleLarge)
                Text(count(thisYear.size, "record"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasMyr) {
            Spacer(Modifier.size(8.dp))
            Text(
                if (s.rmPerSgd != null) "Ringgit costs are converted at your last rate, RM${String.format(Locale.ENGLISH, "%.4f", s.rmPerSgd)} per S$1."
                else "Ringgit costs aren't included until you add a Malaysian fill-up with a rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "All" plus a pill for each kind of work that has records. */
@Composable
private fun TypeFilter(records: List<ServiceRecord>, selected: ServiceType?, onSelect: (ServiceType?) -> Unit) {
    val types = ServiceType.entries.filter { t -> records.any { it.type == t } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All", null, selected == null) { onSelect(null) }
        types.forEach { t -> FilterPill(t.label, t.color(), selected == t) { onSelect(if (selected == t) null else t) } }
    }
}

@Composable
internal fun FilterPill(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = outline(if (selected) MaterialTheme.colorScheme.tertiary else null),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)); Spacer(Modifier.width(8.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecordRow(record: ServiceRecord, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(record.type.color(), CircleShape))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(record.description.ifBlank { record.type.label }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(record.type.label, record.date.short(), record.odometerKm?.let { km(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        record.costText()?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontFamily = NumberFont) }
    }
}
