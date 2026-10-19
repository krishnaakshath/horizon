package dev.tvlauncher.home

import android.app.Activity
import dev.tvlauncher.ambient.AmbientIdle
import dev.tvlauncher.ambient.HomeWallpaperProvider
import dev.tvlauncher.ambient.IdleTimer
import dev.tvlauncher.ambient.WallpaperDrawable
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.settings.AppearanceFormat
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.SystemActions

/**
 * Ambient on the home screen: the idle timer that starts AmbientActivity after Settings > Ambient >
 * "Start ambient after" minutes (default 10) without a key, and the optional wallpaper layer under
 * the backdrop ("Use wallpaper on home", default off).
 *
 * Lifecycle: [start] in onStart attaches the optional wallpaper; [resume] in onResume re-reads the
 * timeout and starts the timer; [pause] in onPause cancels it, so it only runs while Home is
 * resumed; [stop] in onStop cancels it and frees the wallpaper bitmap. The host forwards keys and
 * interaction to [onUserActivity] and toggles [setPaused] for Spotlight and panels. At the deadline
 * [AmbientGate] decides again (window focus covers dialogs) and Safe Mode is checked; when it says
 * no, the timer re-arms for a full period.
 */
class HomeAmbient(
    private val activity: Activity,
    private val look: HomeLook,
    private val overlayOpen: () -> Boolean,
    private val spotlightOpen: () -> Boolean,
    private val splashRunning: () -> Boolean
) {
    private val store = WallpaperStore(activity)
    private val extras = HomeExtrasPrefs(activity)
    private val idle = AmbientIdle(AmbientGate.timeoutMs(store.load())) { onIdle() }
    private var resumed = false
    private var wallpaper: WallpaperDrawable? = null

    fun resume() {
        resumed = true
        idle.setTimeoutMinutes(store.load().idleMinutes)
        idle.start()
    }

    fun pause() {
        resumed = false
        idle.stop()
    }

    fun onUserActivity() = idle.onUserActivity()

    fun setPaused(flag: Int, paused: Boolean) = idle.setPaused(flag, paused)

    private fun onIdle() {
        val safeMode = try { SafeMode.guard(activity).isTripped } catch (e: Throwable) { true }
        val ok = AmbientGate.shouldStart(resumed, overlayOpen(), spotlightOpen(), activity.hasWindowFocus(), safeMode, splashRunning())
        if (ok) startNow() else idle.onUserActivity() // re-arm for a full period
    }

    /** Starts AmbientActivity now (idle deadline or the Spotlight action); the clock follows Appearance. */
    fun startNow(): Boolean {
        try {
            val s = store.load()
            val a = look.settings
            val is24h = AppearanceFormat.is24h(a.clockFormat, android.text.format.DateFormat.is24HourFormat(activity))
            val synced = AmbientGate.withClock(s, is24h)
            if (synced !== s) store.save(synced)
        } catch (e: RuntimeException) {
        }
        return SystemActions.ambient(activity)
    }

    /** onStart: the wallpaper layer under the backdrop when "Use wallpaper on home" is on. */
    fun start(backdrop: BackdropView) {
        detachWallpaper(backdrop)
        if (!extras.wallpaperOnHome) return
        val d = HomeWallpaperProvider.drawableFor(activity, store.load(), look.profile.device)
        wallpaper = d
        backdrop.setBaseLayer(d)
    }

    /** onStop: timer cancelled, wallpaper bitmap released (re-created in [start]). */
    fun stop(backdrop: BackdropView) {
        pause()
        detachWallpaper(backdrop)
    }

    private fun detachWallpaper(backdrop: BackdropView) {
        val d = wallpaper ?: return
        wallpaper = null
        backdrop.setBaseLayer(null)
        d.release()
    }

    companion object {
        const val PAUSE_SPOTLIGHT = IdleTimer.PAUSE_SPOTLIGHT
        const val PAUSE_DIALOG = IdleTimer.PAUSE_DIALOG
        const val PAUSE_PANEL = IdleTimer.PAUSE_PANEL
    }
}
