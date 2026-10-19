package dev.tvlauncher.room

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Cache in front of the weather companion's sun rows. No Android, so it is unit-tested.
 *
 * - [times] and [status] never block: they answer from the cache and, when it is missing,
 *   older than [ttlMs], from another local day, or [invalidate]d (the companion stored new
 *   data), start ONE background [reader] call on [executor].
 * - A failed read keeps the last good rows (sun times stay valid for their own dates) and is
 *   retried after [retryMs]; "not installed" or "no city" clears them.
 * - Listeners run through [post] (the main thread) only when a read changed something.
 */
class SunCache(
    private val reader: () -> SunRead,
    private val executor: Executor,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val elapsed: () -> Long = { SystemClock.elapsedRealtime() },
    private val zone: () -> TimeZone = { TimeZone.getDefault() },
    private val post: (Runnable) -> Unit = { it.run() },
    private val ttlMs: Long = 6 * 3_600_000L,
    private val retryMs: Long = 10 * 60_000L
) {
    private val lock = Any()
    private var read: SunRead? = null
    private var readAt = 0L
    private var readDay = Long.MIN_VALUE
    private var dirty = false
    private var inFlight = false
    private var retryAt = Long.MIN_VALUE
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Today's sane times, or null (not read yet, no companion, no city, polar, implausible). */
    fun times(): SunTimes? {
        maybeRefresh()
        val r = synchronized(lock) { read } ?: return null
        return WeatherSun.pick(r.rows, wallClock(), zone())
    }

    fun status(): WeatherSunStatus {
        maybeRefresh()
        return WeatherSun.status(synchronized(lock) { read }, wallClock(), zone())
    }

    /** The companion announced new data: re-read now. */
    fun invalidate() {
        synchronized(lock) { dirty = true; retryAt = Long.MIN_VALUE }
        maybeRefresh()
    }

    fun maybeRefresh() {
        val now = elapsed()
        synchronized(lock) {
            if (inFlight) return
            val stale = read == null || dirty || now - readAt !in 0 until ttlMs ||
                WeatherSun.localDay(wallClock(), zone()) != readDay
            if (!stale) return
            if (!dirty && retryAt != Long.MIN_VALUE && now < retryAt) return
            inFlight = true
            dirty = false
        }
        try {
            executor.execute { runRead() }
        } catch (e: RuntimeException) {
            synchronized(lock) { inFlight = false; retryAt = now + retryMs }
        }
    }

    private fun runRead() {
        val r = try { reader() } catch (e: Exception) { SunRead.FAILED }
        val changed: Boolean
        synchronized(lock) {
            inFlight = false
            val now = elapsed()
            if (r.rows == null && r.installed) {
                // Keep the last good rows; try again a little later.
                retryAt = now + retryMs
                changed = read == null
                if (read == null) read = r
            } else {
                retryAt = Long.MIN_VALUE
                changed = r != read
                read = r
                readAt = now
                readDay = WeatherSun.localDay(wallClock(), zone())
            }
        }
        if (changed) post(Runnable { for (l in listeners) try { l() } catch (e: RuntimeException) { } })
    }
}

/**
 * The weather companion as the Room schedule's first sunrise/sunset source:
 * `content://dev.tvlauncher.weather/sun` (see the companion's WeatherContract). One instance per
 * process ([get]), shared by the home screen and Settings.
 *
 * Main-thread safety: [sunriseSunsetMinutes] only reads the [SunCache]; the provider is queried
 * on one background thread (after a cheap installed check there), re-read every 6 h, on the
 * first evaluation of a new day, and whenever the companion announces new data. Never throws.
 */
class WeatherDaylightSource private constructor(context: Context) : DaylightSource {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    val cache = SunCache(
        reader = { readProvider() },
        executor = Executors.newSingleThreadExecutor { r -> Thread(r, "room-sun").apply { isDaemon = true } },
        post = { main.post(it) }
    )

    init {
        try {
            app.contentResolver.registerContentObserver(URI, false, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) { cache.invalidate() }
            })
        } catch (e: RuntimeException) {
            // No observer: the 6 h and new-day re-reads still apply.
        }
    }

    override fun sunriseSunsetMinutes(): Pair<Int, Int>? = try { cache.times()?.pair() } catch (e: Exception) { null }

    fun status(): WeatherSunStatus = try { cache.status() } catch (e: Exception) {
        WeatherSunStatus(WeatherSunState.NO_TIMES, null, null)
    }

    /** Starts the first read early (no-op while the cache is fresh). */
    fun prime() { try { cache.maybeRefresh() } catch (e: Exception) { } }

    private fun readProvider(): SunRead {
        if (app.packageManager.resolveContentProvider(AUTHORITY, 0) == null) return SunRead.MISSING
        val c: Cursor = app.contentResolver.query(URI, null, null, null, null) ?: return SunRead.FAILED
        c.use {
            // An older companion answers /sun with its /current columns and no rows.
            if (it.getColumnIndex(WeatherSun.COL_SUNRISE_MS) < 0) return SunRead(true, false, emptyList())
            val rows = ArrayList<Map<String, Any?>>()
            while (it.moveToNext() && rows.size < MAX_ROWS) {
                val m = HashMap<String, Any?>()
                for (name in WeatherSun.COLUMNS) {
                    val i = it.getColumnIndex(name)
                    if (i < 0 || it.isNull(i)) continue
                    m[name] = if (name == WeatherSun.COL_LOCATION) it.getString(i) else it.getLong(i)
                }
                rows += m
            }
            return SunRead(true, true, WeatherSun.parse(rows))
        }
    }

    companion object {
        const val AUTHORITY = "dev.tvlauncher.weather"
        val URI: Uri = Uri.parse("content://$AUTHORITY/sun")
        private const val MAX_ROWS = 10

        @Volatile private var instance: WeatherDaylightSource? = null

        fun get(context: Context): WeatherDaylightSource =
            instance ?: synchronized(this) { instance ?: WeatherDaylightSource(context).also { instance = it } }
    }
}
