package com.astheral.bikedashboard.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.ui.DistanceCostUnit
import com.astheral.bikedashboard.ui.EconomyUnit
import com.astheral.bikedashboard.ui.PriceUnit
import com.astheral.bikedashboard.ui.TimeCostUnit
import com.astheral.bikedashboard.ui.Units
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

    /** The trip distance from before profiles; it now lives on each profile. Only read when upgrading. */
    val legacyTripKm: Double get() = prefs.getFloat(KEY_TRIP_KM, DEFAULT_TRIP_KM.toFloat()).toDouble()

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

    private val _units = MutableStateFlow(
        Units(
            economy = enumOr(KEY_UNIT_ECONOMY, EconomyUnit.KM_PER_L),
            distanceCost = enumOr(KEY_UNIT_DISTANCE_COST, DistanceCostUnit.PER_KM),
            timeCost = enumOr(KEY_UNIT_TIME_COST, TimeCostUnit.PER_DAY),
            price = enumOr(KEY_UNIT_PRICE, PriceUnit.SGD_PER_L),
        ),
    )

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

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == prefs.getString(key, null) } ?: default

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

    companion object {
        const val NO_PROFILE = -1L
        private const val KEY_TRIP_KM = "trip_km"
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

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
