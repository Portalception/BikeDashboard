package com.astheral.bikedashboard.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.calc.Greetings
import com.astheral.bikedashboard.ui.components.AppBackground
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

/**
 * Picked once per app launch, so the greeting doesn't change if the splash is shown again
 * (and doesn't change when the clock passes into the next part of the day).
 */
private val sessionGreeting: String by lazy { Greetings.pick(LocalTime.now().hour, Random.nextLong()) }

/**
 * A short greeting shown over the app when it starts. The backdrop covers the app straight
 * away; once the bike has loaded ([ready]) the words rise in, stay about a second, and the
 * whole thing fades out to the dashboard underneath. A tap skips it.
 */
@Composable
fun GreetingSplash(bikeName: String?, ready: Boolean, onDone: () -> Unit) {
    val cover = remember { Animatable(1f) }
    val words = remember { Animatable(0f) }
    val rise = remember { Animatable(24f) }
    LaunchedEffect(ready) {
        if (!ready) {
            // No bike yet (a brand-new install goes to bike setup): don't keep the app covered.
            delay(NO_BIKE_MS)
            cover.animateTo(0f, tween(OUT_MS))
            onDone()
            return@LaunchedEffect
        }
        coroutineScope {
            launch { rise.animateTo(0f, tween(IN_MS)) }
            words.animateTo(1f, tween(IN_MS))
        }
        delay(HOLD_MS)
        cover.animateTo(0f, tween(OUT_MS))
        onDone()
    }
    AppBackground(
        Modifier
            .graphicsLayer { alpha = cover.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = words.value
                    translationY = rise.value * density
                },
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ready) return@Column
            Text(
                Greetings.fill(sessionGreeting, bikeName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            bikeName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val IN_MS = 350
private const val HOLD_MS = 1100L
private const val OUT_MS = 300
private const val NO_BIKE_MS = 800L
