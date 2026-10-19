package dev.tvlauncher.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the public REFRESH action (rate limited) and BOOT_COMPLETED (re-schedule). */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            WeatherContract.ACTION_REFRESH -> {
                val store = WeatherStore(context)
                val now = System.currentTimeMillis()
                if (RateLimiter.allow(now, store.lastRequestMs(), WeatherContract.REFRESH_MIN_INTERVAL_MS)) {
                    store.setLastRequestMs(now)
                    RefreshScheduler.refreshSoon(context)
                }
            }
        }
    }
}
