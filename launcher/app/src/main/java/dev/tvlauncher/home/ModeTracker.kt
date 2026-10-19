package dev.tvlauncher.home

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.insights.AppMode
import dev.tvlauncher.insights.DEFAULT_IGNORE
import dev.tvlauncher.insights.ModeClassifier
import dev.tvlauncher.insights.ModeDetector
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.insights.UsageAggregator
import dev.tvlauncher.insights.UsageEvent
import dev.tvlauncher.insights.UsageStatsSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Pure: what the user was doing right before the launcher came back. */
object RecentMode {
    const val LOOKBACK_MS = 6L * 60 * 60 * 1000
    const val WINDOW_MS = 3L * 60 * 1000

    /**
     * The mode of the app that is still in front ([ModeDetector]), else of the last app session
     * (launcher, SystemUI and other [ignore]d packages skipped) that ended at most [WINDOW_MS]
     * before [nowMs]. OTHER when there is none or that app is not video, music or a game.
     */
    fun recentMode(
        events: List<UsageEvent>,
        classify: (String) -> AppMode,
        nowMs: Long,
        ignore: Set<String> = DEFAULT_IGNORE
    ): AppMode {
        val live = ModeDetector.currentMode(events, classify, ignore, nowMs = nowMs, maxStaleMs = LOOKBACK_MS)
        if (live != AppMode.OTHER) return live
        val last = UsageAggregator.sessions(events, nowMs - LOOKBACK_MS, nowMs, ignore).maxByOrNull { it.endMs }
            ?: return AppMode.OTHER
        return if (nowMs - last.endMs <= WINDOW_MS) classify(last.pkg) else AppMode.OTHER
    }

    /** The hero's mode chip; null hides it. */
    fun chipText(mode: AppMode): String? = when (mode) {
        AppMode.VIDEO -> "Watching · Video"
        AppMode.GAME -> "Gaming"
        AppMode.MUSIC -> "Music"
        AppMode.OTHER -> null
    }
}

/**
 * Works out [mode] on every resume, off the main thread, only when usage access is granted
 * (otherwise OTHER, so nothing is shown). [onMode] runs on the main thread. [mode] is kept for
 * later features; today it only drives the hero chip.
 */
class ModeTracker(context: Context, private val onMode: (AppMode) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mode-tracker").apply { isDaemon = true } }
    private val generation = AtomicInteger()
    @Volatile private var released = false

    @Volatile
    var mode: AppMode = AppMode.OTHER
        private set

    fun onResume() {
        val gen = generation.incrementAndGet()
        val resumedAt = System.currentTimeMillis()
        try {
            worker.execute {
                val m = try {
                    if (!UsageAccess.hasAccess(app)) AppMode.OTHER
                    else RecentMode.recentMode(UsageStatsSource(app).events(resumedAt - RecentMode.LOOKBACK_MS, resumedAt), ::classify, resumedAt)
                } catch (e: Throwable) {
                    AppMode.OTHER
                }
                main.post { if (!released && gen == generation.get()) { mode = m; onMode(m) } }
            }
        } catch (e: RuntimeException) {
            // Released: nothing to do.
        }
    }

    private fun classify(pkg: String): AppMode {
        val category = if (Build.VERSION.SDK_INT >= 26) {
            try { app.packageManager.getApplicationInfo(pkg, 0).category } catch (e: Exception) { null }
        } else null
        return ModeClassifier.classify(pkg, category)
    }

    fun release() {
        released = true
        worker.shutdownNow()
    }
}
