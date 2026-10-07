package com.astheral.bikedashboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

val CardShape = RoundedCornerShape(24.dp)
val TileShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(percent = 50)

/** Numbers use a monospace face, so digits line up and read like a gauge. */
val NumberFont = FontFamily.Monospace

/** The thin edge drawn around cards, tiles and buttons. */
@Composable
fun outline(color: Color? = null, width: Dp = 1.dp) =
    BorderStroke(width, color ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

/** The app's backdrop: the theme background with a few large, faint circles behind everything. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bubble = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(bubble, radius = w * 0.55f, center = Offset(w * 0.05f, h * 0.22f))
            drawCircle(bubble, radius = w * 0.45f, center = Offset(w * 1.0f, h * 0.12f))
            drawCircle(bubble, radius = w * 0.5f, center = Offset(w * 0.85f, h * 0.62f))
            drawCircle(bubble, radius = w * 0.35f, center = Offset(w * 0.1f, h * 0.9f))
        }
        // Text and icons drawn straight on the backdrop take the theme's text colour.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** A heading above a group of cards, with an icon, like "Theme" or "Backup". */
@Composable
fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 8.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A rounded card with a thin outline. [edge] tints the outline, e.g. with the accent colour. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    edge: Color? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick == null) {
        Surface(modifier.fillMaxWidth(), shape = CardShape, color = color, border = outline(edge), content = body)
    } else {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CardShape, color = color, border = outline(edge), content = body)
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** One line in a settings-style card: icon, title, optional subtitle and something on the right. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** A square-ish choice button with an icon over a label; the chosen one is filled in and outlined. */
@Composable
fun ChoiceTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    sublabel: String? = null,
    big: Boolean = false,
) {
    val fill by animateColorAsState(
        if (selected) (tint?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        label = "tileFill",
    )
    val edge by animateColorAsState(
        if (selected) (tint ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "tileEdge",
    )
    Column(
        modifier
            .clip(TileShape)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, edge, TileShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = if (big) 22.dp else 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (big && tint != null) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp)) }
        } else {
            Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurface)
        }
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        sublabel?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Two or three tabs in an outlined pill; the chosen tab is filled with the accent colour. */
@Composable
fun PillTabs(options: List<Pair<ImageVector?, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), PillShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, (icon, label) ->
            val isSelected = i == selected
            val fill by animateColorAsState(if (isSelected) accent.copy(alpha = 0.25f) else Color.Transparent, label = "pill")
            val edge by animateColorAsState(if (isSelected) accent else Color.Transparent, label = "pillEdge")
            val text by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "pillText",
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(fill)
                    .border(1.5.dp, edge, PillShape)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let { Icon(it, contentDescription = null, tint = text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                Text(label, color = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** A small rounded label in the accent colour, like "117 fill-ups". */
@Composable
fun AccentChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = NumberFont,
        maxLines = 1,
    )
}

/** A progress bar whose filled part is a wave, followed by a flat track for the rest. */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.tertiary) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "wavy")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val stroke = 5.dp.toPx()
        val mid = size.height / 2
        val amplitude = size.height / 2 - stroke / 2
        val wavelength = 22.dp.toPx()
        val end = size.width * shown
        val gap = 8.dp.toPx()
        if (end + gap < size.width) {
            drawLine(track, Offset(end + gap, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        if (end > 0f) {
            val path = Path().apply {
                moveTo(0f, mid)
                var x = 0f
                while (x <= end) {
                    lineTo(x, mid + amplitude * sin(2 * PI * x / wavelength).toFloat())
                    x += 2f
                }
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * A row of thin bars, one per fill-up, oldest on the left. The last [highlight] bars are drawn
 * in [color] and the rest are dimmed, so the tile shows which fill-ups the number covers.
 */
@Composable
fun MiniBars(values: List<Double>, highlight: Int, color: Color, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: return@Canvas
        val minV = values.minOrNull() ?: 0.0
        // Start bars a little above zero so small differences still show.
        val floor = max(0.0, minV - (maxV - minV) * 0.6)
        val slot = size.width / values.size
        val barW = (slot * 0.62f).coerceIn(2f, 7.dp.toPx())
        values.forEachIndexed { i, v ->
            val frac = ((v - floor) / (maxV - floor)).toFloat().coerceIn(0.15f, 1f)
            val h = size.height * frac
            drawRoundRect(
                color = if (i >= values.size - highlight) color else dim,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

/** A dot followed by a small label, as used at the top of each stat tile. */
@Composable
fun DotLabel(text: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
