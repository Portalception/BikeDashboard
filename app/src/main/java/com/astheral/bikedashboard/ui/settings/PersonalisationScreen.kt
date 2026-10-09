package com.astheral.bikedashboard.ui.settings

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.DistanceCostUnit
import com.astheral.bikedashboard.ui.EconomyUnit
import com.astheral.bikedashboard.ui.PriceUnit
import com.astheral.bikedashboard.ui.TimeCostUnit
import com.astheral.bikedashboard.ui.Units
import com.astheral.bikedashboard.ui.components.ChoiceTile
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.ProgressLine
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.theme.AppIcon
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import com.astheral.bikedashboard.ui.theme.ColorPreset
import com.astheral.bikedashboard.ui.theme.ProgressStyle
import com.astheral.bikedashboard.ui.theme.SwatchColors
import com.astheral.bikedashboard.ui.theme.ThemeMode
import com.astheral.bikedashboard.ui.theme.WallpaperSupported
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonalisationViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    private val profiles = Profiles.get(application)

    /** The look of the bike being shown; each bike keeps its own. */
    val appearance: StateFlow<AppearanceSettings> =
        profiles.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, profiles.appearanceNow())
    val bikeName: StateFlow<String?> =
        profiles.active.map { it?.name }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val units: StateFlow<Units> = settings.units
    val appIcon: StateFlow<AppIcon> = settings.appIcon
    val legacyIconUnlocked: StateFlow<Boolean> = settings.legacyIconUnlocked
    val greetings: StateFlow<Boolean> = settings.greetings

    fun setGreetings(on: Boolean) = settings.setGreetings(on)

    /** Saved now; the launcher switches over once you leave the app (see MainActivity). */
    fun setAppIcon(icon: AppIcon) = settings.setAppIcon(icon)

    fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { profiles.updateAppearance(change) }
    }
    fun updateUnits(change: (Units) -> Units) = settings.updateUnits(change)
}

/** Settings > Personalisation: how the app looks, and the units the dashboard uses. */
@Composable
fun PersonalisationScreen(onBack: () -> Unit, onOpenBackground: () -> Unit, viewModel: PersonalisationViewModel = viewModel()) {
    val appearance by viewModel.appearance.collectAsState()
    val units by viewModel.units.collectAsState()
    val appIcon by viewModel.appIcon.collectAsState()
    val legacyIconUnlocked by viewModel.legacyIconUnlocked.collectAsState()
    val bikeName by viewModel.bikeName.collectAsState()
    val greetings by viewModel.greetings.collectAsState()
    val update = viewModel::updateAppearance

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Personalisation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        SectionHeader(Icons.Filled.Palette, "Theme")
        bikeName?.let { name ->
            Text(
                "For $name. Each bike keeps its own look.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
            )
        }
        GroupCard(contentPadding = 16.dp) {
            Label("Theme Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val icons = mapOf(ThemeMode.SYSTEM to Icons.Filled.PhoneAndroid, ThemeMode.LIGHT to Icons.Filled.LightMode, ThemeMode.DARK to Icons.Filled.DarkMode)
                ThemeMode.entries.forEach { mode ->
                    ChoiceTile(icons.getValue(mode), mode.label, appearance.mode == mode, { update { it.copy(mode = mode) } }, Modifier.weight(1f))
                }
            }
            if (WallpaperSupported) {
                Spacer(Modifier.height(16.dp))
                Label("Colour Style")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile(Icons.Filled.ColorLens, "Your Colours", !appearance.wallpaper, { update { it.copy(wallpaper = false) } }, Modifier.weight(1f))
                    ChoiceTile(Icons.Filled.Wallpaper, "Wallpaper", appearance.wallpaper, { update { it.copy(wallpaper = true) } }, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            SettingRow(
                Icons.Filled.Contrast,
                "Pure Black",
                "True black backgrounds in dark mode, for AMOLED screens",
                onClick = { update { it.copy(pureBlack = !it.pureBlack) } },
            ) {
                Switch(checked = appearance.pureBlack, onCheckedChange = { on -> update { it.copy(pureBlack = on) } })
            }
        }

        Spacer(Modifier.height(12.dp))
        GroupCard(contentPadding = 16.dp) {
            Label("Progress Line", "The line at the top of the dashboard and while adding a fill-up")
            ProgressStylePicker(appearance.progressStyle) { style -> update { it.copy(progressStyle = style) } }
        }

        Spacer(Modifier.height(12.dp))
        GroupCard {
            SettingRow(
                Icons.Filled.Wallpaper,
                "Background",
                backgroundSummary(appearance.backgroundStyle),
                onClick = onOpenBackground,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(!appearance.wallpaper) {
            Column {
                SectionHeader(Icons.Filled.ColorLens, "Colours")
                GroupCard(contentPadding = 16.dp) {
                    Label("Presets", "Monochrome, or each maker's racing colours")
                    PresetRow(appearance) { preset -> update { it.copy(primary = preset.primary, accent = preset.accent) } }
                    Spacer(Modifier.height(18.dp))
                    Label("Primary Colour", "Buttons, the bottom bar and charts")
                    SwatchPicker(appearance.primary) { c -> update { it.copy(primary = c) } }
                    Spacer(Modifier.height(18.dp))
                    Label("Accent Colour", "Highlights: chips, the selected tab, progress and the + button")
                    SwatchPicker(appearance.accent) { c -> update { it.copy(accent = c) } }
                }
            }
        }

        SectionHeader(Icons.Filled.WavingHand, "Start-Up")
        GroupCard {
            SettingRow(
                Icons.Filled.WavingHand,
                "Greeting Phrases",
                "A short hello for the time of day when the app opens",
                onClick = { viewModel.setGreetings(!greetings) },
            ) {
                Switch(checked = greetings, onCheckedChange = viewModel::setGreetings)
            }
        }

        SectionHeader(Icons.Filled.Apps, "App Icon")
        GroupCard(contentPadding = 16.dp) {
            Label("Launcher Icon", "Your home screen shows the new one once you leave the app")
            IconPicker(appIcon, showLegacy = legacyIconUnlocked || appIcon == AppIcon.LEGACY, onPick = viewModel::setAppIcon)
        }

        SectionHeader(Icons.Filled.Straighten, "Units")
        GroupCard(contentPadding = 16.dp) {
            Label("Fuel Economy")
            UnitTiles(EconomyUnit.entries, units.economy, { it.label }) { u -> viewModel.updateUnits { it.copy(economy = u) } }
            Spacer(Modifier.height(16.dp))
            Label("Cost per Distance")
            UnitTiles(DistanceCostUnit.entries, units.distanceCost, { it.label }) { u -> viewModel.updateUnits { it.copy(distanceCost = u) } }
            Spacer(Modifier.height(16.dp))
            Label("Cost over Time")
            UnitTiles(TimeCostUnit.entries, units.timeCost, { it.label }) { u -> viewModel.updateUnits { it.copy(timeCost = u) } }
            Spacer(Modifier.height(16.dp))
            Label("Petrol Price", "Ringgit uses the last exchange rate you entered")
            UnitTiles(PriceUnit.entries, units.price, { it.label }) { u -> viewModel.updateUnits { it.copy(price = u) } }
            Spacer(Modifier.height(10.dp))
            Text(
                "You can also tap a tile on the dashboard to switch its unit.",
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Each launcher icon as a small preview; the chosen one has a ring around it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: AppIcon, showLegacy: Boolean, onPick: (AppIcon) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon.entries.filter { showLegacy || it != AppIcon.LEGACY }.forEach { icon ->
            val isSelected = icon == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { onPick(icon) }.padding(4.dp),
            ) {
                val ring by animateDpAsState(if (isSelected) 3.dp else 0.dp, label = "iconRing")
                Box(
                    Modifier
                        .size(56.dp)
                        .border(ring, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(18.dp))
                        .padding(if (isSelected) 5.dp else 0.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The real icon layers. Launchers show the middle 72 of the 108dp layers, so zoom in to match.
                    val zoom = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                    Image(painterResource(icon.background), contentDescription = null, modifier = zoom)
                    Image(painterResource(icon.foreground), contentDescription = null, modifier = zoom)
                }
                Text(
                    icon.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One small tile per unit, side by side. */
@Composable
private fun <T> UnitTiles(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ChoiceTile(
                Icons.Filled.Straighten,
                label(option),
                option == selected,
                { onPick(option) },
                Modifier.weight(1f),
            )
        }
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
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
    }
}

/** Each shape as a small live preview; the chosen one is outlined in the accent colour. */
@Composable
private fun ProgressStylePicker(selected: ProgressStyle, onPick: (ProgressStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressStyle.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val isSelected = style == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            )
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable(role = Role.RadioButton) { onPick(style) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProgressLine(0.7f, style = style, animate = false)
                        Text(
                            style.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/** Colours are compared by their ARGB value, since a colour read back from settings is a new object. */
private fun same(a: Color, b: Color) = a.toArgb() == b.toArgb()

/** Each preset as a two-tone circle: primary on the left, accent on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(appearance: AppearanceSettings, onPick: (ColorPreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorPreset.entries.forEach { preset ->
            val selected = same(preset.primary, appearance.primary) && same(preset.accent, appearance.accent)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { onPick(preset) }.padding(4.dp),
            ) {
                Swatch(
                    Brush.horizontalGradient(0.5f to preset.primary, 0.5f to preset.accent),
                    selected,
                    checkTint = Color.White,
                )
                Text(preset.brand, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp), maxLines = 1, softWrap = false)
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
    // One row, spread across the card.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
            Icon(if (isCustom) Icons.Filled.Check else Icons.Filled.Add, contentDescription = "Custom Colour", tint = if (isCustom) contentOn(selected) else Color.White)
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

/**
 * Pick any colour: the hue, vividness and brightness sliders reach every colour, including
 * black, white and greys, and the hex box takes an exact #RRGGBB code. The two stay in step.
 */
@Composable
private fun CustomColorDialog(start: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    // The colour exactly as typed (or as it started), so it isn't nudged by rounding through
    // the sliders. Moving a slider clears it.
    var exact by remember { mutableStateOf<Color?>(start) }
    var hex by remember { mutableStateOf(hexOf(start)) }
    val color = exact ?: Color.hsv(hue, saturation, value)
    val hexValid = colorFromHex(hex) != null

    fun fromSliders(h: Float = hue, s: Float = saturation, v: Float = value) {
        hue = h; saturation = s; value = v
        exact = null
        hex = hexOf(Color.hsv(h, s, v))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(color))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed.filter { it.digitToIntOrNull(16) != null }.take(6).uppercase()
                        colorFromHex(hex)?.let { parsed ->
                            exact = parsed
                            val out = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed.toArgb(), it) }
                            // Greys have no hue, and black no vividness either: leave those
                            // sliders where they were, so moving them carries on from there.
                            if (out[1] > 0f && out[2] > 0f) hue = out[0]
                            if (out[2] > 0f) saturation = out[1]
                            value = out[2]
                        }
                    },
                    label = { Text("Hex Code") },
                    prefix = { Text("#", fontFamily = NumberFont) },
                    isError = !hexValid,
                    supportingText = if (hexValid) null else ({ Text("Six digits 0–9 or A–F, like 1B3F9A") }),
                    textStyle = LocalTextStyle.current.copy(fontFamily = NumberFont),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                SliderTrack((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })
                Slider(value = hue, onValueChange = { fromSliders(h = it) }, valueRange = 0f..360f)
                Text("Vividness", style = MaterialTheme.typography.labelMedium)
                SliderTrack(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)))
                Slider(value = saturation, onValueChange = { fromSliders(s = it) }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                SliderTrack(listOf(Color.Black, Color.hsv(hue, saturation, 1f)))
                Slider(value = value, onValueChange = { fromSliders(v = it) }, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }, enabled = hexValid) { Text("Use Colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A thin bar above a slider showing the colours it moves through. */
@Composable
private fun SliderTrack(colors: List<Color>) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(colors)))
}

/** The colour as six hex digits, like "1B3F9A" (no "#", no transparency). */
internal fun hexOf(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)

/** Six hex digits, with or without a "#", as a solid colour; null if it isn't that. */
internal fun colorFromHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000.toInt() or digits.toInt(16))
}

