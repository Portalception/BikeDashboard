package com.astheral.bikedashboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.ui.background.BackgroundLook
import com.astheral.bikedashboard.ui.background.BackgroundPreview
import com.astheral.bikedashboard.ui.background.BackgroundStyle
import com.astheral.bikedashboard.ui.background.MAX_MOVEMENT
import com.astheral.bikedashboard.ui.background.MAX_SPEED
import com.astheral.bikedashboard.ui.background.MIN_MOVEMENT
import com.astheral.bikedashboard.ui.background.MIN_SPEED
import com.astheral.bikedashboard.ui.background.RaceTrack
import com.astheral.bikedashboard.ui.background.animationsOn
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.theme.LocalBackgroundLook
import kotlin.math.roundToInt

/** Cards on this page let the live background show through. */
@Composable
private fun glass(): Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)

/** What the background setting shows as, e.g. "Space", or "Random (today: Space)". */
@Composable
fun backgroundSummary(style: BackgroundStyle): String =
    if (style == BackgroundStyle.RANDOM) "Random, today: ${LocalBackgroundLook.current.style.label}" else style.label

/**
 * Settings > Personalisation > Background: the picture behind every screen, and how it moves.
 * The chosen one runs full screen behind the page, so changes show straight away.
 */
@Composable
fun BackgroundScreen(onBack: () -> Unit, viewModel: PersonalisationViewModel = viewModel()) {
    val appearance by viewModel.appearance.collectAsState()
    val bikeName by viewModel.bikeName.collectAsState()
    val update = viewModel::updateAppearance
    val shown = LocalBackgroundLook.current
    val context = LocalContext.current
    val canAnimate = remember(context) { animationsOn(context) }

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
            Text("Background", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        SectionHeader(Icons.Filled.Wallpaper, "Style")
        GroupCard(color = glass(), contentPadding = 16.dp) {
            Text(
                buildString {
                    append("Moves gently behind every screen, in your colours.")
                    bikeName?.let { append(" For $it; each bike keeps its own.") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            if (appearance.backgroundStyle == BackgroundStyle.RANDOM) {
                Text(
                    "Today: ${shown.style.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
            var holding by remember { mutableStateOf<BackgroundStyle?>(null) }
            StyleTiles(
                appearance.backgroundStyle,
                shown,
                hintFor = { style ->
                    when (style) {
                        BackgroundStyle.RACETRACK -> "${appearance.backgroundTrack.label} · hold for more"
                        else -> null
                    }
                },
                onHold = { style -> if (style == BackgroundStyle.RACETRACK) holding = style },
            ) { style -> update { it.copy(backgroundStyle = style) } }
            if (holding == BackgroundStyle.RACETRACK) {
                TrackPicker(
                    selected = appearance.backgroundTrack,
                    shown = shown,
                    onPick = { track -> update { it.copy(backgroundStyle = BackgroundStyle.RACETRACK, backgroundTrack = track) } },
                    onDismiss = { holding = null },
                )
            }
        }

        SectionHeader(Icons.Filled.Tune, "Motion")
        GroupCard(color = glass()) {
            SettingRow(
                Icons.Filled.ScreenRotation,
                "Parallax Effect",
                "Shifts gently as you tilt the phone",
                onClick = { update { it.copy(backgroundParallax = !it.backgroundParallax) } },
            ) {
                Switch(checked = appearance.backgroundParallax, onCheckedChange = { on -> update { it.copy(backgroundParallax = on) } })
            }
            GroupDivider()
            PercentSlider(
                Icons.Filled.OpenWith,
                "Movement",
                "How far things drift and sway",
                value = appearance.backgroundMovement,
                range = MIN_MOVEMENT..MAX_MOVEMENT,
                step = 0.1f,
            ) { v -> update { it.copy(backgroundMovement = v) } }
            GroupDivider()
            PercentSlider(
                Icons.Filled.Speed,
                "Speed",
                "How fast it all runs",
                value = appearance.backgroundSpeed,
                range = MIN_SPEED..MAX_SPEED,
                step = 0.25f,
            ) { v -> update { it.copy(backgroundSpeed = v) } }
            if (!canAnimate) {
                Text(
                    "Your phone's \"Remove animations\" setting is on, so the background stays still.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/**
 * A slider shown as a percentage (100% is normal), moving in [step]s. Each step is saved as it's
 * reached, so the background behind the page changes while you drag.
 */
@Composable
private fun PercentSlider(
    icon: ImageVector,
    title: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${(value * 100).roundToInt()}%", fontFamily = NumberFont, style = MaterialTheme.typography.titleMedium)
        }
        val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = (range.start + ((raw - range.start) / step).roundToInt() * step).coerceIn(range)
                if ((snapped - value) * (snapped - value) > 1e-6f) onChange(snapped)
            },
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = 44.dp),
        )
    }
}

/**
 * Each background as a small preview; the chosen one is outlined in the accent colour and moves,
 * the others show a still frame. Random's tile shows today's pick under a shuffle badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleTiles(
    selected: BackgroundStyle,
    shown: BackgroundLook,
    hintFor: (BackgroundStyle) -> String?,
    onHold: (BackgroundStyle) -> Unit,
    onPick: (BackgroundStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundStyle.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val isSelected = style == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                            )
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(18.dp),
                            )
                            .combinedClickable(role = Role.RadioButton, onLongClick = { onHold(style) }) { onPick(style) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (style == BackgroundStyle.RANDOM) {
                                // Today's pick if Random is on; otherwise just a hint of one.
                                val sample = if (isSelected) shown.style else BackgroundStyle.SPACE
                                BackgroundPreview(sample, animate = isSelected, look = shown)
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(22.dp)) }
                            } else {
                                BackgroundPreview(style, animate = isSelected, look = shown)
                            }
                        }
                        Column(Modifier.padding(start = 6.dp, bottom = 4.dp)) {
                            Text(
                                style.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            val hint = if (style == BackgroundStyle.RANDOM) {
                                if (isSelected) "Today: ${shown.style.label}" else "A new one each launch"
                            } else {
                                hintFor(style)
                            }
                            hint?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Keep a lone last tile at half width.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The Racetrack's circuits as small moving previews, opened by holding its tile. Picking one also switches to Racetrack. */
@Composable
private fun TrackPicker(selected: RaceTrack, shown: BackgroundLook, onPick: (RaceTrack) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Racetracks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RaceTrack.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { track ->
                            val isSelected = track == selected
                            PickerTile(track.label, isSelected, Modifier.weight(1f), onClick = { onPick(track) }) {
                                BackgroundPreview(
                                    BackgroundStyle.RACETRACK,
                                    animate = isSelected,
                                    look = shown.copy(track = track),
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
    )
}

/** One choice in a hold-to-open picker: a small picture above a label, outlined when chosen. */
@Composable
private fun PickerTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    picture: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp),
            )
            .combinedClickable(role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { picture() }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            maxLines = 1,
        )
    }
}
