package com.astheral.bikedashboard.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.astheral.bikedashboard.calc.DEFAULT_PAPERWORK_LEAD_DAYS
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.calc.LegalItem
import com.astheral.bikedashboard.reminders.ReminderFrequency
import com.astheral.bikedashboard.ui.DistanceCostUnit
import com.astheral.bikedashboard.ui.EconomyUnit
import com.astheral.bikedashboard.ui.PriceUnit
import com.astheral.bikedashboard.ui.TimeCostUnit
import com.astheral.bikedashboard.ui.Units
import com.astheral.bikedashboard.ui.theme.AppIcon
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import com.astheral.bikedashboard.ui.theme.ColorPreset
import com.astheral.bikedashboard.ui.theme.ProgressStyle
import com.astheral.bikedashboard.ui.theme.ThemeMode
import com.astheral.bikedashboard.ui.theme.WallpaperSupported
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Small app settings, kept on the phone. */
class Settings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * The trip distance from before profiles (version 1 of the database). Only read when upgrading
     * that database; the trip now lives in [tripKm], under its own key.
     */
    val legacyTripKm: Double get() = prefs.getFloat(KEY_TRIP_KM, DEFAULT_TRIP_KM.toFloat()).toDouble()

    private val _tripKm = MutableStateFlow(loadTripKm())

    /**
     * Round trip into Malaysia to pump, taken off the savings of every bike. Null until it's been
     * set; [Profiles.tripKm] then starts it from the trip that used to be kept on each bike.
     */
    val tripKm: StateFlow<Double?> = _tripKm.asStateFlow()

    fun setTripKm(km: Double) {
        prefs.edit().putFloat(KEY_SAVINGS_TRIP_KM, km.toFloat()).apply()
        _tripKm.value = km
    }

    private fun loadTripKm(): Double? =
        if (prefs.contains(KEY_SAVINGS_TRIP_KM)) prefs.getFloat(KEY_SAVINGS_TRIP_KM, DEFAULT_TRIP_KM.toFloat()).toDouble() else null

    private val _activeProfileId = MutableStateFlow(prefs.getLong(KEY_ACTIVE_PROFILE, NO_PROFILE))

    /** The bike being shown. [NO_PROFILE] means "the first one". */
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    fun setActiveProfile(id: Long) {
        prefs.edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
        _activeProfileId.value = id
    }

    private val _ideasUnlocked = MutableStateFlow(prefs.getBoolean(KEY_IDEAS_UNLOCKED, false))

    /** The hidden ideas notebook shows in Settings once found. */
    val ideasUnlocked: StateFlow<Boolean> = _ideasUnlocked.asStateFlow()

    fun unlockIdeas() {
        prefs.edit().putBoolean(KEY_IDEAS_UNLOCKED, true).apply()
        _ideasUnlocked.value = true
    }

    private val _units = MutableStateFlow(loadUnits())

    /** The unit picked for each number on the dashboard; tap a tile to change it. */
    val units: StateFlow<Units> = _units.asStateFlow()

    fun updateUnits(change: (Units) -> Units) {
        _units.update(change)
        val u = _units.value
        prefs.edit()
            .putString(KEY_UNIT_ECONOMY, u.economy.name)
            .putString(KEY_UNIT_DISTANCE_COST, u.distanceCost.name)
            .putString(KEY_UNIT_TIME_COST, u.timeCost.name)
            .putString(KEY_UNIT_PRICE, u.price.name)
            .apply()
    }

    private fun loadUnits() = Units(
        economy = enumOr(KEY_UNIT_ECONOMY, EconomyUnit.KM_PER_L),
        distanceCost = enumOr(KEY_UNIT_DISTANCE_COST, DistanceCostUnit.PER_KM),
        timeCost = enumOr(KEY_UNIT_TIME_COST, TimeCostUnit.PER_DAY),
        price = enumOr(KEY_UNIT_PRICE, PriceUnit.SGD_PER_L),
    )

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == prefs.getString(key, null) } ?: default

    private val _reminderFrequency = MutableStateFlow(enumOr(KEY_REMINDER_FREQUENCY, ReminderFrequency.WEEKLY))

    /** How often to repeat a service reminder once it's due soon. */
    val reminderFrequency: StateFlow<ReminderFrequency> = _reminderFrequency.asStateFlow()

    private val _reminderHour = MutableStateFlow(prefs.getInt(KEY_REMINDER_HOUR, 9))

    /** The hour of the day reminders arrive, 0–23. */
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    fun setReminders(frequency: ReminderFrequency = _reminderFrequency.value, hour: Int = _reminderHour.value) {
        prefs.edit().putString(KEY_REMINDER_FREQUENCY, frequency.name).putInt(KEY_REMINDER_HOUR, hour).apply()
        _reminderFrequency.value = frequency
        _reminderHour.value = hour
    }

    private val _paperworkReminders = MutableStateFlow(loadPaperworkReminders())

    /**
     * Which paperwork expiries to be reminded about, per bike. Off unless switched on. Each is
     * its own saved setting ("paperwork_remind_<bike id>_<item>"), so full backups include them.
     */
    val paperworkReminders: StateFlow<Map<Long, Set<LegalItem>>> = _paperworkReminders.asStateFlow()

    fun setPaperworkReminder(profileId: Long, item: LegalItem, on: Boolean) {
        prefs.edit().putBoolean(paperworkKey(profileId, item), on).apply()
        _paperworkReminders.value = loadPaperworkReminders()
    }

    private fun loadPaperworkReminders(): Map<Long, Set<LegalItem>> =
        prefs.all.mapNotNull { (key, value) ->
            if (value != true || !key.startsWith(KEY_PAPERWORK_PREFIX)) return@mapNotNull null
            val (id, name) = key.removePrefix(KEY_PAPERWORK_PREFIX).split('_', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val item = LegalItem.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            id.toLongOrNull()?.let { it to item }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    private fun paperworkKey(profileId: Long, item: LegalItem) = "$KEY_PAPERWORK_PREFIX${profileId}_${item.name}"

    private val _paperworkLeadDays = MutableStateFlow(prefs.getInt(KEY_PAPERWORK_LEAD_DAYS, DEFAULT_PAPERWORK_LEAD_DAYS))

    /** How many days before a paperwork expiry the reminders start. */
    val paperworkLeadDays: StateFlow<Int> = _paperworkLeadDays.asStateFlow()

    fun setPaperworkLeadDays(days: Int) {
        prefs.edit().putInt(KEY_PAPERWORK_LEAD_DAYS, days).apply()
        _paperworkLeadDays.value = days
    }

    private val _appIcon = MutableStateFlow(enumOr(KEY_APP_ICON, AppIcon.MONOCHROME))

    /** The launcher icon picked in Personalisation. */
    val appIcon: StateFlow<AppIcon> = _appIcon.asStateFlow()

    fun setAppIcon(icon: AppIcon) {
        prefs.edit().putString(KEY_APP_ICON, icon.name).apply()
        _appIcon.value = icon
    }

    private val _appearance = MutableStateFlow(loadAppearance())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) {
        _appearance.update(change)
        val a = _appearance.value
        prefs.edit()
            .putString(KEY_THEME_MODE, a.mode.name)
            .putBoolean(KEY_PURE_BLACK, a.pureBlack)
            .putBoolean(KEY_WALLPAPER, a.wallpaper)
            .putInt(KEY_PRIMARY, a.primary.toArgb())
            .putInt(KEY_ACCENT, a.accent.toArgb())
            .putString(KEY_PROGRESS_STYLE, a.progressStyle.name)
            .remove(KEY_OLD_COLOR_THEME)
            .apply()
    }

    private fun loadAppearance(): AppearanceSettings {
        // Version 0.1+ had one "colour theme" setting; carry it over to the matching colours.
        val old = prefs.getString(KEY_OLD_COLOR_THEME, null)
        val default = when (old) {
            "YAMAHA_BLUE" -> ColorPreset.YAMAHA
            "RACING_RED" -> ColorPreset.HONDA
            "MIDNIGHT" -> ColorPreset.SUZUKI
            "FOREST" -> ColorPreset.KAWASAKI
            else -> ColorPreset.MONOCHROME
        }
        return AppearanceSettings(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) } ?: ThemeMode.SYSTEM,
            pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false),
            wallpaper = WallpaperSupported && prefs.getBoolean(KEY_WALLPAPER, old == "WALLPAPER"),
            primary = Color(prefs.getInt(KEY_PRIMARY, default.primary.toArgb())),
            accent = Color(prefs.getInt(KEY_ACCENT, default.accent.toArgb())),
            progressStyle = ProgressStyle.entries.firstOrNull { it.name == prefs.getString(KEY_PROGRESS_STYLE, null) }
                ?: ProgressStyle.CHAIN,
        )
    }

    /** Every saved setting, for a full backup. */
    fun backup(): SettingsBackup = SettingsBackup(
        prefs.all.mapNotNull { (key, value) ->
            when (value) {
                is Boolean -> key to "b:$value"
                is Int -> key to "i:$value"
                is Long -> key to "l:$value"
                is Float -> key to "f:$value"
                is String -> key to "s:$value"
                else -> null
            }
        }.toMap(),
    )

    /** Replaces every setting with the backup's, and shows them straight away. */
    fun restore(backup: SettingsBackup) {
        val edit = prefs.edit().clear()
        backup.values.forEach { (key, coded) ->
            val value = coded.drop(2)
            when (coded.take(2)) {
                "b:" -> edit.putBoolean(key, value.toBoolean())
                "i:" -> value.toIntOrNull()?.let { edit.putInt(key, it) }
                "l:" -> value.toLongOrNull()?.let { edit.putLong(key, it) }
                "f:" -> value.toFloatOrNull()?.let { edit.putFloat(key, it) }
                "s:" -> edit.putString(key, value)
            }
        }
        edit.commit()
        _activeProfileId.value = prefs.getLong(KEY_ACTIVE_PROFILE, NO_PROFILE)
        _ideasUnlocked.value = prefs.getBoolean(KEY_IDEAS_UNLOCKED, false)
        _units.value = loadUnits()
        _appearance.value = loadAppearance()
        _reminderFrequency.value = enumOr(KEY_REMINDER_FREQUENCY, ReminderFrequency.WEEKLY)
        _reminderHour.value = prefs.getInt(KEY_REMINDER_HOUR, 9)
        _appIcon.value = enumOr(KEY_APP_ICON, AppIcon.MONOCHROME)
        _tripKm.value = loadTripKm()
        _paperworkReminders.value = loadPaperworkReminders()
        _paperworkLeadDays.value = prefs.getInt(KEY_PAPERWORK_LEAD_DAYS, DEFAULT_PAPERWORK_LEAD_DAYS)
    }

    companion object {
        const val NO_PROFILE = -1L
        private const val KEY_TRIP_KM = "trip_km"

        /** Not [KEY_TRIP_KM]: that one may still hold a value from before profiles, which would hide the bike's own trip. */
        private const val KEY_SAVINGS_TRIP_KM = "savings_trip_km"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_IDEAS_UNLOCKED = "ideas_unlocked"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_WALLPAPER = "wallpaper_colors"
        private const val KEY_PRIMARY = "primary_color"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_PROGRESS_STYLE = "progress_style"
        private const val KEY_OLD_COLOR_THEME = "color_theme"
        private const val KEY_UNIT_ECONOMY = "unit_economy"
        private const val KEY_UNIT_DISTANCE_COST = "unit_distance_cost"
        private const val KEY_UNIT_TIME_COST = "unit_time_cost"
        private const val KEY_UNIT_PRICE = "unit_price"
        private const val KEY_REMINDER_FREQUENCY = "reminder_frequency"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_APP_ICON = "app_icon"
        private const val KEY_PAPERWORK_PREFIX = "paperwork_remind_"
        private const val KEY_PAPERWORK_LEAD_DAYS = "paperwork_lead_days"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
