package com.astheral.bikedashboard.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How reminders repeat. */
enum class RepeatKind(val label: String) {
    OFF("Off"),
    EVERY_N_DAYS("Days"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

/** The most days allowed between "every N days" reminders. */
const val MAX_EVERY_DAYS = 60

/**
 * When reminders arrive: a time of day, on the days [kind] picks.
 *
 * - [RepeatKind.EVERY_N_DAYS]: every [everyDays] days (1 is daily), counted from [anchor].
 * - [RepeatKind.WEEKLY]: on each of [weekdays].
 * - [RepeatKind.MONTHLY]: on [dayOfMonth] each month; months too short for it use their last day.
 *
 * On each of those days every service and paperwork item that's due gets a notification.
 */
data class ReminderSchedule(
    val kind: RepeatKind = RepeatKind.WEEKLY,
    val time: LocalTime = LocalTime.of(9, 0),
    val everyDays: Int = 1,
    val anchor: LocalDate = LocalDate.of(2026, 1, 1),
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val dayOfMonth: Int = 1,
) {
    val on: Boolean get() = kind != RepeatKind.OFF

    /** Whether reminders arrive on [date]. */
    fun runsOn(date: LocalDate): Boolean = when (kind) {
        RepeatKind.OFF -> false
        RepeatKind.EVERY_N_DAYS -> Math.floorMod(ChronoUnit.DAYS.between(anchor, date), everyDays.coerceAtLeast(1).toLong()) == 0L
        RepeatKind.WEEKLY -> date.dayOfWeek in weekdays
        RepeatKind.MONTHLY -> date.dayOfMonth == dayOfMonth.coerceIn(1, 31).coerceAtMost(date.lengthOfMonth())
    }

    /** The first time reminders arrive strictly after [after], or null when they're off (or no day is picked). */
    fun next(after: LocalDateTime): LocalDateTime? {
        if (!on) return null
        // Every kind repeats within 62 days (two months, or MAX_EVERY_DAYS), so this always finds one.
        var day = after.toLocalDate()
        repeat(MAX_EVERY_DAYS + 63) {
            val at = day.atTime(time)
            if (at.isAfter(after) && runsOn(day)) return at
            day = day.plusDays(1)
        }
        return null
    }

    /** "Every Wednesday at 7:30 pm", "Every 2 days at 9:00 am", "On the 4th of every month at …". */
    fun summary(): String = when (kind) {
        RepeatKind.OFF -> "Off"
        RepeatKind.EVERY_N_DAYS -> (if (everyDays <= 1) "Every day" else "Every $everyDays days") + " at ${timeLabel(time)}"
        RepeatKind.WEEKLY -> "${weekdaysLabel(weekdays)} at ${timeLabel(time)}"
        RepeatKind.MONTHLY -> "On the ${ordinal(dayOfMonth)} of every month at ${timeLabel(time)}"
    }
}

/** "7:30 pm", "12:00 am". */
fun timeLabel(time: LocalTime): String {
    val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
    return "$hour:${time.minute.toString().padStart(2, '0')} ${if (time.hour < 12) "am" else "pm"}"
}

/** "1st", "2nd", "3rd", "4th", "11th", "21st". */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** "Every Wednesday", "Every Mon, Wed and Fri", "Every day", "Every weekday". */
fun weekdaysLabel(days: Set<DayOfWeek>): String {
    val sorted = days.sorted()
    return when {
        sorted.isEmpty() -> "No days picked"
        sorted.size == 7 -> "Every day"
        sorted == DayOfWeek.entries.take(5) -> "Every weekday"
        sorted == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Every weekend"
        sorted.size == 1 -> "Every " + sorted[0].getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        else -> {
            val names = sorted.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            "Every " + names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }
}

/**
 * The schedule for someone upgrading from the old setting, which only had a frequency (OFF,
 * DAILY or WEEKLY, as saved) and an hour. Daily becomes every day, weekly becomes weekly on
 * [today]'s weekday, and the hour carries over on the hour. Nothing saved counts as weekly,
 * the old default.
 */
fun migrateOldReminders(oldFrequency: String?, oldHour: Int, today: LocalDate): ReminderSchedule {
    val time = LocalTime.of(oldHour.coerceIn(0, 23), 0)
    val base = ReminderSchedule(time = time, anchor = today, weekdays = setOf(today.dayOfWeek), dayOfMonth = today.dayOfMonth)
    return when (oldFrequency) {
        "OFF" -> base.copy(kind = RepeatKind.OFF)
        "DAILY" -> base.copy(kind = RepeatKind.EVERY_N_DAYS, everyDays = 1)
        else -> base.copy(kind = RepeatKind.WEEKLY)
    }
}
