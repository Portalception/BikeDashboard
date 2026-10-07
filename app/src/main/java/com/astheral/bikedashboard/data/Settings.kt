package com.astheral.bikedashboard.data

import android.content.Context
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.ui.theme.ColorTheme
import com.astheral.bikedashboard.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _themeMode = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_COLOR_THEME, null), ColorTheme.YAMAHA_BLUE)
            .takeIf { it in ColorTheme.available } ?: ColorTheme.YAMAHA_BLUE,
    )
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setColorTheme(theme: ColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
        _colorTheme.value = theme
    }

    companion object {
        private const val KEY_TRIP_KM = "trip_km"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
