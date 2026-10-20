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
    /** The art shown on the card (null when nothing plays); Ambient widgets' album-art mode reuses it. */
    var onArt: ((android.graphics.Bitmap?) -> Unit)? = null
    /** "Screensaver follows the music": the full-screen song layer ([AmbientMusic]), fed by the same tracker. */
    var music: AmbientMusic? = null

    private fun bind(n: NowPlaying?, art: android.graphics.Bitmap?) {
        card.bind(n, art)
        onArt?.invoke(if (n == null) null else art)
        music?.update(n, art)
    }

    fun start() {
        val follows = music?.start() == true
        val run = DiscoverStore(app).loadNowPlaying().showAmbient(NowPlayingAccess.isGranted(app)) || follows
        if (!run) { bind(null, null); return }
        val t = tracker ?: NowPlayingTracker(
            app,
            NowPlayingAccess.listenerComponent(app),
            onChanged = { n -> bind(n, tracker?.currentArt) },
            onArt = { _, bmp -> bind(tracker?.current, bmp) },
            onAccessChanged = { ok -> if (!ok) bind(null, null) }
        ).also { tracker = it }
        t.start()
        card.start()
    }

    fun stop() {
        music?.stop()
        card.stop()
        tracker?.stop()
        bind(null, null)
    }
}
