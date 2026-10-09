package com.astheral.bikedashboard.ui.service

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.EstimateKind
import com.astheral.bikedashboard.calc.OdometerEstimate
import com.astheral.bikedashboard.calc.estimateOdometer
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.data.nameOf
import com.astheral.bikedashboard.data.newCategory
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillTabs
import com.astheral.bikedashboard.ui.edit.DateCard
import com.astheral.bikedashboard.ui.edit.NumberCard
import com.astheral.bikedashboard.ui.edit.QuestionCard
import com.astheral.bikedashboard.ui.edit.QuickChip
import com.astheral.bikedashboard.ui.edit.StepFlow
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class ServiceEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: ServiceForm = ServiceForm(),
    val step: ServiceStep = ServiceStep.CATEGORY,
    /** +1 when moving forward through the cards, -1 when going back; picks the slide direction. */
    val direction: Int = 1,
    /** Message for the current card's answer, shown after trying to go on. */
    val error: String? = null,
    /** Every category, to pick from. */
    val categories: List<ServiceCategory> = emptyList(),
    /** The bike's fill-ups, to estimate the odometer from. */
    val fillUps: List<FillUp> = emptyList(),
    /** The bike's other records, for one-tap descriptions. */
    val records: List<ServiceRecord> = emptyList(),
    /** Where the bike's odometer is now, for a service done today. */
    val odometerNow: Int? = null,
    /** The last exchange rate entered, to show a ringgit cost in SGD. */
    val rmPerSgd: Double? = null,
    val done: Boolean = false,
) {
    val index: Int get() = step.ordinal
    val count: Int get() = ServiceStep.entries.size

    /** The odometer worked out from the fill-ups around the date, or null with no fill-ups. */
    val estimate: OdometerEstimate? get() = estimateOdometer(form.date, fillUps)
}

class ServiceEditViewModel(application: Application, private val id: Long) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val dao = db.serviceDao()
    private var existing: ServiceRecord? = null
    private var profileId = 0L

    private val _state = MutableStateFlow(ServiceEditState())
    val state: StateFlow<ServiceEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = if (id == 0L) null else dao.getById(id)
            existing = r
            profileId = r?.profileId ?: Profiles.get(application).active.first()?.id ?: return@launch
            val categories = db.categoryDao().getAll()
            val fills = db.fillUpDao().getAll(profileId)
            val records = dao.getAll(profileId).filter { it.id != id }
            _state.value = ServiceEditState(
                loading = false,
                isNew = r == null,
                // A new record starts on today with oil (the most common job); editing opens on the final check.
                form = r?.let { ServiceForm.from(it) } ?: ServiceForm(
                    type = categories.firstOrNull { it.id == DefaultCategory.ENGINE_OIL }?.id ?: categories.firstOrNull()?.id ?: DefaultCategory.OTHER,
                ),
                step = if (r == null) ServiceStep.CATEGORY else ServiceStep.REVIEW,
                categories = categories,
                fillUps = fills,
                records = records,
                odometerNow = currentOdometer(fills, records),
                rmPerSgd = fills.lastOrNull { it.rmPerSgd != null }?.rmPerSgd,
            )
        }
    }

    fun edit(change: (ServiceForm) -> ServiceForm) = _state.update { it.copy(form = change(it.form), error = null) }

    /** Picks the kind of work and moves straight on, since that's all the first card asks. */
    fun chooseCategory(id: String) {
        edit { it.copy(type = id) }
        if (_state.value.isNew) next() else goTo(ServiceStep.REVIEW)
    }

    /** Adds a category from the first card and picks it. */
    fun addCategory(name: String) {
        val category = newCategory(name, _state.value.categories)
        viewModelScope.launch {
            db.categoryDao().insertAll(listOf(category))
            _state.update { it.copy(categories = it.categories + category) }
            chooseCategory(category.id)
        }
    }

    /** Changes the date; an estimated odometer is worked out again for the new date. */
    fun setDate(date: LocalDate) {
        edit { it.copy(date = date) }
        if (_state.value.form.odometerEstimated) estimate()
    }

    /** Fills in the odometer from the fill-ups around the date. */
    fun estimate() {
        val e = _state.value.estimate ?: return
        edit { it.copy(odometer = e.km.toString(), odometerEstimated = true) }
    }

    /** Goes to the next card, unless this card's answer is wrong. */
    fun next() {
        val s = _state.value
        s.form.errorFor(s.step)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        val nextStep = ServiceStep.entries.getOrNull(s.index + 1) ?: return
        _state.update { it.copy(step = nextStep, direction = 1, error = null) }
    }

    /** Goes back a card; returns false on the first card so the screen can close instead. */
    fun back(): Boolean {
        val s = _state.value
        if (s.index == 0) return false
        _state.update { it.copy(step = ServiceStep.entries[s.index - 1], direction = -1, error = null) }
        return true
    }

    fun goTo(step: ServiceStep) = _state.update {
        it.copy(step = step, direction = if (step.ordinal < it.index) -1 else 1, error = null)
    }

    fun save() {
        val s = _state.value
        val record = s.form.toRecord(existing?.id ?: 0, profileId)
        if (record == null) {
            s.form.firstStepWithError()?.let { step -> _state.update { it.copy(step = step, direction = -1, error = s.form.errorFor(step)) } }
            return
        }
        viewModelScope.launch {
            if (existing == null) dao.insert(record) else dao.update(record)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val r = existing ?: return
        viewModelScope.launch {
            dao.delete(r)
            _state.update { it.copy(done = true) }
        }
    }
}

/** Add or change one service record, one question per card like adding a fill-up. */
@Composable
fun ServiceEditScreen(id: Long, onDone: () -> Unit) {
    val vm: ServiceEditViewModel = viewModel(key = "service-$id") { ServiceEditViewModel(this[APPLICATION_KEY]!!, id) }
    val s by vm.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onDone() }
    // The phone's back gesture goes back a card, and only closes from the first one.
    BackHandler(enabled = !s.loading && s.index > 0 && s.isNew) { vm.back() }
    if (s.loading) return

    val f = s.form
    val editingReview = !s.isNew && s.step == ServiceStep.REVIEW
    val blank = when (s.step) {
        ServiceStep.DESCRIPTION -> f.description.isBlank()
        ServiceStep.COST -> f.cost.isBlank()
        ServiceStep.ODOMETER -> f.odometer.isBlank()
        else -> false
    }
    StepFlow(
        title = if (s.isNew) "New Service" else "Edit Service",
        step = s.step,
        index = s.index,
        count = s.count,
        direction = s.direction,
        onClose = onDone,
        onDelete = if (s.isNew) null else ({ confirmDelete = true }),
        backCloses = s.index == 0 || editingReview,
        onBack = { if (editingReview) onDone() else if (!vm.back()) onDone() },
        primaryText = when {
            s.step == ServiceStep.REVIEW -> "Save"
            blank -> "Skip"
            !s.isNew -> "Done"
            else -> "Next"
        },
        primaryIsFinal = s.step == ServiceStep.REVIEW,
        onPrimary = {
            when {
                s.step == ServiceStep.REVIEW -> vm.save()
                // When editing, each card returns to the final check once it's answered.
                !s.isNew -> if (f.errorFor(s.step) == null) vm.goTo(ServiceStep.REVIEW) else vm.next()
                else -> vm.next()
            }
        },
    ) { step -> ServiceStepCard(step, s, vm) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("${s.categories.nameOf(f.type)}${if (f.description.isNotBlank()) ": ${f.description}" else ""} on ${f.date.long()}. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ServiceStepCard(step: ServiceStep, s: ServiceEditState, vm: ServiceEditViewModel) {
    val f = s.form
    when (step) {
        ServiceStep.CATEGORY -> CategoryCard(s, vm)
        ServiceStep.DATE -> DateCard("When was it done?", f.date, onPick = vm::setDate, onNext = vm::next)
        ServiceStep.DESCRIPTION -> NumberCard(
            question = "What was done exactly?",
            hint = "Optional, e.g. Motul 10w-40 + oil filter.",
            value = f.description, error = s.error,
            onChange = { v -> vm.edit { it.copy(description = v) } }, onNext = vm::next,
            text = true, capitalization = KeyboardCapitalization.Sentences,
            quick = recentDescriptions(s.records, f.type).map { it to it },
        )
        ServiceStep.COST -> NumberCard(
            question = "What did it cost?",
            hint = costHint(f, s.rmPerSgd),
            value = f.cost, prefix = f.currency.symbol, error = s.error,
            onChange = { v -> vm.edit { it.copy(cost = v) } }, onNext = vm::next,
        ) {
            Spacer(Modifier.height(16.dp))
            PillTabs(
                Currency.entries.map { null to it.symbol },
                selected = f.currency.ordinal,
                onSelect = { i -> vm.edit { it.copy(currency = Currency.entries[i]) } },
            )
        }
        ServiceStep.ODOMETER -> OdometerCard(s, vm)
        ServiceStep.REVIEW -> ReviewCard(s, vm)
    }
}

private fun costHint(f: ServiceForm, rmPerSgd: Double?): String {
    val cost = parseNumber(f.cost)
    return if (f.currency == Currency.MYR && cost != null && rmPerSgd != null) "That's about ${sgd(cost / rmPerSgd)} at your last rate."
    else "Optional. Leave it blank if it was free or you don't know."
}

/** First card: a pill per category (tap one to move on), and a way to add a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(s: ServiceEditState, vm: ServiceEditViewModel) {
    var adding by remember { mutableStateOf(false) }
    QuestionCard(null, "What was done?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.categories.forEach { c -> FilterPill(c.name, categoryColor(c.id), s.form.type == c.id) { vm.chooseCategory(c.id) } }
            FilterPill("New Category", null, false) { adding = true }
        }
    }
    if (adding) {
        CategoryNameDialog(
            title = "New Category",
            initial = "",
            taken = s.categories.map { it.name },
            note = "It's shared by all your bikes. Change or remove categories from the Servicing page.",
            onDismiss = { adding = false },
            onSave = { name -> adding = false; vm.addCategory(name) },
        )
    }
}

/** How an estimate was worked out, e.g. "Between 12,340 km on 3 Mar and 12,820 km on 17 Mar." */
private fun explain(e: OdometerEstimate): String = when (e.kind) {
    EstimateKind.BETWEEN -> "Between ${km(e.before!!.km)} on ${e.before.date.short()} and ${km(e.after!!.km)} on ${e.after.date.short()}, going by the dates."
    EstimateKind.SAME_DAY -> "From your fill-up that day, at ${km(e.before!!.km)}."
    EstimateKind.BEFORE_ONLY -> "There's no fill-up after this date yet, so it's your last one before: ${km(e.before!!.km)} on ${e.before.date.short()}."
    EstimateKind.AFTER_ONLY -> "There's no fill-up before this date, so it's your first one after: ${km(e.after!!.km)} on ${e.after.date.short()}."
}

/** Optional: type the reading, or estimate it from the fill-ups around the date. */
@Composable
private fun OdometerCard(s: ServiceEditState, vm: ServiceEditViewModel) {
    val f = s.form
    val estimate = s.estimate
    NumberCard(
        question = "What was the odometer?",
        hint = "Optional. Leave it blank if you don't know.",
        value = f.odometer, suffix = "km", error = s.error, whole = true,
        onChange = { v -> vm.edit { it.copy(odometer = v, odometerEstimated = it.odometerEstimated && v.isNotBlank()) } },
        onNext = vm::next,
        quick = s.odometerNow?.takeIf { f.date == LocalDate.now() && !f.odometerEstimated }
            ?.let { listOf("Now: ${km(it)}" to it.toString()) }.orEmpty(),
    ) {
        Spacer(Modifier.height(16.dp))
        if (f.odometerEstimated && estimate != null) {
            GroupCard(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("Estimated", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    explain(estimate) + if (f.odometer != estimate.km.toString()) " You've changed it since." else " You can change the number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                QuickChip("It's an Exact Reading") { vm.edit { it.copy(odometerEstimated = false) } }
            }
        } else {
            FilledTonalButton(onClick = vm::estimate, enabled = estimate != null) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Estimate from Fill-Ups")
            }
            if (estimate == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "This bike has no fill-ups to estimate from yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Last card: every answer; tap one to change it. */
@Composable
private fun ReviewCard(s: ServiceEditState, vm: ServiceEditViewModel) {
    val f = s.form
    val cost = parseNumber(f.cost)
    val odometer = f.odometerNumber()?.toInt()
    val rows = listOf(
        ServiceStep.CATEGORY to ("What" to s.categories.nameOf(f.type)),
        ServiceStep.DATE to ("When" to f.date.long()),
        ServiceStep.DESCRIPTION to ("Details" to f.description.ifBlank { "—" }),
        ServiceStep.COST to ("Cost" to (cost?.let { if (f.currency == Currency.SGD) sgd(it) else "RM" + String.format(Locale.ENGLISH, "%,.2f", it) } ?: "—")),
        ServiceStep.ODOMETER to ("Odometer" to (odometer?.let { (if (f.odometerEstimated) "≈ " else "") + km(it) } ?: "—")),
    )
    GroupCard {
        rows.forEachIndexed { i, (step, row) ->
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth().clickable { vm.goTo(step) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.first, Modifier.padding(end = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    row.second,
                    Modifier.weight(1f),
                    fontFamily = if (step == ServiceStep.COST || step == ServiceStep.ODOMETER) NumberFont else null,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (f.odometerEstimated && odometer != null) {
        Text(
            "≈ means the odometer was estimated from your fill-ups.",
            Modifier.padding(start = 8.dp, top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
