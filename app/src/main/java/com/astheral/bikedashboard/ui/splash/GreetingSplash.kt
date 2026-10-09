package com.astheral.bikedashboard.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.astheral.bikedashboard.calc.Greetings
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
    PhraseCover(
        cover = cover.value,
        words = words.value,
        rise = rise.value,
        phrase = if (ready) Greetings.fill(sessionGreeting, bikeName) else null,
        subtitle = bikeName,
        onTap = onDone,
    )
}

private const val HOLD_MS = 1100L
private const val NO_BIKE_MS = 800L
