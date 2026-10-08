package com.astheral.bikedashboard.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.csv.writeBackup
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.profile.ProfileTitle
import com.astheral.bikedashboard.ui.profile.icon
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    private val dao = AppDatabase.get(application).fillUpDao()
    private val profiles = Profiles.get(application)

    val profile: StateFlow<Profile?> = profiles.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ideasUnlocked: StateFlow<Boolean> = settings.ideasUnlocked

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun setTripKm(km: Double) {
        val p = profile.value ?: return
        viewModelScope.launch { profiles.update(p.copy(tripKm = km)) }
    }

    fun unlockIdeas() = settings.unlockIdeas()

    fun export(uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val fillUps = profiles.activeNow()?.let { dao.getAll(it.id) }.orEmpty()
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

@Composable
fun SettingsScreen(
    onImport: () -> Unit,
    onProfileClick: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onOpenIdeas: () -> Unit,
    onOpenPersonalisation: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val ideasUnlocked by viewModel.ideasUnlocked.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val tripKm = profile?.tripKm ?: DEFAULT_TRIP_KM
    val exportMessage by viewModel.exportMessage.collectAsState()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 16.dp)) {
            ProfileTitle(profile, onProfileClick)
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
        }

        SectionHeader(Icons.Filled.TwoWheeler, "Bike")
        GroupCard {
            profile?.let { p ->
                SettingRow(
                    p.kind.icon(),
                    p.name,
                    "${p.kind.label} · ${trimKm(p.tankLitres)} L tank · ${trimKm(p.tripKm)} km trip",
                    onClick = { onEditProfile(p.id) },
                ) { Chevron() }
                GroupDivider()
            }
            SettingRow(Icons.Filled.SwapHoriz, "Switch or add a bike", "Your other bikes, or a rental", onClick = onProfileClick) { Chevron() }
        }

        SectionHeader(Icons.Filled.Palette, "Personalisation")
        GroupCard {
            SettingRow(
                Icons.Filled.Palette,
                "Personalisation",
                "Theme, colours, progress line and units",
                onClick = onOpenPersonalisation,
            ) { Chevron() }
        }

        SectionHeader(Icons.Filled.Route, "Savings offset")
        GroupCard(contentPadding = 16.dp) { TripKmField(tripKm, viewModel::setTripKm, profile?.name) }

        SectionHeader(Icons.Filled.Save, "Backup")
        GroupCard {
            SettingRow(
                Icons.Filled.Upload,
                "Export CSV",
                exportMessage ?: "Save every fill-up to a file you choose, e.g. Downloads or Google Drive",
                onClick = { exportTo.launch("bike-dashboard-${fileSafe(profile?.name)}-${LocalDate.now()}.csv") },
            ) { Chevron() }
            GroupDivider()
            SettingRow(
                Icons.Filled.Download,
                "Import CSV",
                "Restore a backup, or load your old spreadsheet. Replaces everything in the app",
                onClick = onImport,
            ) { Chevron() }
        }

        SectionHeader(Icons.Filled.Info, "About")
        GroupCard { AboutCard(ideasUnlocked, onFound = viewModel::unlockIdeas, onOpenIdeas = onOpenIdeas) }
    }
}

/** "Yamaha R3" -> "yamaha-r3", for the backup file name. */
private fun fileSafe(name: String?) =
    name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "bike" }

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TripKmField(saved: Double, onSave: (Double) -> Unit, bikeName: String?) {
    var text by remember(saved, bikeName) { mutableStateOf(if (saved == Math.rint(saved)) saved.toLong().toString() else saved.toString()) }
    val value = parseNumber(text)
    val valid = value != null && value >= 0
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseNumber(it)?.takeIf { km -> km >= 0 }?.let(onSave)
        },
        label = { Text(if (bikeName == null) "Trip to Malaysia and back" else "Trip to Malaysia and back on $bikeName") },
        suffix = { Text("km") },
        singleLine = true,
        isError = !valid,
        shape = RoundedCornerShape(16.dp),
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

/**
 * About: the version, the changelog, and a hidden ideas notebook. Tap the version seven times to
 * find the notebook (like Android's developer options); after that it stays in the list.
 */
@Composable
private fun AboutCard(ideasUnlocked: Boolean, onFound: () -> Unit, onOpenIdeas: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val tapsLeft = SECRET_TAPS - taps
    SettingRow(
        Icons.Filled.TwoWheeler,
        "Bike Dashboard",
        when {
            ideasUnlocked && taps >= SECRET_TAPS -> "Version ${appVersion()} · You found the ideas notebook"
            !ideasUnlocked && taps >= 3 -> "Version ${appVersion()} · $tapsLeft more…"
            else -> "Version ${appVersion()}"
        },
        onClick = {
            taps++
            if (taps == SECRET_TAPS && !ideasUnlocked) onFound()
        },
    )
    if (ideasUnlocked) {
        GroupDivider()
        SettingRow(Icons.Filled.Lightbulb, "Ideas notebook", "Jot down feature ideas in plain text", onClick = onOpenIdeas) { Chevron() }
    }
    GroupDivider()
    SettingRow(Icons.Filled.History, "Changelog", "What's new in each version", onClick = { open = !open }) {
        Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(open) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CHANGELOG.forEach { release ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Version ${release.version} · ${release.date.short()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
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

private const val SECRET_TAPS = 7

private fun trimKm(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

/** The versionName set in app/build.gradle.kts. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
