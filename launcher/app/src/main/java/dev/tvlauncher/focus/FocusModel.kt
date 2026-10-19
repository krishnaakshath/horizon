package dev.tvlauncher.focus

/**
 * Focus mode: a quiet state for watching. While it is on the launcher plays no UI sounds, shows
 * no device cards or notification chips, does not start the ambient screen when idle, and dims
 * the top bar clock; a tiny accent dot in the top bar says it is on. It ends by itself after the
 * chosen time, or stays until it is turned off. Pure.
 *
 * [endsAt] is an epoch time: [OFF] (0) is off, [UNTIL_OFF] stays on until turned off.
 */
data class FocusState(val endsAt: Long = OFF) {
    fun isOn(now: Long): Boolean = endsAt == UNTIL_OFF || (endsAt != OFF && now < endsAt)

    /** Whole minutes left (rounded up), or null when off or open-ended. */
    fun minutesLeft(now: Long): Int? {
        if (!isOn(now) || endsAt == UNTIL_OFF) return null
        return ((endsAt - now + 59_999L) / 60_000L).toInt()
    }

    /** Milliseconds until it ends by itself, or null when off or open-ended. */
    fun msLeft(now: Long): Long? = if (!isOn(now) || endsAt == UNTIL_OFF) null else endsAt - now

    companion object {
        const val OFF = 0L
        const val UNTIL_OFF = Long.MAX_VALUE
    }
}

object FocusModel {
    /** Lengths offered in Settings and used by the one-press toggle; null = until I turn it off. */
    val LENGTHS: List<Int?> = listOf(30, 60, 120, null)
    const val DEFAULT_LENGTH_INDEX = 1

    fun lengthLabel(minutes: Int?): String = when (minutes) {
        null -> "Until I turn it off"
        60 -> "1 hour"
        120 -> "2 hours"
        else -> "$minutes minutes"
    }

    fun start(now: Long, minutes: Int?): FocusState =
        if (minutes == null) FocusState(FocusState.UNTIL_OFF)
        else FocusState(now + minutes.coerceIn(1, 24 * 60) * 60_000L)

    fun stop(): FocusState = FocusState(FocusState.OFF)

    /** The one-press toggle (Spotlight): on for [minutes], or off when it is on. */
    fun toggle(s: FocusState, now: Long, minutes: Int?): FocusState = if (s.isOn(now)) stop() else start(now, minutes)

    /** A saved state that has run out reads as off (and is cleared on the next write). */
    fun normalize(s: FocusState, now: Long): FocusState = if (s.isOn(now)) s else stop()

    fun status(s: FocusState, now: Long): String {
        if (!s.isOn(now)) return "Off"
        val m = s.minutesLeft(now) ?: return "On until you turn it off"
        return if (m >= 60 && m % 60 == 0) "On · ${m / 60} h left" else if (m >= 60) "On · ${m / 60} h ${m % 60} min left" else "On · $m min left"
    }

    /** What the launcher quiets while Focus is on. */
    data class Quiet(val sounds: Boolean, val deviceCards: Boolean, val notificationChips: Boolean, val ambient: Boolean, val dimClock: Boolean)

    fun quiet(s: FocusState, now: Long): Quiet {
        val on = s.isOn(now)
        return Quiet(sounds = on, deviceCards = on, notificationChips = on, ambient = on, dimClock = on)
    }

    const val CLOCK_DIM_ALPHA = 0.5f
    const val DESCRIPTION = "Quiet while you watch: no sounds, cards or screensaver from the launcher."
    const val QUIETS = "Launcher sounds, device cards, notification chips and the idle screensaver; the clock dims"

    fun toggledText(on: Boolean, minutes: Int?): String =
        if (!on) "Focus mode is off" else if (minutes == null) "Focus mode is on" else "Focus mode is on for ${lengthLabel(minutes)}"
}
