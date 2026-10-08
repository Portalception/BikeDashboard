package com.astheral.bikedashboard.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.astheral.bikedashboard.MainActivity
import com.astheral.bikedashboard.R
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.DueLevel
import com.astheral.bikedashboard.data.IntervalStatus
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.data.status
import com.astheral.bikedashboard.data.withDefaults
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.short
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class ReminderFrequency(val label: String) { OFF("Off"), DAILY("Daily"), WEEKLY("Weekly") }

/** Hours offered for the reminder, 24-hour clock. */
val REMINDER_HOURS = listOf(7, 9, 12, 18, 21)

fun hourLabel(hour: Int) = when {
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}

/**
 * Service reminders. Once a day (at the hour picked in Settings) the app looks at every bike's
 * intervals. When something enters its warning window you get a notification, then another one
 * each day or each week (as picked) until it's done, which you mark by adding a service record.
 */
object Reminders {
    private const val CHANNEL = "service_reminders"
    private const val REQUEST_CHECK = 1
    const val EXTRA_OPEN_SERVICING = "open_servicing_for_profile"

    /** Sets up (or cancels) the daily check to match Settings. Safe to call any number of times. */
    fun schedule(context: Context) {
        val settings = Settings.get(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CHECK, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        if (settings.reminderFrequency.value == ReminderFrequency.OFF) return
        var next = LocalDateTime.now().withHour(settings.reminderHour.value).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact: Android may shift it a little to save battery, which is fine for a reminder.
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pending)
    }

    /** Whether the app may post notifications (Android 13+ asks the user). */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Every enabled interval on every bike that's due soon or overdue. */
    suspend fun dueNow(context: Context, today: LocalDate = LocalDate.now()): List<Pair<Profile, IntervalStatus>> {
        val db = AppDatabase.get(context)
        return db.profileDao().getAll().flatMap { profile ->
            val records = db.serviceDao().getAll(profile.id)
            val odometer = currentOdometer(db.fillUpDao().getAll(profile.id), records)
            withDefaults(profile.id, db.intervalDao().getAll(profile.id))
                .filter { it.enabled }
                .map { profile to status(it, records, odometer, today) }
        }.filter { (_, s) -> s.level == DueLevel.SOON || s.level == DueLevel.OVERDUE }
    }

    /** Posts the notifications that are due today, following the daily or weekly choice. */
    suspend fun check(context: Context, today: LocalDate = LocalDate.now()) {
        val frequency = Settings.get(context).reminderFrequency.value
        if (frequency == ReminderFrequency.OFF) return
        val sent = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val due = dueNow(context, today)
        val dueKeys = due.map { (p, s) -> key(p, s) }.toSet()
        // Anything no longer due (it was done) starts afresh next time.
        sent.edit().apply { sent.all.keys.filter { it !in dueKeys }.forEach { remove(it) } }.apply()
        if (!allowed(context)) return
        createChannel(context)
        due.forEach { (profile, s) ->
            val k = key(profile, s)
            val lastSent = sent.getLong(k, Long.MIN_VALUE)
            val gap = if (frequency == ReminderFrequency.WEEKLY) 7 else 1
            if (lastSent == Long.MIN_VALUE || today.toEpochDay() - lastSent >= gap) {
                notify(context, profile, s)
                sent.edit().putLong(k, today.toEpochDay()).apply()
            }
        }
    }

    private fun key(profile: Profile, s: IntervalStatus) = "${profile.id}:${s.type.name}"

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Service reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When an oil change or service is coming up"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @Suppress("MissingPermission") // checked by allowed() first
    private fun notify(context: Context, profile: Profile, s: IntervalStatus) {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_SERVICING, profile.id)
        val id = (profile.id * 100 + s.type.ordinal).toInt()
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (s.level == DueLevel.OVERDUE) "${s.type.label} is due · ${profile.name}" else "${s.type.label} coming up · ${profile.name}"
        val last = s.last?.let { r -> "Last done ${r.date.short()}" + (r.odometerKm?.let { " at ${km(it)}" } ?: "") + "." }
        val text = s.summary() + "."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(text, last).joinToString(" ")))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/** The daily check, run by the alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.check(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}

/** Alarms are cleared when the phone restarts, so set the daily check up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(context.applicationContext)
        }
    }
}
