package com.astheral.bikedashboard.ui.service

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.data.ServiceInterval
import com.astheral.bikedashboard.data.defaultInterval
import com.astheral.bikedashboard.data.findByName
import com.astheral.bikedashboard.data.newCategory
import com.astheral.bikedashboard.data.withDefaults
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.components.TextFieldDialog
import com.astheral.bikedashboard.ui.edit.parseNumber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** One item on the page: a category and this bike's interval for it. */
data class IntervalItem(val category: ServiceCategory, val interval: ServiceInterval)

data class IntervalsState(
    val profile: Profile,
    val items: List<IntervalItem>,
    /** Every category, for checking names and offering the ones not on the page yet. */
    val categories: List<ServiceCategory>,
)

class IntervalsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val dao = db.intervalDao()
    private val categoryDao = db.categoryDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<IntervalsState?> = Profiles.get(application).active.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(null)
        } else {
            combine(dao.observe(profile.id), categoryDao.observe()) { saved, cats ->
                val items = withDefaults(profile.id, saved, cats).map { i -> IntervalItem(cats.first { it.id == i.type }, i) }
                IntervalsState(profile, items, cats)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(interval: ServiceInterval) {
        viewModelScope.launch { dao.save(interval) }
    }

    /** Renames the item's category, which renames it everywhere (records too). */
    fun rename(category: ServiceCategory, name: String) {
        viewModelScope.launch { categoryDao.rename(category.id, name) }
    }

    /**
     * Adds an item called [name], switched on for this bike. A category you already have with that
     * name is used; otherwise a new category is made.
     */
    fun add(name: String) {
        val s = state.value ?: return
        val category = s.categories.findByName(name) ?: newCategory(name, s.categories)
        addExisting(category)
    }

    fun addExisting(category: ServiceCategory) {
        val profile = state.value?.profile ?: return
        viewModelScope.launch {
            categoryDao.addInterval(category, defaultInterval(profile.id, category.id).copy(enabled = true))
        }
    }

    /** Takes the item off the page on every bike. Its category and records stay. */
    fun remove(category: ServiceCategory) {
        viewModelScope.launch { categoryDao.removeInterval(category.id) }
    }
}

/** Which number a dialog is changing. */
private enum class IntervalField(val title: String, val suffix: String, val question: String, val icon: ImageVector) {
    EVERY_KM("Every", "km", "Due every how many km?", Icons.Filled.Speed),
    EVERY_DAYS("Or Every", "days", "Or every how many days?", Icons.Filled.CalendarMonth),
    WARN_KM("Remind Me from", "km before", "Start reminding how many km before?", Icons.Filled.Warning),
    WARN_DAYS("Or From", "days before", "Or how many days before?", Icons.Filled.Timer),
}

private fun ServiceInterval.get(f: IntervalField): Int? = when (f) {
    IntervalField.EVERY_KM -> everyKm
    IntervalField.EVERY_DAYS -> everyDays
    IntervalField.WARN_KM -> warnKm
    IntervalField.WARN_DAYS -> warnDays
}

private fun ServiceInterval.with(f: IntervalField, v: Int?): ServiceInterval = when (f) {
    IntervalField.EVERY_KM -> copy(everyKm = v)
    IntervalField.EVERY_DAYS -> copy(everyDays = v)
    IntervalField.WARN_KM -> copy(warnKm = v)
    IntervalField.WARN_DAYS -> copy(warnDays = v)
}

/**
 * How often each kind of work is due on this bike, and how early to start reminding you.
 * Each is "every X km or Y days, whichever comes first"; leave either blank to use only the other.
 */
@Composable
fun IntervalsScreen(onBack: () -> Unit, viewModel: IntervalsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val (profile, items, categories) = state ?: return
    var editing by remember { mutableStateOf<Pair<IntervalItem, IntervalField>?>(null) }
    var renaming by remember { mutableStateOf<ServiceCategory?>(null) }
    var removing by remember { mutableStateOf<ServiceCategory?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Service Intervals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Text(
            "Each is due every so many km or days, whichever comes first, counted from the last record of that kind. " +
                "Reminders start in the warning window and repeat daily or weekly (Settings > Reminders) until you add a new record.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            val interval = item.interval
            GroupCard {
                Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(categoryColor(item.category.id), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Text(item.category.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { renaming = item.category }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename or remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = interval.enabled, onCheckedChange = { on -> viewModel.save(interval.copy(enabled = on)) })
                }
                AnimatedVisibility(interval.enabled) {
                    Column {
                        IntervalField.entries.forEachIndexed { i, f ->
                            if (i > 0) GroupDivider()
                            val v = interval.get(f)
                            SettingRow(
                                f.icon,
                                "${f.title} ${v?.let { String.format(Locale.ENGLISH, "%,d", it) } ?: "—"} ${f.suffix}",
                                subtitle = if (v == null) "Not used" else null,
                                onClick = { editing = item to f },
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        GroupCard {
            SettingRow(
                Icons.Filled.Add,
                "Add an Item",
                subtitle = "Something else to keep track of, like a chain clean or new tyres",
                onClick = { adding = true },
            )
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                "Turn notifications on or off, and pick daily or weekly, in Settings > Reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    renaming?.let { category ->
        CategoryNameDialog(
            title = "Rename",
            initial = category.name,
            taken = categories.filter { it.id != category.id }.map { it.name },
            note = "This renames it everywhere, on every bike and in your service records.",
            onDismiss = { renaming = null },
            onSave = { name -> viewModel.rename(category, name); renaming = null },
            onRemove = { renaming = null; removing = category },
        )
    }
    removing?.let { category ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${category.name}?") },
            text = {
                Text(
                    "It's taken off this page for every bike, with its interval and reminders. " +
                        "Your ${category.name.lowercase()} records stay, and you can add it back later.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(category); removing = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (adding) {
        CategoryNameDialog(
            title = "Add an Item",
            initial = "",
            taken = items.map { it.category.name },
            onDismiss = { adding = false },
            onSave = { name -> viewModel.add(name); adding = false },
            suggestions = categories.filter { !it.hasInterval },
            onPickSuggestion = { c -> viewModel.addExisting(c); adding = false },
        )
    }

    editing?.let { (item, f) ->
        val interval = item.interval
        TextFieldDialog(
            title = "${item.category.name}: ${f.question.replaceFirstChar { it.lowercase() }}",
            initial = interval.get(f)?.toString().orEmpty(),
            onDismiss = { editing = null },
            onSave = { t ->
                viewModel.save(interval.with(f, parseNumber(t)?.toInt()?.takeIf { it > 0 }))
                editing = null
            },
            number = true,
            suffix = f.suffix,
            validate = { t -> if (parseNumber(t)?.takeIf { it > 0 } == null) "A whole number, or leave it blank to not use it" else null },
        )
    }
}
