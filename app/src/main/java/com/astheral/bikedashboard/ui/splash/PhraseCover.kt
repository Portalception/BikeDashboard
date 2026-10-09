package com.astheral.bikedashboard.ui.splash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.ui.components.AppBackground

/** How long the words take to rise in, and the backdrop to fade. */
internal const val IN_MS = 350
internal const val OUT_MS = 300

/**
 * The app's backdrop covering everything, with one big phrase in the middle and a small coloured
 * line under it: the look of the greeting when the app opens and of the send-off after a
 * fill-up. [cover] is the backdrop's opacity, [words] the phrase's, and [rise] how far (in dp)
 * the phrase still sits below its place. A tap anywhere calls [onTap].
 */
@Composable
fun PhraseCover(cover: Float, words: Float, rise: Float, phrase: String?, subtitle: String?, onTap: () -> Unit) {
    AppBackground(
        Modifier
            .graphicsLayer { alpha = cover }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = words
                    translationY = rise * density
                },
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (phrase == null) return@Column
            Text(
                phrase,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
