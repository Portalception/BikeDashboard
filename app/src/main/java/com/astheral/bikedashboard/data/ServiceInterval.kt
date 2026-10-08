package com.astheral.bikedashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * How often one kind of work is due on one bike: every [everyKm] km or [everyDays] days,
 * whichever comes first. Reminders start [warnKm] km or [warnDays] days before it's due.
 */
@Serializable
@Entity(tableName = "service_intervals", primaryKeys = ["profileId", "type"])
data class ServiceInterval(
    val profileId: Long,
    val type: ServiceType,
    val enabled: Boolean = true,
    val everyKm: Int? = null,
    val everyDays: Int? = null,
    val warnKm: Int? = null,
    val warnDays: Int? = null,
)

/** The kinds of work that can have an interval, in the order they're shown. */
val INTERVAL_TYPES = listOf(
    ServiceType.ENGINE_OIL, ServiceType.MINOR_SERVICING, ServiceType.MAJOR_SERVICING,
    ServiceType.FLUID_CHANGE, ServiceType.BIKE_WASH,
)

/**
 * Starting intervals for a new bike. Oil every 5,000 km matches the spreadsheet's oil changes
 * (about every 5,000–5,900 km); servicing follows common small sports bike schedules.
 */
fun defaultInterval(profileId: Long, type: ServiceType): ServiceInterval = when (type) {
    ServiceType.ENGINE_OIL -> ServiceInterval(profileId, type, true, everyKm = 5000, everyDays = 180, warnKm = 500, warnDays = 14)
    ServiceType.MINOR_SERVICING -> ServiceInterval(profileId, type, true, everyKm = 10000, everyDays = 365, warnKm = 1000, warnDays = 30)
    ServiceType.MAJOR_SERVICING -> ServiceInterval(profileId, type, true, everyKm = 20000, everyDays = 730, warnKm = 2000, warnDays = 60)
    ServiceType.FLUID_CHANGE -> ServiceInterval(profileId, type, false, everyKm = 20000, everyDays = 730, warnKm = 1000, warnDays = 30)
    else -> ServiceInterval(profileId, type, false, everyKm = null, everyDays = 30, warnKm = null, warnDays = 5)
}

/** The bike's saved intervals, with the defaults for any it hasn't changed. */
fun withDefaults(profileId: Long, saved: List<ServiceInterval>): List<ServiceInterval> =
    INTERVAL_TYPES.map { type -> saved.firstOrNull { it.type == type } ?: defaultInterval(profileId, type) }

enum class DueLevel { OK, SOON, OVERDUE, UNKNOWN }

data class IntervalStatus(
    val interval: ServiceInterval,
    /** The last time this was done, or null if it never has been. */
    val last: ServiceRecord?,
    val kmLeft: Int?,
    val daysLeft: Long?,
    val level: DueLevel,
    /** How much of the interval has been used, 0 to 1 (or more when overdue). */
    val used: Float,
) {
    val type: ServiceType get() = interval.type

    /** "340 km or 12 days left", "Overdue by 120 km", "Due today". */
    fun summary(): String {
        if (level == DueLevel.UNKNOWN) return "Not done yet, so nothing to count from"
        val km = kmLeft
        val days = daysLeft
        val parts = mutableListOf<String>()
        if (level == DueLevel.OVERDUE) {
            if (km != null && km <= 0) parts += "${fmt(-km)} km"
            if (days != null && days <= 0) parts += if (days == 0L) "today" else "${-days} days"
            return if (parts == listOf("today")) "Due today" else "Overdue by " + parts.joinToString(" and ")
        }
        km?.let { parts += "${fmt(it)} km" }
        days?.let { parts += if (it == 1L) "1 day" else "$it days" }
        return parts.joinToString(" or ") + " left"
    }

    private fun fmt(v: Int) = String.format(java.util.Locale.ENGLISH, "%,d", v)
}

/**
 * Where an interval stands, from the last record of its kind and the bike's current odometer.
 * [records] can be in any order.
 */
fun status(interval: ServiceInterval, records: List<ServiceRecord>, odometerKm: Int?, today: LocalDate): IntervalStatus {
    val last = records.filter { it.type == interval.type }.maxWithOrNull(compareBy({ it.date }, { it.odometerKm ?: 0 }))
    if (last == null) return IntervalStatus(interval, null, null, null, DueLevel.UNKNOWN, 0f)
    val kmLeft = interval.everyKm?.let { every -> last.odometerKm?.let { at -> odometerKm?.let { now -> at + every - now } } }
    val daysLeft = interval.everyDays?.let { every -> ChronoUnit.DAYS.between(today, last.date.plusDays(every.toLong())) }
    if (kmLeft == null && daysLeft == null) return IntervalStatus(interval, last, null, null, DueLevel.UNKNOWN, 0f)
    val level = when {
        (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0) -> DueLevel.OVERDUE
        (kmLeft != null && interval.warnKm != null && kmLeft <= interval.warnKm) ||
            (daysLeft != null && interval.warnDays != null && daysLeft <= interval.warnDays) -> DueLevel.SOON
        else -> DueLevel.OK
    }
    val kmUsed = kmLeft?.let { 1f - it.toFloat() / interval.everyKm } ?: 0f
    val daysUsed = daysLeft?.let { 1f - it.toFloat() / interval.everyDays } ?: 0f
    return IntervalStatus(interval, last, kmLeft, daysLeft, level, max(kmUsed, daysUsed).coerceAtLeast(0f))
}

@Dao
interface IntervalDao {
    @Query("SELECT * FROM service_intervals WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<List<ServiceInterval>>

    @Query("SELECT * FROM service_intervals WHERE profileId = :profileId")
    suspend fun getAll(profileId: Long): List<ServiceInterval>

    @Upsert
    suspend fun save(interval: ServiceInterval)
}
