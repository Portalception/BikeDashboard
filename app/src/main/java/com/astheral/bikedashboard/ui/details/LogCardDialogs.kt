package com.astheral.bikedashboard.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.data.BikeDetails
import com.astheral.bikedashboard.logcard.LogCardIssue
import com.astheral.bikedashboard.logcard.asLogCard
import com.astheral.bikedashboard.logcard.logCardColour
import com.astheral.bikedashboard.logcard.logCardTheme
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PickDateDialog
import java.time.LocalDate

/** A small error-coloured "Expired" label, for paperwork whose date has passed. */
@Composable
internal fun ExpiredChip() {
    Text(
        "Expired",
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
internal fun ReadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Reading the Log Card…") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        confirmButton = {},
    )
}

/**
 * A date on the card that doesn't look right (already expired, in the future or unreadable):
 * type in the right one, upload a newer PDF, or keep what the card says.
 */
@Composable
internal fun CardIssueDialog(
    issue: LogCardIssue,
    current: LocalDate?,
    onEnter: (LocalDate) -> Unit,
    onUpload: () -> Unit,
    onKeep: () -> Unit,
    onCancel: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Check the ${issue.date.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(issue.message)
                Text(
                    if (issue.kind == LogCardIssue.Kind.EXPIRED) "If you've renewed it since, enter the new date or upload an updated log card."
                    else "Enter the right date, or upload an updated log card.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enter the Date")
                }
                OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload a Newer PDF")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeep) {
                Text(if (issue.kind == LogCardIssue.Kind.UNREADABLE) "Skip It" else "Keep This Date")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
    if (picking) {
        PickDateDialog(current, onDismiss = { picking = false }, onPick = { d -> picking = false; d?.let(onEnter) }, canClear = false)
    }
}

/** Everything the card will fill in, with what it replaces, before anything is saved. */
@Composable
internal fun CardPreviewDialog(
    review: CardImport.Review,
    current: BikeDetails,
    onUseColours: (Boolean) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val card = review.card
    // What each preview row shows now, so changed values can say what they replace.
    val old = current.asLogCard().previewRows().toMap()
    val theme = logCardTheme(card.primaryColour, card.secondaryColour)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Fill In from Log Card") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("These will be saved for this bike:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                card.previewRows().forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontFamily = NumberFont)
                        old[label]?.takeIf { it != value }?.let {
                            Text("Replaces $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                if (theme != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOfNotNull(logCardColour(card.primaryColour) ?: theme.first, logCardColour(card.secondaryColour)).forEach {
                            Box(
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(it))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Use as App Colours", style = MaterialTheme.typography.titleSmall)
                            Text("Primary and accent from the bike's colour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = review.useColours, onCheckedChange = onUseColours)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Fill In") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
