package dev.tvlauncher.weather

enum class WeatherGroup { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

object WmoCodes {
    fun text(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Rime fog"
        51 -> "Light drizzle"
        53 -> "Drizzle"
        55 -> "Heavy drizzle"
        56 -> "Freezing drizzle"
        57 -> "Heavy freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66 -> "Freezing rain"
        67 -> "Heavy freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light rain showers"
        81 -> "Rain showers"
        82 -> "Violent rain showers"
        85 -> "Snow showers"
        86 -> "Heavy snow showers"
        95 -> "Thunderstorm"
        96 -> "Thunderstorm with hail"
        99 -> "Severe thunderstorm with hail"
        else -> "Unknown"
    }

    fun group(code: Int): WeatherGroup = when (code) {
        0, 1 -> WeatherGroup.CLEAR
        2 -> WeatherGroup.PARTLY_CLOUDY
        3 -> WeatherGroup.CLOUDY
        45, 48 -> WeatherGroup.FOG
        51, 53, 55, 56, 57 -> WeatherGroup.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherGroup.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherGroup.SNOW
        95, 96, 99 -> WeatherGroup.STORM
        else -> WeatherGroup.UNKNOWN
    }
}
