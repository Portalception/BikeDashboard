package com.astheral.bikedashboard.ui.edit

import com.astheral.bikedashboard.calc.myPricePerLitre
import com.astheral.bikedashboard.calc.sgPricePerLitre
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.DEFAULT_TANK_LITRES
import com.astheral.bikedashboard.data.FillUp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class Field {
    ODOMETER, SG_LITRES, SG_COST, SG_AFTER_DISCOUNT, MY_LITRES, RM_PAID, RATE, RM_CHARGED,
    LEGACY_MY_COST, UNSPLIT_LITRES, UNSPLIT_COST, BARS_LEFT, RESERVE_KM,
}

/**
 * What's typed into the add/edit form, as text so half-typed numbers are kept.
 *
 * Old fill-ups from the spreadsheet can have shapes new ones don't: a Malaysian
 * fill-up with only an SGD cost (before RM amounts were recorded) and a mixed one
 * with one total for both countries. Those get their own fields while being edited.
 */
data class FillUpForm(
    val date: LocalDate,
    val country: Country,
    val odometer: String = "",
    val sgLitres: String = "",
    /** In Singapore, the full price the pump showed; with no discount that's what you paid. */
    val sgCost: String = "",
    /** Optional, Singapore only: what you paid after a card or membership discount. When filled in, it's the cost. */
    val sgAfterDiscount: String = "",
    val myLitres: String = "",
    val rmPaid: String = "",
    val rate: String = "",
    val rmCharged: String = "",
    val noSpecialTrip: Boolean = false,
    /** Filled to full. A partial fill's economy is worked out at the next full tank. */
    val fullTank: Boolean = true,
    /** The petrol brand picked in Singapore, or blank for none. */
    val brand: String = "",
    val barsLeft: String = "",
    val reserveKm: String = "",
    /** Old MY fill-up with no RM amounts: its SGD cost is edited directly. */
    val legacyMy: Boolean = false,
    val legacyMyCost: String = "",
    /** Old mixed fill-up with one total for both countries. */
    val legacyMixed: Boolean = false,
    val unsplitLitres: String = "",
    val unsplitCost: String = "",
) {
    val hasSgPart get() = country == Country.SG || (country == Country.MIXED && !legacyMixed)
    val hasMyPart get() = country == Country.MY || (country == Country.MIXED && !legacyMixed)

    /** Switching country turns an old-style fill-up into a normal one. */
    fun withCountry(new: Country): FillUpForm =
        if (new == country) this else copy(country = new, legacyMy = false, legacyMixed = false)

    companion object {
        fun from(fill: FillUp): FillUpForm {
            val legacyMixed = fill.country == Country.MIXED && fill.unsplitLitres != null
            val legacyMy = fill.country == Country.MY && (fill.rmCharged == null || fill.rmPerSgd == null)
            return FillUpForm(
                date = fill.date,
                country = fill.country,
                odometer = fill.odometerKm.toString(),
                sgLitres = text(fill.sgLitres),
                // With a discount, the main box holds the full price and the cost goes in the second box.
                sgCost = text(fill.sgBeforeDiscountSgd ?: fill.sgCostSgd),
                sgAfterDiscount = if (fill.sgBeforeDiscountSgd != null) text(fill.sgCostSgd) else "",
                myLitres = text(fill.myLitres),
                rmPaid = text(fill.rmPaid),
                rate = rateText(fill.rmPerSgd),
                rmCharged = text(fill.rmCharged),
                noSpecialTrip = fill.noSpecialTrip,
                fullTank = fill.fullTank,
                brand = fill.brand.orEmpty(),
                barsLeft = fill.barsLeft?.toString().orEmpty(),
                reserveKm = text(fill.reserveKm),
                legacyMy = legacyMy,
                legacyMyCost = if (legacyMy) text(fill.myCostSgd) else "",
                legacyMixed = legacyMixed,
                unsplitLitres = text(fill.unsplitLitres),
                unsplitCost = text(fill.unsplitCostSgd),
            )
        }

        /** Rates worked out from the spreadsheet have many decimals; 4 is plenty to show. */
        fun rateText(rate: Double?): String = rate?.let { text((it * 10_000).roundToInt() / 10_000.0) }.orEmpty()

        private fun text(value: Double?): String = value?.let {
            if (it == Math.rint(it)) it.toLong().toString() else it.toString()
        }.orEmpty()
    }
}

/** Reads "12.3" or "12,3". Blank is null. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

sealed interface FormResult {
    data class Ok(val fillUp: FillUp) : FormResult
    data class Invalid(val errors: Map<Field, String>) : FormResult
}

/**
 * Turns the form into a [FillUp], or says which fields are missing or not numbers.
 * [base] is the fill-up being edited, so its id and hidden spreadsheet values are kept.
 */
fun FillUpForm.toFillUp(base: FillUp?): FormResult {
    val errors = mutableMapOf<Field, String>()

    fun required(field: Field, text: String): Double? {
        val value = parseNumber(text)
        when {
            text.isBlank() -> errors[field] = "Required"
            value == null -> errors[field] = "Not a number"
            value <= 0 -> errors[field] = "Must be more than 0"
            else -> return value
        }
        return null
    }

    fun optional(field: Field, text: String): Double? {
        if (text.isBlank()) return null
        val value = parseNumber(text)
        if (value == null || value < 0) errors[field] = "Not a number"
        return value
    }

    val odometer = odometer.trim().toIntOrNull()
    when {
        this.odometer.isBlank() -> errors[Field.ODOMETER] = "Required"
        odometer == null || odometer <= 0 -> errors[Field.ODOMETER] = "Whole km, e.g. 163903"
    }

    val sgLitres = if (hasSgPart) required(Field.SG_LITRES, sgLitres) else null
    val sgFull = if (hasSgPart) required(Field.SG_COST, sgCost) else null
    // A discounted amount, when given, is what you paid; the full price is then kept just for tracking.
    val sgAfter = if (country == Country.SG) optional(Field.SG_AFTER_DISCOUNT, sgAfterDiscount)?.takeIf { it > 0 } else null
    val sgCost = sgAfter ?: sgFull

    val myLitres = if (hasMyPart) required(Field.MY_LITRES, myLitres) else null
    val newStyleMy = hasMyPart && !(country == Country.MY && legacyMy)
    val rmPaid = if (newStyleMy) optional(Field.RM_PAID, rmPaid) else null
    val rate = if (newStyleMy) required(Field.RATE, rate) else null
    val rmCharged = if (newStyleMy) required(Field.RM_CHARGED, rmCharged) else null
    val legacyMyCost = if (hasMyPart && !newStyleMy) required(Field.LEGACY_MY_COST, legacyMyCost) else null
    val myCost = when {
        rmCharged != null && rate != null -> rmCharged / rate
        else -> legacyMyCost
    }

    val unsplitLitres = if (country == Country.MIXED && legacyMixed) required(Field.UNSPLIT_LITRES, unsplitLitres) else null
    val unsplitCost = if (country == Country.MIXED && legacyMixed) required(Field.UNSPLIT_COST, unsplitCost) else null

    val bars = if (barsLeft.isBlank()) null else barsLeft.trim().toIntOrNull().also {
        if (it == null || it < 0) errors[Field.BARS_LEFT] = "Whole number of bars"
    }
    val reserve = optional(Field.RESERVE_KM, reserveKm)

    if (errors.isNotEmpty() || odometer == null) return FormResult.Invalid(errors)

    val keepOldMixedRm = country == Country.MIXED && legacyMixed
    val fill = (base ?: FillUp(date = date, country = country, odometerKm = odometer)).copy(
        date = date,
        country = country,
        odometerKm = odometer,
        sgLitres = sgLitres,
        sgCostSgd = sgCost,
        myLitres = myLitres,
        myCostSgd = if (hasMyPart) myCost else null,
        // Old mixed fill-ups keep the RM record they were imported with.
        rmPaid = if (keepOldMixedRm) base?.rmPaid else rmPaid,
        rmPerSgd = if (keepOldMixedRm) base?.rmPerSgd else rate,
        rmCharged = if (keepOldMixedRm) base?.rmCharged else rmCharged,
        unsplitLitres = unsplitLitres,
        unsplitCostSgd = unsplitCost,
        barsLeft = bars,
        reserveKm = reserve?.takeIf { it > 0 },
        noSpecialTrip = if (country == Country.SG) false else noSpecialTrip,
        fullTank = fullTank,
        // The brand is only asked for in Singapore.
        brand = brand.trim().takeIf { it.isNotEmpty() && country == Country.SG },
        sgBeforeDiscountSgd = if (sgAfter != null) sgFull else null,
    )
    return FormResult.Ok(fill)
}

/** The previous full tank before a fill-up, and the partial fills since it. */
data class SegmentBefore(val previousFull: FillUp, val partials: List<FillUp>)

/**
 * The stretch [fill] closes as a full tank: the latest full tank among [others] before it (by
 * date, then odometer, the order everything else uses), and the partial fills after that one.
 * Null when there's no earlier full tank.
 */
fun segmentBefore(fill: FillUp, others: List<FillUp>): SegmentBefore? {
    val earlier = others.sortedWith(compareBy({ it.date }, { it.odometerKm }))
        .filter { it.date < fill.date || (it.date == fill.date && it.odometerKm < fill.odometerKm) }
    val fullIndex = earlier.indexOfLast { it.fullTank }
    if (fullIndex < 0) return null
    return SegmentBefore(earlier[fullIndex], earlier.drop(fullIndex + 1))
}

/** Extra a full tank can take when you squeeze fuel in, on top of its stated size. */
const val SQUEEZE_LITRES = 0.5

/**
 * Things that look wrong but might be right. The form shows these and lets you save anyway.
 * [others] is every other fill-up in the app (not the one being edited).
 */
fun warningsFor(fill: FillUp, others: List<FillUp>, today: LocalDate, tankLitres: Double = DEFAULT_TANK_LITRES): List<String> {
    val warnings = mutableListOf<String>()
    val sorted = others.sortedWith(compareBy({ it.date }, { it.odometerKm }))
    // On the same day there may be two fill-ups; the latest one already saved counts as "previous".
    val previous = sorted.lastOrNull { it.date <= fill.date }
    val next = sorted.firstOrNull { it.date > fill.date }

    if (fill.date.isAfter(today)) warnings += "The date is in the future."
    if (others.any { it.date == fill.date && it.odometerKm == fill.odometerKm }) {
        warnings += "There's already a fill-up on this date with the same odometer reading."
    }

    if (previous != null && fill.odometerKm <= previous.odometerKm) {
        warnings += "The odometer isn't higher than the previous fill-up (${previous.odometerKm} km on ${previous.date.format(DAY)})."
    }
    // Distance and economy are only known for a full tank, measured from the previous full tank.
    val segment = if (fill.fullTank) segmentBefore(fill, sorted) else null
    if (segment != null && previous != null && fill.odometerKm > previous.odometerKm) {
        val distance = fill.odometerKm - segment.previousFull.odometerKm
        val since = if (segment.partials.isEmpty()) "the last fill-up" else "the last full tank"
        if (distance > 500) warnings += "That's $distance km since $since, more than a tank usually lasts."
        val kmPerLitre = distance / (segment.partials.sumOf { it.totalLitres } + fill.totalLitres)
        if (kmPerLitre < 15 || kmPerLitre > 35) {
            warnings += "That works out to ${fmt(1, kmPerLitre)} km/L, which is unusual."
        }
    }
    if (next != null && fill.odometerKm >= next.odometerKm) {
        warnings += "The odometer is higher than the next fill-up (${next.odometerKm} km on ${next.date.format(DAY)})."
    }

    if (fill.totalLitres > tankLitres + SQUEEZE_LITRES) {
        warnings += "${fmt(2, fill.totalLitres)} L is more than the ${fmt(1, tankLitres)} L tank holds."
    }
    fill.sgPricePerLitre?.let {
        if (it < 1.5 || it > 4.0) warnings += "Singapore price works out to S$${fmt(2, it)}/L, which is unusual."
    }
    fill.myPricePerLitre?.let {
        if (it < 0.4 || it > 3.0) warnings += "Malaysia price works out to S$${fmt(2, it)}/L, which is unusual."
    }
    fill.rmPerSgd?.let {
        if (it < 2.5 || it > 4.0) warnings += "A rate of ${fmt(4, it)} RM per SGD is unusual."
    }
    val sgBefore = fill.sgBeforeDiscountSgd
    val sgPaid = fill.sgCostSgd
    if (sgBefore != null && sgPaid != null && sgBefore < sgPaid) {
        warnings += "The amount after discount (S$${fmt(2, sgPaid)}) is more than the full price (S$${fmt(2, sgBefore)})."
    }
    val rmPaid = fill.rmPaid
    val rmCharged = fill.rmCharged
    if (rmPaid != null && rmCharged != null && rmCharged > rmPaid) {
        warnings += "The pump charged more (RM ${fmt(2, rmCharged)}) than you paid (RM ${fmt(2, rmPaid)})."
    }
    if (fill.barsLeft != null && fill.reserveKm != null) {
        warnings += "Both bars left and km on reserve are filled in. Usually it's one or the other."
    }
    if (fill.totalCostSgd <= 0) warnings += "The cost is zero."
    return warnings
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun fmt(digits: Int, value: Double) = String.format(Locale.ENGLISH, "%.${digits}f", value)
