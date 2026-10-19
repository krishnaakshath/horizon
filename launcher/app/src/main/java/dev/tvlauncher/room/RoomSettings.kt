package dev.tvlauncher.room

import org.json.JSONObject

/**
 * Room-lighting preferences, stored as JSON in SharedPreferences "room_settings" (see [RoomPrefs]).
 * [fromJson] never throws: unknown keys are ignored, wrong types/out-of-range values fall back to
 * the default of that field, corrupt JSON gives all defaults.
 *
 * @param latitudeDeg when set, AUTO estimates sunrise/sunset offline ([SolarEstimator]); null =
 *   fixed schedule (unless the weather companion supplies sun times).
 * @param transitionSeconds crossfade length; 0 = instant. LITE devices cap it ([RoomTransitions]).
 * @param eveningStart/nightStart/dayStart fixed-schedule overrides, minutes of the local day.
 * @param boostTextInBright BRIGHT may use heavier faces and a larger minimum text size.
 * @param longitudeDeg optional, east positive (-180..180). With it the sunrise/sunset estimate is
 *   about +/-10 min; without it the time zone's meridian is assumed (error 4 min per degree from
 *   that meridian: ~20 min for Delhi, up to ~40 min in far-west-of-zone places).
 */
data class RoomSettings(
    val choice: RoomChoice = RoomChoice.AUTO,
    val latitudeDeg: Double? = null,
    val transitionSeconds: Float = 1.2f,
    val eveningStart: Int = Schedule.DEFAULT.eveningStart,
    val nightStart: Int = Schedule.DEFAULT.nightStart,
    val dayStart: Int = Schedule.DEFAULT.dayStart,
    val boostTextInBright: Boolean = true,
    val longitudeDeg: Double? = null
) {
    val schedule: Schedule get() = Schedule(dayStart = dayStart, eveningStart = eveningStart, nightStart = nightStart)

    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("choice", choice.name)
        .put("latitudeDeg", latitudeDeg ?: JSONObject.NULL)
        .put("longitudeDeg", longitudeDeg ?: JSONObject.NULL)
        .put("transitionSeconds", transitionSeconds.toDouble())
        .put("eveningStart", eveningStart)
        .put("nightStart", nightStart)
        .put("dayStart", dayStart)
        .put("boostTextInBright", boostTextInBright)
        .toString()

    companion object {
        const val MAX_TRANSITION_SECONDS = 5f

        fun fromJson(s: String?): RoomSettings {
            val d = RoomSettings()
            if (s.isNullOrBlank()) return d
            return try {
                val j = JSONObject(s)
                RoomSettings(
                    choice = RoomChoice.parse(j.opt("choice") as? String) ?: d.choice,
                    latitudeDeg = latitude(j.opt("latitudeDeg")),
                    transitionSeconds = (j.opt("transitionSeconds") as? Number)?.toFloat()
                        ?.takeIf { it.isFinite() }?.coerceIn(0f, MAX_TRANSITION_SECONDS) ?: d.transitionSeconds,
                    eveningStart = minute(j.opt("eveningStart")) ?: d.eveningStart,
                    nightStart = minute(j.opt("nightStart")) ?: d.nightStart,
                    dayStart = minute(j.opt("dayStart")) ?: d.dayStart,
                    boostTextInBright = (j.opt("boostTextInBright") as? Boolean) ?: d.boostTextInBright,
                    longitudeDeg = longitude(j.opt("longitudeDeg"))
                )
            } catch (e: Exception) {
                d
            }
        }

        fun latitude(v: Any?): Double? =
            (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= -90.0 && it <= 90.0 }

        /** -180..180, finite, else null. */
        fun longitude(v: Any?): Double? =
            (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= -180.0 && it <= 180.0 }

        internal fun minute(v: Any?): Int? {
            val n = v as? Number ?: return null
            val d = n.toDouble()
            if (!d.isFinite() || d != Math.floor(d)) return null
            val i = d.toInt()
            return i.takeIf { it in 0 until 1440 }
        }
    }
}

/** Crossfade duration for a mode change. */
object RoomTransitions {
    const val LITE_CAP_MS = 600L

    /** 0 when the system animator scale is 0 or the user picked "Off"; LITE halves and caps it. */
    fun durationMs(s: RoomSettings, lite: Boolean, animatorScale: Float): Long {
        if (animatorScale == 0f) return 0L
        val base = (s.transitionSeconds.coerceIn(0f, RoomSettings.MAX_TRANSITION_SECONDS) * 1000f).toLong()
        if (base <= 0L) return 0L
        return if (lite) minOf(base / 2, LITE_CAP_MS) else base
    }
}
