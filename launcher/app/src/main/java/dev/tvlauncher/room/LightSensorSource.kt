package dev.tvlauncher.room

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Ambient light (Sensor.TYPE_LIGHT) while started. Most TVs - including the owner's TCL 32S5403AF -
 * have no sensors at all: then [isAvailable] is false and [start] does nothing.
 *
 * Registered with SENSOR_DELAY_NORMAL on the main looper only between [start] and [stop]
 * (call them from onStart/onStop). [onLux] runs on the main thread, at most once per
 * [minIntervalMs]; the latest value of a burst is delivered when the interval ends.
 */
class LightSensorSource(
    context: Context,
    private val minIntervalMs: Long = 2_000L,
    private val onLux: (Float) -> Unit
) : SensorEventListener {
    private val manager: SensorManager? =
        try { context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager } catch (e: RuntimeException) { null }
    private val sensor: Sensor? =
        try { manager?.getDefaultSensor(Sensor.TYPE_LIGHT) } catch (e: RuntimeException) { null }
    private val main = Handler(Looper.getMainLooper())
    private val gate = RateGate(minIntervalMs)
    private var started = false

    private val trailing = Runnable {
        if (!started) return@Runnable
        gate.takeTrailing(SystemClock.uptimeMillis())?.let(onLux)
    }

    val isAvailable: Boolean get() = sensor != null
    val isStarted: Boolean get() = started

    fun start() {
        if (started || sensor == null || manager == null) return
        started = try {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, main)
        } catch (e: RuntimeException) {
            false
        }
    }

    fun stop() {
        main.removeCallbacks(trailing)
        gate.reset()
        if (!started) return
        started = false
        try { manager?.unregisterListener(this) } catch (e: RuntimeException) { }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.values.isEmpty()) return
        val lux = event.values[0]
        if (!lux.isFinite() || lux < 0f) return
        val now = SystemClock.uptimeMillis()
        if (gate.offer(lux, now)) {
            main.removeCallbacks(trailing)
            onLux(lux)
        } else {
            val due = gate.trailingDueAt() ?: return
            main.removeCallbacks(trailing)
            main.postAtTime(trailing, due)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
