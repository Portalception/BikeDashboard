@file:OptIn(ExperimentalMaterial3Api::class)

package com.astheral.bikedashboard.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerLitre
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun EditScreen(fillUpId: Long, onDone: () -> Unit, onDeleted: (Long) -> Unit) {
    val viewModel: EditViewModel = viewModel(key = "edit-$fillUpId") {
        EditViewModel(this[APPLICATION_KEY]!!, fillUpId)
    }
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add fill-up" else "Edit fill-up") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                    TextButton(onClick = { viewModel.save() }, enabled = !state.loading) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!state.loading) {
            Form(state, viewModel::edit, onSave = { viewModel.save() }, modifier = Modifier.padding(padding))
        }
    }

    if (state.pendingWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissWarnings,
            title = { Text("Does this look right?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.pendingWarnings.forEach { Text("• $it") }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.save(anyway = true) }) { Text("Save anyway") } },
            dismissButton = { TextButton(onClick = viewModel::dismissWarnings) { Text("Go back") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this fill-up?") },
            text = { Text("${state.form.date.long()} · ${state.form.odometer} km. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleted(fillUpId) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Form(
    state: EditState,
    edit: ((FillUpForm) -> FillUpForm) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.errors
    var pickDate by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Text("  ${form.date.long()}")
        }

        val countries = listOf(Country.SG, Country.MY, Country.MIXED)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            countries.forEachIndexed { i, country ->
                SegmentedButton(
                    selected = form.country == country,
                    onClick = { edit { it.withCountry(country) } },
                    shape = SegmentedButtonDefaults.itemShape(i, countries.size),
                ) { Text(country.label()) }
            }
        }

        NumberField("Odometer", form.odometer, errors[Field.ODOMETER], suffix = "km", whole = true) { v -> edit { it.copy(odometer = v) } }

        if (form.country == Country.MIXED && form.legacyMixed) {
            Text(
                "Old mixed fill-up from your spreadsheet, with one total for both countries.",
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField("Total litres", form.unsplitLitres, errors[Field.UNSPLIT_LITRES], suffix = "L") { v -> edit { it.copy(unsplitLitres = v) } }
            NumberField("Total cost", form.unsplitCost, errors[Field.UNSPLIT_COST], prefix = "S$") { v -> edit { it.copy(unsplitCost = v) } }
        }

        if (form.hasSgPart) {
            if (form.country == Country.MIXED) SectionTitle("Singapore part")
            NumberField("Litres", form.sgLitres, errors[Field.SG_LITRES], suffix = "L") { v -> edit { it.copy(sgLitres = v) } }
            NumberField("Paid", form.sgCost, errors[Field.SG_COST], prefix = "S$") { v -> edit { it.copy(sgCost = v) } }
        }

        if (form.hasMyPart) {
            if (form.country == Country.MIXED) SectionTitle("Malaysia part")
            NumberField("Litres", form.myLitres, errors[Field.MY_LITRES], suffix = "L") { v -> edit { it.copy(myLitres = v) } }
            if (form.country == Country.MY && form.legacyMy) {
                Text("Old fill-up from your spreadsheet, recorded in SGD only.", style = MaterialTheme.typography.bodySmall)
                NumberField("Cost", form.legacyMyCost, errors[Field.LEGACY_MY_COST], prefix = "S$") { v -> edit { it.copy(legacyMyCost = v) } }
            } else {
                NumberField("RM paid", form.rmPaid, errors[Field.RM_PAID], prefix = "RM") { v -> edit { it.copy(rmPaid = v) } }
                NumberField(
                    "Rate", form.rate, errors[Field.RATE], suffix = "RM per S$1",
                    hint = if (state.rateFromLast) "Your last rate. Change it if the bank's is different today." else null,
                ) { v -> edit { it.copy(rate = v) } }
                NumberField(
                    "Pump charged", form.rmCharged, errors[Field.RM_CHARGED], prefix = "RM",
                    hint = state.preview.myCostSgd?.let { "= ${sgd(it)}" },
                ) { v -> edit { it.copy(rmCharged = v) } }
            }
        }

        SectionTitle("Fuel left before filling (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Bars left", form.barsLeft, errors[Field.BARS_LEFT], whole = true, modifier = Modifier.weight(1f)) { v ->
                edit { it.copy(barsLeft = v) }
            }
            NumberField("Km on reserve", form.reserveKm, errors[Field.RESERVE_KM], suffix = "km", modifier = Modifier.weight(1f)) { v ->
                edit { it.copy(reserveKm = v) }
            }
        }

        if (form.country != Country.SG) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("No special trip to MY")
                    Text(
                        "Already in Malaysia, so no trip cost is taken off the savings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.noSpecialTrip, onCheckedChange = { on -> edit { it.copy(noSpecialTrip = on) } })
            }
        }

        PreviewCard(state.preview)

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        edit { it.copy(date = date) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    prefix: String? = null,
    suffix: String? = null,
    hint: String? = null,
    whole: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = error != null,
        prefix = prefix?.let { { Text("$it ") } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun PreviewCard(p: FormPreview) {
    val parts = listOfNotNull(
        p.distanceKm?.let { "+${km(it)} since last fill-up" },
        p.kmPerLitre?.let { kmPerLitre(it) },
        p.totalCostSgd?.let { sgd(it) },
        p.pricePerLitre?.let { sgdPerLitre(it) },
    )
    if (parts.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Text(parts.joinToString(" · "), Modifier.padding(16.dp))
    }
}
