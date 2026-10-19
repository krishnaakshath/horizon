package dev.tvlauncher.home

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.nowplaying.MediaBackdrop
import dev.tvlauncher.nowplaying.NowPlayingAccess
import dev.tvlauncher.nowplaying.NowPlayingCard
import dev.tvlauncher.nowplaying.NowPlayingSettings
import dev.tvlauncher.nowplaying.NowPlayingTracker
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.HeroBox
import dev.tvlauncher.ui.QuickStrip
import dev.tvlauncher.ui.Shelf

/** Pure rules: when the home screen runs a Now Playing tracker at all, and where the mini player sits. */
object NowPlayingHome {
    /** Width of the mini player at the end of the hero row (dp). */
    const val CARD_WIDTH_DP = 400

    /**
     * Layout audit: the mini player lives in the hero box (between the top bar and the shelf), at
     * its bottom end, right above the shelf. The hero text keeps [dev.tvlauncher.ui.HeroLayout]'s
     * end margin while it is shown, so it never overlaps tiles, tile labels or the hero text.
     */
    const val CARD_GRAVITY = android.view.Gravity.END or android.view.Gravity.BOTTOM

    /** Enabled in Settings, mini player or art backdrop on, and notification access granted. */
    fun shouldRun(settings: NowPlayingSettings, accessGranted: Boolean): Boolean =
        settings.showHome(accessGranted) || settings.followBackdrop(accessGranted)

    /** The mini player is offered as UP target of the focused tile only while it is shown. */
    fun upLinkWanted(cardShown: Boolean, focusOnTile: Boolean): Boolean = cardShown && focusOnTile
}

/**
 * Now Playing on the home screen: one tracker feeding the mini player (end of the hero row, built
 * lazily) and the art backdrop ([MediaBackdrop]). Without notification access or with the feature
 * off nothing is created and nothing runs.
 *
 * Focus: UP from a shelf tile reaches the mini player (the focused tile's nextFocusUpId points at
 * it while it is shown); moving through the shelf brings the brand colour back over the art
 * ([dev.tvlauncher.nowplaying.MediaBackdropPolicy]).
 *
 * Lifecycle: [start] in onStart (re-reads the settings and the grant, e.g. after Settings), [stop]
 * in onStop (tracker unsubscribed, text and art dropped), [release] in onDestroy.
 */
class HomeNowPlaying(
    private val activity: Activity,
    private val heroBox: HeroBox,
    private val backdrop: BackdropView,
    private val root: FrameLayout,
    private val full: Boolean,
    fadeMs: Long
) {
    private val store = DiscoverStore(activity)
    private var tracker: NowPlayingTracker? = null
    private var card: NowPlayingCard? = null
    private val media = MediaBackdrop(backdrop, full, fadeMs) { heroBox.hero.recolor(backdrop.heroColor) }
    private var settings = NowPlayingSettings()
    private var linkedTile: View? = null
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now -> onFocus(now) }
    private var listening = false

    fun start() {
        val granted = NowPlayingAccess.isGranted(activity)
        settings = store.loadNowPlaying()
        if (!NowPlayingHome.shouldRun(settings, granted)) {
            media.setEnabled(false)
            return hide()
        }
        val showCard = settings.showHome(granted)
        media.setEnabled(settings.followBackdrop(granted))
        val c = if (showCard) card ?: NowPlayingCard(activity, full = full, onOpen = { tracker?.openApp(activity) }) { action ->
            tracker?.send(action)
        }.also {
            card = it
            heroBox.setSideCard(it, NowPlayingHome.CARD_WIDTH_DP)
            (it.layoutParams as? FrameLayout.LayoutParams)?.let { lp -> lp.gravity = NowPlayingHome.CARD_GRAVITY; it.layoutParams = lp }
            it.onShownChanged = { cardChanged() }
        } else null
        if (!showCard) hide()
        val t = tracker ?: NowPlayingTracker(
            activity,
            NowPlayingAccess.listenerComponent(activity),
            onChanged = { n -> deliver(n, tracker?.currentArt) },
            onArt = { _, bmp -> deliver(tracker?.current, bmp) },
            onAccessChanged = { ok -> if (!ok) deliver(null, null) }
        ).also { tracker = it }
        c?.tracker = t
        t.start()
        c?.start()
        if (c == null) t.retryIfAccessGranted()
        if (!listening) { root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener); listening = true }
        media.onFocus(isInShelf(activity.currentFocus))
    }

    fun stop() {
        card?.stop()
        tracker?.stop()
        media.stop()
        hide()
        if (listening) {
            try { root.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener) } catch (e: RuntimeException) { }
            listening = false
        }
    }

    fun release() = stop()

    /** Every key (HomeModules.onUserActivity): a focus change right after it counts as a move. */
    fun onUserActivity() = media.onKey()

    private fun deliver(n: dev.tvlauncher.nowplaying.NowPlaying?, art: android.graphics.Bitmap?) {
        if (settings.showOnHome) card?.bind(n, art)
        cardChanged()
        media.update(n, art)
    }

    private fun onFocus(now: View?) {
        media.onFocus(isInShelf(now))
        syncUpLink(now)
    }

    private fun cardChanged() {
        heroBox.sideCardChanged()
        syncUpLink(activity.currentFocus)
    }

    /** Points the focused tile's UP at the mini player while it is shown; restores it otherwise. */
    private fun syncUpLink(focused: View?) {
        val tile = focused?.takeIf { it.parent?.parent is Shelf }
        val target = card?.openTarget
        val want = NowPlayingHome.upLinkWanted(card?.isShowing == true, tile != null)
        val old = linkedTile
        if (old != null && (old !== tile || !want)) {
            if (target != null && old.nextFocusUpId == target.id) old.nextFocusUpId = View.NO_ID
            linkedTile = null
        }
        if (want && tile != null && target != null && tile.nextFocusUpId == View.NO_ID) {
            tile.nextFocusUpId = target.id
            linkedTile = tile
        }
    }

    private fun isInShelf(v: View?): Boolean {
        var p: Any? = v
        while (p is View) {
            if (p is Shelf || p is QuickStrip) return true
            p = p.parent
        }
        return false
    }

    private fun hide() {
        val c = card ?: return
        c.bind(null, null)
        cardChanged()
    }
}
