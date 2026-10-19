package dev.tvlauncher.volume.core

/**
 * One way of changing the TV's volume. Implementations wrap a platform API (AudioManager today);
 * a backend that is not usable on this TV or route simply reports so, and the controller tries
 * the next one or leaves the key to the system.
 */
interface VolumeBackend {
    /** Short stable id, shown in the helper's status line. */
    val id: String

    /** The current state, or null when the backend cannot read it (no stream, API error). */
    fun read(): VolumeState?

    /** False when the platform says the volume cannot be changed here (fixed-volume output). */
    fun canControl(): Boolean

    /** Applies [to] (coming from [from]). Errors are swallowed; the controller reads back to verify. */
    fun write(from: VolumeState, to: VolumeState)
}

/** What happened to a key. */
sealed class VolumeResult {
    /** We changed (or deliberately kept) the volume: consume the key and show [state]. */
    data class Handled(val state: VolumeState, val backend: String) : VolumeResult()

    /** Leave the key to the system (the TV's own volume handling and its popup). */
    data class Pass(val reason: String) : VolumeResult()
}

/**
 * Pluggable volume control with a read-back check, so the user never loses volume control:
 *
 * - Backends are tried in order. A backend whose write is not visible in an immediate read-back
 *   (the TV routes volume elsewhere: CEC/ARC full-volume output, vendor middleware) is marked
 *   failed for the current output route, and the next one is tried; with none left the key
 *   passes to the system. Nothing was changed in that case, so the system's own handling does
 *   not double the step.
 * - A press that would change nothing (UP at max, DOWN at 0) is only consumed on a route where a
 *   backend already proved itself; otherwise it passes, because a full-volume output reads as
 *   "max" forever and consuming UP there would lock the user out.
 * - A write that changed something, but not what was asked, is shown and consumed (passing it
 *   would apply a second change), and the backend is marked failed so the next press passes.
 *
 * [routeKey] identifies the current output (fixed flag plus output device types); marks are
 * kept per route, so plugging in a soundbar or headphones starts fresh.
 */
class VolumeController(
    private val backends: List<VolumeBackend>,
    private val routeKey: () -> String,
) {
    private val failed = HashMap<String, MutableSet<String>>()
    private val proven = HashMap<String, MutableSet<String>>()

    fun reset() {
        failed.clear()
        proven.clear()
    }

    fun isProven(route: String, backend: String): Boolean = proven[route]?.contains(backend) == true
    fun isFailed(route: String, backend: String): Boolean = failed[route]?.contains(backend) == true

    /** The state to show for the first backend that is usable now (for the bar's initial frame). */
    fun current(): VolumeState? {
        val route = safeRoute()
        return backends.firstOrNull { !isFailed(route, it.id) && safe { it.canControl() } == true }?.let { safe { it.read() } }
    }

    fun apply(key: VolumeKey, repeatCount: Int): VolumeResult {
        val route = safeRoute()
        var reason = "no backend"
        for (b in backends) {
            if (isFailed(route, b.id)) {
                reason = "${b.id} failed on this output"
                continue
            }
            if (safe { b.canControl() } != true) {
                reason = "${b.id}: fixed volume"
                continue
            }
            val from = safe { b.read() }
            if (from == null || from.max <= 0) {
                reason = "${b.id}: no reading"
                continue
            }
            val to = VolumeMath.plan(from, key, repeatCount)
            if (to == from) {
                if (VolumeMath.atLimit(from, key) && !isProven(route, b.id)) {
                    return VolumeResult.Pass("${b.id}: at limit, not proven on this output")
                }
                // A repeat that skips, or the end of the range on a proven route: keep the bar up.
                return VolumeResult.Handled(from, b.id)
            }
            safe { b.write(from, to) }
            val after = safe { b.read() }
            if (after == to) {
                proven.getOrPut(route) { HashSet() }.add(b.id)
                return VolumeResult.Handled(after, b.id)
            }
            failed.getOrPut(route) { HashSet() }.add(b.id)
            proven[route]?.remove(b.id)
            if (after != null && after != from) {
                // Something moved: passing now would apply the key twice.
                return VolumeResult.Handled(after, b.id)
            }
            reason = "${b.id}: change not visible"
        }
        return VolumeResult.Pass(reason)
    }

    private fun safeRoute(): String = try {
        routeKey()
    } catch (e: Throwable) {
        "?"
    }

    private inline fun <T> safe(f: () -> T): T? = try {
        f()
    } catch (e: Throwable) {
        null
    }
}
