package dev.tvlauncher.nowplaying

import android.content.Context
import dev.tvlauncher.discover.DiscoverStore

/**
 * Now Playing on the ambient screen: a large, calm card ([NowPlayingCard] ambient variant: no
 * buttons, no progress, not focusable) fed by its own tracker. The host adds [card] under the clock
 * and calls [start] on attach and [stop] on detach. Nothing runs when Settings > Home screen >
 * Now Playing > "Show on Ambient" is off or notification access is missing; the card stays hidden
 * unless something plays (or paused under 10 minutes ago).
 */
class AmbientNowPlaying(context: Context) {
    private val app = context
    val card = NowPlayingCard(context, ambient = true)
    private var tracker: NowPlayingTracker? = null

    fun start() {
        val run = DiscoverStore(app).loadNowPlaying().showAmbient(NowPlayingAccess.isGranted(app))
        if (!run) { card.bind(null, null); return }
        val t = tracker ?: NowPlayingTracker(
            app,
            NowPlayingAccess.listenerComponent(app),
            onChanged = { n -> card.bind(n, tracker?.currentArt) },
            onArt = { _, bmp -> card.bind(tracker?.current, bmp) },
            onAccessChanged = { ok -> if (!ok) card.bind(null, null) }
        ).also { tracker = it }
        t.start()
        card.start()
    }

    fun stop() {
        card.stop()
        tracker?.stop()
        card.bind(null, null)
    }
}
