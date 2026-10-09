package com.astheral.bikedashboard.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.ui.components.PickDateDialog
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.long
import java.time.LocalDate

/**
 * A date question card: the date (today unless you change it) in a tile that opens a calendar,
 * plus "Today" and "Yesterday" buttons that answer and move straight on. Shared by the fill-up
 * and service flows.
 */
@Composable
internal fun DateCard(question: String, date: LocalDate, onPick: (LocalDate) -> Unit, onNext: () -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    QuestionCard(null, question) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(TileShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .border(outline(), TileShape)
                .clickable { pickDate = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(dayLabel(date, today) ?: "Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.long(), style = MaterialTheme.typography.titleMedium)
            }
            Text("Change", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("Today") { onPick(today); onNext() }
            QuickChip("Yesterday") { onPick(today.minusDays(1)); onNext() }
        }
    }
    if (pickDate) {
        PickDateDialog(date, onDismiss = { pickDate = false }, canClear = false, onPick = { d ->
            d?.let(onPick)
            pickDate = false
        })
    }
}

/** "Today" or "Yesterday" for those two dates, otherwise null. */
internal fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String? = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> null
}
