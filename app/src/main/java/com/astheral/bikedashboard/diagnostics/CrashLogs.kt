package com.astheral.bikedashboard.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/** One saved diagnostic log file. */
data class SavedLog(val file: File, val text: String, val summary: LogSummary)

/**
 * Diagnostic logs: crashes are caught by a global handler and written to the app's own storage
 * (nothing leaves the phone unless you share it). Non-fatal errors the app catches can be
 * logged too. Only the newest [MAX_LOGS] are kept.
 */
object CrashLogs {
    private const val MAX_LOGS = 20
    private const val MAX_EVENTS = 15
    private val EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val events = ArrayDeque<String>()
    private lateinit var appContext: Context

    /** Call once when the app starts, before anything else can crash. */
    fun install(context: Context) {
        appContext = context.applicationContext
        event("App started")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing the log must never stop the crash from being handled normally.
            runCatching { write(LogKind.CRASH, error, thread.name, note = null) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Remembers something the app just did (like opening a screen), to show in the next log. */
    fun event(text: String) {
        synchronized(events) {
            events.addLast("${LocalTime.now().format(EVENT_TIME)} $text")
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    /** Logs an error the app caught and recovered from. [note] says what it was doing. */
    fun error(note: String, error: Throwable) {
        if (!::appContext.isInitialized) return
        event("Error: $note")
        runCatching { write(LogKind.ERROR, error, Thread.currentThread().name, note) }
    }

    private fun write(kind: LogKind, error: Throwable, thread: String, note: String?) {
        val text = formatCrashLog(kind, context(thread), error, note)
        val dir = dir(appContext).apply { mkdirs() }
        File(dir, "log-%013d-%s.txt".format(System.currentTimeMillis(), kind.fileTag)).writeText(text)
        prune(dir)
    }

    private fun context(thread: String): CrashContext {
        val info = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0) }.getOrNull()
        return CrashContext(
            time = ZonedDateTime.now(),
            appVersion = info?.versionName ?: "unknown",
            versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            thread = thread,
            events = synchronized(events) { events.toList() },
        )
    }

    private fun dir(context: Context) = File(context.filesDir, "crash_logs")

    private fun files(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.toList().orEmpty()

    private fun prune(dir: File) {
        val all = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.associateBy { it.name }.orEmpty()
        logsToPrune(all.keys.toList(), MAX_LOGS).forEach { all[it]?.delete() }
    }

    /** Every saved log, newest first. Reads files, so call it off the main thread. */
    fun all(context: Context): List<SavedLog> =
        files(context).sortedByDescending { it.name }.mapNotNull { f ->
            runCatching { f.readText() }.getOrNull()?.let { SavedLog(f, it, parseLogSummary(it)) }
        }

    fun delete(log: SavedLog) {
        log.file.delete()
    }

    fun deleteAll(context: Context) {
        files(context).forEach { it.delete() }
    }
}
