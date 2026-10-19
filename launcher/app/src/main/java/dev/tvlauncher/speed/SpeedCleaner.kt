package dev.tvlauncher.speed

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One cleanup. Nothing here touches user data: it deletes only cache files (ours directly,
 * other apps' through the system's own cache eviction) and asks the system to close background
 * apps (the system decides; anything in front, playing or persistent is refused by Android
 * itself). Every step is guarded, so a refused call skips that step. Runs off the main thread;
 * at most one at a time.
 */
object SpeedCleaner {
    const val TAG = "Speed"

    enum class Step(val label: String) {
        CACHES("Clearing caches…"),
        APPS("Closing background apps…"),
        MEASURING("Measuring…")
    }

    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    /** Leave this much below the allocatable figure: free space moves while we ask. */
    private const val ALLOCATE_MARGIN = 4 * 1024 * 1024L
    /** Let the system finish tearing processes down before measuring memory. */
    private const val SETTLE_MS = 1_500L

    /**
     * Runs a cleanup for [trigger] if the rate limit allows it; null when it did not run (busy or
     * too soon). [homeInFront] keeps our own visible image caches. [proceed] is checked before
     * each app is closed (the HOME trigger stops as soon as the owner launches something).
     * [progress] is called from this thread. The HOME trigger only closes apps: no disk work
     * while the owner is navigating.
     */
    fun run(
        context: Context,
        trigger: Trigger,
        homeInFront: Boolean = false,
        proceed: () -> Boolean = { true },
        progress: (Step) -> Unit = {}
    ): CleanupResult? {
        val app = context.applicationContext
        val prefs = SpeedPrefs(app)
        val now = System.currentTimeMillis()
        if (!RateLimit.allows(trigger, now, prefs.history(), prefs.unattended())) {
            Log.i(TAG, "$trigger skipped: rate limit")
            return null
        }
        if (!running.compareAndSet(false, true)) return null
        try {
            prefs.recordStart(trigger, now)
            val before = SpeedSystem.snapshot(app)

            var own = 0L
            if (trigger != Trigger.HOME) {
                progress(Step.CACHES)
                own = clearOwnCaches(app)
                if (!homeInFront) OwnCaches.trimAll()
                evictOtherCaches(app)
            }

            progress(Step.APPS)
            val media = SpeedSystem.media(app)
            val (closed, skipped) = if (media.anyPlaying && media.sessions.isEmpty()) {
                // Something plays and we cannot tell what: close nothing.
                0 to true
            } else {
                closeBackground(app, trigger, media, now, proceed) to false
            }

            progress(Step.MEASURING)
            if (closed != 0) SystemClock.sleep(SETTLE_MS)
            val after = SpeedSystem.snapshot(app)
            val r = CleanupResult(
                trigger = trigger,
                storageFreed = FreedBytes.storage(before, after, own),
                memoryFreed = FreedBytes.memory(before, after),
                ownCacheBytes = own,
                appsClosed = closed,
                closingSkipped = skipped,
                at = System.currentTimeMillis()
            )
            if (trigger != Trigger.HOME) prefs.saveResult(r) // Home runs are silent
            Log.i(TAG, "$trigger done: storage +${r.storageFreed} memory +${r.memoryFreed} closed=${r.appsClosed}")
            return r
        } catch (e: Throwable) {
            Log.w(TAG, "$trigger failed", e)
            return null
        } finally {
            running.set(false)
        }
    }

    /** Deletes the contents of our own cache folders; returns the bytes removed. */
    fun clearOwnCaches(context: Context): Long {
        var total = 0L
        for (dir in listOfNotNull(context.cacheDir, context.externalCacheDir)) {
            try {
                dir.listFiles()?.forEach { total += deleteTree(it) }
            } catch (e: Throwable) { }
        }
        return total
    }

    private fun deleteTree(f: File): Long {
        var n = 0L
        if (f.isDirectory && !isSymlink(f)) f.listFiles()?.forEach { n += deleteTree(it) }
        val len = if (f.isFile) f.length() else 0L
        if (f.delete()) n += len
        return n
    }

    private fun isSymlink(f: File): Boolean = try {
        f.canonicalFile != f.absoluteFile
    } catch (e: Throwable) {
        true
    }

    /**
     * Asks the system for as much free space as it can give without deleting anything but caches
     * (StorageManager.allocateBytes on the volume UUID, Android 8+). The system then evicts other
     * apps' cache files, those furthest over their quota first, and never below its own reserved
     * cache floor. Nothing is written: the UUID form only reserves headroom. Returns false when
     * the TV refused or cannot measure caches.
     */
    fun evictOtherCaches(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return try {
            val sm = context.getSystemService(StorageManager::class.java) ?: return false
            val uuid = sm.getUuidForPath(context.filesDir)
            val allocatable = sm.getAllocatableBytes(uuid)
            val target = allocatable - ALLOCATE_MARGIN
            if (target <= 0) return false
            sm.allocateBytes(uuid, target)
            true
        } catch (e: java.io.IOException) {
            Log.i(TAG, "cache eviction refused: ${e.message}")
            false
        } catch (e: Throwable) {
            Log.w(TAG, "cache eviction failed", e)
            false
        }
    }

    /** Asks the system to close the planned background apps; how many were asked, or -1 for "unknown". */
    private fun closeBackground(context: Context, trigger: Trigger, media: SpeedSystem.Media, now: Long, proceed: () -> Boolean): Int {
        val lastUsed = SpeedSystem.lastUsed(context, now)
        if (trigger == Trigger.HOME && lastUsed == null) return 0 // HomePolicy: needs usage access
        val recent = if (trigger == Trigger.HOME) HomePolicy.RECENT_MS else 0L
        val keep = SpeedSystem.keepContext(context, now, media, lastUsed, recent)
        val plan = ClosePlan.candidates(keep, lastUsed, now)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        var n = 0
        for (pkg in plan) {
            if (!proceed()) break
            try {
                am.killBackgroundProcesses(pkg)
                n++
            } catch (e: SecurityException) {
                return n // the permission is missing: stop, do not retry
            } catch (e: Throwable) { }
        }
        // Without usage access every launchable app is "asked", most of them not running:
        // the count would mean nothing, so report "some" (-1) instead.
        return if (lastUsed == null && n > 0) -1 else n
    }
}
