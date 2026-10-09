package com.astheral.bikedashboard.ui.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.ProfileKind
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.data.Appearances
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfilesState(
    /** Null while loading. */
    val all: List<Profile>? = null,
    val active: Profile? = null,
    val counts: Map<Long, Int> = emptyMap(),
)

/** The bikes, for the switcher and the name shown at the top of every page. */
class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val profiles = Profiles.get(application)

    val state: StateFlow<ProfilesState> =
        combine(profiles.all, profiles.active, profiles.counts) { all, active, counts -> ProfilesState(all, active, counts) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesState())

    /** Every bike's look, so the switch animation can show the new bike's colours. */
    val appearances: StateFlow<Appearances> = Settings.get(application).appearances

    fun switchTo(id: Long) = profiles.switchTo(id)
}

fun ProfileKind.icon() = if (this == ProfileKind.RENTAL) Icons.Filled.Key else Icons.Filled.TwoWheeler

/** The bike's name in big type at the top of a page; tap it to switch bikes. */
@Composable
fun ProfileTitle(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (profile != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            profile?.name ?: "Bike Dashboard",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (profile != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = "Switch bike", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A sheet listing every bike as a card: tap one to switch, the pencil to edit, or add a new one.
 * [onPick] also gets where the card was tapped, in window pixels, for the switch animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    state: ProfilesState,
    onPick: (Profile, Offset) -> Unit,
    onEdit: (Profile) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Your Bikes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            state.all.orEmpty().forEach { profile ->
                val active = profile.id == state.active?.id
                var card by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var touch by remember { mutableStateOf(Offset.Unspecified) }
                Surface(
                    onClick = {
                        val c = card?.takeIf { it.isAttached }
                        val local = if (touch.isSpecified) touch else c?.size?.let { Offset(it.width / 2f, it.height / 2f) } ?: Offset.Zero
                        onPick(profile, c?.localToWindow(local) ?: Offset.Unspecified)
                    },
                    shape = TileShape,
                    color = if (active) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    border = outline(if (active) MaterialTheme.colorScheme.tertiary else null, if (active) 2.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { card = it }
                        .pointerInput(Unit) {
                            awaitEachGesture { touch = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
                        },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(profile.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${profile.kind.label} · ${count(state.counts[profile.id] ?: 0, "fill-up")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = NumberFont,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (active) Icon(Icons.Filled.CheckCircle, contentDescription = "Showing", tint = MaterialTheme.colorScheme.tertiary)
                        IconButton(onClick = { onEdit(profile) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}") }
                    }
                }
            }
            Surface(
                onClick = onAdd,
                shape = TileShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                border = outline(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Add a Bike", style = MaterialTheme.typography.titleMedium)
                        Text("Another bike of yours, or a rental", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
