package com.astheral.bikedashboard.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import com.astheral.bikedashboard.ui.theme.ColorPreset
import com.astheral.bikedashboard.ui.theme.ThemeMode
import com.astheral.bikedashboard.ui.theme.WallpaperSupported
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Small app settings, kept on the phone. */
class Settings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _tripKm = MutableStateFlow(prefs.getFloat(KEY_TRIP_KM, DEFAULT_TRIP_KM.toFloat()).toDouble())

    /** Round-trip distance of a special ride into Malaysia to pump. */
    val tripKm: StateFlow<Double> = _tripKm.asStateFlow()

    fun setTripKm(km: Double) {
        prefs.edit().putFloat(KEY_TRIP_KM, km.toFloat()).apply()
        _tripKm.value = km
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
            .remove(KEY_OLD_COLOR_THEME)
            .apply()
    }

    private fun loadAppearance(): AppearanceSettings {
        // Version 0.1+ had one "colour theme" setting; carry it over to the matching colours.
        val old = prefs.getString(KEY_OLD_COLOR_THEME, null)
        val oldPreset = ColorPreset.entries.firstOrNull { it.name == old }
        val default = oldPreset ?: ColorPreset.YAMAHA_BLUE
        return AppearanceSettings(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) } ?: ThemeMode.SYSTEM,
            pureBlack = prefs.getBoolean(KEY_PURE_BLACK, false),
            wallpaper = WallpaperSupported && prefs.getBoolean(KEY_WALLPAPER, old == "WALLPAPER"),
            primary = Color(prefs.getInt(KEY_PRIMARY, default.primary.toArgb())),
            accent = Color(prefs.getInt(KEY_ACCENT, default.accent.toArgb())),
        )
    }

    companion object {
        private const val KEY_TRIP_KM = "trip_km"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_WALLPAPER = "wallpaper_colors"
        private const val KEY_PRIMARY = "primary_color"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_OLD_COLOR_THEME = "color_theme"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
