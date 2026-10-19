package dev.tvlauncher.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the public REFRESH action only; rate limited, then queues a one-off network job. Boot and update re-scheduling live in [BootReceiver]. */
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

/**
 * Automatic triggers: the launcher's REFRESH_IF_STALE (signature permission, declared in the
 * manifest), the TV's wake-from-standby broadcast and screen-on / unlock. All of them only ask
 * [Refresher.requestIfStale], which applies the staleness and rate rules. Unknown actions and
 * extras are ignored.
 */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!TriggerActions.triggersRefresh(intent?.action)) return
        Refresher.requestIfStale(context)
    }
}
