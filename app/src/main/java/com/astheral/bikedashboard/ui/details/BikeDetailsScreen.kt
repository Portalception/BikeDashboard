package com.astheral.bikedashboard.ui.details

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.Color
import com.astheral.bikedashboard.calc.isExpired
import com.astheral.bikedashboard.logcard.LogCard
import com.astheral.bikedashboard.logcard.LogCardIssue
import com.astheral.bikedashboard.logcard.LogCardParser
import com.astheral.bikedashboard.logcard.LogCardPdf
import com.astheral.bikedashboard.logcard.issues
import com.astheral.bikedashboard.logcard.logCardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.LegalItem
import com.astheral.bikedashboard.calc.ReminderSchedule
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.BikeDetails
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.reminders.Reminders
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PickDateDialog
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.components.TextFieldDialog
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.Dispatchers
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

/** Details shown for the bike, or null while loading. */
data class DetailsState(val profile: Profile, val details: BikeDetails)

private const val NOT_A_LOG_CARD =
    "This doesn't look like an LTA Vehicle Log Card. Download the log card PDF from OneMotoring and try again " +
        "(a photo or scan of the card can't be read)."

/** Importing the LTA Vehicle Log Card PDF, step by step. */
sealed interface CardImport {
    data object Reading : CardImport
    data class Failed(val message: String) : CardImport

    /** Read: [pending] are the questions about odd dates still to answer before the preview. */
    data class Review(val card: LogCard, val pending: List<LogCardIssue>, val useColours: Boolean = true) : CardImport
}

class BikeDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).bikeDetailsDao()
    private val settings = Settings.get(application)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DetailsState?> = Profiles.get(application).active.flatMapLatest { profile ->
        if (profile == null) flowOf(null)
        else dao.observe(profile.id).map { DetailsState(profile, it ?: BikeDetails(profileId = profile.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Which expiries to be reminded about, for every bike. */
    val paperworkReminders: StateFlow<Map<Long, Set<LegalItem>>> = settings.paperworkReminders
    val leadDays: StateFlow<Int> = settings.paperworkLeadDays
    val reminderSchedule: StateFlow<ReminderSchedule> = settings.reminderSchedule

    fun update(change: (BikeDetails) -> BikeDetails) {
        val current = state.value?.details ?: return
        viewModelScope.launch {
            dao.save(change(current))
            recheck()
        }
    }

    fun setReminder(item: LegalItem, on: Boolean) {
        val profileId = state.value?.profile?.id ?: return
        settings.setPaperworkReminder(profileId, item, on)
        recheck()
    }

    fun setLeadDays(days: Int) {
        settings.setPaperworkLeadDays(days)
        recheck()
    }

    private val _cardImport = MutableStateFlow<CardImport?>(null)

    /** Where an import of the LTA log card PDF is up to, or null when there isn't one. */
    val cardImport: StateFlow<CardImport?> = _cardImport.asStateFlow()

    /** Reads the picked PDF and, if it's a log card, shows what it'll fill in (asking about odd dates first). */
    fun logCardChosen(uri: Uri) {
        _cardImport.value = CardImport.Reading
        viewModelScope.launch(Dispatchers.IO) {
            _cardImport.value = try {
                val card = LogCardParser.parse(LogCardPdf.text(getApplication(), uri))
                if (card.isEmpty) CardImport.Failed(NOT_A_LOG_CARD)
                else CardImport.Review(card, card.issues(LocalDate.now()))
            } catch (e: Exception) {
                CardImport.Failed("Couldn't read that PDF (${e.message ?: e.javaClass.simpleName}). Make sure it's the log card downloaded from OneMotoring.")
            }
        }
    }

    /** Answers the first open question about a date: [date] is the one typed in, or null to keep what the card says. */
    fun resolveIssue(date: LocalDate?) {
        val review = _cardImport.value as? CardImport.Review ?: return
        val issue = review.pending.firstOrNull() ?: return
        val card = when {
            date != null -> review.card.withDate(issue.date, date)
            // A date that couldn't be read is left out, so what's saved now stays.
            issue.kind == LogCardIssue.Kind.UNREADABLE -> review.card.withDate(issue.date, null)
            else -> review.card
        }
        _cardImport.value = review.copy(card = card, pending = review.pending.drop(1))
    }

    fun setUseCardColours(on: Boolean) {
        val review = _cardImport.value as? CardImport.Review ?: return
        _cardImport.value = review.copy(useColours = on)
    }

    fun applyCard() {
        val review = _cardImport.value as? CardImport.Review ?: return
        _cardImport.value = null
        update { review.card.applyTo(it) }
        if (review.useColours) {
            val profileId = state.value?.profile?.id
            logCardTheme(review.card.primaryColour, review.card.secondaryColour)?.let { (primary, accent) ->
                // Only this bike's look changes; each bike keeps its own.
                if (profileId != null) settings.updateAppearance(profileId) { it.copy(primary = Color(primary), accent = Color(accent), wallpaper = false) }
            }
        }
    }

    fun cancelCard() {
        _cardImport.value = null
    }

    /** Checks straight away, so an expiry that's already close shows up now rather than at the next check. */
    private fun recheck() {
        viewModelScope.launch(Dispatchers.IO) { Reminders.check(getApplication()) }
    }
}

/** How a field is typed in. */
private enum class Kind { TEXT, LONG_TEXT, NUMBER, DATE }

private class DetailField(
    val label: String,
    val icon: ImageVector,
    val kind: Kind,
    val get: (BikeDetails) -> String?,
    val set: (BikeDetails, String?) -> BikeDetails,
    val suffix: String? = null,
    /** For [Kind.DATE] fields: the date itself and how to change it. */
    val getDate: (BikeDetails) -> LocalDate? = { null },
    val setDate: (BikeDetails, LocalDate?) -> BikeDetails = { d, _ -> d },
)

private fun text(label: String, icon: ImageVector, get: (BikeDetails) -> String?, set: BikeDetails.(String?) -> BikeDetails) =
    DetailField(label, icon, Kind.TEXT, get, set = { d, v -> d.set(v) })

/** A whole number; [get] returns it without the suffix, the row shows it with. */
private fun number(label: String, icon: ImageVector, suffix: String?, get: (BikeDetails) -> Int?, set: BikeDetails.(Int?) -> BikeDetails) =
    DetailField(label, icon, Kind.NUMBER, { get(it)?.toString() }, { d, v -> d.set(v?.trim()?.toIntOrNull()) }, suffix = suffix)

private fun date(label: String, icon: ImageVector, get: (BikeDetails) -> LocalDate?, set: BikeDetails.(LocalDate?) -> BikeDetails) =
    DetailField(label, icon, Kind.DATE, { get(it)?.short() }, { d, _ -> d }, getDate = get, setDate = { d, v -> d.set(v) })

/**
 * What the bike is, following the spreadsheet's dashboard. The current IU number and the
 * "Value" section (amount paid, COE paid, open market value and the odometer when you got the
 * bike) are no longer shown, but [BikeDetails] still keeps them, so older data and backups load
 * as before.
 */
private val BIKE_FIELDS = listOf(
    text("Brand", Icons.Filled.Factory, { it.brand }) { copy(brand = it) },
    text("Model", Icons.Filled.Category, { it.model }) { copy(model = it) },
    text("Licence plate", Icons.Filled.Pin, { it.plate }) { copy(plate = it?.uppercase()) },
    text("Chassis number", Icons.Filled.Build, { it.chassisNumber }) { copy(chassisNumber = it?.uppercase()) },
    text("Engine number", Icons.Filled.Speed, { it.engineNumber }) { copy(engineNumber = it?.uppercase()) },
    text("Registered IU", Icons.Filled.Badge, { it.registeredIu }) { copy(registeredIu = it) },
    date("Registration date", Icons.Filled.Event, { it.registrationDate }) { copy(registrationDate = it) },
    number("Year of manufacture", Icons.Filled.History, null, { it.manufactureYear }) { copy(manufactureYear = it) },
    number("Engine capacity", Icons.Filled.Bolt, "cc", { it.engineCc }) { copy(engineCc = it) },
    text("Colour", Icons.Filled.Palette, { it.colour }) { copy(colour = it) },
)

private val NOTES = DetailField("Notes", Icons.AutoMirrored.Filled.Notes, Kind.LONG_TEXT, { it.notes }, set = { d, v -> d.copy(notes = v) })

private fun LegalItem.icon(): ImageVector = when (this) {
    LegalItem.COE -> Icons.Filled.Description
    LegalItem.INSURANCE -> Icons.Filled.Security
    LegalItem.ROAD_TAX -> Icons.Filled.CalendarMonth
    LegalItem.INSPECTION -> Icons.Filled.VerifiedUser
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

/** The longest lead time offered for paperwork reminders. */
private const val MAX_LEAD_DAYS = 365

/**
 * The bike's paperwork: plate, chassis and engine numbers, and when the COE, road tax,
 * inspection and insurance run out. Each legal item is one row with its number and expiry
 * together; tap any row to fill it in. Below them, switches for being reminded before each
 * one expires.
 */
@Composable
fun BikeDetailsScreen(onBack: () -> Unit, viewModel: BikeDetailsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val reminders by viewModel.paperworkReminders.collectAsState()
    val leadDays by viewModel.leadDays.collectAsState()
    val schedule by viewModel.reminderSchedule.collectAsState()
    val s = state ?: return
    var editing by remember { mutableStateOf<DetailField?>(null) }
    var editingLegal by remember { mutableStateOf<LegalItem?>(null) }
    var editingLead by remember { mutableStateOf(false) }
    val remindOn = reminders[s.profile.id].orEmpty()
    val context = LocalContext.current
    val cardImport by viewModel.cardImport.collectAsState()
    val pickCard = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.logCardChosen(uri)
    }
    val choosePdf = { pickCard.launch(arrayOf("application/pdf")) }
    val today = LocalDate.now()

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

        Spacer(Modifier.height(12.dp))
        GroupCard(edge = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)) {
            SettingRow(
                icon = Icons.Filled.UploadFile,
                title = "Import log card",
                subtitle = "Fill these in from your LTA Vehicle Log Card PDF",
                onClick = choosePdf,
                iconTint = MaterialTheme.colorScheme.tertiary,
            )
        }

        SectionHeader(Icons.Filled.TwoWheeler, "Bike")
        GroupCard {
            BIKE_FIELDS.forEachIndexed { i, field ->
                if (i > 0) GroupDivider()
                DetailRow(field, field.get(s.details)?.let { v -> field.suffix?.let { "$v $it" } ?: v }) { editing = field }
            }
        }

        SectionHeader(Icons.Filled.Gavel, "Legal")
        GroupCard {
            LegalItem.entries.forEachIndexed { i, item ->
                if (i > 0) GroupDivider()
                LegalRow(item, s.details) { editingLegal = item }
            }
        }

        SectionHeader(Icons.Filled.Notifications, "Expiry reminders")
        GroupCard {
            LegalItem.entries.forEach { item ->
                val expiry = item.expiry(s.details)
                SettingRow(
                    icon = item.icon(),
                    title = item.label,
                    subtitle = when {
                        expiry == null -> "Add the expiry date above first"
                        isExpired(expiry, today) -> "Expired ${expiry.short()}"
                        else -> "Expires ${expiry.short()}"
                    },
                    onClick = { viewModel.setReminder(item, item !in remindOn) },
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Switch(checked = item in remindOn, onCheckedChange = { viewModel.setReminder(item, it) })
                }
                GroupDivider()
            }
            SettingRow(
                icon = Icons.Filled.Timer,
                title = "Start reminding me",
                subtitle = if (leadDays == 1) "1 day before it expires" else "$leadDays days before it expires",
                onClick = { editingLead = true },
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (remindOn.isNotEmpty() && (!schedule.on || !Reminders.allowed(context))) {
                Text(
                    "Reminders are off. Turn them on in Settings → Reminders to get these.",
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionHeader(Icons.AutoMirrored.Filled.Notes, "Notes")
        GroupCard { DetailRow(NOTES, NOTES.get(s.details)) { editing = NOTES } }
    }

    editing?.let { field ->
        if (field.kind == Kind.DATE) {
            PickDateDialog(
                field.getDate(s.details),
                onDismiss = { editing = null },
                onPick = { d -> viewModel.update { field.setDate(it, d) }; editing = null },
            )
        } else {
            TextFieldDialog(
                title = field.label,
                initial = field.get(s.details) ?: "",
                onDismiss = { editing = null },
                onSave = { t -> viewModel.update { field.set(it, t.ifBlank { null }) }; editing = null },
                number = field.kind == Kind.NUMBER,
                suffix = field.suffix,
                singleLine = field.kind != Kind.LONG_TEXT,
                validate = { t -> if (field.kind == Kind.NUMBER && t.trim().toIntOrNull() == null) "Enter a whole number" else null },
            )
        }
    }
    when (val c = cardImport) {
        null -> {}
        CardImport.Reading -> ReadingDialog()
        is CardImport.Failed -> AlertDialog(
            onDismissRequest = viewModel::cancelCard,
            title = { Text("Couldn't import") },
            text = { Text(c.message) },
            confirmButton = { TextButton(onClick = { viewModel.cancelCard(); choosePdf() }) { Text("Choose another") } },
            dismissButton = { TextButton(onClick = viewModel::cancelCard) { Text("Close") } },
        )
        is CardImport.Review -> {
            val issue = c.pending.firstOrNull()
            if (issue != null) {
                CardIssueDialog(
                    issue = issue,
                    current = c.card.date(issue.date),
                    onEnter = viewModel::resolveIssue,
                    onUpload = choosePdf,
                    onKeep = { viewModel.resolveIssue(null) },
                    onCancel = viewModel::cancelCard,
                )
            } else {
                CardPreviewDialog(
                    review = c,
                    current = s.details,
                    onUseColours = viewModel::setUseCardColours,
                    onApply = viewModel::applyCard,
                    onCancel = viewModel::cancelCard,
                )
            }
        }
    }
    editingLegal?.let { item ->
        LegalDialog(
            item = item,
            details = s.details,
            onDismiss = { editingLegal = null },
            onSave = { number, expiry -> viewModel.update { item.with(it, number, expiry) }; editingLegal = null },
        )
    }
    if (editingLead) {
        TextFieldDialog(
            title = "Start reminding me",
            initial = leadDays.toString(),
            onDismiss = { editingLead = false },
            onSave = { t -> t.toIntOrNull()?.let(viewModel::setLeadDays); editingLead = false },
            number = true,
            suffix = "days before",
            validate = { t -> if (t.toIntOrNull()?.takeIf { it in 1..MAX_LEAD_DAYS } == null) "Enter 1 to $MAX_LEAD_DAYS days" else null },
        )
    }
}

/** The field's name with its value underneath. */
@Composable
private fun DetailRow(field: DetailField, value: String?, onClick: () -> Unit) {
    SettingRow(
        icon = field.icon,
        title = field.label,
        subtitle = value ?: "Tap to add",
        onClick = onClick,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One paperwork item: its number (if it has one) and expiry together, and how long is left. */
@Composable
private fun LegalRow(item: LegalItem, details: BikeDetails, onClick: () -> Unit) {
    val expiry = item.expiry(details)
    val number = item.number(details)
    val today = LocalDate.now()
    val expired = isExpired(expiry, today)
    val subtitle = listOfNotNull(number, expiry?.let { (if (expired) "Expired " else "Expires ") + it.short() })
        .joinToString(" · ").ifEmpty { "Tap to add" }
    SettingRow(
        icon = item.icon(),
        title = item.label,
        subtitle = subtitle,
        onClick = onClick,
        iconTint = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        if (expired) {
            ExpiredChip()
            Spacer(Modifier.width(10.dp))
        } else if (expiry != null) {
            val soon = ChronoUnit.DAYS.between(LocalDate.now(), expiry) < SOON_DAYS
            Text(
                dueIn(expiry),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = NumberFont,
                color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Edits a paperwork item's number and expiry date in one go. */
@Composable
private fun LegalDialog(item: LegalItem, details: BikeDetails, onDismiss: () -> Unit, onSave: (String?, LocalDate?) -> Unit) {
    var number by remember { mutableStateOf(item.number(details).orEmpty()) }
    var expiry by remember { mutableStateOf(item.expiry(details)) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.numberLabel?.let { label ->
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(label) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Expiry date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(expiry?.let { "${it.short()} · ${dueIn(it)}" } ?: "Pick a date", fontFamily = NumberFont)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(number.trim().ifBlank { null }, expiry) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        PickDateDialog(expiry, onDismiss = { picking = false }, onPick = { expiry = it; picking = false })
    }
}
