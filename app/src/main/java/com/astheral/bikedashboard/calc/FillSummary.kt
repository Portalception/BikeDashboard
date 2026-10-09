package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.FillUp
import kotlin.math.abs

/** Which way is better for a number: more (km/L) or less (price, cost per km), or neither (litres). */
enum class Better { HIGHER, LOWER, NEITHER }

/** Which way a number moved since the previous fill-up. */
enum class Direction { UP, DOWN, SAME }

/** Whether that move is good news, bad news, or neither. */
enum class Verdict { GOOD, BAD, NEUTRAL }

/** One number for this fill-up next to the same number for the one before. */
data class MetricChange(val current: Double?, val previous: Double?, val better: Better) {
    /** How much it changed, or null when either side is missing. */
    val delta: Double? get() = if (current != null && previous != null) current - previous else null

    /** The change as a fraction of the previous value (0.05 = 5% more), or null if it can't be worked out. */
    val fraction: Double? get() = delta?.let { d -> previous?.takeIf { it != 0.0 }?.let { d / abs(it) } }

    val direction: Direction? get() {
        val d = delta ?: return null
        val f = fraction
        // Changes under half a percent read as "the same", so rounding doesn't paint arrows.
        val same = if (f != null) abs(f) < SAME_FRACTION else abs(d) < 1e-9
        return when {
            same -> Direction.SAME
            d > 0 -> Direction.UP
            else -> Direction.DOWN
        }
    }

    val verdict: Verdict get() = when (direction) {
        null, Direction.SAME -> Verdict.NEUTRAL
        Direction.UP -> when (better) { Better.HIGHER -> Verdict.GOOD; Better.LOWER -> Verdict.BAD; Better.NEITHER -> Verdict.NEUTRAL }
        Direction.DOWN -> when (better) { Better.HIGHER -> Verdict.BAD; Better.LOWER -> Verdict.GOOD; Better.NEITHER -> Verdict.NEUTRAL }
    }

    companion object {
        const val SAME_FRACTION = 0.005
    }
}

/**
 * Everything shown on the page after saving a new fill-up: the fill-up's own figures, each
 * compared with the fill-up before it, recent km/L and price per litre for a small trend,
 * and what pumping in Malaysia saved.
 */
data class FillSummary(
    val stats: FillUpStats,
    val previous: FillUpStats?,
    val kmPerLitre: MetricChange,
    val pricePerLitre: MetricChange,
    val costPerKm: MetricChange,
    val costPerDay: MetricChange,
    val totalCost: MetricChange,
    val litres: MetricChange,
    val distanceKm: MetricChange,
    /** km/L of recent fill-ups, oldest first, ending with this one when it has a value. */
    val recentKmPerLitre: List<Double>,
    /** Price per litre of recent fill-ups, oldest first, ending with this one. */
    val recentPricePerLitre: List<Double>,
    /** True when [recentKmPerLitre] ends with this fill-up (it doesn't for the very first one). */
    val kmPerLitreIncludesThis: Boolean,
    val pricePerLitreIncludesThis: Boolean,
    /** Money saved by pumping in Malaysia, worked out the same way as everywhere else ([computeStats]). */
    val savingsSgd: Double?,
    /** This fill-up had litres pumped in Malaysia, so savings apply to it. */
    val hasMalaysiaPart: Boolean,
)

/**
 * The summary for fill-up [id] among [stats] (from [computeStats], oldest first), or null if it
 * isn't there. "Previous" is the fill-up just before it in the same order. [recent] is how many
 * fill-ups the trend bars cover, this one included.
 */
fun summarizeFill(stats: List<FillUpStats>, id: Long, recent: Int = 10): FillSummary? {
    val i = stats.indexOfFirst { it.fillUp.id == id }
    if (i < 0) return null
    val s = stats[i]
    val prev = stats.getOrNull(i - 1)
    val window = stats.subList((i + 1 - recent).coerceAtLeast(0), i + 1)
    fun change(better: Better, of: (FillUpStats) -> Double?) = MetricChange(of(s), prev?.let(of), better)
    return FillSummary(
        stats = s,
        previous = prev,
        kmPerLitre = change(Better.HIGHER) { it.kmPerLitre },
        pricePerLitre = change(Better.LOWER) { it.pricePerLitre },
        costPerKm = change(Better.LOWER) { it.costPerKm },
        costPerDay = change(Better.LOWER) { it.costPerDay },
        totalCost = change(Better.LOWER) { it.fillUp.totalCostSgd },
        litres = change(Better.NEITHER) { it.fillUp.totalLitres.takeIf { l -> l > 0 } },
        distanceKm = change(Better.NEITHER) { it.distanceKm?.toDouble() },
        recentKmPerLitre = window.mapNotNull { it.kmPerLitre },
        recentPricePerLitre = window.mapNotNull { it.pricePerLitre },
        kmPerLitreIncludesThis = s.kmPerLitre != null,
        pricePerLitreIncludesThis = s.pricePerLitre != null,
        savingsSgd = s.savingsSgd,
        hasMalaysiaPart = s.fillUp.hasMalaysiaPart,
    )
}

private val FillUp.hasMalaysiaPart: Boolean get() = (myLitres ?: 0.0) > 0
