package dev.tvlauncher.launch

import dev.tvlauncher.device.EffectsMode

/**
 * The per-app launch motion: the tile grows into a full-screen wash of the app's brand colour
 * with its logo fading in, then the app takes over. Pure.
 *
 * Durations stay within the animation level's budget: LITE at most [LITE_MAX_MS], FULL at most
 * [FULL_MAX_MS]. Animations off (Settings or a system animator scale of 0) skip the motion
 * entirely: only the plain loader shows. Apps known to start slowly get the longest motion of
 * the level (it covers more of the wait); an app started a moment ago (still in memory) the
 * shortest, because it comes back almost at once.
 */
object LaunchMotion {
    const val LITE_MAX_MS = 350L
    const val FULL_MAX_MS = 480L
    const val LITE_MIN_MS = 220L
    const val FULL_MIN_MS = 300L

    /** Apps that take a while to show their first frame on a 1 GB TV. */
    val SLOW_STARTERS = setOf(
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.spotify.tv.android", "com.plexapp.android", "org.xbmc.kodi"
    )

    data class Plan(
        /** Total expand time; 0 = no motion. */
        val expandMs: Long,
        /** When the logo starts fading in, and how long it takes. */
        val logoDelayMs: Long,
        val logoMs: Long,
        /** Washed-out brand colour (blended toward the dark surface) for the expanding card. */
        val washStrength: Float
    ) {
        val animated: Boolean get() = expandMs > 0
    }

    val NONE = Plan(0, 0, 0, 0f)

    fun plan(motion: EffectsMode?, animatorScale: Float, pkg: String, warm: Boolean): Plan {
        if (motion == null || animatorScale <= 0f) return NONE
        val (min, max) = if (motion == EffectsMode.FULL) FULL_MIN_MS to FULL_MAX_MS else LITE_MIN_MS to LITE_MAX_MS
        val base = when {
            warm -> min
            pkg in SLOW_STARTERS -> max
            else -> (min + max) / 2
        }
        val ms = base.coerceIn(min, max)
        return Plan(
            expandMs = ms,
            logoDelayMs = ms * 2 / 5,
            logoMs = ms - ms * 2 / 5,
            washStrength = if (motion == EffectsMode.FULL) 0.62f else 0.5f
        )
    }

    /** "Warm": the launcher started it within [WARM_MS]. */
    const val WARM_MS = 10 * 60_000L

    fun isWarm(lastLaunch: Long?, now: Long): Boolean = lastLaunch != null && now - lastLaunch in 0..WARM_MS
}
