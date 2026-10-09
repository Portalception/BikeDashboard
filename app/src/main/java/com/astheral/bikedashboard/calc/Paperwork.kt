package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.BikeDetails
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How many days before an expiry reminders start, unless changed on the Bike details page. */
const val DEFAULT_PAPERWORK_LEAD_DAYS = 30

/**
 * The bike's paperwork that runs out: each has an expiry date and, for the COE and insurance,
 * a number that goes with it. [numberLabel] is null when there's no number to keep.
 */
enum class LegalItem(val label: String, val numberLabel: String?) {
    COE("COE", "COE / registration ID"),
    INSURANCE("Insurance", "Policy number"),
    ROAD_TAX("Road tax", null),
    INSPECTION("Inspection", null),
    ;

    fun expiry(d: BikeDetails): LocalDate? = when (this) {
        COE -> d.coeExpiry
        INSURANCE -> d.insuranceExpiry
        ROAD_TAX -> d.roadTaxExpiry
        INSPECTION -> d.inspectionExpiry
    }

    fun number(d: BikeDetails): String? = when (this) {
        COE -> d.coeNumber
        INSURANCE -> d.insurancePolicy
        ROAD_TAX, INSPECTION -> null
    }

    /** [d] with this item's number and expiry swapped for the given ones. */
    fun with(d: BikeDetails, number: String?, expiry: LocalDate?): BikeDetails = when (this) {
        COE -> d.copy(coeNumber = number, coeExpiry = expiry)
        INSURANCE -> d.copy(insurancePolicy = number, insuranceExpiry = expiry)
        ROAD_TAX -> d.copy(roadTaxExpiry = expiry)
        INSPECTION -> d.copy(inspectionExpiry = expiry)
    }
}

/** One paperwork item that's running out soon or already has. */
data class LegalDue(val item: LegalItem, val expiry: LocalDate, val daysLeft: Long) {
    val expired: Boolean get() = daysLeft < 0

    /** "Expires in 12 days", "Expires tomorrow", "Expires today", "Expired 3 days ago". */
    fun summary(): String = when {
        daysLeft > 1 -> "Expires in $daysLeft days"
        daysLeft == 1L -> "Expires tomorrow"
        daysLeft == 0L -> "Expires today"
        daysLeft == -1L -> "Expired yesterday"
        else -> "Expired ${-daysLeft} days ago"
    }
}

/**
 * Whether an [expiry] should be reminded about [today]: from [leadDays] days before it until
 * it's renewed (a later date is entered). No date means nothing to remind about.
 */
fun expiryDue(expiry: LocalDate?, leadDays: Int, today: LocalDate): Boolean =
    expiry != null && ChronoUnit.DAYS.between(today, expiry) <= leadDays

/** Whether [expiry] has already passed [today]. A date expiring today still counts as valid. */
fun isExpired(expiry: LocalDate?, today: LocalDate): Boolean = expiry != null && expiry.isBefore(today)

/** The items in [remind] whose expiry on [details] is within [leadDays] days or has passed. */
fun legalDue(details: BikeDetails, remind: Set<LegalItem>, leadDays: Int, today: LocalDate): List<LegalDue> =
    LegalItem.entries.filter { it in remind }.mapNotNull { item ->
        val expiry = item.expiry(details)
        if (expiry != null && expiryDue(expiry, leadDays, today)) LegalDue(item, expiry, ChronoUnit.DAYS.between(today, expiry)) else null
    }
