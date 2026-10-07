package com.astheral.bikedashboard.ui.profile

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.data.DEFAULT_TANK_LITRES
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.ProfileKind
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.ChoiceTile
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.ProgressLine
import com.astheral.bikedashboard.ui.edit.NumberCard
import com.astheral.bikedashboard.ui.edit.PillButton
import com.astheral.bikedashboard.ui.edit.QuestionCard
import com.astheral.bikedashboard.ui.edit.parseNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep { NAME, KIND, TANK, TRIP, REVIEW }

data class SetupState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** No bikes yet: the app's first screen, which can't be closed. */
    val firstRun: Boolean = false,
    val canDelete: Boolean = false,
    val name: String = "",
    val kind: ProfileKind = ProfileKind.OWN,
    val tank: String = trim(DEFAULT_TANK_LITRES),
    val trip: String = trim(DEFAULT_TRIP_KM),
    val step: SetupStep = SetupStep.NAME,
    val direction: Int = 1,
    val error: String? = null,
    val done: Boolean = false,
)

private fun trim(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

/** What's wrong with this step's answer, if anything. */
fun SetupState.errorFor(step: SetupStep): String? = when (step) {
    SetupStep.NAME -> if (name.isBlank()) "Give the bike a name" else null
    SetupStep.TANK -> parseNumber(tank).let { if (it == null || it <= 0 || it > 60) "Litres, e.g. 14" else null }
    SetupStep.TRIP -> parseNumber(trip).let { if (it == null || it < 0) "Km, e.g. 48" else null }
    else -> null
}

class ProfileSetupViewModel(application: Application, private val profileId: Long) : AndroidViewModel(application) {
    private val profiles = Profiles.get(application)
    private var existing: Profile? = null

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = profiles.all.first()
            existing = all.firstOrNull { it.id == profileId }
            val p = existing
            _state.value = if (p == null) {
                SetupState(loading = false, firstRun = all.isEmpty())
            } else {
                SetupState(
                    loading = false,
                    isNew = false,
                    canDelete = all.size > 1,
                    name = p.name,
                    kind = p.kind,
                    tank = trim(p.tankLitres),
                    trip = trim(p.tripKm),
                    // Editing opens on the summary; tap an answer to change it.
                    step = SetupStep.REVIEW,
                )
            }
        }
    }

    fun change(update: (SetupState) -> SetupState) = _state.update { update(it).copy(error = null) }

    fun next() {
        val s = _state.value
        s.errorFor(s.step)?.let { e -> _state.update { it.copy(error = e) }; return }
        val nextStep = when {
            !s.isNew -> SetupStep.REVIEW // editing: each answer goes back to the summary
            else -> SetupStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        }
        _state.update { it.copy(step = nextStep, direction = 1, error = null) }
    }

    fun back(): Boolean {
        val s = _state.value
        if (s.step.ordinal == 0 || !s.isNew) return false
        _state.update { it.copy(step = SetupStep.entries[s.step.ordinal - 1], direction = -1, error = null) }
        return true
    }

    fun goTo(step: SetupStep) = _state.update { it.copy(step = step, direction = -1, error = null) }

    fun save() {
        val s = _state.value
        SetupStep.entries.firstOrNull { s.errorFor(it) != null }?.let { bad ->
            _state.update { it.copy(step = bad, direction = -1, error = s.errorFor(bad)) }
            return
        }
        val profile = Profile(
            id = existing?.id ?: 0,
            name = s.name.trim(),
            kind = s.kind,
            tankLitres = parseNumber(s.tank)!!,
            tripKm = parseNumber(s.trip)!!,
        )
        viewModelScope.launch {
            if (existing == null) profiles.create(profile) else profiles.update(profile)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val p = existing ?: return
        viewModelScope.launch {
            profiles.delete(p)
            _state.update { it.copy(done = true) }
        }
    }
}

/**
 * Setting up a bike, one question per card: its name, whether it's yours or a rental, the tank
 * size (for the "more than the tank holds" warning) and the trip distance for the savings.
 * Editing opens on the summary card.
 */
@Composable
fun ProfileSetupScreen(profileId: Long, onDone: () -> Unit) {
    val vm: ProfileSetupViewModel = viewModel(key = "profile-$profileId") {
        ProfileSetupViewModel(this[APPLICATION_KEY]!!, profileId)
    }
    val state by vm.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    // The back gesture steps back a card (or to the summary when editing) before closing.
    BackHandler(enabled = !state.loading && (if (state.isNew) state.step.ordinal > 0 else state.step != SetupStep.REVIEW)) {
        if (state.isNew) vm.back() else vm.goTo(SetupStep.REVIEW)
    }
    if (state.loading) return

    val steps = SetupStep.entries
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!state.firstRun) {
                IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                when {
                    state.firstRun -> "Welcome"
                    state.isNew -> "New bike"
                    else -> "Edit bike"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.canDelete) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete bike", tint = MaterialTheme.colorScheme.error)
                }
            }
            AccentChip("${state.step.ordinal + 1} / ${steps.size}")
        }
        ProgressLine((state.step.ordinal + 1f) / steps.size, Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val dir = state.direction
                (slideInHorizontally(tween(320)) { it * dir } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(320)) { -it * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            label = "setupStep",
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                StepCard(step, state, vm)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cancel on the first card (or the summary when editing); otherwise go back a card.
            val cancels = if (state.isNew) state.step.ordinal == 0 else state.step == SetupStep.REVIEW
            if (!(cancels && state.firstRun)) {
                PillButton(
                    text = if (cancels) "Cancel" else "Back",
                    icon = if (cancels) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    filled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        cancels -> onDone()
                        state.isNew -> vm.back()
                        else -> vm.goTo(SetupStep.REVIEW)
                    }
                }
            }
            PillButton(
                text = when {
                    state.step != SetupStep.REVIEW -> if (state.isNew) "Next" else "Done"
                    state.isNew -> "Add bike"
                    else -> "Save"
                },
                icon = if (state.step == SetupStep.REVIEW) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                filled = true,
                modifier = Modifier.weight(1f),
            ) { if (state.step == SetupStep.REVIEW) vm.save() else vm.next() }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.name}?") },
            text = { Text("This deletes the bike and all of its fill-ups. Export a backup first if you might want them. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepCard(step: SetupStep, state: SetupState, vm: ProfileSetupViewModel) {
    when (step) {
        SetupStep.NAME -> NumberCard(
            part = if (state.firstRun) "Let's set up your first bike" else null,
            question = "What's the bike called?",
            hint = "Shown at the top of every page, e.g. \"Yamaha R3\" or \"Rental Ninja\".",
            value = state.name, error = state.error, text = true,
            quick = if (state.name.isBlank()) listOf("Yamaha R3" to "Yamaha R3") else emptyList(),
            onChange = { v -> vm.change { it.copy(name = v) } }, onNext = vm::next,
        )
        SetupStep.KIND -> QuestionCard(null, "Is it yours, or a rental?") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ProfileKind.OWN to Icons.Filled.TwoWheeler, ProfileKind.RENTAL to Icons.Filled.Key).forEach { (kind, icon) ->
                    ChoiceTile(
                        icon = icon,
                        label = kind.label,
                        selected = state.kind == kind,
                        onClick = { vm.change { it.copy(kind = kind) }; vm.next() },
                        tint = MaterialTheme.colorScheme.tertiary,
                        big = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Each bike keeps its own fill-ups, so a rental doesn't mix into your own bike's numbers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetupStep.TANK -> NumberCard(
            question = "How big is the tank?",
            hint = "Used to warn when a fill-up is more than the tank holds. The Yamaha R3's is 14 L.",
            value = state.tank, suffix = "L", error = state.error, selectAll = true,
            onChange = { v -> vm.change { it.copy(tank = v) } }, onNext = vm::next,
        )
        SetupStep.TRIP -> NumberCard(
            question = "How far is a trip into Malaysia and back?",
            hint = "The petrol for this ride is taken off the savings, unless you mark a fill-up as no special trip.",
            value = state.trip, suffix = "km", error = state.error, selectAll = true,
            onChange = { v -> vm.change { it.copy(trip = v) } }, onNext = vm::next,
        )
        SetupStep.REVIEW -> GroupCard {
            listOf(
                Triple(SetupStep.NAME, "Name", state.name),
                Triple(SetupStep.KIND, "Type", state.kind.label),
                Triple(SetupStep.TANK, "Tank", "${state.tank} L"),
                Triple(SetupStep.TRIP, "Trip to MY", "${state.trip} km"),
            ).forEachIndexed { i, (s, label, value) ->
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { vm.goTo(s) }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontFamily = if (s == SetupStep.TANK || s == SetupStep.TRIP) NumberFont else null, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Edit, contentDescription = "Change", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
