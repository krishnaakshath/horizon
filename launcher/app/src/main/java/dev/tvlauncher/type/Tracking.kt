package dev.tvlauncher.type

/**
 * Default letter-spacing (em) for Inter by text size: open at small sizes, progressively tighter as the
 * size grows (Inter's own optical-size guidance). Applied by Theme.style when a view has no tracking set.
 */
object Tracking {
    fun forSp(sp: Float): Float = when {
        sp <= 14f -> 0f
        sp <= 24f -> -0.006f
        sp <= 40f -> -0.012f
        sp <= 72f -> -0.018f
        else -> -0.015f // Inter Display (Light) is already drawn tight
    }
}
