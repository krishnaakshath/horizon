package dev.tvlauncher.ambient

/**
 * Defers releasing (e.g. Bitmap.recycle) until at least one full frame has passed after the
 * item stopped being drawn: an item deferred before frame N is released at frame N+1, never
 * during the frame that may still render it. Not thread-safe: use from the main thread.
 */
class RecycleGate<T : Any>(private val release: (T) -> Unit) {
    private var pending = ArrayList<T>()
    private var ready = ArrayList<T>()

    val pendingCount: Int get() = pending.size + ready.size

    fun defer(item: T) {
        if (pending.any { it === item } || ready.any { it === item }) return
        pending.add(item)
    }

    /** The item is in use again (shown anew): do not release it. */
    fun cancel(item: T) {
        pending.removeAll { it === item }
        ready.removeAll { it === item }
    }

    /** Call once per frame tick. */
    fun frame() {
        val out = ready
        ready = pending
        pending = ArrayList()
        out.forEach(release)
    }

    fun flush() {
        val out = ArrayList(ready).apply { addAll(pending) }
        ready.clear(); pending.clear()
        out.forEach(release)
    }
}
