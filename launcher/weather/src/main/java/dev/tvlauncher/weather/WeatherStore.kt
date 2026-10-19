package dev.tvlauncher.weather

import android.content.Context
import android.net.Uri

/** Key/value persistence behind the store (SharedPreferences in the app, a map in tests). */
interface StoreBacking {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * Weather state store. Every instance (activity, job, provider, receiver) shares ONE
 * process-wide [LOCK]; all read-modify-write operations run through [update] under it, so
 * concurrent writers can never overwrite each other with stale copies.
 */
class WeatherStore(
    private val backing: StoreBacking,
    private val onChange: () -> Unit = {},
) {
    constructor(context: Context) : this(PrefsBacking(context.applicationContext), notifier(context.applicationContext))

    fun load(): WeatherState = synchronized(LOCK) { StateCodec.decode(backing.get(KEY_STATE)) }

    /** Atomically loads, applies [transform] and saves (and notifies only when changed). */
    fun update(transform: (WeatherState) -> WeatherState): WeatherState {
        val changed: Boolean
        val result: WeatherState
        synchronized(LOCK) {
            val cur = StateCodec.decode(backing.get(KEY_STATE))
            result = transform(cur)
            changed = result != cur
            if (changed) backing.put(KEY_STATE, StateCodec.encode(result))
        }
        if (changed) onChange()
        return result
    }

    /** Selecting a place drops the old snapshot so another city's data is never served. */
    fun setPlace(place: Place) { update { it.copy(place = place, snapshot = null) } }

    fun setUnit(unit: String) { update { it.copy(unit = Units.normalize(unit)) } }

    /**
     * Compare-and-set: stores [snapshot] only if the currently selected place still equals
     * [placeKey] (the place the fetch was made for). Returns false when dropped.
     */
    fun setSnapshotIfPlace(placeKey: Place, snapshot: WeatherSnapshot): Boolean {
        var stored = false
        update { cur ->
            if (cur.place == placeKey) { stored = true; cur.copy(snapshot = snapshot) } else cur
        }
        return stored
    }

    fun lastRequestMs(): Long = synchronized(LOCK) { backing.get(KEY_LAST_REQUEST)?.toLongOrNull() ?: 0L }
    fun setLastRequestMs(ms: Long) = synchronized(LOCK) { backing.put(KEY_LAST_REQUEST, ms.toString()) }

    /** True when the last attempt to schedule the periodic background job failed. */
    fun scheduleFailed(): Boolean = synchronized(LOCK) { backing.get(KEY_SCHEDULE_FAILED) == "1" }
    fun setScheduleFailed(failed: Boolean) = synchronized(LOCK) { backing.put(KEY_SCHEDULE_FAILED, if (failed) "1" else "0") }

    /** Start times (epoch ms) of recent network calls, newest last; only the last hour is kept. */
    fun attempts(): List<Long> = synchronized(LOCK) {
        backing.get(KEY_ATTEMPTS)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    fun recordAttempt(now: Long) = synchronized(LOCK) {
        val kept = attempts().filter { it <= now && now - it < RefreshPolicy.HOUR_MS }.takeLast(RefreshPolicy.MAX_PER_HOUR * 2)
        backing.put(KEY_ATTEMPTS, (kept + now).joinToString(","))
    }

    /** Consecutive failed network calls; reset by a success. */
    fun failures(): Int = synchronized(LOCK) { backing.get(KEY_FAILURES)?.toIntOrNull() ?: 0 }
    fun setFailures(n: Int) = synchronized(LOCK) { backing.put(KEY_FAILURES, n.coerceAtLeast(0).toString()) }

    private class PrefsBacking(context: Context) : StoreBacking {
        private val prefs = context.getSharedPreferences("weather_state", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    }

    companion object {
        private val LOCK = Any()
        private const val KEY_STATE = "state"
        private const val KEY_LAST_REQUEST = "last_request_ms"
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_FAILURES = "failures"
        private const val KEY_SCHEDULE_FAILED = "schedule_failed"

        private fun notifier(app: Context): () -> Unit = {
            val r = app.contentResolver
            r.notifyChange(Uri.parse(WeatherContract.URI_CURRENT), null)
            r.notifyChange(Uri.parse(WeatherContract.URI_DAILY), null)
            r.notifyChange(Uri.parse(WeatherContract.URI_SUN), null)
        }
    }
}
