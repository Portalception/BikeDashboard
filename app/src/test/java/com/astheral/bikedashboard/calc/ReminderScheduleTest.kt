package com.astheral.bikedashboard.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderScheduleTest {
    private val time = LocalTime.of(19, 30)
    private fun at(date: String, hh: Int = 12, mm: Int = 0) = LocalDate.parse(date).atTime(hh, mm)

    @Test
    fun offNeverRuns() {
        assertNull(ReminderSchedule(kind = RepeatKind.OFF).next(at("2026-10-09")))
    }

    @Test
    fun laterTodayWhenTheTimeHasNotPassed() {
        val daily = ReminderSchedule(RepeatKind.EVERY_N_DAYS, time, everyDays = 1, anchor = LocalDate.parse("2026-10-01"))
        assertEquals(at("2026-10-09", 19, 30), daily.next(at("2026-10-09", 19, 29)))
        // Exactly at the time counts as passed, so a run that's just happened isn't repeated.
        assertEquals(at("2026-10-10", 19, 30), daily.next(at("2026-10-09", 19, 30)))
    }

    @Test
    fun everyNDaysCountsFromTheAnchor() {
        val s = ReminderSchedule(RepeatKind.EVERY_N_DAYS, time, everyDays = 3, anchor = LocalDate.parse("2026-10-01"))
        // Runs on 1, 4, 7, 10 Oct …
        assertEquals(at("2026-10-10", 19, 30), s.next(at("2026-10-08")))
        assertEquals(at("2026-10-07", 19, 30), s.next(at("2026-10-07", 8)))
        assertTrue(s.runsOn(LocalDate.parse("2026-09-28"))) // before the anchor too
        assertFalse(s.runsOn(LocalDate.parse("2026-09-29")))
    }

    @Test
    fun weeklyWrapsIntoNextWeek() {
        val s = ReminderSchedule(RepeatKind.WEEKLY, time, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        // Fri 9 Oct 2026 -> Mon 12 Oct
        assertEquals(at("2026-10-12", 19, 30), s.next(at("2026-10-09")))
        // Wed 14 Oct after the time -> Mon 19 Oct
        assertEquals(at("2026-10-19", 19, 30), s.next(at("2026-10-14", 20)))
        // Sunday -> Monday, across the end of the week
        assertEquals(at("2026-10-12", 19, 30), s.next(at("2026-10-11", 23)))
    }

    @Test
    fun weeklyWithNoDaysNeverRuns() {
        assertNull(ReminderSchedule(RepeatKind.WEEKLY, time, weekdays = emptySet()).next(at("2026-10-09")))
    }

    @Test
    fun monthlyUsesTheLastDayOfShortMonths() {
        val s = ReminderSchedule(RepeatKind.MONTHLY, time, dayOfMonth = 31)
        assertEquals(at("2026-10-31", 19, 30), s.next(at("2026-10-09")))
        assertEquals(at("2026-11-30", 19, 30), s.next(at("2026-10-31", 20)))
        assertEquals(at("2027-02-28", 19, 30), s.next(at("2027-01-31", 20)))
        assertEquals(at("2028-02-29", 19, 30), s.next(at("2028-02-01"))) // leap year
        val fourth = ReminderSchedule(RepeatKind.MONTHLY, time, dayOfMonth = 4)
        assertEquals(at("2026-11-04", 19, 30), fourth.next(at("2026-10-09")))
        assertEquals(at("2027-01-04", 19, 30), fourth.next(at("2026-12-04", 21)))
    }

    @Test
    fun summaries() {
        assertEquals("Every Wednesday at 7:30 pm", ReminderSchedule(RepeatKind.WEEKLY, time, weekdays = setOf(DayOfWeek.WEDNESDAY)).summary())
        assertEquals(
            "Every Mon, Wed and Fri at 9:00 am",
            ReminderSchedule(RepeatKind.WEEKLY, weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)).summary(),
        )
        assertEquals("Every day at 12:05 am", ReminderSchedule(RepeatKind.EVERY_N_DAYS, LocalTime.of(0, 5), everyDays = 1).summary())
        assertEquals("Every 2 days at 12:00 pm", ReminderSchedule(RepeatKind.EVERY_N_DAYS, LocalTime.NOON, everyDays = 2).summary())
        assertEquals("On the 4th of every month at 7:30 pm", ReminderSchedule(RepeatKind.MONTHLY, time, dayOfMonth = 4).summary())
        assertEquals("21st", ordinal(21))
        assertEquals("12th", ordinal(12))
        assertEquals("Every weekday", weekdaysLabel(DayOfWeek.entries.take(5).toSet()))
    }

    @Test
    fun migratesTheOldSetting() {
        val today = LocalDate.parse("2026-10-09") // a Friday
        val daily = migrateOldReminders("DAILY", 18, today)
        assertEquals(RepeatKind.EVERY_N_DAYS, daily.kind)
        assertEquals(1, daily.everyDays)
        assertEquals(LocalTime.of(18, 0), daily.time)
        val weekly = migrateOldReminders("WEEKLY", 7, today)
        assertEquals(RepeatKind.WEEKLY, weekly.kind)
        assertEquals(setOf(DayOfWeek.FRIDAY), weekly.weekdays)
        assertEquals(LocalTime.of(7, 0), weekly.time)
        assertEquals(RepeatKind.OFF, migrateOldReminders("OFF", 9, today).kind)
        assertEquals(RepeatKind.WEEKLY, migrateOldReminders(null, 9, today).kind)
    }

    @Test
    fun nextIsAlwaysFoundForTheLongestGap() {
        val s = ReminderSchedule(RepeatKind.EVERY_N_DAYS, time, everyDays = MAX_EVERY_DAYS, anchor = LocalDate.parse("2026-10-09"))
        assertEquals(LocalDateTime.of(2026, 12, 8, 19, 30), s.next(at("2026-10-09", 20)))
    }
}
