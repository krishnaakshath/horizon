package dev.tvlauncher.room

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.util.Calendar
import java.util.TimeZone

/**
 * Owns the [RoomLightModel] state for the home screen. Call [start] from onStart and [stop] from
 * onStop. While started it:
 * - re-evaluates once a minute, aligned to the wall-clock minute (one Handler runnable, removed
 *   on [stop]), and on every (smoothed, rate-limited) lux event;
 * - registers the light sensor only if the device has one AND the choice is AUTO;
 * - emits [onModeChanged] (main thread) only when the mode or palette changes. The first emission
 *   after construction has transitionMs = 0 (no crossfade at launch); later ones use
 *   [RoomTransitions.durationMs] (1200 ms; LITE 600; 0 when animations are off).
 *
 * @param choiceProvider the current [RoomChoice]; defaults to the saved [RoomSettings].
 * @param liteProvider true on LITE effects devices (shorter crossfade).
 */
class RoomLightController(
    context: Context,
    private val choiceProvider: (() -> RoomChoice)? = null,
    private val liteProvider: () -> Boolean = { false },
    private val onModeChanged: (mode: RoomMode, palette: Palette, transitionMs: Long) -> Unit
) {
    private val app = context.applicationContext
    private val prefs = RoomPrefs(app)
    private val main = Handler(Looper.getMainLooper())
    private val smoother = LuxSmoother(alpha = 0.25f, clock = { SystemClock.elapsedRealtime() })
    private val sensor = LightSensorSource(app) { lux -> onLux(lux) }
    private val weather = WeatherDaylightSource(app)

    private var settings: RoomSettings = prefs.load()
    private var state: RoomLightModel.State? = null
    private var palette: Palette = Palettes.of(RoomMode.STANDARD)
    private var emitted = false
    private var started = false

    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            evaluate()
            scheduleTick()
        }
    }

    /** Whether this device has an ambient light sensor (false on the TCL 32S5403AF). */
    val hasLightSensor: Boolean get() = sensor.isAvailable

    /** The mode and palette last decided (STANDARD before the first evaluation). */
    fun current(): Pair<RoomMode, Palette> = (state?.mode ?: RoomMode.STANDARD) to palette

    fun start() {
        if (started) return
        started = true
        settings = prefs.load()
        updateSensor()
        evaluate()
        scheduleTick()
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(tick)
        sensor.stop()
        smoother.reset()
    }

    /** Re-reads [RoomSettings] (after Settings or the quick action changed them) and re-evaluates. */
    fun refresh() {
        settings = prefs.load()
        if (!started) return
        updateSensor()
        evaluate()
    }

    /**
     * Spotlight quick action: saves the next choice (Auto -> Dark -> Evening -> Normal -> Bright ->
     * Auto), applies it and returns the toast text, e.g. "Room lighting: Bright room (manual)".
     */
    fun cycleChoice(): String {
        val s = prefs.load()
        val next = s.copy(choice = RoomChoiceCycler.next(s.choice))
        prefs.save(next)
        settings = next
        if (started) { updateSensor(); evaluate() }
        else state = null
        val effective = state?.mode ?: next.choice.manualMode ?: RoomMode.STANDARD
        return RoomChoiceCycler.toast(next.choice, effective)
    }

    private fun choice(): RoomChoice = try {
        choiceProvider?.invoke() ?: settings.choice
    } catch (e: RuntimeException) {
        settings.choice
    }

    private fun updateSensor() {
        if (choice() == RoomChoice.AUTO && sensor.isAvailable) sensor.start()
        else { sensor.stop(); smoother.reset() }
    }

    private fun onLux(lux: Float) {
        if (!started) return
        smoother.add(lux)
        evaluate()
    }

    private fun evaluate() {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val c = choice()
        val lux = if (c == RoomChoice.AUTO && sensor.isStarted) smoother.value() else null
        // Sun times only matter for the AUTO schedule; skip the provider IPC otherwise.
        val sun = if (c == RoomChoice.AUTO && lux == null) daylight(cal).let { src ->
            try { src.sunriseSunsetMinutes() } catch (e: RuntimeException) { null }
        } else null
        val inputs = RoomLightModel.Inputs(
            lux = lux,
            minuteOfDay = minute,
            sunriseMin = sun?.first,
            sunsetMin = sun?.second,
            choice = c,
            nowMs = SystemClock.elapsedRealtime(),
            schedule = settings.schedule
        )
        val prev = state
        val next = if (prev == null) RoomLightModel.initial(inputs) else RoomLightModel.next(prev, inputs)
        state = next
        val p = Palettes.of(next.mode, settings.boostTextInBright)
        if (emitted && prev?.mode == next.mode && p == palette) return
        palette = p
        val ms = if (!emitted) 0L else RoomTransitions.durationMs(settings, liteProvider(), animatorScale())
        emitted = true
        onModeChanged(next.mode, p, ms)
    }

    private fun daylight(cal: Calendar): DaylightSource {
        val lat = settings.latitudeDeg ?: return weather
        val tz = TimeZone.getDefault()
        val now = cal.timeInMillis
        val estimate = EstimatedDaylight(
            latitudeDeg = lat,
            longitudeDeg = null,
            utcOffsetMinutes = { tz.getOffset(now) / 60_000 },
            dayOfYear = { cal.get(Calendar.DAY_OF_YEAR) },
            dstMinutes = { (tz.getOffset(now) - tz.rawOffset) / 60_000 }
        )
        return DaylightSources.firstOf(weather, estimate)
    }

    private fun scheduleTick() {
        main.removeCallbacks(tick)
        val wall = System.currentTimeMillis()
        // Just after the next minute boundary.
        val delay = 60_000L - (wall % 60_000L) + 250L
        main.postDelayed(tick, delay)
    }

    private fun animatorScale(): Float = try {
        Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: RuntimeException) {
        1f
    }
}
