package dev.tvlauncher.room

import android.os.Looper

/**
 * The process-wide room palette. Views read their colours from here and repaint on [Listener].
 *
 * - [palette]: the settled TARGET palette (thread-safe read; use it when building a view).
 * - [frame]: the live, possibly mid-crossfade values (main thread only; use it when painting).
 *   Always the same object, so a view may keep a reference to it.
 *
 * Listeners are held WEAKLY ([ListenerList]): keep your listener in a field. Still call
 * [removeListener] in onStop/onDestroy; a forgotten one is pruned once its owner is collected.
 *
 * Intended consumption points (wired by the controller in HomeActivity, not in this package):
 * - BackdropView: base gradient [Palette.backdropTop] -> [Palette.backdropBottom]; brand colour
 *   mixed in at [Palette.accentBlend]; every stop clamped to at least [Palette.backdropFloor]'s
 *   luminance (BRIGHT must never show pure black); artwork scrim alpha x [Palette.scrimStrength].
 * - HeroView: title [Palette.textPrimary], subtitle [Palette.textSecondary]; when
 *   [Palette.textWeightBoost] >= 0.5 use the next heavier face; size x max(1, [Palette.minTextScale]).
 * - TileView: focus ring [Palette.focusRingColor] at [Palette.focusRingWidthDp] dp; unfocused alpha
 *   [Palette.tileDimAlpha]; focus glow alpha x [Palette.glowStrength]; shadow x [Palette.shadowStrength].
 * - TopBar: clock/date/status icons [Palette.textPrimary]/[Palette.textSecondary].
 * - Glass surfaces (ui/Glass, quick strip, Spotlight glass): fill [Palette.glassFill], 1 dp
 *   border [Palette.glassBorder] (BRIGHT: draw it, it replaces the glow as the edge cue).
 * - Settings (appsettings): pane [Palette.surface], focused row [Palette.surfaceElevated],
 *   texts textPrimary/textSecondary/textDisabled.
 */
object RoomTheme {
    fun interface Listener {
        /** Main thread. [settled] = last frame of a crossfade (or a snap). */
        fun onRoomPalette(frame: PaletteFrame, mode: RoomMode, settled: Boolean)
    }

    @Volatile var palette: Palette = Palettes.of(RoomMode.STANDARD)
        private set

    @Volatile var mode: RoomMode = RoomMode.STANDARD
        private set

    /** Live values (main thread). Stable identity. */
    val frame: PaletteFrame = PaletteFrame().set(palette)

    private var animator: PaletteAnimator? = null
    private val listeners = ListenerList<Listener>()
    private var dispatchSettled = false
    private val dispatchAction: (Listener) -> Unit = { it.onRoomPalette(frame, mode, dispatchSettled) }

    fun addListener(l: Listener) {
        checkMain()
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        checkMain()
        listeners.remove(l)
    }

    /**
     * Sets the new target and crossfades to it over [durationMs] (0 = snap). Main thread.
     * Pass this as RoomLightController's onModeChanged.
     */
    fun apply(newMode: RoomMode, newPalette: Palette, durationMs: Long) {
        checkMain()
        mode = newMode
        palette = newPalette
        val a = animator ?: PaletteAnimator(frame) { _, settled -> dispatch(settled) }.also { animator = it }
        a.animateTo(newPalette, durationMs)
    }

    /** Stops a running crossfade at its target (e.g. in onStop). */
    fun finishTransition() {
        val a = animator ?: return
        if (a.isRunning) { a.cancel(); dispatch(true) }
    }

    private fun dispatch(settled: Boolean) {
        dispatchSettled = settled
        listeners.forEach(dispatchAction)
    }

    private fun checkMain() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "RoomTheme is main-thread only" }
    }
}
