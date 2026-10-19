package dev.tvlauncher.weather

/**
 * The provider's rows as plain arrays, in [WeatherContract] column order (pure, so the read-only
 * contract is unit-testable without a ContentResolver).
 */
object ProviderRows {
    const val MAX_DAYS = 5

    /** /current: zero rows = no city; a location-only row = first update pending. */
    fun current(state: WeatherState): List<Array<Any?>> {
        val place = state.place ?: return emptyList()
        val snap = state.snapshot
            ?: return listOf(arrayOf(place.name, null, null, null, null, null, null, null, null, null, 0L, state.unit))
        return listOf(arrayOf(
            place.name, snap.tempC, snap.feelsC, snap.code,
            WmoCodes.text(snap.code), if (snap.isDay) 1 else 0, snap.highC, snap.lowC,
            snap.humidity, snap.windKmh, snap.updatedMs, state.unit,
        ))
    }

    /** /daily: up to [MAX_DAYS] days, oldest first; sun columns NULL when unknown. */
    fun daily(state: WeatherState): List<Array<Any?>> = days(state).map {
        val sun = sunOf(it)
        arrayOf(it.dayMs, it.code, WmoCodes.text(it.code), it.highC, it.lowC, sun?.first, sun?.second)
    }

    /** /sun: one row per day (NULL times on polar days); empty without a city or snapshot. */
    fun sun(state: WeatherState): List<Array<Any?>> {
        val place = state.place ?: return emptyList()
        val snap = state.snapshot ?: return emptyList()
        return days(state).map {
            val sun = sunOf(it)
            arrayOf(it.dayMs, sun?.first, sun?.second, place.name, snap.updatedMs)
        }
    }

    private fun days(state: WeatherState): List<DailyForecast> =
        state.snapshot?.daily?.sortedBy { it.dayMs }?.take(MAX_DAYS) ?: emptyList()

    private fun sunOf(d: DailyForecast): Pair<Long, Long>? =
        if (SunParser.validStored(d.sunriseMs, d.sunsetMs)) d.sunriseMs!! to d.sunsetMs!! else null
}
