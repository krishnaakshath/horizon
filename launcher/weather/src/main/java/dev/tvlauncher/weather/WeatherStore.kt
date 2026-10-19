package dev.tvlauncher.weather

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences-backed store; serialization lives in [StateCodec]. */
class WeatherStore(private val context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("weather_state", Context.MODE_PRIVATE)

    @Synchronized fun load(): WeatherState = StateCodec.decode(prefs.getString(KEY_STATE, null))

    @Synchronized private fun save(s: WeatherState) {
        prefs.edit().putString(KEY_STATE, StateCodec.encode(s)).apply()
        val resolver = context.applicationContext.contentResolver
        resolver.notifyChange(android.net.Uri.parse(WeatherContract.URI_CURRENT), null)
        resolver.notifyChange(android.net.Uri.parse(WeatherContract.URI_DAILY), null)
    }

    /** Selecting a new place drops the old snapshot so stale data of another city is never served. */
    @Synchronized fun setPlace(place: Place) = save(load().copy(place = place, snapshot = null))

    @Synchronized fun setUnit(unit: String) = save(load().copy(unit = Units.normalize(unit)))

    /** Stores a fetched snapshot only if the place has not changed meanwhile. */
    @Synchronized fun setSnapshot(forPlace: Place, snapshot: WeatherSnapshot) {
        val cur = load()
        if (cur.place == forPlace) save(cur.copy(snapshot = snapshot))
    }

    @Synchronized fun lastRequestMs(): Long = prefs.getLong(KEY_LAST_REQUEST, 0L)
    @Synchronized fun setLastRequestMs(ms: Long) { prefs.edit().putLong(KEY_LAST_REQUEST, ms).apply() }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_LAST_REQUEST = "last_request_ms"
    }
}
