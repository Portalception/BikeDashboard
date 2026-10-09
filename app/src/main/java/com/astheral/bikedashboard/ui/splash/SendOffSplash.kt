package com.astheral.bikedashboard.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The send-off before leaving for the navigation app: the backdrop fades in over the page,
 * [phrase] rises in like the greeting when the app opens, stays a moment, then [onGo] runs
 * (about 1.4 s in all). A tap or Back skips straight to [onGo], which only ever runs once.
 */
@Composable
fun SendOffSplash(phrase: String, subtitle: String?, onGo: () -> Unit) {
    val cover = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val rise = remember { Animatable(24f) }
    var gone by remember { mutableStateOf(false) }
    val currentOnGo by rememberUpdatedState(onGo)
    val go = {
        if (!gone) {
            gone = true
            currentOnGo()
        }
    }
    LaunchedEffect(Unit) {
        cover.animateTo(1f, tween(COVER_MS))
        coroutineScope {
            launch { rise.animateTo(0f, tween(IN_MS)) }
            words.animateTo(1f, tween(IN_MS))
        }
        delay(HOLD_MS)
        go()
    }
    BackHandler(onBack = go)
    PhraseCover(cover = cover.value, words = words.value, rise = rise.value, phrase = phrase, subtitle = subtitle, onTap = go)
}

private const val COVER_MS = 250
private const val HOLD_MS = 800L
