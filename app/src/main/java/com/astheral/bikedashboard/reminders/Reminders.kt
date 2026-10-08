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
import com.astheral.bikedashboard.calc.LegalDue
import com.astheral.bikedashboard.calc.legalDue
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.DueLevel
import com.astheral.bikedashboard.data.IntervalStatus
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Settings
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.data.nameOf
import com.astheral.bikedashboard.data.status
import com.astheral.bikedashboard.data.withDefaults
import com.astheral.bikedashboard.ui.km
import com.astheral.bikedashboard.ui.short
import com.astheral.bikedashboard.calc.ReminderSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Service reminders. On the days and at the time picked in Settings (every N days, some
 * weekdays, or a day of the month; see [ReminderSchedule]) the app looks at every bike's
 * intervals. Everything in its warning window or overdue gets a notification on each of those
 * days until it's done, which you mark by adding a service record.
 *
 * The same check also covers paperwork (COE, insurance, road tax, inspection) switched on in
 * Bike details: from a set number of days before it expires until a new date is entered. Those
 * go to their own "Paperwork reminders" channel and open the bike's details page.
 */
object Reminders {
    private const val CHANNEL = "service_reminders"
    private const val REQUEST_CHECK = 1
    private const val WINDOW_MS = 30 * 60 * 1000L
    private const val GROUP = "service_reminders"
    private const val SUMMARY_ID = 1 // reminders use a number made from the bike and category, never 1
    const val EXTRA_OPEN_SERVICING = "open_servicing_for_profile"
    const val EXTRA_OPEN_DETAILS = "open_details_for_profile"
    private const val PAPERWORK_CHANNEL = "paperwork_reminders"
    private const val PAPERWORK_GROUP = "paperwork_reminders"
    /** Paperwork notifications are tagged, so their ids never clash with the service ones. */
    private const val PAPERWORK_TAG = "paperwork"
    private const val PAPERWORK_SUMMARY_ID = 0
    /** Added to paperwork tap request codes, so they never replace a servicing one. */
    private const val PAPERWORK_REQUEST_BASE = 1_000_000

    /** Sets up (or cancels) the next check to match Settings. Safe to call any number of times. */
    fun schedule(context: Context) {
        val settings = Settings.get(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CHECK, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        val next = settings.reminderSchedule.value.next(LocalDateTime.now()) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Within half an hour of the chosen time. A repeating inexact alarm can run up to 18 hours
        // late, and reminder days aren't evenly spaced anyway, so this is a one-off that the
        // receiver sets again for the next reminder day.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
    }

    /** Whether the app may post notifications (Android 13+ asks the user). */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Every enabled interval on every bike that's due soon or overdue. */
    suspend fun dueNow(context: Context, today: LocalDate = LocalDate.now()): List<Pair<Profile, IntervalStatus>> {
        val db = AppDatabase.get(context)
        val categories = db.categoryDao().getAll()
        return db.profileDao().getAll().flatMap { profile ->
            val records = db.serviceDao().getAll(profile.id)
            val odometer = currentOdometer(db.fillUpDao().getAll(profile.id), records)
            withDefaults(profile.id, db.intervalDao().getAll(profile.id), categories)
                .filter { it.enabled }
                .map { profile to status(it, records, odometer, today, categories.nameOf(it.type)) }
        }.filter { (_, s) -> s.level == DueLevel.SOON || s.level == DueLevel.OVERDUE }
    }

    /** Every paperwork expiry, on every bike, that's switched on and within its reminder window. */
    suspend fun paperworkDue(context: Context, today: LocalDate = LocalDate.now()): List<Pair<Profile, LegalDue>> {
        val db = AppDatabase.get(context)
        val settings = Settings.get(context)
        val remind = settings.paperworkReminders.value
        val lead = settings.paperworkLeadDays.value
        return db.profileDao().getAll().flatMap { profile ->
            val items = remind[profile.id].orEmpty()
            val details = if (items.isEmpty()) null else db.bikeDetailsDao().get(profile.id)
            details?.let { legalDue(it, items, lead, today) }.orEmpty().map { profile to it }
        }
    }

    /**
     * Posts a notification for everything that's due. [scheduled] is true for the alarm's run on
     * a reminder day, which covers everything not already notified today. Other checks (right
     * after changing a setting) only post what has never been notified, so editing doesn't
     * bring back reminders you've already seen.
     */
    suspend fun check(context: Context, today: LocalDate = LocalDate.now(), scheduled: Boolean = false) {
        if (!Settings.get(context).reminderSchedule.value.on) return
        val sent = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val due = dueNow(context, today)
        val paperwork = paperworkDue(context, today)
        val dueKeys = due.map { (p, s) -> key(p, s) }.toSet() + paperwork.map { (p, l) -> key(p, l) }
        // Anything no longer due (it was done) starts afresh next time.
        sent.edit().apply { sent.all.keys.filter { it !in dueKeys }.forEach { remove(it) } }.apply()
        if (!allowed(context)) return
        createChannel(context)
        // Never the same thing twice in one day.
        fun ready(key: String): Boolean {
            val lastSent = sent.getLong(key, Long.MIN_VALUE)
            return lastSent == Long.MIN_VALUE || (scheduled && lastSent != today.toEpochDay())
        }
        val toSend = due.filter { (profile, s) -> ready(key(profile, s)) }
        toSend.forEach { (profile, s) ->
            notify(context, profile, s)
            sent.edit().putLong(key(profile, s), today.toEpochDay()).apply()
        }
        if (toSend.size > 1) notifySummary(context, toSend)

        val papersToSend = paperwork.filter { (profile, l) -> ready(key(profile, l)) }
        if (papersToSend.isNotEmpty()) createPaperworkChannel(context)
        papersToSend.forEach { (profile, l) ->
            notifyPaperwork(context, profile, l)
            sent.edit().putLong(key(profile, l), today.toEpochDay()).apply()
        }
        if (papersToSend.size > 1) notifyPaperworkSummary(context, papersToSend)
    }

    private fun key(profile: Profile, s: IntervalStatus) = "${profile.id}:${s.type}"

    private fun key(profile: Profile, l: LegalDue) = "paperwork:${profile.id}:${l.item.name}"

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Service reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When an oil change or service is coming up"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun createPaperworkChannel(context: Context) {
        val channel = NotificationChannel(PAPERWORK_CHANNEL, "Paperwork reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When the COE, insurance, road tax or inspection is about to run out"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Opens [profileId]'s Bike details page. */
    private fun openDetails(context: Context, profileId: Long, requestCode: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_DETAILS, profileId)
        return PendingIntent.getActivity(
            context, PAPERWORK_REQUEST_BASE + requestCode, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "COE expires soon · R3", saying how long is left and the date. */
    @Suppress("MissingPermission") // checked by allowed() first
    private fun notifyPaperwork(context: Context, profile: Profile, l: LegalDue) {
        val id = (profile.id * 10 + l.item.ordinal + 1).toInt() // never PAPERWORK_SUMMARY_ID
        val title = "${l.item.label} ${if (l.expired) "has expired" else "expires soon"} · ${profile.name}"
        val text = "${l.summary()} (${l.expiry.short()})."
        val notification = NotificationCompat.Builder(context, PAPERWORK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openDetails(context, profile.id, id))
            .setGroup(PAPERWORK_GROUP)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(PAPERWORK_TAG, id, notification)
    }

    /** Our own summary for a bundle of paperwork reminders, so tapping it opens Bike details. */
    @Suppress("MissingPermission") // checked by allowed() first
    private fun notifyPaperworkSummary(context: Context, due: List<Pair<Profile, LegalDue>>) {
        val (profile, _) = due.first()
        val notification = NotificationCompat.Builder(context, PAPERWORK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle("Paperwork reminders")
            .setContentText("${due.size} expiry dates need attention")
            .setGroup(PAPERWORK_GROUP)
            .setGroupSummary(true)
            .setContentIntent(openDetails(context, profile.id, PAPERWORK_SUMMARY_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(PAPERWORK_TAG, PAPERWORK_SUMMARY_ID, notification)
    }

    /** Opens [profileId]'s Servicing page. */
    private fun openServicing(context: Context, profileId: Long, requestCode: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_SERVICING, profileId)
        return PendingIntent.getActivity(context, requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Our own summary for the bundle Android makes of several reminders, so tapping the bundle
     * opens Servicing too (Android's own summary only opens the app).
     */
    @Suppress("MissingPermission") // checked by allowed() first
    private fun notifySummary(context: Context, due: List<Pair<Profile, IntervalStatus>>) {
        val (profile, _) = due.first()
        val text = "${due.size} services need attention"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle("Service reminders")
            .setContentText(text)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(openServicing(context, profile.id, SUMMARY_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, notification)
    }

    @Suppress("MissingPermission") // checked by allowed() first
    private fun notify(context: Context, profile: Profile, s: IntervalStatus) {
        // The same bike and category always get the same number, so a repeat replaces the last one.
        val id = key(profile, s).hashCode().let { if (it == SUMMARY_ID) SUMMARY_ID + 1 else it }
        val tap = openServicing(context, profile.id, id)
        val title = if (s.level == DueLevel.OVERDUE) "${s.name} is due · ${profile.name}" else "${s.name} coming up · ${profile.name}"
        val last = s.last?.let { r -> "Last done ${r.date.short()}" + (r.odometerKm?.let { " at ${km(it)}" } ?: "") + "." }
        val text = s.summary() + "."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fuel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(text, last).joinToString(" ")))
            .setContentIntent(tap)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/** The check on each reminder day, run by the alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.check(context.applicationContext, scheduled = true)
            } finally {
                Reminders.schedule(context.applicationContext) // the next reminder day
                result.finish()
            }
        }
    }
}

/** Alarms are cleared when the phone restarts, so set the next check up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(context.applicationContext)
        }
    }
}
