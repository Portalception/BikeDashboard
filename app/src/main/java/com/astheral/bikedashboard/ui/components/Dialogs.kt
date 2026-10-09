package com.astheral.bikedashboard.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Asks for one value in a dialog. [validate] returns an error to show, or null when the text is
 * fine; saving an empty field clears the value.
 */
@Composable
fun TextFieldDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    number: Boolean = false,
    suffix: String? = null,
    singleLine: Boolean = true,
    validate: (String) -> String? = { null },
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val error = value.text.takeIf { it.isNotBlank() }?.let(validate)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                suffix = suffix?.let { { Text(it) } },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
                    capitalization = if (number) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text.trim()) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


/** A calendar for picking a date, with "Clear" when [canClear] to remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate?) -> Unit, canClear: Boolean = true) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { ms -> onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            if (canClear && initial != null) {
                TextButton(onClick = { onPick(null) }) { Text("Clear") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(picker) }
}

/** Asks "are you sure?" before something that can't be undone, with the [confirm] button in red. */
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
