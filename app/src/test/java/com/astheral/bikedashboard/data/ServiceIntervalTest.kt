package com.astheral.bikedashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ServiceIntervalTest {
    private val oil = ServiceInterval(1, ServiceType.ENGINE_OIL, everyKm = 5000, everyDays = 180, warnKm = 500, warnDays = 14)
    private val today = LocalDate.of(2026, 10, 8)
    private fun oilChange(date: String, km: Int) = ServiceRecord(profileId = 1, date = LocalDate.parse(date), odometerKm = km, type = ServiceType.ENGINE_OIL)

    @Test
    fun countsFromTheLatestRecordOfThatKind() {
        val records = listOf(
            oilChange("2026-01-10", 148330),
            oilChange("2026-07-15", 159057),
            ServiceRecord(profileId = 1, date = LocalDate.parse("2026-09-01"), odometerKm = 161000, type = ServiceType.BIKE_WASH),
        )
        val s = status(oil, records, odometerKm = 162000, today = today)
        assertEquals(159057 + 5000 - 162000, s.kmLeft)
        assertEquals(95L, s.daysLeft) // 15 Jul + 180 days = 11 Jan
        assertEquals(DueLevel.OK, s.level)
        assertEquals("2,057 km or 95 days left", s.summary())
    }

    @Test
    fun warnsInsideEitherWindow() {
        val byKm = status(oil, listOf(oilChange("2026-09-01", 159057)), odometerKm = 163600, today = today)
        assertEquals(DueLevel.SOON, byKm.level)
        val byDays = status(oil, listOf(oilChange("2026-04-20", 160000)), odometerKm = 160100, today = today)
        assertEquals(DueLevel.SOON, byDays.level) // due 17 Oct, 9 days away
    }

    @Test
    fun overdueWhenEitherRunsOut() {
        val s = status(oil, listOf(oilChange("2026-09-01", 157000)), odometerKm = 162120, today = today)
        assertEquals(DueLevel.OVERDUE, s.level)
        assertEquals("Overdue by 120 km", s.summary())
    }

    @Test
    fun unknownWithoutARecord() {
        val s = status(oil, emptyList(), odometerKm = 160000, today = today)
        assertEquals(DueLevel.UNKNOWN, s.level)
        assertNull(s.kmLeft)
    }

    @Test
    fun savedIntervalsReplaceDefaults() {
        val all = withDefaults(1, listOf(oil.copy(everyKm = 4000)))
        assertEquals(INTERVAL_TYPES, all.map { it.type })
        assertEquals(4000, all.first().everyKm)
        assertEquals(10000, all[1].everyKm)
    }
}
