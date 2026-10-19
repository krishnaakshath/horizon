package dev.tvlauncher.home

import dev.tvlauncher.ambient.WallpaperSettings

/** Pure rules for starting the ambient screen from the idle home screen. */
object AmbientGate {
    /**
     * Only from a resumed, focused home screen with nothing on top: no panel ([overlayOpen]),
     * no Spotlight, no dialog (the window has no focus then), not under the splash, never while
     * the crash guard has Safe Mode tripped.
     */
    fun shouldStart(
        resumed: Boolean,
        overlayOpen: Boolean,
        spotlightOpen: Boolean,
        hasWindowFocus: Boolean,
        safeMode: Boolean,
        splash: Boolean
    ): Boolean = resumed && !overlayOpen && !spotlightOpen && hasWindowFocus && !safeMode && !splash

    /** Idle timeout from Settings > Ambient ("Start ambient after", default 10 min). */
    fun timeoutMs(s: WallpaperSettings): Long = s.idleMinutes * 60_000L

    /** The ambient clock follows Settings > Appearance; returns [s] itself when nothing changes. */
    fun withClock(s: WallpaperSettings, is24h: Boolean): WallpaperSettings =
        if (s.clock24h == is24h) s else s.copy(clock24h = is24h)
}
