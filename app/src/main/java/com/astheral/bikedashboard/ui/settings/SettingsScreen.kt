package com.astheral.bikedashboard.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.csv.writeBackup
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.NoInsets
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    private val dao = AppDatabase.get(application).fillUpDao()

    val tripKm: StateFlow<Double> = settings.tripKm

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun setTripKm(km: Double) = settings.setTripKm(km)

    fun export(uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val fillUps = dao.getAll()
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("couldn't open the file")
                    out.bufferedWriter(Charsets.UTF_8).use { it.write(writeBackup(fillUps)) }
                }
                "Exported ${fillUps.size} fill-ups."
            } catch (e: IOException) {
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                "Export failed: ${e.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onImport: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val tripKm by viewModel.tripKm.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, contentWindowInsets = NoInsets) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Savings offset")
            TripKmField(tripKm, viewModel::setTripKm)

            HorizontalDivider()
            Section("Backup")
            Text(
                "Export saves every fill-up to a CSV file you choose, e.g. in Downloads or Google Drive. " +
                    "Import it on any phone to get everything back.",
            )
            Button(onClick = { exportTo.launch("bike-dashboard-${LocalDate.now()}.csv") }) { Text("Export CSV") }
            exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            OutlinedButton(onClick = onImport) { Text("Import CSV") }
            Text(
                "Import takes a backup made here, or the CSV from your old spreadsheet. It replaces everything in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Section("About")
            Text("Bike Dashboard ${appVersion()}")
            Text("Changelog", style = MaterialTheme.typography.titleSmall)
            CHANGELOG.forEach { release ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Version ${release.version} · ${release.date.short()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    release.changes.forEach { change ->
                        Row {
                            Text("•  ")
                            Text(change, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The versionName set in app/build.gradle.kts. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TripKmField(saved: Double, onSave: (Double) -> Unit) {
    var text by remember(saved) { mutableStateOf(if (saved == Math.rint(saved)) saved.toLong().toString() else saved.toString()) }
    val value = parseNumber(text)
    val valid = value != null && value >= 0
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseNumber(it)?.takeIf { km -> km >= 0 }?.let(onSave)
        },
        label = { Text("Trip to Malaysia and back") },
        suffix = { Text("km") },
        singleLine = true,
        isError = !valid,
        supportingText = {
            Text(
                if (valid) "The petrol for this ride is taken off the savings, unless you turn on \"No special trip to MY\"."
                else "Enter a distance in km, e.g. 48",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
