package dev.tvlauncher.input

/**
 * The owner's controller choices (Settings > System > Controllers). Stored in the launcher; the
 * companion apps and the helper read them through the launcher's read-only provider
 * ([GamepadSettingsSource]). The text form is tiny and versionless so every app can parse it:
 * "swap=0;sens=1;guide=1;vib=0". Unknown or broken parts fall back to the defaults.
 */
data class GamepadSettings(
    /** Nintendo layout: B confirms, A goes back. */
    val swapAB: Boolean = false,
    /** [LOW], [NORMAL] or [HIGH]: how far the stick must tilt to move focus. */
    val sensitivity: Int = NORMAL,
    /** The Guide / PS / Home button on the controller opens the home screen. */
    val guideHome: Boolean = true,
    /** A short, light buzz on focus moves (controllers with a motor only). */
    val vibrate: Boolean = false,
) {
    val stick: StickTuning get() = StickTuning.forSensitivity(sensitivity)

    fun encode(): String =
        "swap=${b(swapAB)};sens=${sensitivity.coerceIn(LOW, HIGH)};guide=${b(guideHome)};vib=${b(vibrate)}"

    companion object {
        const val LOW = 0
        const val NORMAL = 1
        const val HIGH = 2

        /** Row labels for [sensitivity], index = value. */
        val SENSITIVITY_LABELS = listOf("Low", "Normal", "High")

        private fun b(v: Boolean) = if (v) "1" else "0"

        fun decode(s: String?): GamepadSettings {
            val d = GamepadSettings()
            if (s.isNullOrBlank()) return d
            val map = s.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            fun flag(k: String, def: Boolean) = when (map[k]) { "1" -> true; "0" -> false; else -> def }
            return GamepadSettings(
                swapAB = flag("swap", d.swapAB),
                sensitivity = map["sens"]?.toIntOrNull()?.takeIf { it in LOW..HIGH } ?: d.sensitivity,
                guideHome = flag("guide", d.guideHome),
                vibrate = flag("vib", d.vibrate),
            )
        }
    }
}

/**
 * Stick thresholds. Anything inside [deadzone] is drift and ignored. Focus moves once the stick
 * passes [press] and stops when it falls back under [release] (hysteresis, so a stick resting
 * near the threshold does not chatter). [release] is never below [deadzone].
 */
data class StickTuning(val deadzone: Float, val press: Float, val release: Float) {
    companion object {
        const val DEADZONE = 0.35f

        fun forSensitivity(level: Int): StickTuning = when (level) {
            GamepadSettings.LOW -> StickTuning(DEADZONE, 0.70f, 0.55f)
            GamepadSettings.HIGH -> StickTuning(DEADZONE, 0.42f, DEADZONE)
            else -> StickTuning(DEADZONE, 0.55f, 0.42f)
        }
    }
}
