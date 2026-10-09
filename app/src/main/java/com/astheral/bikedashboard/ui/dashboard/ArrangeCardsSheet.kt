package com.astheral.bikedashboard.ui.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.astheral.bikedashboard.calc.CardLayout
import com.astheral.bikedashboard.calc.DashCard
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline

/** The detail page each dashboard card opens; the names match. */
val DashCard.metric: Metric get() = Metric.valueOf(name)

val DashCard.title: String get() = metric.title

/**
 * A sheet listing every dashboard card: drag a card by its handle to move it, and switch it off to
 * hide it. Every change is passed to [onChange] straight away, so the dashboard behind follows along.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeCardsSheet(layout: CardLayout, onChange: (CardLayout) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val current by rememberUpdatedState(layout)
        val change by rememberUpdatedState(onChange)
        val haptic = LocalHapticFeedback.current
        val listState = rememberLazyListState()
        var dragging by remember { mutableStateOf<DashCard?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        // After a move, wait for the list to be laid out again before the next one.
        var expectedIndex by remember { mutableStateOf<Int?>(null) }

        fun move(card: DashCard, to: Int) {
            change(current.move(card, to))
            // Stop the list scrolling to follow the first card when it moves.
            listState.requestScrollToItem(0)
        }

        fun swapIfPassed() {
            val card = dragging ?: return
            val items = listState.layoutInfo.visibleItemsInfo
            val item = items.firstOrNull { it.key == card } ?: return
            if (expectedIndex != null && item.index != expectedIndex) return
            expectedIndex = null
            val center = item.offset + item.size / 2f + dragOffset
            val next = items.firstOrNull { it.index == item.index + 1 }
            val prev = items.firstOrNull { it.index == item.index - 1 }
            val target = when {
                next != null && center > next.offset + next.size / 2f -> next
                prev != null && center < prev.offset + prev.size / 2f -> prev
                else -> return
            }
            // Where the dragged card lands once moved, so it stays under the finger.
            val newOffset = if (target.index > item.index) target.offset + target.size - item.size else target.offset
            dragOffset -= newOffset - item.offset
            expectedIndex = target.index
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            move(card, target.index)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arrange Cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Drag to reorder, switch off to hide",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { change(CardLayout()) }, enabled = !layout.isDefault) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(layout.order, key = { it }) { card ->
                    val lifted = card == dragging
                    val index = layout.order.indexOf(card)
                    CardRow(
                        card = card,
                        shown = card !in layout.hidden,
                        canHide = layout.canHide(card),
                        lifted = lifted,
                        onShown = { change(current.setShown(card, it)) },
                        onMoveUp = if (index > 0) ({ move(card, index - 1) }) else null,
                        onMoveDown = if (index < layout.order.lastIndex) ({ move(card, index + 1) }) else null,
                        modifier = if (lifted) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                        } else {
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                        },
                        handle = Modifier.pointerInput(card) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = card
                                    dragOffset = 0f
                                    expectedIndex = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { pointer, amount ->
                                    pointer.consume()
                                    dragOffset += amount.y
                                    swapIfPassed()
                                },
                                onDragEnd = { dragging = null; dragOffset = 0f },
                                onDragCancel = { dragging = null; dragOffset = 0f },
                            )
                        },
                    )
                }
            }
            Text(
                "At least one card stays on the dashboard.",
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One card in the sheet: a drag handle, its name and a switch. Lifted while being dragged. */
@Composable
private fun CardRow(
    card: DashCard,
    shown: Boolean,
    canHide: Boolean,
    lifted: Boolean,
    onShown: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier,
    handle: Modifier,
) {
    val elevation by animateDpAsState(if (lifted) 8.dp else 0.dp, label = "lift")
    val scale by animateFloatAsState(if (lifted) 1.03f else 1f, label = "liftScale")
    val accent = MaterialTheme.colorScheme.tertiary
    Surface(
        shape = TileShape,
        color = if (lifted) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = outline(if (lifted) accent else null, if (lifted) 2.dp else 1.dp),
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction("Move up") { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction("Move down") { it(); true } },
                )
            },
    ) {
        Row(Modifier.padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Drag to move ${card.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                card.title,
                Modifier.weight(1f).alpha(if (shown) 1f else 0.55f),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(checked = shown, onCheckedChange = onShown, enabled = canHide)
        }
    }
}
