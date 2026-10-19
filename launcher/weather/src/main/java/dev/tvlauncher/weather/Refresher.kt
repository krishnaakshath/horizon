package dev.tvlauncher.weather

import android.content.Context

object Refresher {
    /**
     * Fetches and stores the forecast for the selected place. Blocking.
     * Returns true when finished (success, or nothing to do); false means "retry later".
     * The last stored data is left untouched on failure. Every network call is counted for the
     * rate limits in [RefreshPolicy].
     */
    fun refresh(context: Context): Boolean {
        val store = WeatherStore(context)
        val place = store.load().place ?: return true
        store.recordAttempt(System.currentTimeMillis())
        val result = OpenMeteoClient.fetchForecast(place)
        val snap = result.getOrNull()
        if (snap == null) {
            store.setFailures(store.failures() + 1)
            return false
        }
        store.setSnapshotIfPlace(place, snap)
        store.setFailures(0)
        return true
    }

    /** What the rules say right now for [store]. */
    fun decide(store: WeatherStore, now: Long): RefreshPolicy.Decision {
        val s = store.load()
        return RefreshPolicy.decide(now, s.place != null, s.snapshot?.updatedMs, store.attempts(), store.failures())
    }

    /**
     * Entry point of every automatic trigger: queues a one-off job that waits for a network, but
     * only when the data is stale and the limits allow a call. Cheap; safe on the main thread.
     */
    fun requestIfStale(context: Context, now: Long = System.currentTimeMillis()): RefreshPolicy.Decision {
        val d = try { decide(WeatherStore(context), now) } catch (e: RuntimeException) { return RefreshPolicy.Decision.NO_PLACE }
        if (d == RefreshPolicy.Decision.REFRESH) RefreshScheduler.refreshWhenOnline(context)
        return d
    }
}
