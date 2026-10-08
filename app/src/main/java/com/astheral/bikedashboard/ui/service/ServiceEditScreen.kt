package com.astheral.bikedashboard.ui.service

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.data.nameOf
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.PickDateDialog
import com.astheral.bikedashboard.ui.components.PillTabs
import com.astheral.bikedashboard.ui.edit.PillButton
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.long
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ServiceForm(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: String = DefaultCategory.ENGINE_OIL,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val odometer: String = "",
    val cost: String = "",
    val currency: Currency = Currency.SGD,
    val error: String? = null,
    val done: Boolean = false,
    /** Every category, to pick from. */
    val categories: List<ServiceCategory> = emptyList(),
)

private fun trim(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

class ServiceEditViewModel(application: Application, private val id: Long) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val dao = db.serviceDao()
    private var existing: ServiceRecord? = null
    private var profileId = 0L

    private val _form = MutableStateFlow(ServiceForm())
    val form: StateFlow<ServiceForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val r = if (id == 0L) null else dao.getById(id)
            existing = r
            profileId = r?.profileId ?: Profiles.get(application).active.first()?.id ?: return@launch
            val categories = db.categoryDao().getAll()
            _form.value = if (r == null) {
                // A new record starts at today's date and where the bike's odometer is now.
                val odo = currentOdometer(db.fillUpDao().getAll(profileId), dao.getAll(profileId))
                ServiceForm(
                    loading = false,
                    odometer = odo?.toString().orEmpty(),
                    type = categories.firstOrNull { it.id == DefaultCategory.ENGINE_OIL }?.id ?: categories.firstOrNull()?.id ?: DefaultCategory.OTHER,
                    categories = categories,
                )
            } else {
                ServiceForm(
                    loading = false,
                    isNew = false,
                    type = r.type,
                    description = r.description,
                    date = r.date,
                    odometer = r.odometerKm?.toString().orEmpty(),
                    cost = r.cost?.let(::trim).orEmpty(),
                    currency = r.currency,
                    categories = categories,
                )
            }
        }
    }

    fun edit(change: (ServiceForm) -> ServiceForm) = _form.update { change(it).copy(error = null) }

    fun save() {
        val f = _form.value
        val odo = f.odometer.trim().takeIf { it.isNotEmpty() }?.let { parseNumber(it) }
        if (f.odometer.isNotBlank() && (odo == null || odo < 0)) {
            _form.update { it.copy(error = "The odometer should be a number of km") }
            return
        }
        val cost = f.cost.trim().takeIf { it.isNotEmpty() }?.let { parseNumber(it) }
        if (f.cost.isNotBlank() && (cost == null || cost < 0)) {
            _form.update { it.copy(error = "The cost should be a number") }
            return
        }
        val record = ServiceRecord(
            id = existing?.id ?: 0,
            profileId = profileId,
            date = f.date,
            odometerKm = odo?.toInt(),
            type = f.type,
            description = f.description.trim(),
            cost = cost,
            currency = f.currency,
        )
        viewModelScope.launch {
            if (existing == null) dao.insert(record) else dao.update(record)
            _form.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val r = existing ?: return
        viewModelScope.launch {
            dao.delete(r)
            _form.update { it.copy(done = true) }
        }
    }
}

/** Add or change one service record. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceEditScreen(id: Long, onDone: () -> Unit) {
    val vm: ServiceEditViewModel = viewModel(key = "service-$id") { ServiceEditViewModel(this[APPLICATION_KEY]!!, id) }
    val f by vm.form.collectAsState()
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(f.done) { if (f.done) onDone() }
    if (f.loading) return

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") }
            Text(
                if (f.isNew) "New service" else "Edit service",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!f.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupCard(contentPadding = 18.dp) {
                Text("What was done?", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    f.categories.forEach { c ->
                        FilterPill(c.name, categoryColor(c.id), f.type == c.id) { vm.edit { it.copy(type = c.id) } }
                    }
                }
                OutlinedTextField(
                    value = f.description,
                    onValueChange = { v -> vm.edit { it.copy(description = v) } },
                    label = { Text("Description, e.g. Motul 10w-40 + filter") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
            GroupCard {
                Row(
                    Modifier.fillMaxWidth().clickable { pickDate = true }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(f.date.long(), style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Change", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                }
            }
            GroupCard(contentPadding = 18.dp) {
                OutlinedTextField(
                    value = f.odometer,
                    onValueChange = { v -> vm.edit { it.copy(odometer = v) } },
                    label = { Text("Odometer") },
                    suffix = { Text("km") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = f.cost,
                        onValueChange = { v -> vm.edit { it.copy(cost = v) } },
                        label = { Text("Cost") },
                        prefix = { Text(f.currency.symbol) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    PillTabs(
                        Currency.entries.map { null to it.symbol },
                        selected = f.currency.ordinal,
                        onSelect = { i -> vm.edit { it.copy(currency = Currency.entries[i]) } },
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", Icons.Filled.Close, filled = false, modifier = Modifier.weight(1f), onClick = onDone)
            PillButton(if (f.isNew) "Add" else "Save", Icons.Filled.Check, filled = true, modifier = Modifier.weight(1f), onClick = vm::save)
        }
    }

    if (pickDate) {
        PickDateDialog(f.date, onDismiss = { pickDate = false }, canClear = false, onPick = { d ->
            d?.let { date -> vm.edit { it.copy(date = date) } }
            pickDate = false
        })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("${f.categories.nameOf(f.type)}${if (f.description.isNotBlank()) ": ${f.description}" else ""} on ${f.date.long()}. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
