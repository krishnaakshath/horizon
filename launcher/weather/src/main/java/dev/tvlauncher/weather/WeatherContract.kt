package dev.tvlauncher.weather

/**
 * Public, stable contract of the weather companion. Other apps (the launcher) depend on
 * these names; do not change them.
 */
object WeatherContract {
    const val AUTHORITY = "dev.tvlauncher.weather"
    const val PATH_CURRENT = "current"
    const val PATH_DAILY = "daily"
    const val PATH_SUN = "sun"
    const val URI_CURRENT = "content://$AUTHORITY/$PATH_CURRENT"
    const val URI_DAILY = "content://$AUTHORITY/$PATH_DAILY"
    const val URI_SUN = "content://$AUTHORITY/$PATH_SUN"

    const val ACTION_REFRESH = "dev.tvlauncher.weather.action.REFRESH"
    const val ACTION_SETTINGS = "dev.tvlauncher.weather.action.SETTINGS"

    /**
     * Sent by the launcher (same signer only, guarded by [PERMISSION_REFRESH]) when its copy of
     * the data is older than 20 min. The companion refreshes only if its own data is stale and
     * its rate limits allow it.
     */
    const val ACTION_REFRESH_IF_STALE = "dev.tvlauncher.weather.action.REFRESH_IF_STALE"
    const val PERMISSION_REFRESH = "dev.tvlauncher.permission.WEATHER_REFRESH"

    /** Minimum gap between broadcast-triggered refreshes. */
    const val REFRESH_MIN_INTERVAL_MS = 2 * 60 * 1000L

    // Columns of /current
    const val COL_LOCATION = "location"
    const val COL_TEMP_C = "temp_c"
    const val COL_FEELS_C = "feels_c"
    const val COL_CODE = "code"
    const val COL_CONDITION = "condition"
    const val COL_IS_DAY = "is_day"
    const val COL_HIGH_C = "high_c"
    const val COL_LOW_C = "low_c"
    const val COL_HUMIDITY = "humidity"
    const val COL_WIND_KMH = "wind_kmh"
    const val COL_UPDATED_MS = "updated_ms"
    const val COL_UNIT = "unit"

    // Columns of /daily (code, condition, high_c, low_c shared names above)
    const val COL_DAY_MS = "day_ms"
    /** Epoch ms; NULL for polar day/night or when unknown. Appended (added in v2), nullable. */
    const val COL_SUNRISE_MS = "sunrise_ms"
    const val COL_SUNSET_MS = "sunset_ms"

    // Columns of /sun: one row per forecast day (sorted, at most 5) with day_ms, sunrise_ms,
    // sunset_ms (NULL on polar days), plus the place name and updated_ms of the snapshot.
    // Zero rows: no city chosen, or the first update for a new city is still pending.
    // Sun times do not go stale like temperatures: a row stays valid for its own date.

    val CURRENT_COLUMNS = arrayOf(
        COL_LOCATION, COL_TEMP_C, COL_FEELS_C, COL_CODE, COL_CONDITION, COL_IS_DAY,
        COL_HIGH_C, COL_LOW_C, COL_HUMIDITY, COL_WIND_KMH, COL_UPDATED_MS, COL_UNIT,
    )
    /** The first five are the original v1 columns, in their original order. */
    val DAILY_COLUMNS = arrayOf(COL_DAY_MS, COL_CODE, COL_CONDITION, COL_HIGH_C, COL_LOW_C, COL_SUNRISE_MS, COL_SUNSET_MS)
    val SUN_COLUMNS = arrayOf(COL_DAY_MS, COL_SUNRISE_MS, COL_SUNSET_MS, COL_LOCATION, COL_UPDATED_MS)
}
