package dev.tvlauncher.speed

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.insights.UsageAccess
import java.lang.ref.WeakReference

/**
 * Read-only facts about the TV for [KeepRules] and the Speed page. Public APIs only, every call
 * guarded: a missing service or a refused permission yields an empty answer, never a crash.
 */
object SpeedSystem {
    private const val FOREGROUND_LOOKBACK_MS = 6 * 60 * 60_000L

    fun memory(context: Context): Triple<Long, Long, Boolean> = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        Triple(mi.availMem, mi.totalMem, mi.lowMemory)
    } catch (e: Throwable) {
        Triple(0L, 0L, false)
    }

    /** Free and total bytes of the data partition (where apps and their caches live). */
    fun storage(): Pair<Long, Long> = try {
        val s = StatFs(Environment.getDataDirectory().path)
        s.availableBytes to s.totalBytes
    } catch (e: Throwable) {
        0L to 0L
    }

    fun snapshot(context: Context): Snapshot = Snapshot(storage().first, memory(context).first)

    fun screenOn(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    } catch (e: Throwable) {
        true // unknown: assume someone is watching
    }

    fun usageAccess(context: Context): Boolean = UsageAccess.hasAccess(context)

    /** Packages with a launcher entry (TV or phone launcher). */
    fun launchable(context: Context): Set<String> {
        val out = HashSet<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            try {
                context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
                    .forEach { out += it.activityInfo.packageName }
            } catch (e: Throwable) { }
        }
        return out
    }

    fun homeApps(context: Context): Set<String> = try {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    } catch (e: Throwable) {
        emptySet()
    }

    private fun secure(context: Context, key: String): String? = try {
        Settings.Secure.getString(context.contentResolver, key)
    } catch (e: Throwable) {
        null
    }

    fun inputMethods(context: Context): Set<String> =
        ComponentLists.packages(secure(context, "enabled_input_methods")) +
            ComponentLists.packages(secure(context, Settings.Secure.DEFAULT_INPUT_METHOD))

    fun accessibility(context: Context): Set<String> =
        ComponentLists.packages(secure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))

    fun screensaver(context: Context): Set<String> =
        ComponentLists.packages(secure(context, "screensaver_components"))

    /** What plays, or holds a media session. [anyPlaying] also covers audio we cannot attribute. */
    data class Media(val sessions: Set<String>, val anyPlaying: Boolean)

    fun media(context: Context): Media {
        val music = try {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        } catch (e: Throwable) {
            false
        }
        val sessions = HashSet<String>()
        var playing = music
        if (NotificationAccess.isEnabled(context)) {
            try {
                val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                for (c in m.getActiveSessions(NotificationAccess.component(context)).orEmpty()) {
                    sessions += c.packageName
                    when (c.playbackState?.state) {
                        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
                        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> playing = true
                        else -> {}
                    }
                }
            } catch (e: Throwable) { }
        }
        return Media(sessions, playing)
    }

    /** Package to last-used time over the last three days; null without usage access. */
    fun lastUsed(context: Context, now: Long): Map<String, Long>? {
        if (!usageAccess(context)) return null
        return try {
            val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val out = HashMap<String, Long>()
            for (s in um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3 * RateLimit.DAY_MS, now).orEmpty()) {
                val t = s.lastTimeUsed
                if (t > (out[s.packageName] ?: 0L)) out[s.packageName] = t
            }
            out
        } catch (e: Throwable) {
            null
        }
    }

    /** The app on screen per usage events; empty without usage access. */
    fun foreground(context: Context, now: Long): Set<String> {
        if (!usageAccess(context)) return emptySet()
        return try {
            val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = um.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
            val list = ArrayList<ForegroundGuess.Event>()
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                val kind = when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> ForegroundGuess.Kind.FOREGROUND
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> ForegroundGuess.Kind.BACKGROUND
                    else -> null
                } ?: continue
                list += ForegroundGuess.Event(e.packageName, kind, e.timeStamp)
            }
            setOfNotNull(ForegroundGuess.current(list))
        } catch (e: Throwable) {
            emptySet()
        }
    }

    /**
     * Everything [KeepRules] needs. [recentWindowMs] > 0 also keeps apps used that recently
     * (the HOME trigger). Off the main thread.
     */
    fun keepContext(context: Context, now: Long, media: Media, lastUsed: Map<String, Long>?, recentWindowMs: Long = 0L) =
        KeepRules.Context(
            ownPackage = context.packageName,
            launchable = launchable(context),
            owner = SpeedPrefs(context).keep,
            playing = media.sessions,
            foreground = foreground(context, now),
            inputMethods = inputMethods(context),
            accessibility = accessibility(context),
            homeApps = homeApps(context),
            screensaver = screensaver(context),
            recent = if (recentWindowMs > 0 && lastUsed != null) {
                lastUsed.filterValues { now - it in 0..recentWindowMs }.keys
            } else emptySet()
        )

    /** Per-app cache sizes (needs usage access on Android 8+). Largest first; empty when not measurable. */
    fun cacheByApp(context: Context, packages: Collection<String>): List<Pair<String, Long>> {
        if (Build.VERSION.SDK_INT < 26 || !usageAccess(context)) return emptyList()
        return try {
            val ssm = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
            val uuid = android.os.storage.StorageManager.UUID_DEFAULT
            val user = android.os.Process.myUserHandle()
            packages.mapNotNull { p ->
                try { p to ssm.queryStatsForPackage(uuid, p, user).cacheBytes } catch (e: Throwable) { null }
            }.filter { it.second > 0 }.sortedByDescending { it.second }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** All apps' cache bytes on internal storage (needs usage access), or null when not measurable. */
    fun totalCache(context: Context): Long? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            if (!usageAccess(context)) return null
            context.getSystemService(android.app.usage.StorageStatsManager::class.java)
                .queryStatsForUser(android.os.storage.StorageManager.UUID_DEFAULT, android.os.Process.myUserHandle()).cacheBytes
        } catch (e: Throwable) {
            null
        }
    }

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) {
        pkg
    }
}

/**
 * Our own in-memory image caches. A module registers itself with a trimmer that receives it
 * back (so the trimmer captures nothing and the owner is held only weakly); a cleanup calls them
 * only while the home screen is not in front, so nothing visible flickers.
 */
object OwnCaches {
    private class Entry<T : Any>(owner: T, private val trim: (T) -> Unit) {
        private val ref = WeakReference(owner)
        val alive get() = ref.get() != null
        fun run(): Boolean = ref.get()?.let { trim(it); true } ?: false
    }

    private val entries = ArrayList<Entry<*>>()

    @Synchronized
    fun <T : Any> register(owner: T, trim: (T) -> Unit) {
        entries.removeAll { !it.alive }
        entries += Entry(owner, trim)
    }

    /** Runs every live trimmer; returns how many ran. */
    @Synchronized
    fun trimAll(): Int {
        entries.removeAll { !it.alive }
        var n = 0
        for (e in entries) {
            try { if (e.run()) n++ } catch (t: Throwable) { }
        }
        return n
    }
}
