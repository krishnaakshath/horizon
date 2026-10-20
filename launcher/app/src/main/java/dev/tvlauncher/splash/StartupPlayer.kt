package dev.tvlauncher.splash

import android.content.Context
import android.view.View
import dev.tvlauncher.ui.RevealTargets
import dev.tvlauncher.ui.SplashOverlay
import dev.tvlauncher.ui.SplashReveal

/**
 * One start-up overlay, whatever its style: [prepare] hides what the reveal brings in, [start] plays the
 * intro while apps load, [reveal] hands over to the home and removes the overlay when done.
 */
interface StartupPlayer {
    val view: View
    /** Upper bound of the whole sequence (focus sounds wait for it). */
    val totalMs: Long
    fun prepare(t: RevealTargets)
    fun start()
    fun reveal(t: RevealTargets)
}

object StartupPlayers {
    /**
     * The player for [style], or null when nothing should play: style Off, animations off (level Off or
     * a system animator scale of 0, i.e. [motionOn] false).
     */
    fun create(context: Context, style: StartupStyle, full: Boolean, motionOn: Boolean, accent: Int, pace: MotionPace): StartupPlayer? {
        if (!motionOn || !SplashOverlay.enabled(context)) return null
        return when (style) {
            StartupStyle.OFF -> null
            StartupStyle.SUNRISE -> SunrisePlayer(SplashOverlay(context, full, accent), full)
            StartupStyle.MINIMAL -> MinimalSplash(context, full, accent, StartupTimelines.minimal(full, pace))
            StartupStyle.AURORA -> AuroraSplash(context, full, accent, StartupTimelines.aurora(full, pace))
        }
    }

    private class SunrisePlayer(private val o: SplashOverlay, full: Boolean) : StartupPlayer {
        override val view: View get() = o
        override val totalMs = SplashReveal.totalMs(full)
        override fun prepare(t: RevealTargets) = o.prepare(t)
        override fun start() = o.start()
        override fun reveal(t: RevealTargets) = o.reveal(t)
    }
}
