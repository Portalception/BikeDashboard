package com.astheral.bikedashboard.data

import android.content.Context
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
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

    companion object {
        private const val KEY_TRIP_KM = "trip_km"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
