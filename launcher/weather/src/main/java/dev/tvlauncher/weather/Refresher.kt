package dev.tvlauncher.weather

import android.content.Context

object Refresher {
    /**
     * Fetches and stores the forecast for the selected place. Blocking.
     * Returns true when finished (success, or nothing to do); false means "retry later".
     */
    fun refresh(context: Context): Boolean {
        val store = WeatherStore(context)
        val place = store.load().place ?: return true
        val result = OpenMeteoClient.fetchForecast(place)
        val snap = result.getOrNull() ?: return false
        store.setSnapshot(place, snap)
        return true
    }
}
