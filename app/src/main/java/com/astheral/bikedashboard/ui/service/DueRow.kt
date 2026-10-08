package com.astheral.bikedashboard.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.data.DueLevel
import com.astheral.bikedashboard.data.IntervalStatus
import com.astheral.bikedashboard.ui.components.DotLabel
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.ProgressLine
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme

/** The colour for how urgent an interval is: the category's own colour until it's nearly due. */
@Composable
fun IntervalStatus.levelColor(): Color = when (level) {
    DueLevel.OVERDUE -> MaterialTheme.colorScheme.error
    DueLevel.SOON -> if (LocalDarkTheme.current) Color(0xFFFFB74D) else Color(0xFFE65100)
    else -> categoryColor(type)
}

/** One interval: its name and what's left, over a progress line of how much has been used. */
@Composable
fun DueRow(status: IntervalStatus, modifier: Modifier = Modifier) {
    val color = status.levelColor()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The label keeps its width; a long summary wraps instead of squeezing it.
            DotLabel(status.name, categoryColor(status.type))
            Text(
                status.summary(),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = NumberFont,
                textAlign = TextAlign.End,
                color = if (status.level == DueLevel.OK || status.level == DueLevel.UNKNOWN) MaterialTheme.colorScheme.onSurface else color,
            )
        }
        if (status.level != DueLevel.UNKNOWN) ProgressLine(status.used, Modifier.padding(top = 2.dp), color = color)
    }
}
