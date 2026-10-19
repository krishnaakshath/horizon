package dev.tvlauncher.volume.core

/**
 * Decides, per key event, whether the helper takes a volume key (pure, JVM-tested).
 *
 * - Off ([enabled] false), or a key the [controller] cannot handle: the key passes untouched.
 * - A key whose first DOWN was taken keeps its repeats and its UP (so apps and the system never
 *   see half a key press). A key whose first DOWN passed passes completely.
 * - If a repeat can no longer be handled (the output changed mid-hold), that repeat passes, so
 *   the system keeps changing the volume while the key is held.
 *
 * [onShow] gets the state to draw after each handled DOWN; [onRelease] runs on the UP of a taken
 * key (the bar's hide countdown restarts there); [onSeen] counts keys for the helper's status line.
 */
class VolumeKeyRouter(
    private val enabled: () -> Boolean,
    private val controller: VolumeController,
    private val onShow: (VolumeState) -> Unit,
    private val onRelease: () -> Unit = {},
    private val onSeen: (VolumeKey, VolumeResult?) -> Unit = { _, _ -> },
) {
    private val taken = HashSet<VolumeKey>()

    fun reset() = taken.clear()

    /** True to consume the event. Never throws. */
    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, canceled: Boolean): Boolean = try {
        handle(keyCode, down, repeatCount, canceled)
    } catch (e: Throwable) {
        taken.clear()
        false
    }

    private fun handle(keyCode: Int, down: Boolean, repeatCount: Int, canceled: Boolean): Boolean {
        val key = VolumeKey.of(keyCode) ?: return false
        if (!down) {
            val mine = taken.remove(key)
            if (mine && !canceled) onRelease()
            return mine
        }
        if (repeatCount <= 0) {
            taken.remove(key)
            if (!enabled()) {
                onSeen(key, null)
                return false
            }
        } else if (key !in taken) {
            return false
        }
        val r = controller.apply(key, repeatCount)
        if (repeatCount <= 0) onSeen(key, r)
        return when (r) {
            is VolumeResult.Handled -> {
                taken.add(key)
                onShow(r.state)
                true
            }
            is VolumeResult.Pass -> false
        }
    }
}

/** The bar's auto-hide countdown (pure): every [poke] restarts it. */
class HideTimer(val durationMs: Long = DEFAULT_MS) {
    var deadline: Long = -1L
        private set

    val armed: Boolean get() = deadline >= 0

    fun poke(now: Long) {
        deadline = now + durationMs
    }

    fun cancel() {
        deadline = -1L
    }

    fun due(now: Long): Boolean = armed && now >= deadline

    /** Milliseconds until [due]; 0 when due or not armed. */
    fun remaining(now: Long): Long = if (!armed) 0L else maxOf(0L, deadline - now)

    companion object {
        const val DEFAULT_MS = 1500L
    }
}
