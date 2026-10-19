package dev.tvlauncher.weather

data class DailyForecast(val dayMs: Long, val code: Int, val highC: Double, val lowC: Double)

data class WeatherSnapshot(
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    /** Today's high/low; null when today's daily entry is missing or malformed. */
    val highC: Double?,
    val lowC: Double?,
    val updatedMs: Long,
    val daily: List<DailyForecast>,
)

data class Place(
    val name: String,
    val admin1: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    fun label(): String = listOfNotNull(name, admin1?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
        .joinToString(", ")
}

data class WeatherState(val place: Place?, val unit: String, val snapshot: WeatherSnapshot?)
