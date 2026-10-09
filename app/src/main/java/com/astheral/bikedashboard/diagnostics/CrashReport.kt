package com.astheral.bikedashboard.diagnostics

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A crash closes the app; an error was caught and the app carried on. */
enum class LogKind(val label: String, val fileTag: String) {
    CRASH("Crash (the app closed)", "crash"),
    ERROR("Error (caught, the app kept running)", "error"),
}

/** Where and when something went wrong, everything but the error itself. */
data class CrashContext(
    val time: ZonedDateTime,
    val appVersion: String,
    val versionCode: Long,
    val androidVersion: String,
    val sdk: Int,
    val device: String,
    val thread: String,
    /** The last few things the app did (screens opened and so on), oldest first. */
    val events: List<String> = emptyList(),
)

/** One saved log as shown in the list: its kind, when, and a one-line summary. */
data class LogSummary(val kind: LogKind, val time: String, val summary: String)

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
private const val SUMMARY = "Summary: "
private const val TYPE = "Type: "
private const val WHEN = "Time: "

/**
 * A plain-text diagnostic log, written to be pasted into Claude (or any assistant) to fix the
 * bug: a short header saying what app this is, the details of the phone and app version, the
 * recent app events and the full stack trace with every cause.
 */
fun formatCrashLog(kind: LogKind, context: CrashContext, error: Throwable, note: String? = null): String = buildString {
    appendLine("=== Bike Dashboard diagnostic log ===")
    appendLine(
        "This log comes from Bike Dashboard (package com.astheral.bikedashboard), a personal Android " +
            "fuel and servicing tracker written in Kotlin with Jetpack Compose and Room. " +
            "Please help me find the cause of this ${if (kind == LogKind.CRASH) "crash" else "error"} and fix it.",
    )
    appendLine()
    appendLine("$TYPE${kind.label}")
    appendLine("$WHEN${context.time.format(TIME)}")
    appendLine("App version: ${context.appVersion} (versionCode ${context.versionCode})")
    appendLine("Android: ${context.androidVersion} (API ${context.sdk})")
    appendLine("Device: ${context.device}")
    appendLine("Thread: ${context.thread}")
    if (!note.isNullOrBlank()) appendLine("While: $note")
    appendLine("$SUMMARY${summaryOf(error)}")
    appendLine()
    appendLine("Recent app events (oldest first):")
    if (context.events.isEmpty()) appendLine("- (none recorded)")
    context.events.forEach { appendLine("- $it") }
    appendLine()
    appendLine("Stack trace:")
    append(error.stackTraceToString().trimEnd())
    appendLine()
}

/** "ExceptionClass: message", plus the deepest cause when there is one, which is usually the real problem. */
fun summaryOf(error: Throwable): String {
    fun one(t: Throwable) = t.message?.let { "${t.javaClass.name}: ${it.lineSequence().first()}" } ?: t.javaClass.name
    var root = error
    val seen = mutableSetOf<Throwable>()
    while (true) {
        val cause = root.cause ?: break
        if (!seen.add(cause) || cause === root) break
        root = cause
    }
    return if (root === error) one(error) else "${one(error)} (caused by ${one(root)})"
}

/** Reads the kind, time and summary back out of a saved log, for the list of logs. */
fun parseLogSummary(text: String): LogSummary {
    val lines = text.lineSequence().take(20).toList()
    fun field(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
    val type = field(TYPE)
    return LogSummary(
        kind = LogKind.entries.firstOrNull { it.label == type } ?: LogKind.CRASH,
        time = field(WHEN).orEmpty(),
        summary = field(SUMMARY) ?: "Unknown error",
    )
}

/** Which saved logs to delete so only the newest [keep] stay. Names sort oldest first. */
fun logsToPrune(names: List<String>, keep: Int): List<String> =
    names.sorted().dropLast(keep.coerceAtLeast(0))
