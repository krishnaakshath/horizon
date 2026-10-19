package dev.tvlauncher.room

import android.os.Looper

/**
 * The process-wide room palette. Views read their colours from here and repaint on [Listener].
 *
 * - [palette]: the settled TARGET palette (thread-safe read; use it when building a view).
 * - [frame]: the live, possibly mid-crossfade values (main thread only; use it when painting).
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

    private var animator: PaletteAnimator? = null
    private val fallbackFrame = PaletteFrame().set(palette)

    /** Live values (main thread). */
    val frame: PaletteFrame get() = animator?.frame ?: fallbackFrame

    private val listeners = ArrayList<Listener>()
    private var snapshot: Array<Listener> = emptyArray()

    fun addListener(l: Listener) {
        checkMain()
        if (l !in listeners) { listeners += l; snapshot = listeners.toTypedArray() }
    }

    fun removeListener(l: Listener) {
        checkMain()
        if (listeners.remove(l)) snapshot = listeners.toTypedArray()
    }

    /**
     * Sets the new target and crossfades to it over [durationMs] (0 = snap). Main thread.
     * Pass this as RoomLightController's onModeChanged.
     */
    fun apply(newMode: RoomMode, newPalette: Palette, durationMs: Long) {
        checkMain()
        mode = newMode
        palette = newPalette
        val a = animator ?: PaletteAnimator(fallbackFrame.toPalette()) { f, settled -> dispatch(f, settled) }.also { animator = it }
        a.animateTo(newPalette, durationMs)
    }

    /** Stops a running crossfade at its target (e.g. in onStop). */
    fun finishTransition() {
        val a = animator ?: return
        if (a.isRunning) { a.cancel(); dispatch(a.frame, true) }
    }

    private fun dispatch(f: PaletteFrame, settled: Boolean) {
        val ls = snapshot
        for (l in ls) l.onRoomPalette(f, mode, settled)
    }

    private fun checkMain() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "RoomTheme is main-thread only" }
    }
}
