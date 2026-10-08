package com.astheral.bikedashboard.calc

import com.astheral.bikedashboard.data.FillUp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A known odometer reading from a fill-up. */
data class OdometerReading(val date: LocalDate, val km: Int)

/** How an [OdometerEstimate] was worked out. */
enum class EstimateKind {
    /** A fill-up on that very day; its reading is used as it is. */
    SAME_DAY,

    /** In a straight line between the fill-ups before and after. */
    BETWEEN,

    /** Only fill-ups before the date, so the last of them. */
    BEFORE_ONLY,

    /** Only fill-ups after the date, so the first of them. */
    AFTER_ONLY,
}

/**
 * A guess at the odometer on a past date, from the fill-ups around it. [before] is the reading it
 * counts from (the closest fill-up on or before the date) and [after] the closest one after it;
 * which are there depends on [kind].
 */
data class OdometerEstimate(val km: Int, val kind: EstimateKind, val before: OdometerReading?, val after: OdometerReading?)

/**
 * Estimates the odometer on [date] from [fillUps] (in any order). Between two fill-ups it goes in a
 * straight line by date: halfway between them in time is halfway in km. With fill-ups on only one
 * side, it's that nearest reading. Null without any fill-ups.
 */
fun estimateOdometer(date: LocalDate, fillUps: List<FillUp>): OdometerEstimate? {
    val readings = fillUps.map { OdometerReading(it.date, it.odometerKm) }
    // On a day with several fill-ups, the highest reading is the latest one.
    readings.filter { it.date == date }.maxByOrNull { it.km }?.let { return OdometerEstimate(it.km, EstimateKind.SAME_DAY, it, null) }
    val before = readings.filter { it.date < date }.maxWithOrNull(compareBy({ it.date }, { it.km }))
    val after = readings.filter { it.date > date }.minWithOrNull(compareBy({ it.date }, { it.km }))
    return when {
        before != null && after != null -> {
            val span = ChronoUnit.DAYS.between(before.date, after.date).toDouble()
            val into = ChronoUnit.DAYS.between(before.date, date).toDouble()
            OdometerEstimate((before.km + (after.km - before.km) * into / span).roundToInt(), EstimateKind.BETWEEN, before, after)
        }
        before != null -> OdometerEstimate(before.km, EstimateKind.BEFORE_ONLY, before, null)
        after != null -> OdometerEstimate(after.km, EstimateKind.AFTER_ONLY, null, after)
        else -> null
    }
}
