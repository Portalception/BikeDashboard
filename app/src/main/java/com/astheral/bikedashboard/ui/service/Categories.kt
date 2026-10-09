package com.astheral.bikedashboard.ui.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.ui.theme.LocalDarkTheme

/** Colours for categories you add yourself, as (dark theme, light theme), picked by the category's id. */
private val EXTRA_COLORS = listOf(
    Color(0xFF9FA8DA) to Color(0xFF283593),
    Color(0xFFAED581) to Color(0xFF558B2F),
    Color(0xFFBCAAA4) to Color(0xFF5D4037),
    Color(0xFFFFAB91) to Color(0xFFD84315),
    Color(0xFF81D4FA) to Color(0xFF0277BD),
    Color(0xFFDCE775) to Color(0xFF827717),
)

/** A colour per category, readable on light and dark backgrounds. The same category always gets the same colour. */
@Composable
fun categoryColor(id: String): Color {
    val dark = LocalDarkTheme.current
    return when (id) {
        DefaultCategory.MAJOR_SERVICING -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        DefaultCategory.MINOR_SERVICING -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        DefaultCategory.ENGINE_OIL -> if (dark) Color(0xFFFFD54F) else Color(0xFF9A7400)
        DefaultCategory.FLUID_CHANGE -> if (dark) Color(0xFF4DD0E1) else Color(0xFF00838F)
        DefaultCategory.REPAIR -> if (dark) Color(0xFF82B1FF) else Color(0xFF1565C0)
        DefaultCategory.PERFORMANCE_MOD -> if (dark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
        DefaultCategory.COSMETIC_MOD -> if (dark) Color(0xFFF48FB1) else Color(0xFFAD1457)
        DefaultCategory.BIKE_WASH -> if (dark) Color(0xFF80CBC4) else Color(0xFF00695C)
        DefaultCategory.OTHER -> if (dark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
        else -> EXTRA_COLORS[Math.floorMod(id.hashCode(), EXTRA_COLORS.size)].let { (d, l) -> if (dark) d else l }
    }
}

/**
 * Asks for a category's name. [taken] are the names already used (other than this one), which
 * can't be used twice. [suggestions] are shown as pills under the box to pick from instead;
 * [onRemove], when given, adds a red "Remove" button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryNameDialog(
    title: String,
    initial: String,
    taken: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    note: String? = null,
    suggestions: List<ServiceCategory> = emptyList(),
    onPickSuggestion: (ServiceCategory) -> Unit = {},
    onRemove: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val name = value.text.trim()
    val error = when {
        taken.any { it.trim().equals(name, ignoreCase = true) } -> "There's already one called that"
        name.length > 40 -> "Keep it under 40 letters"
        else -> null
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (suggestions.isNotEmpty()) {
                    Text("Or Pick One You Already Have", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { c -> FilterPill(c.name, categoryColor(c.id), false) { onPickSuggestion(c) } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotEmpty() && error == null) { Text("Save") }
        },
        dismissButton = {
            Row {
                onRemove?.let { TextButton(onClick = it) { Text("Remove", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
