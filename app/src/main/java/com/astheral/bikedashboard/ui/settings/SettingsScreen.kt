package com.astheral.bikedashboard.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.csv.writeBackup
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.components.ChoiceTile
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import com.astheral.bikedashboard.ui.theme.ColorPreset
import com.astheral.bikedashboard.ui.theme.SwatchColors
import com.astheral.bikedashboard.ui.theme.ThemeMode
import com.astheral.bikedashboard.ui.theme.WallpaperSupported
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
    val appearance: StateFlow<AppearanceSettings> = settings.appearance

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun setTripKm(km: Double) = settings.setTripKm(km)
    fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) = settings.updateAppearance(change)

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

@Composable
fun SettingsScreen(onImport: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val tripKm by viewModel.tripKm.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val update = viewModel::updateAppearance

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            "Settings",
            Modifier.padding(start = 4.dp, top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        SectionHeader(Icons.Filled.Palette, "Theme")
        GroupCard(contentPadding = 16.dp) {
            Label("Theme mode")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val icons = mapOf(ThemeMode.SYSTEM to Icons.Filled.PhoneAndroid, ThemeMode.LIGHT to Icons.Filled.LightMode, ThemeMode.DARK to Icons.Filled.DarkMode)
                ThemeMode.entries.forEach { mode ->
                    ChoiceTile(icons.getValue(mode), mode.label, appearance.mode == mode, { update { it.copy(mode = mode) } }, Modifier.weight(1f))
                }
            }
            if (WallpaperSupported) {
                Spacer(Modifier.height(16.dp))
                Label("Colour style")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile(Icons.Filled.ColorLens, "Your colours", !appearance.wallpaper, { update { it.copy(wallpaper = false) } }, Modifier.weight(1f))
                    ChoiceTile(Icons.Filled.Wallpaper, "Wallpaper", appearance.wallpaper, { update { it.copy(wallpaper = true) } }, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            SettingRow(
                Icons.Filled.Contrast,
                "Pure black",
                "True black backgrounds in dark mode, for AMOLED screens",
                onClick = { update { it.copy(pureBlack = !it.pureBlack) } },
            ) {
                Switch(checked = appearance.pureBlack, onCheckedChange = { on -> update { it.copy(pureBlack = on) } })
            }
        }

        AnimatedVisibility(!appearance.wallpaper) {
            Column {
                SectionHeader(Icons.Filled.ColorLens, "Colours")
                GroupCard(contentPadding = 16.dp) {
                    Label("Presets")
                    PresetRow(appearance) { preset -> update { it.copy(primary = preset.primary, accent = preset.accent) } }
                    Spacer(Modifier.height(18.dp))
                    Label("Primary colour", "Buttons, the bottom bar and charts")
                    SwatchPicker(appearance.primary) { c -> update { it.copy(primary = c) } }
                    Spacer(Modifier.height(18.dp))
                    Label("Accent colour", "Highlights: chips, the selected tab, progress and the + button")
                    SwatchPicker(appearance.accent) { c -> update { it.copy(accent = c) } }
                }
            }
        }

        SectionHeader(Icons.Filled.Route, "Savings offset")
        GroupCard(contentPadding = 16.dp) { TripKmField(tripKm, viewModel::setTripKm) }

        SectionHeader(Icons.Filled.Save, "Backup")
        GroupCard {
            SettingRow(
                Icons.Filled.Upload,
                "Export CSV",
                exportMessage ?: "Save every fill-up to a file you choose, e.g. Downloads or Google Drive",
                onClick = { exportTo.launch("bike-dashboard-${LocalDate.now()}.csv") },
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
        GroupCard { AboutCard() }
    }
}

@Composable
private fun Label(text: String, hint: String? = null) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = if (hint == null) 10.dp else 0.dp))
    hint?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Colours are compared by their ARGB value, since a colour read back from settings is a new object. */
private fun same(a: Color, b: Color) = a.toArgb() == b.toArgb()

/** Each preset as a two-tone circle: primary on the left, accent on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(appearance: AppearanceSettings, onPick: (ColorPreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorPreset.entries.forEach { preset ->
            val selected = same(preset.primary, appearance.primary) && same(preset.accent, appearance.accent)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { onPick(preset) }.padding(4.dp),
            ) {
                Swatch(
                    Brush.horizontalGradient(0.5f to preset.primary, 0.5f to preset.accent),
                    selected,
                    checkTint = Color.White,
                )
                Text(preset.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A grid of colour circles plus a "+" for picking any colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchPicker(selected: Color, onPick: (Color) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val isCustom = SwatchColors.none { same(it, selected) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwatchColors.forEach { color ->
            Box(Modifier.clip(CircleShape).clickable(role = Role.RadioButton) { onPick(color) }) {
                Swatch(Brush.linearGradient(listOf(color, color)), same(selected, color), checkTint = contentOn(color), size = 40)
            }
        }
        // Custom: shows the chosen colour if it isn't one of the swatches.
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isCustom) Brush.linearGradient(listOf(selected, selected))
                    else Brush.sweepGradient(listOf(Color(0xFFE57373), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFBA68C8), Color(0xFFE57373))),
                )
                .border(if (isCustom) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable { custom = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isCustom) Icons.Filled.Check else Icons.Filled.Add, contentDescription = "Custom colour", tint = if (isCustom) contentOn(selected) else Color.White)
        }
    }
    if (custom) CustomColorDialog(selected, onDismiss = { custom = false }, onPick = { onPick(it); custom = false })
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, checkTint: Color, size: Int = 44) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "ring")
    Box(
        Modifier
            .size(size.dp)
            .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(20.dp))
    }
}

private fun contentOn(color: Color) = if (color.luminance() > 0.5f) Color.Black else Color.White

/** Pick any colour with hue, vividness and brightness sliders. */
@Composable
private fun CustomColorDialog(start: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.2f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f)) }
    val color = Color.hsv(hue, saturation, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(color))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..359f)
                Text("Vividness", style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.15f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0.3f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

@Composable
private fun AboutCard() {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    SettingRow(Icons.Filled.TwoWheeler, "Bike Dashboard", "Version ${appVersion()}")
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

/** The versionName set in app/build.gradle.kts. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
