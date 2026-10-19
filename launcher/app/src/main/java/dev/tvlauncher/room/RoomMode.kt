package dev.tvlauncher.room

/**
 * How lit the room in front of the TV is, darkest first. The order matters: [ordinal] is used as
 * "brightness rank" by [RoomLightModel] (hysteresis bands, big-jump dwell) and by [Palettes]
 * (black floor strictly rising, focus ring never thinner).
 *
 * The launcher cannot change the panel's backlight or picture mode; every mode only changes the
 * luminance, contrast, translucency and glow of what we draw.
 */
enum class RoomMode {
    /** Dark room / cinema: true dark theme, white capped at ~82 %, soft glows, near-black gradients. */
    CINEMA,
    /** Dim room, a lamp on, evening: slightly lifted blacks, warmer, mid contrast. */
    EVENING,
    /** Normally lit room: balanced; black floor is a dark gray so the panel isn't a black mirror. */
    STANDARD,
    /** Daylight / glare / reflective room: lifted slate floor, opaque glass, thick ring, white text. */
    BRIGHT
}

/** The user's setting. [AUTO] follows the light sensor (if any) or the time of day. */
enum class RoomChoice {
    AUTO, CINEMA, EVENING, STANDARD, BRIGHT;

    /** The fixed mode of a manual choice, or null for [AUTO]. */
    val manualMode: RoomMode?
        get() = when (this) {
            AUTO -> null
            CINEMA -> RoomMode.CINEMA
            EVENING -> RoomMode.EVENING
            STANDARD -> RoomMode.STANDARD
            BRIGHT -> RoomMode.BRIGHT
        }

    companion object {
        /** Tolerant parse (case-insensitive); null when unknown. */
        fun parse(s: String?): RoomChoice? {
            if (s == null) return null
            val t = s.trim().uppercase()
            return values().firstOrNull { it.name == t }
        }
    }
}
