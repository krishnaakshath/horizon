package dev.tvlauncher.switcher

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.speed.KeepRules
import dev.tvlauncher.speed.SpeedSystem
import dev.tvlauncher.ui.TileArt
import dev.tvlauncher.ui.TileArtResolver

/** One card of the strip, read off the main thread. */
data class RecentCard(
    val app: AppEntry,
    val art: TileArt<Drawable>,
    /** What the app's active media session is showing, when it has one. */
    val mediaTitle: String?,
    val close: CloseGuard.Verdict
)

/** What the strip shows: its cards, and whether the list came from usage access. */
data class RecentsResult(val cards: List<RecentCard>, val usageAccess: Boolean)

/**
 * Reads the recent apps for the strip: usage events (last foreground per app) when usage access
 * is granted, else the launcher's own launch log; media titles from active sessions when
 * notification access is on. Public APIs only; every step guarded. Call off the main thread.
 */
object SwitcherSystem {
    private const val LOOKBACK_MS = 3 * 24 * 60 * 60_000L

    fun load(context: Context, store: SwitcherStore, now: Long = System.currentTimeMillis()): RecentsResult {
        val app = context.applicationContext
        val access = UsageAccess.hasAccess(app)
        val usage = if (access) lastForeground(app, now) else null
        val keep = keepContext(app)
        val launchable = SpeedSystem.launchable(app)
        val order = RecentsOrder.build(usage, store.log(), launchable, { RecentsFilter.excluded(it, keep) }, store.closed(), store.count, store.clearedAt)
        if (usage != null) try { store.remember(order) } catch (e: Throwable) { }
        val media = media(app)
        val unattributed = media.anyPlaying && media.playing.isEmpty()
        val pm = app.packageManager
        val cards = order.mapNotNull { use ->
            try {
                val launch = pm.getLeanbackLaunchIntentForPackage(use.pkg) ?: pm.getLaunchIntentForPackage(use.pkg)
                val cls = launch?.component?.className ?: return@mapNotNull null
                val label = pm.getApplicationLabel(pm.getApplicationInfo(use.pkg, 0)).toString()
                val entry = AppEntry(use.pkg, label, cls)
                RecentCard(entry, TileArtResolver.resolve(pm, entry), media.titles[use.pkg],
                    CloseGuard.check(use.pkg, keep, media.playing, unattributed))
            } catch (e: Throwable) {
                null
            }
        }
        return RecentsResult(cards, access)
    }

    private fun keepContext(app: Context) = KeepRules.Context(
        ownPackage = app.packageName,
        inputMethods = SpeedSystem.inputMethods(app),
        accessibility = SpeedSystem.accessibility(app),
        homeApps = SpeedSystem.homeApps(app),
        screensaver = SpeedSystem.screensaver(app)
    )

    /** Package to its last foreground time (MOVE_TO_FOREGROUND, the same value as ACTIVITY_RESUMED). */
    fun lastForeground(context: Context, now: Long): Map<String, Long>? = try {
        val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = um.queryEvents(now - LOOKBACK_MS, now)
        val list = ArrayList<RecentUse>()
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) list += RecentUse(e.packageName, e.timeStamp)
        }
        RecentsOrder.lastForeground(list)
    } catch (e: Throwable) {
        null
    }

    private class Media(val titles: Map<String, String>, val playing: Set<String>, val anyPlaying: Boolean)

    private fun media(context: Context): Media {
        val titles = HashMap<String, String>()
        val playing = HashSet<String>()
        val music = try { SpeedSystem.media(context).anyPlaying } catch (e: Throwable) { false }
        if (NotificationAccess.isEnabled(context)) {
            try {
                val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                for (c in m.getActiveSessions(NotificationAccess.component(context)).orEmpty()) {
                    val md = c.metadata
                    val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                        ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    if (title != null && c.packageName !in titles) titles[c.packageName] = title
                    when (c.playbackState?.state) {
                        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
                        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> playing += c.packageName
                        else -> {}
                    }
                }
            } catch (e: Throwable) { }
        }
        return Media(titles, playing, music || playing.isNotEmpty())
    }

    /**
     * Asks Android to close [pkg]'s background processes (KILL_BACKGROUND_PROCESSES). The system
     * itself never closes anything in front, visible or perceptible. False when refused.
     */
    fun close(context: Context, pkg: String): Boolean = try {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        true
    } catch (e: Throwable) {
        false
    }
}
