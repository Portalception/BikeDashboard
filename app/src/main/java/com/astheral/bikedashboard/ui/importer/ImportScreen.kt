package com.astheral.bikedashboard.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.NumberFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.csv.SheetMismatch
import com.astheral.bikedashboard.csv.SheetProblem
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/** File types a CSV can show up as, depending on where it was saved from. */
private val CSV_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "application/csv",
    "application/vnd.ms-excel", "text/plain", "text/*",
)

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.fileChosen(uri)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Import CSV", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                ImportState.Idle -> {
                    Text(
                        "Choose a Bike Dashboard backup, or the CSV exported from your fuel spreadsheet. " +
                            "You'll see a preview before anything is saved.",
                    )
                    Button(onClick = { pickFile.launch(CSV_TYPES) }) { Text("Choose CSV file") }
                }
                ImportState.Reading, ImportState.Saving -> CircularProgressIndicator()
                is ImportState.Preview -> Preview(
                    s,
                    onConfirm = viewModel::confirm,
                    onCancel = viewModel::reset,
                )
                is ImportState.Done -> {
                    Text("Imported ${s.count} fill-ups.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) { Text("Done") }
                }
                is ImportState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    if (s.problems.isNotEmpty()) ProblemList(s.problems)
                    Button(onClick = { pickFile.launch(CSV_TYPES) }) { Text("Choose another file") }
                }
            }
        }
    }
}

@Composable
private fun Preview(s: ImportState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    GroupCard(contentPadding = 20.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.fileName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CountUpText(s.fillUps.size.toDouble(), { "${it.toInt()} fill-ups" }, style = MaterialTheme.typography.headlineSmall)
            Text("${s.firstDate.format(DATE)} – ${s.lastDate.format(DATE)}")
            Text(
                "SG ${s.countByCountry[Country.SG] ?: 0} · MY ${s.countByCountry[Country.MY] ?: 0} · " +
                    "Mixed ${s.countByCountry[Country.MIXED] ?: 0}",
            )
        }
    }

    if (s.isBackup) {
        Text("Bike Dashboard backup. Importing restores exactly what was exported.")
    } else {
        CheckReport(s.mismatches)
    }
    if (s.problems.isNotEmpty()) {
        Text(
            "${s.problems.size} ${if (s.isBackup) "lines" else "rows"} couldn't be read and will be left out:",
            color = MaterialTheme.colorScheme.error,
        )
        ProblemList(s.problems)
    }

    if (s.existingCount > 0) {
        Text(
            "This replaces the ${s.existingCount} fill-ups already on ${s.profileName}.",
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onConfirm) { Text("Import ${s.fillUps.size} fill-ups") }
    }
}

@Composable
private fun CheckReport(mismatches: List<SheetMismatch>) {
    val ok = mismatches.isEmpty()
    val dark = LocalDarkTheme.current
    val okText = if (dark) Color(0xFFB7E4BC) else Color(0xFF1B5E20)
    GroupCard(
        color = if (ok) (if (dark) Color(0xFF1E3A22) else Color(0xFFE3F2E5)) else MaterialTheme.colorScheme.errorContainer,
        edge = if (ok) okText.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
        contentPadding = 20.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ok) {
                Text("Check passed", style = MaterialTheme.typography.titleSmall, color = okText)
                Text(
                    "The app recalculated distance, days, km/L, cost per km, cost per day and price per litre " +
                        "for every row, and they all match your spreadsheet.",
                    color = okText,
                )
            } else {
                Text("${mismatches.size} figures don't match your spreadsheet", style = MaterialTheme.typography.titleSmall)
                mismatches.take(50).forEach { m ->
                    Text("Row ${m.rowNumber} · ${m.figure}: sheet ${fmt(m.sheet)}, app ${fmt(m.app)}")
                }
                if (mismatches.size > 50) Text("…and ${mismatches.size - 50} more")
            }
        }
    }
}

@Composable
private fun ProblemList(problems: List<SheetProblem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        problems.forEach { p -> Text(if (p.rowNumber > 0) "#${p.rowNumber}: ${p.message}" else p.message) }
    }
}

private fun fmt(value: Double?) = value?.let { String.format(Locale.ENGLISH, "%.4f", it) } ?: "blank"
