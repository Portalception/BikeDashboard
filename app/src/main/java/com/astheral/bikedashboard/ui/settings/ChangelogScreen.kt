package com.astheral.bikedashboard.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillShape
import com.astheral.bikedashboard.ui.short

/** The timeline rail: where it sits, how thick it is and how big the dots on it are. */
private val RailWidth = 24.dp
private val RailGap = 12.dp
private val RailStroke = 2.dp
private val DotRadius = 5.dp
private val MinorDotRadius = 7.dp
private val MinorHaloRadius = 12.dp

/** How far down a card its version line sits, so each dot lines up with it. */
private val DotY = 30.dp
private val ReleaseGap = 14.dp

/**
 * Settings > About > Changelog: every release on a timeline, newest first. A rail runs down the
 * left with a dot for each release (bigger, in the accent colour, for minor versions like 0.7).
 * The newest card starts open; tap any other to open it.
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val installed = appVersionName()
    // Which releases are open, kept while scrolling and when the screen comes back.
    var open by rememberSaveable { mutableStateOf(listOf(CHANGELOG.first().version)) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item(key = "header") { Header(installed, onBack) }
        itemsIndexed(CHANGELOG, key = { _, r -> r.version }) { i, release ->
            val isOpen = release.version in open
            TimelineEntry(
                release = release,
                first = i == 0,
                last = i == CHANGELOG.lastIndex,
                installed = release.version == installed,
                open = isOpen,
                onToggle = { open = if (isOpen) open - release.version else open + release.version },
            )
        }
    }
}

/** The app's icon in a circle, then "Changelog" and which version is installed. */
@Composable
private fun Header(installed: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val icon by remember(context) { Settings.get(context).appIcon }.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .border(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f), CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // The launcher icon's layers; launchers show the middle 72 of 108dp, so zoom to match.
                val zoom = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                Image(painterResource(icon.background), contentDescription = null, modifier = zoom)
                Image(painterResource(icon.foreground), contentDescription = null, modifier = zoom)
            }
            Text(
                "Changelog",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (installed.isEmpty()) "Bike Dashboard" else "Bike Dashboard · v$installed",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = NumberFont,
            )
            AccentChip("${CHANGELOG.size} releases", Modifier.padding(top = 12.dp))
        }
    }
}

/** One release: its stretch of the rail with a dot, and its card beside it. */
@Composable
private fun TimelineEntry(release: Release, first: Boolean, last: Boolean, installed: Boolean, open: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.tertiary
    val rail = MaterialTheme.colorScheme.outlineVariant
    val patchDot = MaterialTheme.colorScheme.onSurfaceVariant
    val backdrop = MaterialTheme.colorScheme.background
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RailWidth.toPx() / 2
                val dotY = DotY.toPx()
                // The rail starts at the first dot and ends at the last one.
                drawLine(rail, Offset(x, if (first) dotY else 0f), Offset(x, if (last) dotY else size.height), strokeWidth = RailStroke.toPx())
                if (release.isMinor) {
                    drawCircle(accent.copy(alpha = 0.25f), radius = MinorHaloRadius.toPx(), center = Offset(x, dotY))
                    drawCircle(accent, radius = MinorDotRadius.toPx(), center = Offset(x, dotY))
                } else {
                    // A ring the colour of the page, so the rail doesn't run through the dot.
                    drawCircle(backdrop, radius = DotRadius.toPx() + 2.dp.toPx(), center = Offset(x, dotY))
                    drawCircle(if (installed) accent else patchDot, radius = DotRadius.toPx(), center = Offset(x, dotY))
                }
            }
            .padding(start = RailWidth + RailGap, bottom = if (last) 0.dp else ReleaseGap),
    ) {
        ReleaseCard(release, installed, open, onToggle)
    }
}

@Composable
private fun ReleaseCard(release: Release, installed: Boolean, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron")
    val accent = MaterialTheme.colorScheme.tertiary
    GroupCard(edge = if (release.isMinor || installed) accent.copy(alpha = 0.6f) else null) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle)
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "v${release.version}",
                        style = if (release.isMinor) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontFamily = NumberFont,
                        fontWeight = FontWeight.SemiBold,
                        color = if (release.isMinor) accent else MaterialTheme.colorScheme.onSurface,
                    )
                    if (installed) AccentChip("Installed")
                }
                Text(
                    release.date.short() + if (open) "" else " · ${changeCount(release)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(turn),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(250)) + fadeOut(tween(200)),
        ) {
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                release.changes.forEach { change ->
                    if (change.endsWith(":")) {
                        // "Everything from 0.6.1 to 0.6.13:" introduces a summary, not a change.
                        Text(
                            change.removeSuffix(":"),
                            style = MaterialTheme.typography.labelLarge,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .background(accent, PillShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(change, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** "3 changes", not counting a summary's opening line. */
private fun changeCount(release: Release): String {
    val n = release.changes.count { !it.endsWith(":") }
    return if (n == 1) "1 change" else "$n changes"
}

/** The versionName set in app/build.gradle.kts. */
@Composable
internal fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
