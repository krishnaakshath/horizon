package dev.tvlauncher.home

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.SpotlightPanel
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.TopBar

/**
 * The wave-2 modules wired into the home screen, behind one lifecycle so HomeActivity only
 * forwards calls: [panels] (Wi-Fi, Bluetooth, Devices; device toasts), [ambient] (idle timer and
 * optional home wallpaper) and the weather chip ([HomeWeather]).
 *
 * Lifecycle: [start] in onStart, [resume] in onResume, [pause] in onPause, [stop] in onStop,
 * [destroy] in onDestroy. Input: [onUserActivity] for every key and interaction, [onBack] first in
 * BACK handling, [spotlightChanged] when Spotlight opens or closes.
 */
class HomeModules(
    private val activity: Activity,
    root: FrameLayout,
    look: HomeLook,
    private val sounds: HomeSounds,
    topBar: TopBar,
    private val backdrop: BackdropView,
    spotlight: SpotlightPanel,
    splashRunning: () -> Boolean,
    focusHome: () -> Unit
) {
    private val weather = HomeWeather(activity, topBar)
    val panels: HomePanels
    val ambient: HomeAmbient

    init {
        lateinit var p: HomePanels
        ambient = HomeAmbient(activity, look, { p.isShowing }, { spotlight.isOpen }, splashRunning)
        p = HomePanels(activity, root, look, sounds, splashRunning, { spotlight.close(animate = false) }, focusHome) { open ->
            ambient.setPaused(HomeAmbient.PAUSE_PANEL, open)
        }
        panels = p
    }

    /** True while a panel is open or closing: Spotlight stays closed. */
    val isShowing: Boolean get() = panels.isShowing

    fun start() {
        panels.start()
        ambient.start(backdrop)
    }

    fun resume() {
        ambient.resume()
        weather.resume()
    }

    fun pause() {
        ambient.pause()
        weather.pause()
    }

    fun stop() {
        panels.stop() // closes panels; stops the Wi-Fi/Bluetooth controllers and the device monitor
        ambient.stop(backdrop) // idle timer cancelled, wallpaper bitmap freed
    }

    fun destroy() {
        panels.release()
        weather.release()
    }

    fun onUserActivity() = ambient.onUserActivity()

    fun onBack(): Boolean = panels.onBack()

    /** HOME pressed while Home is in front: close every panel. */
    fun closeAll() = panels.release()

    fun spotlightChanged(open: Boolean) = ambient.setPaused(HomeAmbient.PAUSE_SPOTLIGHT, open)

    fun openWifi(from: View) = panels.openWifi(from)
    fun openBluetooth(from: View) = panels.openBluetooth(from)

    /** Spotlight quick action: a home panel or ambient, else the guarded system intent. */
    fun quickAction(item: QuickActions.Item) {
        if (!panels.quickAction(item, ambient::startNow)) sounds.action(SystemActions.quickAction(activity, item))
    }
}
