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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.ServiceInterval
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class IntervalsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).intervalDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Pair<Profile, List<ServiceInterval>>?> = Profiles.get(application).active.flatMapLatest { profile ->
        if (profile == null) flowOf(null) else dao.observe(profile.id).map { profile to withDefaults(profile.id, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(interval: ServiceInterval) {
        viewModelScope.launch { dao.save(interval) }
    }
}

/** Which number a dialog is changing. */
private enum class IntervalField(val title: String, val suffix: String, val question: String, val icon: ImageVector) {
    EVERY_KM("Every", "km", "Due every how many km?", Icons.Filled.Speed),
    EVERY_DAYS("Or every", "days", "Or every how many days?", Icons.Filled.CalendarMonth),
    WARN_KM("Remind me from", "km before", "Start reminding how many km before?", Icons.Filled.Warning),
    WARN_DAYS("Or from", "days before", "Or how many days before?", Icons.Filled.Timer),
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
    val (profile, intervals) = state ?: return
    var editing by remember { mutableStateOf<Pair<ServiceInterval, IntervalField>?>(null) }

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
                Text("Service intervals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
        intervals.forEach { interval ->
            GroupCard {
                Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(interval.type.color(), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Text(interval.type.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
                                onClick = { editing = interval to f },
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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

    editing?.let { (interval, f) ->
        TextFieldDialog(
            title = "${interval.type.label}: ${f.question.replaceFirstChar { it.lowercase() }}",
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
