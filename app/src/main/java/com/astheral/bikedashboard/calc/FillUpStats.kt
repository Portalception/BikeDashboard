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

/** SGD per litre for the Malaysia part, or null if there isn't one. */
val FillUp.myPricePerLitre: Double?
    get() = perLitre(myCostSgd, myLitres)

private fun perLitre(cost: Double?, litres: Double?): Double? =
    if (cost != null && litres != null && litres > 0) cost / litres else null

/**
 * Everything worked out for one fill-up. Values that need the previous fill-up
 * (distance, days and what depends on them) are null for the very first one.
 * Because the tank is always filled to full, the litres pumped now are the fuel
 * used since the previous fill-up.
 */
data class FillUpStats(
    val fillUp: FillUp,
    val distanceKm: Int?,
    val days: Long?,
    val kmPerLitre: Double?,
    val costPerKm: Double?,
    val costPerDay: Double?,
    val pricePerLitre: Double?,
    /** Latest price per litre actually paid in Singapore, up to and including this fill-up. */
    val sgReferencePricePerLitre: Double?,
    /** Money saved by pumping in Malaysia, in SGD. Null when there's no MY part or not enough data. */
    val savingsSgd: Double?,
)

/**
 * Works out [FillUpStats] for every fill-up, oldest first.
 *
 * Savings = (latest SG price/L × MY litres − MY cost) − trip cost, where the trip
 * cost is [tripKm] × this fill-up's cost per km, skipped when there was no special
 * trip. Fill-ups imported from the spreadsheet keep the sheet's own savings figure.
 */
fun computeStats(fillUps: List<FillUp>, tripKm: Double = DEFAULT_TRIP_KM): List<FillUpStats> {
    val sorted = fillUps.sortedWith(compareBy({ it.date }, { it.odometerKm }))
    var latestSgPrice: Double? = null
    return sorted.mapIndexed { i, fill ->
        val previous = sorted.getOrNull(i - 1)
        fill.sgPricePerLitre?.let { latestSgPrice = it }

        val litres = fill.totalLitres
        val cost = fill.totalCostSgd
        val distance = previous?.let { fill.odometerKm - it.odometerKm }
        val days = previous?.let { ChronoUnit.DAYS.between(it.date, fill.date) }
        val costPerKm = distance?.takeIf { it > 0 }?.let { cost / it }

        FillUpStats(
            fillUp = fill,
            distanceKm = distance,
            days = days,
            kmPerLitre = distance?.takeIf { litres > 0 }?.let { it / litres },
            costPerKm = costPerKm,
            costPerDay = days?.takeIf { it > 0 }?.let { cost / it },
            pricePerLitre = if (litres > 0) cost / litres else null,
            sgReferencePricePerLitre = latestSgPrice,
            savingsSgd = fill.importedSavingsSgd
                ?: savings(fill, latestSgPrice, costPerKm, tripKm),
        )
    }
}

private fun savings(fill: FillUp, sgPrice: Double?, costPerKm: Double?, tripKm: Double): Double? {
    val myLitres = fill.myLitres ?: return null
    val myCost = fill.myCostSgd ?: return null
    if (sgPrice == null) return null
    val priceDifference = sgPrice * myLitres - myCost
    val tripCost = if (fill.noSpecialTrip) 0.0 else tripKm * (costPerKm ?: return null)
    return priceDifference - tripCost
}
