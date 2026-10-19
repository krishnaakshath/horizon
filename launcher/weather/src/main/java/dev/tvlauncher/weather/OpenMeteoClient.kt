package dev.tvlauncher.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Minimal HTTPS client. Blocking: never call from the main thread. Never logs coordinates. */
object OpenMeteoClient {
    private const val TIMEOUT_MS = 8_000
    private const val MAX_BYTES = 256 * 1024
    private const val USER_AGENT = "TVLauncherWeather/1.0"

    class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

    internal fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
            "&timezone=auto&forecast_days=5&wind_speed_unit=kmh"

    const val MAX_QUERY_CHARS = 64

    /** Trims and truncates a user query to [MAX_QUERY_CHARS] without splitting a surrogate pair. */
    fun normalizeQuery(query: String): String {
        var q = query.trim()
        if (q.length > MAX_QUERY_CHARS) {
            q = q.substring(0, MAX_QUERY_CHARS)
            if (Character.isHighSurrogate(q.last())) q = q.dropLast(1)
            q = q.trim()
        }
        return q
    }

    internal fun searchUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(normalizeQuery(query), "UTF-8")}" +
            "&count=8&language=en&format=json"

    private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)

    fun fetchForecast(place: Place): Result<WeatherSnapshot> = runCatching {
        val body = get(forecastUrl(place.latitude, place.longitude))
        OpenMeteoParser.parseForecast(body) ?: throw NetworkException("Unexpected response")
    }

    fun searchPlaces(query: String): Result<List<Place>> = runCatching {
        if (normalizeQuery(query).isEmpty()) emptyList() else GeocodingParser.parse(get(searchUrl(query)))
    }

    private fun get(url: String): String {
        require(url.startsWith("https://")) { "HTTPS only" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code != 200) throw NetworkException("HTTP $code")
            if (conn.contentLengthLong > MAX_BYTES) throw NetworkException("Response too large")
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_BYTES) throw NetworkException("Response too large")
                    out.write(buf, 0, n)
                }
                return out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }
}
