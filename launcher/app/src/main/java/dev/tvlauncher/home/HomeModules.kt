package dev.tvlauncher.home

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvlauncher.discover.DiscoverPanel
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.HeroBox
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.SpotlightPanel
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.TopBar

/**
 * The wave-2 modules wired into the home screen, behind one lifecycle so HomeActivity only
 * forwards calls: [panels] (Wi-Fi, Bluetooth, Devices; device toasts), [ambient] (idle timer and
 * optional home wallpaper), the weather chip ([HomeWeather]), room lighting ([HomeRoom]), the
 * Now Playing card ([HomeNowPlaying]) and the "Get more apps" panel ([DiscoverPanel]).
 *
 * Lifecycle: [start] in onStart, [resume] in onResume, [pause] in onPause, [stop] in onStop,
 * [destroy] in onDestroy. Input: [onUserActivity] for every key and interaction, [onBack] first in
 * BACK handling, [spotlightChanged] when Spotlight opens or closes.
 */
class HomeModules(
    private val activity: Activity,
    private val root: FrameLayout,
    look: HomeLook,
    private val sounds: HomeSounds,
    topBar: TopBar,
    private val backdrop: BackdropView,
    private val spotlight: SpotlightPanel,
    splashRunning: () -> Boolean,
    focusHome: () -> Unit,
    heroBox: HeroBox,
    /** Launchable packages (AppRepository); Discover hides what is installed. */
    installedPackages: () -> Set<String>,
    /** Focus target after the Discover panel closes (the shelf's "Get more apps" tile). */
    private val onDiscoverClosed: () -> Unit
) {
    private val weather = HomeWeather(activity, topBar)
    val room = HomeRoom(activity, lite = !look.profile.full, backdrop = backdrop)
    private val nowPlaying = HomeNowPlaying(activity, heroBox)
    val panels: HomePanels
    val ambient: HomeAmbient
    private val discover: DiscoverPanel

    init {
        lateinit var p: HomePanels
        lateinit var d: DiscoverPanel
        ambient = HomeAmbient(activity, look, { p.isShowing || d.isShowing }, { spotlight.isOpen }, splashRunning)
        p = HomePanels(activity, root, look, sounds, splashRunning, { spotlight.close(animate = false) }, focusHome) { open ->
            ambient.setPaused(HomeAmbient.PAUSE_PANEL, open)
        }
        panels = p
        d = DiscoverPanel(activity, look.profile.device, DiscoverStore(activity), installedPackages, onDismissed = {
            ambient.setPaused(HomeAmbient.PAUSE_PANEL, false)
            onDiscoverClosed()
        })
        discover = d
    }

    /** True while a panel is open or closing: Spotlight stays closed. */
    val isShowing: Boolean get() = panels.isShowing || discover.isShowing

    fun start() {
        room.start() // before the first frame of a cold start, so the backdrop starts in the room's palette
        panels.start()
        ambient.start(backdrop)
        nowPlaying.start()
    }

    fun resume() {
        ambient.resume()
        weather.resume()
        discover.refresh() // back from the Play Store: an installed app drops off the list
    }

    fun pause() {
        ambient.pause()
        weather.pause()
    }

    fun stop() {
        panels.stop() // closes panels; stops the Wi-Fi/Bluetooth controllers and the device monitor
        ambient.stop(backdrop) // idle timer cancelled, wallpaper bitmap freed
        nowPlaying.stop()
        room.stop()
        // Discover stays open across the Play Store round trip; HOME (closeAll) closes it.
    }

    fun destroy() {
        panels.release()
        weather.release()
        discover.release()
    }

    fun onUserActivity() = ambient.onUserActivity()

    fun onBack(): Boolean = discover.onBack() || panels.onBack()

    /** HOME pressed while Home is in front: close every panel. */
    fun closeAll() {
        panels.release()
        discover.dismiss()
    }

    /** The shelf's "Get more apps" tile. One panel at a time; Spotlight closes first. */
    fun openDiscover(): Boolean {
        if (isShowing) return false
        spotlight.close(animate = false)
        discover.show(root)
        ambient.setPaused(HomeAmbient.PAUSE_PANEL, true)
        sounds.panel(opened = true)
        return true
    }

    fun spotlightChanged(open: Boolean) = ambient.setPaused(HomeAmbient.PAUSE_SPOTLIGHT, open)

    fun openWifi(from: View) = panels.openWifi(from)
    fun openBluetooth(from: View) = panels.openBluetooth(from)

    /** Spotlight quick action: room lighting, a home panel or ambient, else the guarded system intent. */
    fun quickAction(item: QuickActions.Item) {
        if (item == QuickActions.Item.ROOM) {
            val text = room.cycle()
            spotlight.refreshAction(item)
            sounds.action(true)
            try { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() } catch (e: RuntimeException) { }
            return
        }
        if (!panels.quickAction(item, ambient::startNow)) sounds.action(SystemActions.quickAction(activity, item))
    }

    /** Spotlight's icon for [item] when it depends on state (room lighting follows the saved choice). */
    fun actionIcon(item: QuickActions.Item): Int? =
        if (item == QuickActions.Item.ROOM) HomeRoom.iconRes(room.choice()) else null
}
