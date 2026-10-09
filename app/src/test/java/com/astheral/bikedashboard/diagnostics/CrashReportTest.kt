package com.astheral.bikedashboard.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CrashReportTest {
    private val context = CrashContext(
        time = ZonedDateTime.of(2026, 10, 9, 14, 30, 12, 0, ZoneOffset.ofHours(8)),
        appVersion = "0.5",
        versionCode = 29,
        androidVersion = "16",
        sdk = 36,
        device = "Google Pixel 8",
        thread = "main",
        events = listOf("14:29:58 App started", "14:30:01 Opened SettingsRoute"),
    )

    private val error = IllegalStateException("Fill-up not found", IOException("disk full"))

    @Test
    fun logHasHeaderDetailsEventsAndFullTrace() {
        val log = formatCrashLog(LogKind.CRASH, context, error)
        assertTrue(log.startsWith("=== Bike Dashboard diagnostic log ==="))
        assertTrue(log.contains("com.astheral.bikedashboard"))
        assertTrue(log.contains("Type: Crash (the app closed)"))
        assertTrue(log.contains("Time: 2026-10-09 14:30:12 +08:00"))
        assertTrue(log.contains("App version: 0.5 (versionCode 29)"))
        assertTrue(log.contains("Android: 16 (API 36)"))
        assertTrue(log.contains("Device: Google Pixel 8"))
        assertTrue(log.contains("Thread: main"))
        assertTrue(log.contains("- 14:30:01 Opened SettingsRoute"))
        assertTrue(log.contains("java.lang.IllegalStateException: Fill-up not found"))
        assertTrue(log.contains("Caused by: java.io.IOException: disk full"))
        assertTrue(log.contains("CrashReportTest"))
    }

    @Test
    fun noteAndMissingEventsAreShown() {
        val log = formatCrashLog(LogKind.ERROR, context.copy(events = emptyList()), IOException("nope"), note = "Saving a full backup")
        assertTrue(log.contains("While: Saving a full backup"))
        assertTrue(log.contains("- (none recorded)"))
    }

    @Test
    fun summaryNamesTheRootCause() {
        assertEquals(
            "java.lang.IllegalStateException: Fill-up not found (caused by java.io.IOException: disk full)",
            summaryOf(error),
        )
        assertEquals("java.lang.NullPointerException", summaryOf(NullPointerException()))
    }

    @Test
    fun summaryReadsBackFromTheSavedText() {
        val error = formatCrashLog(LogKind.ERROR, context, IOException("nope"))
        assertEquals(LogSummary(LogKind.ERROR, "2026-10-09 14:30:12 +08:00", "java.io.IOException: nope"), parseLogSummary(error))
        assertEquals(LogKind.CRASH, parseLogSummary(formatCrashLog(LogKind.CRASH, context, error = IOException())).kind)
        assertEquals("Unknown error", parseLogSummary("garbage").summary)
    }

    @Test
    fun pruneKeepsTheNewest() {
        val names = (1..25).map { "log-%013d-crash.txt".format(it * 1000L) }.shuffled()
        val gone = logsToPrune(names, 20)
        assertEquals((1..5).map { "log-%013d-crash.txt".format(it * 1000L) }, gone)
        assertEquals(emptyList<String>(), logsToPrune(names.take(3), 20))
    }
}
