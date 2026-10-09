package com.astheral.bikedashboard.ui.settings

import android.Manifest
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import android.app.Application
import android.os.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.astheral.bikedashboard.diagnostics.CrashLogs
import com.astheral.bikedashboard.calc.MAX_EVERY_DAYS
import com.astheral.bikedashboard.calc.ReminderSchedule
import com.astheral.bikedashboard.calc.RepeatKind
import com.astheral.bikedashboard.calc.ordinal
import com.astheral.bikedashboard.calc.timeLabel
import com.astheral.bikedashboard.reminders.Reminders
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.components.ChoiceTile
import com.astheral.bikedashboard.ui.components.NumberFont
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.csv.writeBackup
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.BackupJson
import com.astheral.bikedashboard.data.Backups
import com.astheral.bikedashboard.data.FullBackup
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.SectionHeader
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.edit.parseNumber
import com.astheral.bikedashboard.ui.profile.ProfileTitle
import com.astheral.bikedashboard.ui.profile.icon
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings.get(application)
    private val dao = AppDatabase.get(application).fillUpDao()
    private val profiles = Profiles.get(application)

    val profile: StateFlow<Profile?> = profiles.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ideasUnlocked: StateFlow<Boolean> = settings.ideasUnlocked

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    val reminderSchedule: StateFlow<ReminderSchedule> = settings.reminderSchedule

    fun setReminderSchedule(change: (ReminderSchedule) -> ReminderSchedule) {
        settings.setReminderSchedule(change(reminderSchedule.value))
        Reminders.schedule(getApplication())
        // Check straight away, so something already due shows up now rather than on the next reminder day.
        viewModelScope.launch(Dispatchers.IO) { Reminders.check(getApplication()) }
    }

    /** The trip into Malaysia, the same for every bike. */
    val tripKm: StateFlow<Double> = profiles.tripKm.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_TRIP_KM)

    fun setTripKm(km: Double) = settings.setTripKm(km)

    fun unlockIdeas() = settings.unlockIdeas()

    /** Saves everything (all bikes, settings and the ideas notebook) to one file. */
    fun exportAll(uri: Uri, appVersion: String) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val backup = Backups(getApplication()).create(appVersion)
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("couldn't open the file")
                    out.bufferedWriter(Charsets.UTF_8).use { it.write(BackupJson.encodeToString(FullBackup.serializer(), backup)) }
                }
                val fills = backup.bikes.sumOf { it.fillUps.size }
                "Backed up ${backup.bikes.size} ${if (backup.bikes.size == 1) "bike" else "bikes"}, $fills fill-ups and your settings."
            } catch (e: IOException) {
                CrashLogs.error("Saving a full backup", e)
                "Backup failed: ${e.message}"
            } catch (e: SecurityException) {
                CrashLogs.error("Saving a full backup", e)
                "Backup failed: ${e.message}"
            }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val fillUps = profiles.activeNow()?.let { dao.getAll(it.id) }.orEmpty()
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("couldn't open the file")
                    out.bufferedWriter(Charsets.UTF_8).use { it.write(writeBackup(fillUps)) }
                }
                "Exported ${count(fillUps.size, "fill-up")}."
            } catch (e: IOException) {
                CrashLogs.error("Exporting fill-ups to CSV", e)
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                CrashLogs.error("Exporting fill-ups to CSV", e)
                "Export failed: ${e.message}"
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onImport: () -> Unit,
    onProfileClick: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onOpenIdeas: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenPersonalisation: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenServicing: () -> Unit,
    onOpenIntervals: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val ideasUnlocked by viewModel.ideasUnlocked.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val tripKm by viewModel.tripKm.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val version = appVersion()
    val backupTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportAll(uri, version)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 16.dp)) {
            ProfileTitle(profile, onProfileClick)
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
        }

        SectionHeader(Icons.Filled.TwoWheeler, "Bike")
        GroupCard {
            profile?.let { p ->
                SettingRow(
                    p.kind.icon(),
                    p.name,
                    "${p.kind.label} · ${trimKm(p.tankLitres)} L tank",
                    onClick = { onEditProfile(p.id) },
                ) { Chevron() }
                GroupDivider()
                SettingRow(
                    Icons.Filled.Description,
                    "Bike details",
                    "Plate, chassis and engine numbers, COE, road tax and insurance",
                    onClick = onOpenDetails,
                ) { Chevron() }
                GroupDivider()
                SettingRow(
                    Icons.Filled.Build,
                    "Servicing",
                    "Services, oil changes, repairs and washes for this bike",
                    onClick = onOpenServicing,
                ) { Chevron() }
                GroupDivider()
                SettingRow(
                    Icons.Filled.Timer,
                    "Service intervals",
                    "When oil changes and services are due, and when to start reminding you",
                    onClick = onOpenIntervals,
                ) { Chevron() }
                GroupDivider()
            }
            SettingRow(Icons.Filled.SwapHoriz, "Switch or add a bike", "Your other bikes, or a rental", onClick = onProfileClick) { Chevron() }
        }

        SectionHeader(Icons.Filled.Palette, "Personalisation")
        GroupCard {
            SettingRow(
                Icons.Filled.Palette,
                "Personalisation",
                "Theme, colours, progress line and units",
                onClick = onOpenPersonalisation,
            ) { Chevron() }
        }

        SectionHeader(Icons.Filled.Notifications, "Reminders")
        RemindersCard(viewModel)

        SectionHeader(Icons.Filled.Route, "Savings offset")
        GroupCard(contentPadding = 16.dp) { TripKmField(tripKm, viewModel::setTripKm) }

        SectionHeader(Icons.Filled.Save, "Backup")
        GroupCard {
            SettingRow(
                Icons.Filled.Save,
                "Back up everything",
                exportMessage ?: "Every bike, fill-up and detail, your personalisation and the ideas notebook, in one file",
                onClick = { backupTo.launch("bike-dashboard-backup-${LocalDate.now()}.json") },
            ) { Chevron() }
            GroupDivider()
            SettingRow(
                Icons.Filled.Upload,
                "Export fill-ups as CSV",
                "${profile?.name ?: "This bike"}'s fill-ups, for a spreadsheet",
                onClick = { exportTo.launch("bike-dashboard-${fileSafe(profile?.name)}-${LocalDate.now()}.csv") },
            ) { Chevron() }
            GroupDivider()
            SettingRow(
                Icons.Filled.Download,
                "Import or restore",
                "Restore a full backup, or load fill-ups from a CSV or your old spreadsheet",
                onClick = onImport,
            ) { Chevron() }
        }

        SectionHeader(Icons.Filled.Info, "About")
        GroupCard { AboutCard(ideasUnlocked, onFound = viewModel::unlockIdeas, onOpenIdeas = onOpenIdeas, onOpenLogs = onOpenLogs) }
    }
}

/**
 * When reminders arrive: off, every few days, on some weekdays or on a day of the month, at any
 * time of day. On those days every service or paperwork item that's due gets a notification.
 * Android 13+ asks for permission to show notifications the first time they're turned on.
 */
@Composable
private fun RemindersCard(viewModel: SettingsViewModel) {
    val schedule by viewModel.reminderSchedule.collectAsState()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Reminders.allowed(context)) }
    var refused by remember { mutableStateOf(false) }
    var wanted by remember { mutableStateOf<RepeatKind?>(null) }
    var pickingTime by remember { mutableStateOf(false) }
    val pick = { kind: RepeatKind -> viewModel.setReminderSchedule { it.withKind(kind) } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        wanted?.let(pick)
        allowed = Reminders.allowed(context)
        refused = !granted
        wanted = null
    }
    val askable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    GroupCard(contentPadding = 16.dp) {
        Text(
            "When a service or paperwork is coming up, remind me:",
            Modifier.padding(start = 4.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val icons = mapOf(
                RepeatKind.OFF to Icons.Filled.NotificationsOff,
                RepeatKind.EVERY_N_DAYS to Icons.Filled.Today,
                RepeatKind.WEEKLY to Icons.Filled.DateRange,
                RepeatKind.MONTHLY to Icons.Filled.CalendarMonth,
            )
            RepeatKind.entries.forEach { k ->
                ChoiceTile(icons.getValue(k), k.label, schedule.kind == k, {
                    if (k != RepeatKind.OFF && askable && !allowed) {
                        wanted = k
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        pick(k)
                    }
                }, Modifier.weight(1f))
            }
        }
        AnimatedVisibility(schedule.on) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (schedule.kind) {
                    RepeatKind.EVERY_N_DAYS -> Stepper(
                        "Every",
                        if (schedule.everyDays == 1) "day" else "${schedule.everyDays} days",
                        canLower = schedule.everyDays > 1,
                        canRaise = schedule.everyDays < MAX_EVERY_DAYS,
                        onChange = { step -> viewModel.setReminderSchedule { it.withEveryDays(it.everyDays + step) } },
                    )
                    RepeatKind.WEEKLY -> WeekdayPicker(schedule.weekdays) { days ->
                        viewModel.setReminderSchedule { it.copy(weekdays = days) }
                    }
                    RepeatKind.MONTHLY -> {
                        Stepper(
                            "On the",
                            "${ordinal(schedule.dayOfMonth)} of the month",
                            canLower = schedule.dayOfMonth > 1,
                            canRaise = schedule.dayOfMonth < 31,
                            onChange = { step -> viewModel.setReminderSchedule { it.copy(dayOfMonth = (it.dayOfMonth + step).coerceIn(1, 31)) } },
                        )
                        if (schedule.dayOfMonth > 28) {
                            Text(
                                "Months without a ${ordinal(schedule.dayOfMonth)} use their last day.",
                                Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    RepeatKind.OFF -> {}
                }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("At ${timeLabel(schedule.time)}", fontFamily = NumberFont)
                }
                Text(
                    schedule.summary(),
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (schedule.on && !allowed) {
            Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (refused || !askable) "Notifications are off for Bike Dashboard. Turn them on in your phone's settings to get reminders."
                    else "Allow notifications to get reminders.",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (askable && !refused) {
                    TextButton(onClick = { wanted = schedule.kind; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                }
            }
        }
    }
    if (pickingTime) {
        PickTimeDialog(schedule.time, onDismiss = { pickingTime = false }) { time ->
            viewModel.setReminderSchedule { it.copy(time = time) }
            pickingTime = false
        }
    }
}

/**
 * [kind], starting "every N days" from the next time the reminder can arrive, so the first one
 * comes as soon as possible.
 */
private fun ReminderSchedule.withKind(kind: RepeatKind): ReminderSchedule =
    if (kind == RepeatKind.EVERY_N_DAYS && this.kind != kind) copy(kind = kind, anchor = firstDay(time)) else copy(kind = kind)

private fun ReminderSchedule.withEveryDays(days: Int): ReminderSchedule =
    copy(everyDays = days.coerceIn(1, MAX_EVERY_DAYS), anchor = firstDay(time))

/** Today if [time] is still to come, otherwise tomorrow. */
private fun firstDay(time: LocalTime): LocalDate =
    if (LocalTime.now().isBefore(time)) LocalDate.now() else LocalDate.now().plusDays(1)

/** "Every  [-] 3 days [+]": a label and a value with buttons to lower or raise it by one. */
@Composable
private fun Stepper(label: String, value: String, canLower: Boolean, canRaise: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        FilledTonalIconButton(onClick = { onChange(-1) }, enabled = canLower) { Icon(Icons.Filled.Remove, contentDescription = "Fewer") }
        Text(
            value,
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = NumberFont,
        )
        FilledTonalIconButton(onClick = { onChange(1) }, enabled = canRaise) { Icon(Icons.Filled.Add, contentDescription = "More") }
    }
}

/** Seven round day buttons, Monday first; any number can be picked, but not none. */
@Composable
private fun WeekdayPicker(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (on) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                    .border(if (on) 1.5.dp else 1.dp, if (on) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                    .clickable(role = Role.Checkbox) {
                        val next = if (on) selected - day else selected + day
                        if (next.isNotEmpty()) onChange(next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A clock for picking any time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me at") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Yamaha R3" -> "yamaha-r3", for the backup file name. */
private fun fileSafe(name: String?) =
    name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "bike" }

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TripKmField(saved: Double, onSave: (Double) -> Unit) {
    var text by remember(saved) { mutableStateOf(if (saved == Math.rint(saved)) saved.toLong().toString() else saved.toString()) }
    val value = parseNumber(text)
    val valid = value != null && value >= 0
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseNumber(it)?.takeIf { km -> km >= 0 }?.let(onSave)
        },
        label = { Text("Trip to Malaysia and back") },
        suffix = { Text("km") },
        singleLine = true,
        isError = !valid,
        shape = RoundedCornerShape(16.dp),
        supportingText = {
            Text(
                if (valid) "The same for every bike. The petrol for this ride is taken off the savings, unless you turn on \"No special trip to MY\"."
                else "Enter a distance in km, e.g. 48",
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * About: the version, the changelog, a hidden ideas notebook and the diagnostic logs. Tap the
 * version seven times to find them (like Android's developer options); after that they stay in the list.
 */
@Composable
private fun AboutCard(ideasUnlocked: Boolean, onFound: () -> Unit, onOpenIdeas: () -> Unit, onOpenLogs: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val tapsLeft = SECRET_TAPS - taps
    SettingRow(
        Icons.Filled.TwoWheeler,
        "Bike Dashboard",
        when {
            ideasUnlocked && taps >= SECRET_TAPS -> "Version ${appVersion()} · You found the ideas notebook"
            !ideasUnlocked && taps >= 3 -> "Version ${appVersion()} · $tapsLeft more…"
            else -> "Version ${appVersion()}"
        },
        onClick = {
            taps++
            if (taps == SECRET_TAPS && !ideasUnlocked) onFound()
        },
    )
    if (ideasUnlocked) {
        GroupDivider()
        SettingRow(Icons.Filled.Lightbulb, "Ideas notebook", "Jot down feature ideas in plain text", onClick = onOpenIdeas) { Chevron() }
        GroupDivider()
        SettingRow(Icons.Filled.BugReport, "Diagnostic logs", "Crash reports to share when something breaks", onClick = onOpenLogs) { Chevron() }
    }
    GroupDivider()
    SettingRow(Icons.Filled.History, "Changelog", "What's new in each version", onClick = { open = !open }) {
        Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(open) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CHANGELOG.forEach { release ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Version ${release.version} · ${release.date.short()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    release.changes.forEach { change ->
                        Row {
                            Text("•  ")
                            Text(change, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private const val SECRET_TAPS = 7

private fun trimKm(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

/** The versionName set in app/build.gradle.kts. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
