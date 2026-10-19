package dev.tvlauncher.room

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.util.Calendar

/**
 * Android shell around [RoomEngine] for the home screen. Call [start] from onStart and [stop] from
 * onStop (both idempotent). While started it:
 * - re-evaluates once a minute, aligned to the wall-clock minute (one Handler runnable, removed
 *   on [stop]), and on every rate-limited lux event;
 * - registers the light sensor only if the device has one AND the choice is AUTO;
 * - emits [onModeChanged] (main thread) only when the mode or palette changes. The first emission
 *   after construction has transitionMs = 0; later ones use [RoomTransitions.durationMs]
 *   (1200 ms; LITE 600; 0 when animations are off). After [start] the first lux reading applies
 *   without the 45 s dwell (and fades).
 *
 * @param choiceProvider when non-null it OVERRIDES the saved [RoomSettings.choice] (a test/kiosk
 *   hook). [cycleChoice] still saves the next choice, but it only takes effect once the provider
 *   returns it. Leave it null for the normal launcher.
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
    private val weather = WeatherDaylightSource.get(app).also { it.prime() }
    /** New sun times from Weather (or Weather went away): re-evaluate at once. */
    private val onSunChanged: () -> Unit = { engine.tick() }
    private val engine = RoomEngine(
        clock = { SystemClock.elapsedRealtime() },
        minuteOfDay = {
            val c = Calendar.getInstance()
            c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        },
        daylight = { s -> daylight(s) },
        transitionMs = { s -> RoomTransitions.durationMs(s, liteProvider(), animatorScale()) },
        emit = { m, p, ms -> onModeChanged(m, p, ms) }
    ).also { it.choiceOverride = choiceProvider }
    private val sensor = LightSensorSource(app) { lux -> engine.onLux(lux) }

    private val tick = object : Runnable {
        override fun run() {
            if (!engine.started) return
            engine.tick()
            scheduleTick()
        }
    }

    /** Whether this device has an ambient light sensor (false on the TCL 32S5403AF). */
    val hasLightSensor: Boolean get() = sensor.isAvailable

    /** The mode and palette last emitted (STANDARD before the first emission). */
    fun current(): Pair<RoomMode, Palette> = engine.current()

    fun start() {
        if (engine.started) return
        val s = prefs.load()
        engine.start(s, updateSensor(s))
        weather.cache.addListener(onSunChanged)
        scheduleTick()
    }

    fun stop() {
        weather.cache.removeListener(onSunChanged)
        main.removeCallbacks(tick)
        sensor.stop()
        engine.stop()
    }

    /** Re-reads [RoomSettings] (after Settings changed them) and re-evaluates. */
    fun refresh() {
        val s = prefs.load()
        engine.update(s, if (engine.started) updateSensor(s) else false)
    }

    /**
     * Spotlight quick action: saves the next choice (Auto -> Dark -> Evening -> Normal -> Bright ->
     * Auto), applies it and returns the toast text: "Room lighting: Bright room (manual)" or
     * "Room lighting: Auto (now Evening)" with the mode actually computed for now.
     */
    fun cycleChoice(): String {
        val s = prefs.load()
        val next = s.copy(choice = RoomChoiceCycler.next(s.choice))
        prefs.save(next)
        engine.update(next, if (engine.started) updateSensor(next) else false)
        return RoomChoiceCycler.toast(engine.choice(), engine.previewMode(next))
    }

    /** Starts/stops the sensor for [s]; returns whether lux is flowing. */
    private fun updateSensor(s: RoomSettings): Boolean {
        val choice = try { choiceProvider?.invoke() ?: s.choice } catch (e: RuntimeException) { s.choice }
        return if (choice == RoomChoice.AUTO && sensor.isAvailable) {
            sensor.start()
            sensor.isStarted
        } else {
            sensor.stop()
            false
        }
    }

    /** Fallback chain: Weather's real times -> estimate for the Room location -> (null) fixed table. */
    private fun daylight(s: RoomSettings): DaylightSource {
        val estimate = EstimatedDaylight.forSettings(s) ?: return weather.saneOnly()
        return DaylightSources.firstOf(weather.saneOnly(), estimate.saneOnly())
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
