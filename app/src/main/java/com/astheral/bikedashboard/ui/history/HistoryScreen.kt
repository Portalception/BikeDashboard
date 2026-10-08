package com.astheral.bikedashboard.ui.history

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.ui.profile.ProfileTitle
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.PillShape
import com.astheral.bikedashboard.ui.components.TileShape
import com.astheral.bikedashboard.ui.components.outline
import com.astheral.bikedashboard.ui.theme.CountryColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.FillUpStats
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.label
import com.astheral.bikedashboard.ui.litres
import com.astheral.bikedashboard.ui.long
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerLitre
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).fillUpDao()

    /**
     * Rows that have already slid in. Kept here rather than in the screen so that coming back
     * from the edit form doesn't replay the animation (and a just-deleted row can slide out).
     */
    val shownIds = mutableSetOf<Long>()

    /** Newest first. Null while loading. */
    val fillUps: StateFlow<List<FillUpStats>?> =
        Profiles.get(application).activeFillUps
            .map { (profile, all) -> computeStats(all, profile?.tripKm ?: DEFAULT_TRIP_KM).reversed() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) {
        viewModelScope.launch { dao.getById(id)?.let { dao.delete(it) } }
    }
}

@Composable
fun HistoryScreen(
    profile: Profile?,
    onProfileClick: () -> Unit,
    onAddFillUp: () -> Unit,
    onOpenFillUp: (Long) -> Unit,
    /** A fill-up just deleted on the edit screen: it slides out here, then is deleted. */
    deletingId: Long? = null,
    onDeleteFinished: () -> Unit = {},
    viewModel: HistoryViewModel = viewModel(),
) {
    val fillUps by viewModel.fillUps.collectAsState()
    // Cards slide in the first time they come into view.
    val shownIds = viewModel.shownIds
    val list = fillUps ?: return
    val months = list.groupBy { YearMonth.from(it.fillUp.date) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Column(Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) {
                ProfileTitle(profile, onProfileClick)
                if (list.isEmpty()) {
                    Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                } else {
                    val since = list.last().fillUp.date.format(MONTH_YEAR)
                    CountUpText(
                        list.size.toDouble(),
                        { "History · ${count(it.toInt(), "fill-up")} since $since" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = null,
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item(key = "empty") {
                GroupCard(contentPadding = 28.dp) {
                    Text(
                        "No fill-ups yet. Tap + to add one, or import your spreadsheet in Settings.",
                        Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        var index = 0
        months.forEach { (month, inMonth) ->
            item(key = "m-$month") {
                val countUp = remember { shownIds.add(-month.hashCode().toLong() - 1) } // first time only
                MonthHeader(month, inMonth, countUp, Modifier.animateItem())
            }
            inMonth.forEach { stats ->
                val position = index++
                item(key = stats.fillUp.id) {
                    val id = stats.fillUp.id
                    val slideIn = remember { shownIds.add(id) } // true only the first time
                    SlidingRow(
                        slideIn = slideIn,
                        delayMs = if (position < 8) position * 40 else 0,
                        leaving = id == deletingId,
                        onLeft = {
                            viewModel.delete(id)
                            onDeleteFinished()
                        },
                        modifier = Modifier.animateItem(),
                    ) {
                        FillUpCard(stats, countUp = slideIn, onClick = { onOpenFillUp(id) })
                    }
                }
            }
        }
    }
}

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val MONTH_FULL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** "October 2026" with that month's spend and number of fill-ups on the right. */
@Composable
private fun MonthHeader(month: YearMonth, inMonth: List<FillUpStats>, countUp: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "  ${month.format(MONTH_FULL)}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CountUpText(
            inMonth.sumOf { it.fillUp.totalCostSgd },
            { "${sgd(it)} · ${inMonth.size}" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            animate = countUp,
        )
    }
}

/**
 * Slides a row in from the right when it first appears, and out to the left (closing the
 * gap behind it) when [leaving] becomes true. [onLeft] runs once it has gone.
 */
@Composable
private fun SlidingRow(
    slideIn: Boolean,
    delayMs: Int,
    leaving: Boolean,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visible = remember { MutableTransitionState(!slideIn) }
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(250) // let the edit screen finish closing so you can see the row go
            visible.targetState = false
        } else {
            visible.targetState = true
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (leaving && visible.isIdle && !visible.currentState) onLeft()
    }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = slideInHorizontally(tween(350, delayMillis = delayMs)) { it / 3 } + fadeIn(tween(350, delayMillis = delayMs)),
        exit = slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)) + shrinkVertically(tween(300, delayMillis = 150)),
    ) {
        Column { content() }
    }
}

/** One fill-up as a card: a tinted country badge, the main numbers, and price and economy on the right. */
@Composable
private fun FillUpCard(stats: FillUpStats, countUp: Boolean, onClick: () -> Unit) {
    val fill = stats.fillUp
    val tint = when (fill.country) {
        Country.SG -> CountryColors.sg
        Country.MY -> CountryColors.my
        Country.MIXED -> CountryColors.mixed
    }
    Surface(
        onClick = onClick,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = outline(tint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(fill.country.label(), color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fill.date.long(), style = MaterialTheme.typography.titleSmall)
                Row {
                    CountUpText(fill.totalLitres, ::litres, style = MaterialTheme.typography.bodyMedium, animate = countUp)
                    Text(" · ", style = MaterialTheme.typography.bodyMedium, fontFamily = NumberFont)
                    CountUpText(fill.totalCostSgd, ::sgd, style = MaterialTheme.typography.bodyMedium, animate = countUp)
                }
                Text(
                    km(fill.odometerKm) + (stats.distanceKm?.let { " · +${km(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.pricePerLitre?.let {
                    CountUpText(
                        it,
                        ::sgdPerLitre,
                        Modifier.clip(PillShape).background(tint.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        animate = countUp,
                    )
                }
                stats.kmPerLitre?.let {
                    CountUpText(it, ::kmPerLitre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, animate = countUp)
                }
            }
        }
    }
}
