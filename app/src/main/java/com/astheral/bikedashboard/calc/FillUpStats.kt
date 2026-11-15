package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.FillUp
import java.time.temporal.ChronoUnit

/** Default round-trip distance for a special ride into Malaysia to pump. */
const val DEFAULT_TRIP_KM = 48.0

val FillUp.totalLitres: Double
    get() = (sgLitres ?: 0.0) + (myLitres ?: 0.0) + (unsplitLitres ?: 0.0)

val FillUp.totalCostSgd: Double
    get() = (sgCostSgd ?: 0.0) + (myCostSgd ?: 0.0) + (unsplitCostSgd ?: 0.0)

/** SGD per litre for the Singapore part, or null if there isn't one. */
val FillUp.sgPricePerLitre: Double?
    get() = perLitre(sgCostSgd, sgLitres)

/** A Singapore discount: how much was taken off, and what share of the pump price that is. */
data class Discount(val savedSgd: Double, val percent: Double)

/**
 * The discount between the pump price [beforeSgd] and what was [paidSgd], or null when either is
 * missing or nothing was taken off.
 */
fun discountOf(paidSgd: Double?, beforeSgd: Double?): Discount? {
    if (paidSgd == null || beforeSgd == null || beforeSgd <= 0 || paidSgd >= beforeSgd) return null
    val saved = beforeSgd - paidSgd
    return Discount(saved, saved / beforeSgd * 100)
}

/** This fill-up's Singapore discount, if the price before discount was recorded. */
val FillUp.sgDiscount: Discount?
    get() = discountOf(sgCostSgd, sgBeforeDiscountSgd)

/** SGD per litre for the Malaysia part, or null if there isn't one. */
val FillUp.myPricePerLitre: Double?
    get() = perLitre(myCostSgd, myLitres)

private fun perLitre(cost: Double?, litres: Double?): Double? =
    if (cost != null && litres != null && litres > 0) cost / litres else null

/**
 * Everything worked out for one fill-up.
 *
 * Fuel used is only known between two full tanks: everything pumped after one full tank, up to
 * and including the next, is what the bike burned over that distance. That stretch is a
 * "segment", and its figures are put on the full fill-up that closes it. Partial fills
 * ([FillUp.fullTank] false) only know their own litres, cost and price, so distance and the
 * rates that need it are null for them. They're null too for the first full tank, which has no
 * earlier full tank to measure from.
 */
data class FillUpStats(
    val fillUp: FillUp,
    /** Km since the previous full tank. Only on a full fill-up that has one. */
    val distanceKm: Int?,
    /** Days since the previous full tank. */
    val days: Long?,
    /** Distance ÷ litres pumped over the segment, partial fills included. */
    val kmPerLitre: Double?,
    /** Cost of the whole segment ÷ distance. */
    val costPerKm: Double?,
    /** Cost of the whole segment ÷ days. */
    val costPerDay: Double?,
    /** Kilometres ridden per day since the previous full tank. Null when they were on the same day. */
    val kmPerDay: Double?,
    /** This fill-up's own price per litre, partial or not. */
    val pricePerLitre: Double?,
    /** Latest price per litre actually paid in Singapore, up to and including this fill-up. */
    val sgReferencePricePerLitre: Double?,
    /** Money saved by pumping in Malaysia, in SGD. Null when there's no MY part or not enough data. */
    val savingsSgd: Double?,
    /** Litres pumped over the segment this full fill-up closes: earlier partial fills plus its own. */
    val segmentLitres: Double? = null,
    /** What those litres cost, in SGD. */
    val segmentCostSgd: Double? = null,
    /** How many partial fills the segment includes (0 for a partial fill itself). */
    val partialCount: Int = 0,
)

/**
 * Works out [FillUpStats] for every fill-up, oldest first.
 *
 * Savings = (latest SG price/L × MY litres − MY cost) − trip cost, where the trip cost is
 * [tripKm] × the cost per km of the segment the fill-up belongs to, skipped when there was no
 * special trip. A segment that isn't closed by a full tank yet (or has no distance) uses the
 * most recent segment cost per km known before the fill-up instead. Fill-ups imported from the
 * spreadsheet keep the sheet's own savings figure.
 */
fun computeStats(fillUps: List<FillUp>, tripKm: Double = DEFAULT_TRIP_KM): List<FillUpStats> {
    val sorted = fillUps.sortedWith(compareBy({ it.date }, { it.odometerKm }))

    // First pass: the segment figures, put on each full fill-up that closes one.
    data class Segment(val distance: Int?, val days: Long?, val litres: Double, val cost: Double, val partials: Int)
    val segments = arrayOfNulls<Segment>(sorted.size)
    var lastFull: FillUp? = null
    var pending = mutableListOf<FillUp>()
    sorted.forEachIndexed { i, fill ->
        if (!fill.fullTank) {
            pending += fill
            return@forEachIndexed
        }
        val previousFull = lastFull
        segments[i] = Segment(
            distance = previousFull?.let { fill.odometerKm - it.odometerKm },
            days = previousFull?.let { ChronoUnit.DAYS.between(it.date, fill.date) },
            litres = pending.sumOf { it.totalLitres } + fill.totalLitres,
            cost = pending.sumOf { it.totalCostSgd } + fill.totalCostSgd,
            partials = pending.size,
        )
        lastFull = fill
        pending = mutableListOf()
    }
    val segmentCostPerKm = segments.map { seg -> seg?.distance?.takeIf { it > 0 }?.let { seg.cost / it } }
    // The segment each fill-up belongs to is closed by the next full tank (itself, if it's full).
    val closingCostPerKm = arrayOfNulls<Double>(sorted.size)
    var closing: Double? = null
    for (i in sorted.indices.reversed()) {
        if (sorted[i].fullTank) closing = segmentCostPerKm[i]
        closingCostPerKm[i] = closing
    }

    // Second pass: each fill-up's own figures, and savings using its segment's cost per km.
    var latestSgPrice: Double? = null
    var latestKnownCostPerKm: Double? = null
    return sorted.mapIndexed { i, fill ->
        fill.sgPricePerLitre?.let { latestSgPrice = it }
        val seg = segments[i]
        val costPerKm = segmentCostPerKm[i]
        val tripCostPerKm = closingCostPerKm[i] ?: latestKnownCostPerKm
        costPerKm?.let { latestKnownCostPerKm = it }

        val litres = fill.totalLitres
        val cost = fill.totalCostSgd
        val distance = seg?.distance
        val days = seg?.days
        FillUpStats(
            fillUp = fill,
            distanceKm = distance,
            days = days,
            kmPerLitre = if (seg != null && distance != null && seg.litres > 0) distance / seg.litres else null,
            costPerKm = costPerKm,
            costPerDay = if (seg != null && days != null && days > 0) seg.cost / days else null,
            kmPerDay = kmPerDay(distance, days),
            pricePerLitre = if (litres > 0) cost / litres else null,
            sgReferencePricePerLitre = latestSgPrice,
            savingsSgd = fill.importedSavingsSgd
                ?: savings(fill, latestSgPrice, tripCostPerKm, tripKm),
            segmentLitres = seg?.litres,
            segmentCostSgd = seg?.cost,
            partialCount = seg?.partials ?: 0,
        )
    }
}

/** How many partial fills come after the latest full tank, waiting for the next one to be counted. */
fun partialsWaiting(stats: List<FillUpStats>): Int =
    stats.takeLastWhile { !it.fillUp.fullTank }.size

/** [distanceKm] spread over [days], or null when either is missing or no days passed. */
fun kmPerDay(distanceKm: Int?, days: Long?): Double? =
    if (distanceKm != null && days != null && days > 0) distanceKm.toDouble() / days else null

private fun savings(fill: FillUp, sgPrice: Double?, costPerKm: Double?, tripKm: Double): Double? {
    val myLitres = fill.myLitres ?: return null
    val myCost = fill.myCostSgd ?: return null
    if (sgPrice == null) return null
    val priceDifference = sgPrice * myLitres - myCost
    val tripCost = if (fill.noSpecialTrip) 0.0 else tripKm * (costPerKm ?: return null)
    return priceDifference - tripCost
}
