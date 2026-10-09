package com.astheral.bikedashboard.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.calc.Direction
import com.astheral.bikedashboard.calc.MetricChange
import com.astheral.bikedashboard.calc.Verdict
import com.astheral.bikedashboard.calc.displayDelta
import com.astheral.bikedashboard.ui.DisplayUnit
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme
import kotlin.math.abs

/** Green for better, red for worse, the quiet text colour for neither. */
@Composable
fun verdictColor(v: Verdict): Color {
    val dark = LocalDarkTheme.current
    return when (v) {
        Verdict.GOOD -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Verdict.BAD -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        Verdict.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The arrow for a move: up, down, or a dash for no real change. */
fun directionIcon(direction: Direction): ImageVector = when (direction) {
    Direction.UP -> Icons.Filled.ArrowUpward
    Direction.DOWN -> Icons.Filled.ArrowDownward
    Direction.SAME -> Icons.Filled.Remove
}

fun verdictDescription(v: Verdict): String? = when (v) {
    Verdict.GOOD -> "Better"
    Verdict.BAD -> "Worse"
    Verdict.NEUTRAL -> null
}

/** [delta] formatted with a plus or a proper minus sign in front, e.g. "+0.6 km/L" or "−S$0.02/L". */
fun signed(delta: Double, format: (Double) -> String): String =
    if (delta >= 0) "+${format(delta)}" else "−${format(abs(delta))}"

/**
 * A small arrow and difference for a dashboard card, in [unit] (so km/L going up shows as
 * L/100 km going down when that's the unit picked). Green when better, red when worse, grey for
 * no real change or numbers that are neither good nor bad. [missing] shows when there's nothing to compare.
 */
@Composable
fun CompactChange(change: MetricChange, unit: DisplayUnit, missing: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val direction = change.direction
    val delta = change.displayDelta(unit::convert)
    if (direction == null || delta == null) {
        Text(missing, modifier, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val shown = when {
        direction == Direction.SAME -> Direction.SAME
        delta > 0 -> Direction.UP
        else -> Direction.DOWN
    }
    val color = verdictColor(change.verdict)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(directionIcon(shown), contentDescription = verdictDescription(change.verdict), tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            if (shown == Direction.SAME) "Same" else signed(delta, unit::format),
            style = style,
            color = color,
            fontFamily = if (shown == Direction.SAME) null else NumberFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
