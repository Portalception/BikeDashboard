package com.astheral.bikedashboard.ui.background

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * How far the phone is tilted from the way it's usually held, from -1 to 1 each way, for the
 * parallax effect. "Usually held" follows slowly behind the real angle, so whatever angle you
 * settle at becomes the middle again after a few seconds. Raw readings are smoothed first, so
 * hand shake doesn't show.
 */
internal class Tilt {
    @Volatile var x = 0f
        private set
    @Volatile var y = 0f
        private set

    /** The smoothed gravity reading itself, as a share of full gravity along the phone's sideways and up-down axes. */
    @Volatile var gx = 0f
        private set
    @Volatile var gy = 1f
        private set

    /** False until the first reading arrives. */
    val hasGravity: Boolean get() = !smoothX.isNaN()

    private var smoothX = Float.NaN
    private var smoothY = 0f
    private var restX = 0f
    private var restY = 0f

    /** One gravity reading, in m/s² along the phone's sideways and up-down axes. */
    fun onGravity(gx: Float, gy: Float) {
        val nx = gx / SensorManager.GRAVITY_EARTH
        val ny = gy / SensorManager.GRAVITY_EARTH
        if (smoothX.isNaN()) {
            smoothX = nx; smoothY = ny; restX = nx; restY = ny
            this.gx = nx; this.gy = ny
            return
        }
        smoothX += (nx - smoothX) * SMOOTH
        smoothY += (ny - smoothY) * SMOOTH
        this.gx = smoothX
        this.gy = smoothY
        restX += (smoothX - restX) * REST
        restY += (smoothY - restY) * REST
        // Tilting the right edge down moves the picture right, towards the low side.
        x = (-(smoothX - restX) * GAIN).coerceIn(-1f, 1f)
        y = ((smoothY - restY) * GAIN).coerceIn(-1f, 1f)
    }

    private companion object {
        /** Share of each new reading let through (at about 16 readings a second). */
        const val SMOOTH = 0.3f
        /** How quickly the resting angle catches up: about 4 seconds. */
        const val REST = 0.015f
        /** About 20 degrees of tilt gives the full shift. */
        const val GAIN = 3f
    }
}

/**
 * Listens to the phone's gravity sensor (or the accelerometer if there's none) while [enabled]
 * and the app is on screen, at a modest rate, and stops as soon as it isn't. Phones without
 * either sensor just never tilt.
 */
@Composable
internal fun rememberTilt(enabled: Boolean): Tilt {
    val tilt = remember { Tilt() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle, context) {
        val manager = if (enabled) context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = tilt.onGravity(event.values[0], event.values[1])
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var listening = false
        fun listen(on: Boolean) {
            if (on == listening) return
            listening = on
            if (on) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) else manager.unregisterListener(listener)
        }
        val observer = LifecycleEventObserver { _, _ -> listen(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            listen(false)
        }
    }
    return tilt
}
