package com.astheral.bikedashboard.ui.summary

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.calc.Direction
import com.astheral.bikedashboard.calc.FillPhrases
import com.astheral.bikedashboard.calc.FillSummary
import com.astheral.bikedashboard.calc.MetricChange
import com.astheral.bikedashboard.calc.SendOffs
import com.astheral.bikedashboard.calc.SummaryDetail
import com.astheral.bikedashboard.calc.SummaryDetails
import com.astheral.bikedashboard.calc.SummaryHeadline
import com.astheral.bikedashboard.calc.SummaryMood
import com.astheral.bikedashboard.calc.computeStats
import com.astheral.bikedashboard.calc.summarizeFill
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.ui.background.animationsOn
import com.astheral.bikedashboard.ui.components.CountUpText
import com.astheral.bikedashboard.ui.components.NumberFont
import com.astheral.bikedashboard.ui.components.directionIcon
import com.astheral.bikedashboard.ui.components.verdictColor
import com.astheral.bikedashboard.ui.components.verdictDescription
import com.astheral.bikedashboard.ui.edit.PillButton
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.kmPerLitre
import com.astheral.bikedashboard.ui.litres
import com.astheral.bikedashboard.ui.sgd
import com.astheral.bikedashboard.ui.sgdPerKm
import com.astheral.bikedashboard.ui.sgdPerLitre
import com.astheral.bikedashboard.ui.splash.SendOffSplash
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs
import kotlin.random.Random

data class FillSummaryState(val loading: Boolean = true, val summary: FillSummary? = null)

class FillSummaryViewModel(application: Application, private val id: Long) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FillSummaryState())
    val state: StateFlow<FillSummaryState> = _state.asStateFlow()

    /** Which small lines to show under the big number; picked in Settings > Personalisation. */
    val details: StateFlow<SummaryDetails> = Settings.get(application).summaryDetails

    init {
        viewModelScope.launch {
            val dao = AppDatabase.get(application).fillUpDao()
            val fill = dao.getById(id)
            if (fill == null) {
                _state.value = FillSummaryState(loading = false)
                return@launch
            }
            // Savings are worked out exactly as on the dashboard and in History.
            val tripKm = Profiles.get(application).tripKm.first()
            val stats = computeStats(dao.getAll(fill.profileId), tripKm)
            _state.value = FillSummaryState(loading = false, summary = summarizeFill(stats, id))
        }
    }
}

/**
 * Shown straight after adding a fill-up, drawn right on the backdrop: a cheeky line, then one
 * big number (what Malaysia saved, or else the fuel economy) with its change since last time,
 * then a few small detail lines, each rising in after the one before. The navigation button
 * plays a short send-off before switching to Waze or Maps.
 */
@Composable
fun FillSummaryScreen(id: Long, onDone: () -> Unit) {
    val vm: FillSummaryViewModel = viewModel(key = "summary-$id") { FillSummaryViewModel(this[APPLICATION_KEY]!!, id) }
    val state by vm.state.collectAsState()
    val details by vm.details.collectAsState()
    val context = LocalContext.current
    val navApp = remember { findNavigationApp(context) }
    val animate = remember { animationsOn(context) }
    val seed = rememberSaveable { Random.nextLong() }
    var sendOff by remember { mutableStateOf<String?>(null) }
    if (state.loading) return
    val s = state.summary

    val lines = s?.let { detailLines(it, details) }.orEmpty()
    // Beats: 0 flavour line, 1 big number, 2 its change, then each detail line, then the buttons.
    val buttonsBeat = 3 + lines.size
    var beat by rememberSaveable { mutableIntStateOf(if (animate) -1 else Int.MAX_VALUE) }
    LaunchedEffect(Unit) {
        if (beat >= buttonsBeat) return@LaunchedEffect
        delay(FIRST_MS)
        beat = 0
        delay(HEADLINE_MS)
        beat = 1
        delay(CHANGE_MS)
        beat = 2
        delay(DETAILS_MS)
        while (beat < buttonsBeat) {
            beat++
            delay(STEP_MS)
        }
    }

    fun leave(app: NavigationApp) {
        if (openNavigationApp(context, app)) {
            // Coming back to the app later lands on the dashboard, not on this page.
            onDone()
        } else {
            sendOff = null
            Toast.makeText(context, "Couldn't open it", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (s == null) {
                        Text("This fill-up couldn't be found.", textAlign = TextAlign.Center)
                    } else {
                        Text(
                            FillPhrases.pick(s.mood, seed),
                            Modifier.reveal(beat >= 0),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(28.dp))
                        Headline(s, shown = beat >= 1, animate = animate)
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.reveal(beat >= 2), horizontalAlignment = Alignment.CenterHorizontally) { HeadlineNotes(s) }
                        if (lines.isNotEmpty()) Spacer(Modifier.height(36.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            lines.forEachIndexed { i, line ->
                                Text(
                                    line,
                                    Modifier.reveal(beat >= 3 + i),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).reveal(s == null || beat >= buttonsBeat),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton("Done", Icons.Filled.Check, filled = navApp == null, modifier = Modifier.weight(1f), onClick = onDone)
                navApp?.let { app ->
                    PillButton(app.label, Icons.Filled.Navigation, filled = true, modifier = Modifier.weight(1f)) {
                        if (sendOff != null) return@PillButton
                        if (animate) sendOff = SendOffs.pick(LocalTime.now().hour, seed) else leave(app)
                    }
                }
            }
        }

        val phrase = sendOff
        if (phrase != null && navApp != null) {
            SendOffSplash(phrase, subtitle = navApp.label, onGo = { leave(navApp) })
        }
    }
}

/** Fades and rises into place once [shown]; it keeps its space before that, so nothing jumps. */
@Composable
private fun Modifier.reveal(shown: Boolean): Modifier {
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(REVEAL_MS, easing = FastOutSlowInEasing), label = "reveal")
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * 18.dp.toPx()
    }
}

/** The lead-in sentence and the big number, which counts up once it shows. */
@Composable
private fun Headline(s: FillSummary, shown: Boolean, animate: Boolean) {
    val fill = s.stats.fillUp
    val (lead, value, format) = when (s.headline) {
        SummaryHeadline.SAVINGS -> Triple("Congratulations, you saved", s.savingsSgd ?: 0.0, ::sgd)
        SummaryHeadline.ECONOMY -> Triple("This tank gave you", s.kmPerLitre.current ?: 0.0, ::kmPerLitre)
        SummaryHeadline.PARTIAL -> Triple("You topped up", fill.totalLitres, ::litres)
        SummaryHeadline.FIRST_TANK -> Triple("Tank's full with", fill.totalLitres, ::litres)
    }
    Column(Modifier.reveal(shown), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(lead, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        // A unit after the number ("24.9 km/L") is drawn smaller beside it, not in the big monospace.
        val unit = format(value).substringAfterLast(' ', "")
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpText(
                if (shown || !animate) value else 0.0,
                { format(it).substringBeforeLast(' ') },
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 64.sp),
                color = MaterialTheme.colorScheme.tertiary,
                animate = animate,
            )
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** The line under the big number: its change since last time, or why there's nothing to compare. */
@Composable
private fun HeadlineNotes(s: FillSummary) {
    val fill = s.stats.fillUp
    when (s.headline) {
        SummaryHeadline.SAVINGS -> ChangeLine(s.savings, ::sgd, more = "more", less = "less")
        SummaryHeadline.ECONOMY -> ChangeLine(s.kmPerLitre, ::kmPerLitre, more = "better", less = "worse")
        SummaryHeadline.PARTIAL -> {
            Note(numbers { text("for "); num(sgd(fill.totalCostSgd)) })
            Note(AnnotatedString("Fuel economy comes with your next full tank."))
        }
        SummaryHeadline.FIRST_TANK -> {
            Note(numbers { text("for "); num(sgd(fill.totalCostSgd)) })
            Note(AnnotatedString("Your first full tank on this bike. Economy shows from the next one."))
        }
    }
    // When Malaysia didn't make the headline, say why.
    when (s.mood) {
        SummaryMood.MALAYSIA_PENDING -> Note(
            AnnotatedString(
                if (s.stats.sgReferencePricePerLitre == null) "Add a Singapore fill-up to compare prices, and savings will show."
                else "Savings need a cost per km for the trip, which comes from your next full tank.",
            ),
        )
        SummaryMood.MALAYSIA_NO_SAVINGS -> {
            val saved = s.savingsSgd ?: 0.0
            Note(
                if (saved < 0) numbers { text("Malaysia cost "); num(sgd(abs(saved))); text(" more than Singapore, trip included.") }
                else AnnotatedString("Malaysia came out even with Singapore this time."),
            )
        }
        else -> Unit
    }
}

@Composable
private fun Note(text: AnnotatedString) {
    Text(
        text,
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** "▲ S$1.20 more than last time", green when better and red when worse; nothing without a previous one. */
@Composable
private fun ChangeLine(change: MetricChange, format: (Double) -> String, more: String, less: String) {
    val direction = change.direction ?: return
    val delta = change.delta ?: return
    val color = if (direction == Direction.SAME) MaterialTheme.colorScheme.onSurfaceVariant else verdictColor(change.verdict)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(directionIcon(direction), contentDescription = verdictDescription(change.verdict), tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        val text = when (direction) {
            Direction.SAME -> AnnotatedString("Same as last time")
            Direction.UP -> numbers { num(format(abs(delta))); text(" $more than last time") }
            Direction.DOWN -> numbers { num(format(abs(delta))); text(" $less than last time") }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** The detail lines the user picked that this fill-up has something for, in their fixed order. */
private fun detailLines(s: FillSummary, details: SummaryDetails): List<AnnotatedString> {
    val fill = s.stats.fillUp
    val big = s.headline
    return details.visible.mapNotNull { detail ->
        when (detail) {
            // A partial fill or first tank already shows its litres and cost up top.
            SummaryDetail.COST_LITRES -> if (big == SummaryHeadline.PARTIAL || big == SummaryHeadline.FIRST_TANK) null
            else numbers { num(sgd(fill.totalCostSgd)); text(" for "); num(litres(fill.totalLitres)) }
            SummaryDetail.PRICE -> s.pricePerLitre.current?.let { p -> numbers { text("Paid "); num(sgdPerLitre(p)) } }
            SummaryDetail.DISTANCE -> s.kmSincePrevious?.let { d ->
                numbers {
                    num(km(d))
                    when {
                        s.isPartial -> text(" since your last fill-up")
                        s.partialsIncluded > 0 -> {
                            text(" since your last full tank, ")
                            num(s.partialsIncluded.toString())
                            text(if (s.partialsIncluded == 1) " top-up included" else " top-ups included")
                        }
                        else -> text(" since your last fill-up")
                    }
                }
            }
            SummaryDetail.ECONOMY -> if (big == SummaryHeadline.ECONOMY) null
            else s.kmPerLitre.current?.let { e -> numbers { num(kmPerLitre(e)); text(" this tank") } }
            SummaryDetail.COST_PER_KM -> s.costPerKm.current?.let { c -> numbers { num(sgdPerKm(c)); text(" to ride") } }
            SummaryDetail.DAYS -> s.daysSincePrevious?.let { d ->
                if (d == 0L) AnnotatedString("Same day as your last fill-up")
                else numbers { num(d.toString()); text(if (d == 1L) " day since your last fill-up" else " days since your last fill-up") }
            }
            SummaryDetail.ODOMETER -> numbers { num(km(fill.odometerKm)); text(" on the clock") }
            SummaryDetail.SAVINGS_30_DAYS -> s.savingsLast30DaysSgd?.let { v -> numbers { num(sgd(v)); text(" saved in the last 30 days") } }
        }
    }
}

/** Builds a line where numbers ([NumberLine.num]) are in the monospace font and words aren't. */
private fun numbers(build: NumberLine.() -> Unit): AnnotatedString = NumberLine().apply(build).builder.toAnnotatedString()

private class NumberLine {
    val builder = AnnotatedString.Builder()
    fun text(t: String) = builder.append(t)
    /** The number in monospace; a unit after a space ("13.90 L") stays in the normal font so it doesn't gap. */
    fun num(t: String) {
        val space = t.lastIndexOf(' ')
        val number = if (space > 0) t.substring(0, space) else t
        builder.withStyle(SpanStyle(fontFamily = NumberFont, fontWeight = FontWeight.Medium)) { append(number) }
        if (space > 0) builder.append(t.substring(space))
    }
}

private const val FIRST_MS = 150L
private const val HEADLINE_MS = 450L
private const val CHANGE_MS = 650L
private const val DETAILS_MS = 250L
private const val STEP_MS = 160L
private const val REVEAL_MS = 420
