@file:OptIn(ExperimentalMaterial3Api::class)

package com.astheral.bikedashboard.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.ui.components.ChoiceTile
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillShape
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerLitre
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.ui.theme.CountryColors
import java.util.Locale

/** [onSaved] is called with the new id after adding a fill-up (not after editing one); otherwise [onDone]. */
@Composable
fun EditScreen(fillUpId: Long, onDone: () -> Unit, onDeleted: (Long) -> Unit, onSaved: ((Long) -> Unit)? = null) {
    val viewModel: EditViewModel = viewModel(key = "edit-$fillUpId") {
        EditViewModel(this[APPLICATION_KEY]!!, fillUpId)
    }
    val state by viewModel.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (!state.done) return@LaunchedEffect
        val newId = state.savedId
        if (newId != null && onSaved != null) onSaved(newId) else onDone()
    }
    // The phone's back gesture goes back a card, and only closes from the first one.
    BackHandler(enabled = !state.loading && state.index > 0 && state.isNew) { viewModel.back() }

    if (state.loading) return
    // When editing, the final check is the starting point, so "back" from it closes.
    val editingReview = !state.isNew && state.step == Step.REVIEW
    StepFlow(
        title = if (state.isNew) "New Fill-Up" else "Edit Fill-Up",
        step = state.step,
        index = state.index,
        count = state.steps.size,
        direction = state.direction,
        onClose = onDone,
        onDelete = if (state.isNew) null else ({ confirmDelete = true }),
        backCloses = state.index == 0 || editingReview,
        onBack = { if (editingReview) onDone() else if (!viewModel.back()) onDone() },
        primaryText = when {
            state.step == Step.REVIEW && state.warnings.isNotEmpty() -> "Save Anyway"
            state.step == Step.REVIEW -> "Save"
            state.step == Step.FUEL_LEFT && state.form.barsLeft.isBlank() && state.form.reserveKm.isBlank() -> "Skip"
            !state.isNew -> "Done"
            else -> "Next"
        },
        primaryIsFinal = state.step == Step.REVIEW,
        onPrimary = {
            when {
                state.step == Step.REVIEW -> viewModel.save()
                // When editing, each card returns to the final check once it's answered.
                !state.isNew -> if (state.form.errorFor(state.step, null) == null) viewModel.goTo(Step.REVIEW) else viewModel.next()
                else -> viewModel.next()
            }
        },
    ) { step -> StepCard(step, state, viewModel) }

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
internal fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (filled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .height(58.dp)
            .clip(PillShape)
            .background(bg)
            .border(outline(if (filled) Color.Transparent else null), PillShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

/** The card for one step: a question, a big answer box and a little help underneath. */
@Composable
private fun StepCard(step: Step, state: EditState, vm: EditViewModel) {
    val form = state.form
    val mixed = form.country == Country.MIXED && !form.legacyMixed
    when (step) {
        Step.WHERE -> WhereCard(state, vm)
        Step.DATE -> DateCard("When did you fill up?", form.date, onPick = { d -> vm.edit { it.copy(date = d) } }, onNext = vm::next)
        Step.RM_PAID -> NumberCard(
            part = if (mixed) "Malaysia Part" else null,
            question = "How much did you pay?",
            hint = "The ringgit you paid at the counter or by card.",
            value = form.rmPaid, prefix = "RM", error = state.error,
            quick = state.commonRmPaid.map { "RM ${trim(it)}" to trim(it) },
            onChange = { v -> vm.edit { it.copy(rmPaid = v) } }, onNext = vm::next,
        )
        Step.RATE -> NumberCard(
            part = if (mixed) "Malaysia Part" else null,
            question = "What's the exchange rate?",
            hint = if (state.rateFromLast && state.isNew) "Your last rate is filled in. Change it if your bank's rate is different today."
            else "Ringgit per Singapore dollar, as your bank converted it.",
            value = form.rate, suffix = "RM per S$1", error = state.error,
            onChange = { v -> vm.edit { it.copy(rate = v) } }, onNext = vm::next,
            selectAll = true,
        )
        Step.RM_CHARGED -> NumberCard(
            part = if (mixed) "Malaysia Part" else null,
            question = "How much did the pump charge?",
            hint = state.preview.myCostSgd?.let { "That's ${sgd(it)}." } ?: "Read it off the pump once you're done.",
            value = form.rmCharged, prefix = "RM", error = state.error,
            quick = parseNumber(form.rmPaid)?.let { listOf("Same as Paid (RM ${trim(it)})" to trim(it)) }.orEmpty(),
            onChange = { v -> vm.edit { it.copy(rmCharged = v) } }, onNext = vm::next,
        )
        Step.MY_LITRES -> NumberCard(
            part = if (mixed) "Malaysia Part" else null,
            question = if (mixed) "How many litres in Malaysia?" else "How many litres did you pump?",
            hint = litreHint(parseNumber(form.rmCharged), state.preview.myCostSgd, parseNumber(form.myLitres), "RM"),
            value = form.myLitres, suffix = "L", error = state.error,
            onChange = { v -> vm.edit { it.copy(myLitres = v) } }, onNext = vm::next,
            extra = { FullTankToggle(step, state, vm) },
        )
        Step.LEGACY_MY_COST -> NumberCard(
            part = "Old Spreadsheet Fill-Up",
            question = "What did it cost?",
            hint = "This fill-up was recorded in SGD only.",
            value = form.legacyMyCost, prefix = "S$", error = state.error,
            onChange = { v -> vm.edit { it.copy(legacyMyCost = v) } }, onNext = vm::next,
        )
        Step.UNSPLIT_LITRES -> NumberCard(
            part = "Old Spreadsheet Fill-Up",
            question = "How many litres in total?",
            hint = "This mixed fill-up has one total for both countries.",
            value = form.unsplitLitres, suffix = "L", error = state.error,
            onChange = { v -> vm.edit { it.copy(unsplitLitres = v) } }, onNext = vm::next,
            extra = { FullTankToggle(step, state, vm) },
        )
        Step.UNSPLIT_COST -> NumberCard(
            part = "Old Spreadsheet Fill-Up",
            question = "What did it cost in total?",
            hint = "In SGD, for both countries together.",
            value = form.unsplitCost, prefix = "S$", error = state.error,
            onChange = { v -> vm.edit { it.copy(unsplitCost = v) } }, onNext = vm::next,
        )
        Step.SG_PAID -> NumberCard(
            part = if (mixed) "Singapore Part" else null,
            question = if (mixed) "How much did you pay in Singapore?" else "How much did you pay?",
            hint = sgPaidHint(parseNumber(form.sgCost), parseNumber(form.sgLitres)),
            value = form.sgCost, prefix = "S$", error = state.error,
            onChange = { v -> vm.edit { it.copy(sgCost = v) } }, onNext = vm::next,
        )
        Step.SG_LITRES -> NumberCard(
            part = if (mixed) "Singapore Part" else null,
            question = if (mixed) "How many litres in Singapore?" else "How many litres did you pump?",
            hint = "Read it off the pump.",
            value = form.sgLitres, suffix = "L", error = state.error,
            onChange = { v -> vm.edit { it.copy(sgLitres = v) } }, onNext = vm::next,
            extra = {
                FullTankToggle(step, state, vm)
                if (form.country == Country.SG) BrandMenu(form.brand) { b -> vm.edit { it.copy(brand = b) } }
            },
        )
        Step.ODOMETER -> NumberCard(
            question = "What's the odometer reading?",
            hint = state.previous?.let { prev ->
                "Last time: ${km(prev.odometerKm)} on ${prev.date.short()}" +
                    (state.preview.distanceKm?.takeIf { it > 0 }?.let { " · +${km(it)} since" } ?: "")
            } ?: "The total km on the bike.",
            value = form.odometer, suffix = "km", error = state.error, whole = true,
            onChange = { v -> vm.edit { it.copy(odometer = v) } }, onNext = vm::next,
        )
        Step.FUEL_LEFT -> FuelLeftCard(state, vm)
        Step.REVIEW -> ReviewCard(state, vm)
    }
}

/** Under "How much did you pay?" in Singapore: the price per litre once both are in. */
private fun sgPaidHint(cost: Double?, litres: Double?): String =
    if (cost != null && cost > 0 && litres != null && litres > 0) "That works out to ${sgdPerLitre(cost / litres)}."
    else "In Singapore dollars."

private fun trim(value: Double) = if (value == Math.rint(value)) value.toLong().toString() else value.toString()

private fun litreHint(localCost: Double?, sgdCost: Double?, litres: Double?, currency: String?): String {
    if (litres == null || litres <= 0) return "Read it off the pump."
    val parts = listOfNotNull(
        if (currency != null && localCost != null) "$currency ${String.format(Locale.ENGLISH, "%.2f", localCost / litres)}/L" else null,
        sgdCost?.let { sgdPerLitre(it / litres) },
    )
    return if (parts.isEmpty()) "Read it off the pump." else "That works out to ${parts.joinToString(" · ")}."
}

@Composable
internal fun QuestionCard(part: String?, question: String, content: @Composable ColumnScope.() -> Unit) {
    GroupCard(contentPadding = 22.dp, edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)) {
        part?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(6.dp))
        }
        Text(question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

/** A question with one big number box. The keyboard opens straight away and "next" moves on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NumberCard(
    question: String,
    hint: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    part: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    whole: Boolean = false,
    quick: List<Pair<String, String>> = emptyList(),
    selectAll: Boolean = false,
    /** A word answer (like a name) rather than a number. */
    text: Boolean = false,
    capitalization: KeyboardCapitalization = if (text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
    /** More under the hint, like buttons that fill in the answer. */
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    // Keep the cursor position while typing; a pre-filled rate starts fully selected so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(value, if (selectAll) TextRange(0, value.length) else TextRange(value.length))) }
    // A quick-pick button changes the answer from outside the box.
    LaunchedEffect(value) { if (field.text != value) field = TextFieldValue(value, TextRange(value.length)) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    QuestionCard(part, question) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it; onChange(it.text) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            textStyle = if (text) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium.copy(fontFamily = NumberFont, fontSize = 34.sp),
            prefix = prefix?.let { { Text("$it ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                cursorColor = MaterialTheme.colorScheme.tertiary,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    text -> KeyboardType.Text
                    whole -> KeyboardType.Number
                    else -> KeyboardType.Decimal
                },
                capitalization = capitalization,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            error ?: hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (quick.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { (label, amount) ->
                    QuickChip(label) { onChange(amount); onNext() }
                }
            }
        }
        extra()
    }
}

@Composable
internal fun QuickChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = NumberFont,
    )
}

/**
 * Big tiles for where you filled up; tapping one moves on. A partial fill covers what "Both"
 * used to, so only old fill-ups from both countries still show it, to keep their split.
 */
@Composable
private fun WhereCard(state: EditState, vm: EditViewModel) {
    val form = state.form
    QuestionCard(null, "Where did you fill up?") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOfNotNull(
                Triple(Country.SG, "Singapore", CountryColors.sg),
                Triple(Country.MY, "Malaysia", CountryColors.my),
                if (state.allowBoth) Triple(Country.MIXED, "Both", CountryColors.mixed) else null,
            ).forEach { (country, name, tint) ->
                ChoiceTile(
                    icon = Icons.Filled.LocalGasStation,
                    label = name,
                    sublabel = country.label(),
                    selected = form.country == country,
                    onClick = { vm.chooseCountry(country) },
                    tint = tint,
                    big = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * "Brand" under "Full Tank?" on a Singapore litres card: an optional pick from a menu of the main
 * brands, with the smaller ones under "Others" and "None" to clear it.
 */
@Composable
private fun BrandMenu(brand: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var others by remember { mutableStateOf(false) }
    Spacer(Modifier.height(10.dp))
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .border(outline(), RoundedCornerShape(18.dp))
                .clickable { others = false; open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Brand", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional. Which petrol station you used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                brand.ifBlank { "None" },
                style = MaterialTheme.typography.titleMedium,
                color = if (brand.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(b: String) {
                onPick(b)
                open = false
            }
            if (!others) {
                SG_MAIN_BRANDS.forEach { b -> BrandItem(b, b == brand) { pick(b) } }
                DropdownMenuItem(
                    text = { Text(if (brand in SG_OTHER_BRANDS) "Others ($brand)" else "Others") },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    onClick = { others = true },
                )
                HorizontalDivider()
                BrandItem("None", brand.isBlank()) { pick("") }
            } else {
                DropdownMenuItem(
                    text = { Text("Back") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) },
                    onClick = { others = false },
                )
                HorizontalDivider()
                SG_OTHER_BRANDS.forEach { b -> BrandItem(b, b == brand) { pick(b) } }
            }
        }
    }
}

@Composable
private fun BrandItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        trailingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = "Picked") }) else null,
        onClick = onClick,
    )
}

/**
 * "Full Tank?" under the litres answer, on the last card that asks for litres. Yes unless you
 * only put some fuel in; a partial fill's economy is worked out at the next full tank.
 */
@Composable
private fun FullTankToggle(step: Step, state: EditState, vm: EditViewModel) {
    if (fullTankStep(state.form) != step) return
    val full = state.form.fullTank
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(outline(), RoundedCornerShape(18.dp))
            .clickable { vm.edit { it.copy(fullTank = !full) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Full Tank?", style = MaterialTheme.typography.titleMedium)
            Text(
                if (full) "Filled right up." else "Partial fill. Economy is worked out at your next full tank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = full, onCheckedChange = { on -> vm.edit { it.copy(fullTank = on) } })
    }
}

/** Optional: tap how many bars were left, or type km ridden on the fuel light. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelLeftCard(state: EditState, vm: EditViewModel) {
    val form = state.form
    QuestionCard(null, "How much fuel was left?") {
        Text("Bars on the Gauge", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { bars ->
                val selected = form.barsLeft == bars.toString()
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(outline(), RoundedCornerShape(14.dp))
                        .clickable { vm.edit { it.copy(barsLeft = if (selected) "" else bars.toString(), reserveKm = "") } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        bars.toString(),
                        fontFamily = NumberFont,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Or km Ridden on the Fuel Light", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = form.reserveKm,
            onValueChange = { v -> vm.edit { it.copy(reserveKm = v, barsLeft = if (v.isNotBlank()) "" else it.barsLeft) } },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = NumberFont),
            suffix = { Text("km") },
            singleLine = true,
            isError = state.error != null,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.next() }),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            state.error ?: "Optional. Skip if you didn't check.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Last card: every answer (tap one to change it), what it works out to, and any warnings. */
@Composable
private fun ReviewCard(state: EditState, vm: EditViewModel) {
    val form = state.form
    val p = state.preview
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // What it works out to, as three small tiles.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile("Cost", p.totalCostSgd, ::sgd, Modifier.weight(1f))
            ResultTile("Price", p.pricePerLitre, ::sgdPerLitre, Modifier.weight(1f))
            ResultTile("Economy", p.kmPerLitre, ::kmPerLitre, Modifier.weight(1f))
        }
        val economyNote = when {
            !form.fullTank -> "Partial fill: fuel economy and cost per km will be worked out at your next full tank."
            p.partialsIncluded == 1 -> "Economy includes 1 partial fill since your last full tank."
            p.partialsIncluded > 1 -> "Economy includes ${p.partialsIncluded} partial fills since your last full tank."
            else -> null
        }
        economyNote?.let {
            Text(it, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.warnings.isNotEmpty()) {
            GroupCard(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                edge = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                contentPadding = 16.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text("Does this look right?", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                state.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        GroupCard {
            val fullStep = fullTankStep(form)
            val rows = buildList {
                state.steps.forEach { step ->
                    answer(step, form)?.let { add(Triple(step, label(step, form), it)) }
                    if (step == fullStep) add(Triple(step, "Full Tank", if (form.fullTank) "Yes" else "No"))
                    if (step == Step.SG_LITRES && form.country == Country.SG) add(Triple(step, "Brand", form.brand.ifBlank { "Not recorded" }))
                }
            }
            rows.forEachIndexed { i, (step, label, value) ->
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { vm.goTo(step) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontFamily = NumberFont, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (form.country != Country.SG) {
            GroupCard {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("No Special Trip to MY", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Already in Malaysia, so no trip cost is taken off the savings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.noSpecialTrip, onCheckedChange = { on -> vm.edit { it.copy(noSpecialTrip = on) } })
                }
            }
        }
    }
}

@Composable
private fun ResultTile(label: String, value: Double?, format: (Double) -> String, modifier: Modifier) {
    Column(
        modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(outline(), TileShape)
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            Text("—", style = MaterialTheme.typography.titleSmall, fontFamily = NumberFont)
        } else {
            CountUpText(value, format, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun label(step: Step, form: FillUpForm): String {
    val mixed = form.country == Country.MIXED && !form.legacyMixed
    return when (step) {
        Step.RM_PAID -> "RM Paid"
        Step.RATE -> "Rate"
        Step.RM_CHARGED -> "Pump Charged"
        Step.MY_LITRES -> if (mixed) "Litres in MY" else "Litres"
        Step.LEGACY_MY_COST -> "Cost"
        Step.UNSPLIT_LITRES -> "Total Litres"
        Step.UNSPLIT_COST -> "Total Cost"
        Step.SG_PAID -> if (mixed) "Paid in SG" else "Paid"
        Step.SG_LITRES -> if (mixed) "Litres in SG" else "Litres"
        Step.ODOMETER -> "Odometer"
        Step.FUEL_LEFT -> "Fuel Left"
        Step.DATE -> "Date"
        Step.WHERE -> "Where"
        Step.REVIEW -> ""
    }
}

private fun answer(step: Step, form: FillUpForm): String? {
    fun or(text: String, show: (String) -> String) = if (text.isBlank()) "—" else show(text)
    return when (step) {
        Step.RM_PAID -> or(form.rmPaid) { "RM $it" }
        Step.RATE -> or(form.rate) { it }
        Step.RM_CHARGED -> or(form.rmCharged) { "RM $it" }
        Step.MY_LITRES -> or(form.myLitres) { "$it L" }
        Step.LEGACY_MY_COST -> or(form.legacyMyCost) { "S$$it" }
        Step.UNSPLIT_LITRES -> or(form.unsplitLitres) { "$it L" }
        Step.UNSPLIT_COST -> or(form.unsplitCost) { "S$$it" }
        Step.SG_PAID -> or(form.sgCost) { "S$$it" }
        Step.SG_LITRES -> or(form.sgLitres) { "$it L" }
        Step.ODOMETER -> or(form.odometer) { "$it km" }
        Step.FUEL_LEFT -> when {
            form.barsLeft.isNotBlank() -> "${form.barsLeft} bars"
            form.reserveKm.isNotBlank() -> "${form.reserveKm} km on reserve"
            else -> "Not recorded"
        }
        Step.DATE -> form.date.long()
        Step.WHERE -> form.country.label()
        Step.REVIEW -> null
    }
}

