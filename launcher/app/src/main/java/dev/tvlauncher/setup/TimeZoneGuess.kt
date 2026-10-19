package dev.tvlauncher.setup

/**
 * A representative place (latitude, longitude; east positive) for common time zones, so room
 * lighting's sunrise/sunset estimate works offline without asking for a location. Rough on
 * purpose (a zone's main population centre); unknown zones return null and room lighting
 * then follows the time of day.
 */
object TimeZoneGuess {
    private val PLACES: Map<String, Pair<Double, Double>> = mapOf(
        "Asia/Kolkata" to (21.1 to 79.0), "Asia/Calcutta" to (21.1 to 79.0),
        "Asia/Karachi" to (24.9 to 67.0), "Asia/Dhaka" to (23.8 to 90.4), "Asia/Kathmandu" to (27.7 to 85.3),
        "Asia/Colombo" to (6.9 to 79.9), "Asia/Dubai" to (25.2 to 55.3), "Asia/Riyadh" to (24.7 to 46.7),
        "Asia/Tehran" to (35.7 to 51.4), "Asia/Bangkok" to (13.8 to 100.5), "Asia/Jakarta" to (-6.2 to 106.8),
        "Asia/Singapore" to (1.35 to 103.8), "Asia/Kuala_Lumpur" to (3.1 to 101.7), "Asia/Manila" to (14.6 to 121.0),
        "Asia/Ho_Chi_Minh" to (10.8 to 106.7), "Asia/Shanghai" to (31.2 to 121.5), "Asia/Hong_Kong" to (22.3 to 114.2),
        "Asia/Taipei" to (25.0 to 121.5), "Asia/Seoul" to (37.6 to 127.0), "Asia/Tokyo" to (35.7 to 139.7),
        "Australia/Sydney" to (-33.9 to 151.2), "Australia/Melbourne" to (-37.8 to 145.0),
        "Australia/Brisbane" to (-27.5 to 153.0), "Australia/Perth" to (-31.95 to 115.9),
        "Australia/Adelaide" to (-34.9 to 138.6), "Pacific/Auckland" to (-36.8 to 174.8),
        "Europe/London" to (51.5 to -0.1), "Europe/Dublin" to (53.3 to -6.3), "Europe/Lisbon" to (38.7 to -9.1),
        "Europe/Madrid" to (40.4 to -3.7), "Europe/Paris" to (48.9 to 2.35), "Europe/Berlin" to (52.5 to 13.4),
        "Europe/Amsterdam" to (52.4 to 4.9), "Europe/Brussels" to (50.85 to 4.35), "Europe/Rome" to (41.9 to 12.5),
        "Europe/Zurich" to (47.4 to 8.5), "Europe/Vienna" to (48.2 to 16.4), "Europe/Stockholm" to (59.3 to 18.1),
        "Europe/Oslo" to (59.9 to 10.75), "Europe/Copenhagen" to (55.7 to 12.6), "Europe/Helsinki" to (60.2 to 24.9),
        "Europe/Warsaw" to (52.2 to 21.0), "Europe/Prague" to (50.1 to 14.4), "Europe/Athens" to (37.98 to 23.7),
        "Europe/Istanbul" to (41.0 to 29.0), "Europe/Moscow" to (55.75 to 37.6), "Europe/Kiev" to (50.45 to 30.5),
        "Europe/Kyiv" to (50.45 to 30.5), "Africa/Cairo" to (30.0 to 31.2), "Africa/Lagos" to (6.5 to 3.4),
        "Africa/Nairobi" to (-1.3 to 36.8), "Africa/Johannesburg" to (-26.2 to 28.0),
        "America/New_York" to (40.7 to -74.0), "America/Toronto" to (43.7 to -79.4), "America/Chicago" to (41.9 to -87.6),
        "America/Denver" to (39.7 to -105.0), "America/Phoenix" to (33.4 to -112.1),
        "America/Los_Angeles" to (34.05 to -118.2), "America/Vancouver" to (49.3 to -123.1),
        "America/Anchorage" to (61.2 to -149.9), "Pacific/Honolulu" to (21.3 to -157.9),
        "America/Mexico_City" to (19.4 to -99.1), "America/Bogota" to (4.7 to -74.1), "America/Lima" to (-12.05 to -77.0),
        "America/Santiago" to (-33.45 to -70.7), "America/Buenos_Aires" to (-34.6 to -58.4),
        "America/Argentina/Buenos_Aires" to (-34.6 to -58.4), "America/Sao_Paulo" to (-23.55 to -46.6)
    )

    /** (latitude, longitude) for [zoneId], or null when the zone is not in the table. */
    fun place(zoneId: String?): Pair<Double, Double>? = zoneId?.trim()?.let { PLACES[it] }
}
