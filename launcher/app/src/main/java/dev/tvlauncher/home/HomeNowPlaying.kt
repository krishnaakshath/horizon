package dev.tvlauncher.home

import android.content.Context
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.nowplaying.NowPlayingAccess
import dev.tvlauncher.nowplaying.NowPlayingCard
import dev.tvlauncher.nowplaying.NowPlayingSettings
import dev.tvlauncher.nowplaying.NowPlayingTracker
import dev.tvlauncher.ui.HeroBox

/** Pure rule: when the home screen runs a Now Playing tracker at all. */
object NowPlayingHome {
    /** Width of the card at the end of the hero row (dp). */
    const val CARD_WIDTH_DP = 400

    /** Enabled in Settings, "Show on Home" on, and notification access granted. */
    fun shouldRun(settings: NowPlayingSettings, accessGranted: Boolean): Boolean = settings.showHome(accessGranted)
}

/**
 * The Now Playing card at the end of the hero row. Built lazily, only once [NowPlayingHome.shouldRun]
 * says so: without notification access (or with the feature off, the default) nothing is created
 * and nothing runs. The card hides itself when nothing plays.
 *
 * Lifecycle: [start] in onStart (re-reads the setting and the grant, e.g. after Settings), [stop] in
 * onStop (tracker unsubscribed, text and art dropped).
 */
class HomeNowPlaying(context: Context, private val heroBox: HeroBox) {
    private val app = context
    private val store = DiscoverStore(context)
    private var tracker: NowPlayingTracker? = null
    private var card: NowPlayingCard? = null

    fun start() {
        val run = NowPlayingHome.shouldRun(store.loadNowPlaying(), NowPlayingAccess.isGranted(app))
        if (!run) return hide()
        val c = card ?: NowPlayingCard(app) { action -> tracker?.send(action) }.also {
            card = it
            heroBox.setSideCard(it, NowPlayingHome.CARD_WIDTH_DP)
        }
        val t = tracker ?: NowPlayingTracker(
            app,
            NowPlayingAccess.listenerComponent(app),
            onChanged = { n -> c.bind(n, tracker?.currentArt); heroBox.sideCardChanged() },
            onArt = { _, bmp -> c.bind(tracker?.current, bmp) },
            onAccessChanged = { granted -> if (!granted) { c.bind(null, null); heroBox.sideCardChanged() } }
        ).also { tracker = it }
        c.tracker = t
        t.start()
        c.start()
    }

    fun stop() {
        card?.stop()
        tracker?.stop()
        hide()
    }

    private fun hide() {
        val c = card ?: return
        c.bind(null, null)
        heroBox.sideCardChanged()
    }
}
