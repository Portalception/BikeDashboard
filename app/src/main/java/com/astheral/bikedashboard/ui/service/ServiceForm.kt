package com.astheral.bikedashboard.ui.service

import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.ui.edit.parseNumber
import java.time.LocalDate

/** One question card when adding or changing a service record, in the order they're asked. */
enum class ServiceStep { CATEGORY, DATE, DESCRIPTION, COST, ODOMETER, REVIEW }

/** The answers so far, as typed. */
data class ServiceForm(
    val type: String = DefaultCategory.ENGINE_OIL,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val cost: String = "",
    val currency: Currency = Currency.SGD,
    val odometer: String = "",
    /** The odometer was filled in by "Estimate from fill-ups" (and may have been adjusted since). */
    val odometerEstimated: Boolean = false,
) {
    /** The odometer as a number; commas are thousands here ("12,340"), never decimals. */
    fun odometerNumber(): Double? = parseNumber(odometer.replace(",", ""))

    /** What's wrong with this card's answer, if anything. Everything but the category and date can be left blank. */
    fun errorFor(step: ServiceStep): String? = when (step) {
        ServiceStep.COST -> if (cost.isNotBlank() && parseNumber(cost)?.takeIf { it >= 0 } == null) "The cost should be a number, or leave it blank" else null
        ServiceStep.ODOMETER -> if (odometer.isNotBlank() && odometerNumber()?.takeIf { it >= 0 } == null) "The odometer should be a number of km, or leave it blank" else null
        else -> null
    }

    /** The first card whose answer is wrong, to jump back to from the final check. */
    fun firstStepWithError(): ServiceStep? = ServiceStep.entries.firstOrNull { errorFor(it) != null }

    /** The record these answers make, or null if one is wrong. */
    fun toRecord(id: Long, profileId: Long): ServiceRecord? {
        if (firstStepWithError() != null) return null
        val km = odometerNumber()?.toInt()
        return ServiceRecord(
            id = id,
            profileId = profileId,
            date = date,
            odometerKm = km,
            type = type,
            description = description.trim(),
            cost = cost.takeIf { it.isNotBlank() }?.let { parseNumber(it) },
            currency = currency,
            odometerEstimated = km != null && odometerEstimated,
        )
    }

    companion object {
        fun from(r: ServiceRecord) = ServiceForm(
            type = r.type,
            date = r.date,
            description = r.description,
            cost = r.cost?.let { if (it == Math.rint(it)) it.toLong().toString() else it.toString() }.orEmpty(),
            currency = r.currency,
            odometer = r.odometerKm?.toString().orEmpty(),
            odometerEstimated = r.odometerEstimated,
        )
    }
}

/** The descriptions used most recently for category [type], newest first, for one-tap answers. */
fun recentDescriptions(records: List<ServiceRecord>, type: String, count: Int = 3): List<String> =
    records.filter { it.type == type && it.description.isNotBlank() }
        .sortedByDescending { it.date }
        .map { it.description.trim() }
        .distinct()
        .take(count)
