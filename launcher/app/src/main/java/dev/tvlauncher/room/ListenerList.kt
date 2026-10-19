package dev.tvlauncher.room

import java.lang.ref.WeakReference

/**
 * Listeners held through (by default weak) references: explicit [add]/[remove] still work, and
 * a listener whose owner was collected without removing itself is pruned on the next [forEach].
 * Removing during [forEach] is safe (the slot is cleared and compacted afterwards). Adding during
 * [forEach] takes effect on the next dispatch. No allocation per [forEach]. Single-threaded
 * (RoomTheme uses it on the main thread). Pure: [refFactory] is the test seam for the GC.
 *
 * Callers must keep a strong reference to their listener (e.g. a field), or it may vanish.
 */
class ListenerList<T : Any>(
    private val refFactory: (T) -> Ref<T> = { WeakRef(it) }
) {
    interface Ref<T> {
        fun get(): T?
    }

    private class WeakRef<T>(v: T) : Ref<T> {
        private val w = WeakReference(v)
        override fun get(): T? = w.get()
    }

    private val refs = ArrayList<Ref<T>?>()
    private var dispatching = 0

    /** Live entries (cleared references count until the next prune). */
    val size: Int get() = refs.count { it?.get() != null }

    fun add(l: T) {
        for (r in refs) if (r?.get() === l) return
        refs.add(refFactory(l))
    }

    fun remove(l: T) {
        for (i in refs.indices) {
            if (refs[i]?.get() === l) {
                if (dispatching > 0) refs[i] = null else refs.removeAt(i)
                return
            }
        }
    }

    fun forEach(action: (T) -> Unit) {
        val n = refs.size
        dispatching++
        try {
            var i = 0
            while (i < n && i < refs.size) {
                val l = refs[i]?.get()
                if (l == null) refs[i] = null else action(l)
                i++
            }
        } finally {
            dispatching--
            if (dispatching == 0) prune()
        }
    }

    private fun prune() {
        var i = refs.size - 1
        while (i >= 0) {
            if (refs[i]?.get() == null) refs.removeAt(i)
            i--
        }
    }
}
