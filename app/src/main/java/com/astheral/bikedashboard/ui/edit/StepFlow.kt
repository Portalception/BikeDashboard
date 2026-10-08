package com.astheral.bikedashboard.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.ProgressLine

/**
 * The frame around a one-question-per-card flow, like adding a fill-up or a service: a title row
 * with "3 / 7", a progress line, the current card (sliding in from the side the flow is moving,
 * +1 forward or -1 back, per [direction]) and two big buttons at the bottom.
 *
 * The left button reads "Cancel" when [backCloses], otherwise "Back"; it calls [onBack] either way.
 * The right one reads [primaryText], with a tick when [primaryIsFinal].
 */
@Composable
internal fun <S> StepFlow(
    title: String,
    step: S,
    index: Int,
    count: Int,
    direction: Int,
    onClose: () -> Unit,
    onDelete: (() -> Unit)?,
    backCloses: Boolean,
    onBack: () -> Unit,
    primaryText: String,
    primaryIsFinal: Boolean,
    onPrimary: () -> Unit,
    card: @Composable (S) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            onDelete?.let {
                IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
            }
            AccentChip("${index + 1} / $count")
        }
        ProgressLine((index + 1f) / count, Modifier.padding(horizontal = 4.dp, vertical = 8.dp))

        // Each question is its own card; moving on slides the next one in from the side.
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it * direction } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(320)) { -it * direction } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            label = "step",
        ) { shown ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { card(shown) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                text = if (backCloses) "Cancel" else "Back",
                icon = if (backCloses) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = onBack,
            )
            PillButton(
                text = primaryText,
                icon = if (primaryIsFinal) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onPrimary,
            )
        }
    }
}
