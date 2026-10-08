package com.astheral.bikedashboard.ui.details

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.BikeDetails
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PickDateDialog
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.components.TextFieldDialog
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Details shown for the bike, or null while loading. */
data class DetailsState(val profile: Profile, val details: BikeDetails)

class BikeDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).bikeDetailsDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DetailsState?> = Profiles.get(application).active.flatMapLatest { profile ->
        if (profile == null) flowOf(null)
        else dao.observe(profile.id).map { DetailsState(profile, it ?: BikeDetails(profileId = profile.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (BikeDetails) -> BikeDetails) {
        val current = state.value?.details ?: return
        viewModelScope.launch { dao.save(change(current)) }
    }
}

/** How a field is typed in and shown. */
private enum class Kind { TEXT, MONEY, KM, DATE, LONG_TEXT }

private class DetailField(
    val label: String,
    val icon: ImageVector,
    val kind: Kind,
    val get: (BikeDetails) -> Any?,
    val set: (BikeDetails, Any?) -> BikeDetails,
)

private fun text(label: String, icon: ImageVector, get: (BikeDetails) -> String?, set: BikeDetails.(String?) -> BikeDetails) =
    DetailField(label, icon, Kind.TEXT, get) { d, v -> d.set(v as String?) }

private fun money(label: String, icon: ImageVector, get: (BikeDetails) -> Double?, set: BikeDetails.(Double?) -> BikeDetails) =
    DetailField(label, icon, Kind.MONEY, get) { d, v -> d.set(v as Double?) }

private fun date(label: String, icon: ImageVector, get: (BikeDetails) -> LocalDate?, set: BikeDetails.(LocalDate?) -> BikeDetails) =
    DetailField(label, icon, Kind.DATE, get) { d, v -> d.set(v as LocalDate?) }

/** The groups of fields, following the spreadsheet's dashboard. */
private val SECTIONS: List<Triple<String, ImageVector, List<DetailField>>> = listOf(
    Triple(
        "Bike", Icons.Filled.TwoWheeler,
        listOf(
            text("Brand", Icons.Filled.Factory, { it.brand }) { copy(brand = it) },
            text("Model", Icons.Filled.Category, { it.model }) { copy(model = it) },
            text("Licence plate", Icons.Filled.Pin, { it.plate }) { copy(plate = it?.uppercase()) },
            text("Chassis number", Icons.Filled.Build, { it.chassisNumber }) { copy(chassisNumber = it?.uppercase()) },
            text("Engine number", Icons.Filled.Speed, { it.engineNumber }) { copy(engineNumber = it?.uppercase()) },
            text("IU number (current)", Icons.Filled.Contactless, { it.iuNumber }) { copy(iuNumber = it) },
            text("Registered IU", Icons.Filled.Badge, { it.registeredIu }) { copy(registeredIu = it) },
        ),
    ),
    Triple(
        "Value", Icons.Filled.Savings,
        listOf(
            money("Amount paid", Icons.Filled.Payments, { it.purchasePrice }) { copy(purchasePrice = it) },
            money("COE paid", Icons.Filled.Payments, { it.coePaid }) { copy(coePaid = it) },
            money("Open market value", Icons.Filled.Payments, { it.openMarketValue }) { copy(openMarketValue = it) },
            DetailField("Odometer when you got it", Icons.Filled.Speed, Kind.KM, { it.startOdometerKm }) { d, v ->
                d.copy(startOdometerKm = (v as Double?)?.roundToInt())
            },
        ),
    ),
    Triple(
        "Legal", Icons.Filled.Gavel,
        listOf(
            date("COE expiry", Icons.Filled.CalendarMonth, { it.coeExpiry }) { copy(coeExpiry = it) },
            text("COE / registration ID", Icons.Filled.Description, { it.coeNumber }) { copy(coeNumber = it) },
            date("Road tax expiry", Icons.Filled.CalendarMonth, { it.roadTaxExpiry }) { copy(roadTaxExpiry = it) },
            date("Inspection due", Icons.Filled.VerifiedUser, { it.inspectionExpiry }) { copy(inspectionExpiry = it) },
            date("Insurance expiry", Icons.Filled.Security, { it.insuranceExpiry }) { copy(insuranceExpiry = it) },
            text("Insurance policy number", Icons.Filled.Policy, { it.insurancePolicy }) { copy(insurancePolicy = it) },
        ),
    ),
    Triple(
        "Notes", Icons.AutoMirrored.Filled.Notes,
        listOf(DetailField("Notes", Icons.AutoMirrored.Filled.Notes, Kind.LONG_TEXT, { it.notes }) { d, v -> d.copy(notes = v as String?) }),
    ),
)

private fun trim(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

private fun shown(value: Any?): String? = when (value) {
    null -> null
    is LocalDate -> value.short()
    is Double -> sgd(value)
    is Int -> km(value)
    else -> value.toString()
}

/** "in 299 days", "today" or "3 days ago", for expiry dates. */
fun dueIn(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days > 0 -> "in $days days"
        days == -1L -> "yesterday"
        else -> "${-days} days ago"
    }
}

/** Expiry dates closer than this are shown in the warning colour. */
private const val SOON_DAYS = 30

/**
 * The bike's paperwork: plate, chassis and engine numbers, value, and when the COE, road tax,
 * inspection and insurance run out. Tap any row to fill it in.
 */
@Composable
fun BikeDetailsScreen(onBack: () -> Unit, viewModel: BikeDetailsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val s = state ?: return
    var editing by remember { mutableStateOf<DetailField?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Bike details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(s.profile.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            s.details.plate?.let { AccentChip(it) }
        }
        SECTIONS.forEach { (title, icon, fields) ->
            SectionHeader(icon, title)
            GroupCard {
                fields.forEachIndexed { i, field ->
                    if (i > 0) GroupDivider()
                    DetailRow(field, field.get(s.details)) { editing = field }
                }
            }
        }
    }

    editing?.let { field ->
        val current = field.get(s.details)
        val close = { editing = null }
        val save = { v: Any? -> viewModel.update { field.set(it, v) }; editing = null }
        when (field.kind) {
            Kind.DATE -> PickDateDialog(current as LocalDate?, onDismiss = close, onPick = save)
            Kind.MONEY, Kind.KM -> TextFieldDialog(
                title = field.label,
                initial = when (current) {
                    is Double -> trim(current)
                    is Int -> current.toString()
                    else -> ""
                },
                onDismiss = close,
                onSave = { t -> save(parseNumber(t)) },
                number = true,
                suffix = if (field.kind == Kind.KM) "km" else "S$",
                validate = { t -> if (parseNumber(t)?.takeIf { it >= 0 } == null) "Enter a number" else null },
            )
            Kind.TEXT, Kind.LONG_TEXT -> TextFieldDialog(
                title = field.label,
                initial = current as String? ?: "",
                onDismiss = close,
                onSave = { t -> save(t.ifBlank { null }) },
                singleLine = field.kind == Kind.TEXT,
            )
        }
    }
}

/** The field's name with its value underneath; dates also say how long until they run out. */
@Composable
private fun DetailRow(field: DetailField, value: Any?, onClick: () -> Unit) {
    SettingRow(
        icon = field.icon,
        title = field.label,
        subtitle = shown(value) ?: "Tap to add",
        onClick = onClick,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        if (value is LocalDate) {
            val soon = ChronoUnit.DAYS.between(LocalDate.now(), value) < SOON_DAYS
            Text(
                dueIn(value),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = NumberFont,
                color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
