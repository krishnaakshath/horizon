package dev.tvlauncher.room

/**
 * The pure core of [RoomLightController]: owns the [RoomLightModel.State], the [LuxSmoother] and
 * the "emit only on change" bookkeeping. No Android: the controller supplies the clock, the local
 * minute of the day, the daylight source and the crossfade length, and calls [start]/[stop],
 * [tick] (once a minute), [onLux] and [update] (settings or sensor availability changed).
 *
 * - The first emission ever has 0 ms (no crossfade at launch); later ones use [transitionMs].
 * - [start] forgets the model state, so the decision is re-made from scratch, and the FIRST lux
 *   reading after [start] (or after the sensor becomes active) applies without the dwell. Because
 *   the last emitted palette is kept, an unchanged result emits nothing and a changed one fades.
 * - [choiceOverride], when set, wins over the saved [RoomSettings.choice].
 */
class RoomEngine(
    private val clock: () -> Long,
    private val minuteOfDay: () -> Int,
    private val daylight: (RoomSettings) -> DaylightSource?,
    private val transitionMs: (RoomSettings) -> Long,
    private val emit: (mode: RoomMode, palette: Palette, transitionMs: Long) -> Unit,
    smootherAlpha: Float = 0.25f
) {
    var choiceOverride: (() -> RoomChoice)? = null

    var settings: RoomSettings = RoomSettings()
        private set
    var started: Boolean = false
        private set
    var state: RoomLightModel.State? = null
        private set

    private val smoother = LuxSmoother(smootherAlpha, clock)
    private var luxActive = false
    private var awaitFirstLux = false
    private var lastMode: RoomMode? = null
    private var lastPalette: Palette? = null

    fun choice(): RoomChoice = try {
        choiceOverride?.invoke() ?: settings.choice
    } catch (e: RuntimeException) {
        settings.choice
    }

    /** No-op when already started. */
    fun start(settings: RoomSettings, luxActive: Boolean) {
        if (started) return
        started = true
        this.settings = settings
        state = null
        smoother.reset()
        this.luxActive = luxActive
        awaitFirstLux = luxActive
        evaluate()
    }

    /** No-op when already stopped. */
    fun stop() {
        if (!started) return
        started = false
        smoother.reset()
        awaitFirstLux = false
    }

    /** New settings and/or sensor state; re-evaluates when started. */
    fun update(settings: RoomSettings, luxActive: Boolean) {
        this.settings = settings
        if (luxActive != this.luxActive) {
            smoother.reset()
            awaitFirstLux = luxActive
            this.luxActive = luxActive
        }
        if (started) evaluate()
    }

    fun tick() {
        if (started) evaluate()
    }

    fun onLux(lux: Float) {
        if (!started || !luxActive) return
        smoother.add(lux)
        if (awaitFirstLux && smoother.value() != null) {
            awaitFirstLux = false
            state = state?.copy(reenter = true)
        }
        evaluate()
    }

    /** Last emitted mode and palette (STANDARD before any emission). */
    fun current(): Pair<RoomMode, Palette> =
        (lastMode ?: RoomMode.STANDARD) to (lastPalette ?: Palettes.of(RoomMode.STANDARD))

    /**
     * The mode [s] leads to right now: the live decision while started, else a fresh evaluation
     * (no lux) that changes nothing. For the quick action's toast.
     */
    fun previewMode(s: RoomSettings): RoomMode {
        if (started) state?.let { return it.mode }
        val c = choiceOverride?.let { runCatching { it() }.getOrNull() } ?: s.choice
        return RoomLightModel.initial(inputs(s, c, lux = null)).mode
    }

    private fun evaluate() {
        val c = choice()
        val lux = if (c == RoomChoice.AUTO && luxActive) smoother.value() else null
        val inputs = inputs(settings, c, lux)
        val prev = state
        val next = if (prev == null) RoomLightModel.initial(inputs) else RoomLightModel.next(prev, inputs)
        state = next
        val p = Palettes.of(next.mode, settings.boostTextInBright)
        if (next.mode == lastMode && p == lastPalette) return
        val first = lastMode == null
        lastMode = next.mode
        lastPalette = p
        emit(next.mode, p, if (first) 0L else transitionMs(settings))
    }

    private fun inputs(s: RoomSettings, c: RoomChoice, lux: Float?): RoomLightModel.Inputs {
        // Sun times only matter for the AUTO schedule.
        val sun = if (c == RoomChoice.AUTO && lux == null) {
            try { daylight(s)?.sunriseSunsetMinutes() } catch (e: RuntimeException) { null }
        } else null
        return RoomLightModel.Inputs(
            lux = lux,
            minuteOfDay = minuteOfDay(),
            sunriseMin = sun?.first,
            sunsetMin = sun?.second,
            choice = c,
            nowMs = clock(),
            schedule = s.schedule
        )
    }
}
