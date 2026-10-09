package com.astheral.bikedashboard.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The main tabs side by side: swipe sideways to move between them. On the dashboard a swipe
 * first goes through its periods ([periodPager]); past the last one ("Lifetime") it carries on
 * to History. Swiping back from History lands on Lifetime, the period right next to it.
 */
@Composable
fun MainTabs(tabPager: PagerState, periodPager: PagerState, page: @Composable (Int) -> Unit) {
    val flingPx = with(LocalDensity.current) { 400.dp.toPx() }
    val handOff = remember(tabPager, flingPx) { TabsNestedScroll(tabPager, flingPx) }

    // Dragging the dashboard in from History brings it in on Lifetime.
    val dragged by tabPager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(tabPager, periodPager) {
        snapshotFlow { dragged && tabPager.currentPage + tabPager.currentPageOffsetFraction < 1f }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                val last = periodPager.pageCount - 1
                if (periodPager.currentPage != last) periodPager.scrollToPage(last)
            }
    }

    HorizontalPager(
        state = tabPager,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        pageNestedScrollConnection = handOff,
    ) { i -> page(i) }
}

/**
 * How a swipe inside a tab (the dashboard's periods) hands over to the tabs. The pager's
 * own version keeps a fling inside the page, so a quick flick past Lifetime would only move
 * the tabs if dragged more than halfway. This one is the same except that a flick finishes
 * the move to the next tab once the tabs have started moving.
 */
private class TabsNestedScroll(private val tabs: PagerState, private val flingPx: Float) : NestedScrollConnection {
    /** Once the tabs are part-way between two pages, they take the drag before anything inside them. */
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val offset = tabs.currentPageOffsetFraction
        if (source != NestedScrollSource.UserInput || abs(offset) <= 1e-6f || available.x == 0f) return Offset.Zero
        val space = (tabs.layoutInfo.pageSize + tabs.layoutInfo.pageSpacing).toFloat()
        val current = offset * space
        val next = current + space * -sign(offset)
        val (min, max) = if (offset > 0f) next to current else current to next
        val consumed = -tabs.dispatchRawDelta(-available.x.coerceIn(min, max))
        return Offset(consumed, 0f)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val offset = tabs.currentPageOffsetFraction
        if (abs(offset) <= 1e-6f) return Velocity.Zero
        val position = tabs.currentPage + offset
        val target = when {
            available.x < -flingPx -> ceil(position).toInt()
            available.x > flingPx -> floor(position).toInt()
            else -> position.roundToInt()
        }.coerceIn(0, tabs.pageCount - 1)
        tabs.animateScrollToPage(target, animationSpec = tween(250))
        return available.copy(y = 0f)
    }

    /** Whatever fling is left after the page inside is never passed on to the tabs. */
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available.copy(y = 0f)
}
