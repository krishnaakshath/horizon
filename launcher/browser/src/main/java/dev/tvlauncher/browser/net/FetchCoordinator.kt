package dev.tvlauncher.browser.net

import java.util.concurrent.RejectedExecutionException

/**
 * De-duplicates background fetches per key, caches results, remembers failures for [failTtlMs]
 * (bounded), and discards in-flight results after [clear] (generation counter). Callbacks run
 * via [post] (the main thread in production). After shutdown, loads deliver null.
 */
class FetchCoordinator<V>(
    private val execute: (Runnable) -> Unit,
    private val post: (Runnable) -> Unit,
    private val clock: () -> Long,
    private val cache: Cache<V>,
    private val failTtlMs: Long
) {
    interface Cache<V> { fun get(key: String): V?; fun put(key: String, value: V); fun clear() }

    private val waiting = HashMap<String, MutableList<(V?) -> Unit>>()
    private val failedAt = LinkedHashMap<String, Long>()
    private var generation = 0

    fun peek(key: String): V? = cache.get(key)

    fun load(key: String, cb: (V?) -> Unit, work: () -> V?) {
        cache.get(key)?.let { return cb(it) }
        failedAt[key]?.let { if (clock() - it < failTtlMs) return cb(null) else failedAt.remove(key) }
        waiting[key]?.let { it.add(cb); return }
        waiting[key] = mutableListOf(cb)
        val gen = generation
        try {
            execute(Runnable {
                val v = try { work() } catch (t: Throwable) { null }
                post(Runnable { finish(key, gen, v) })
            })
        } catch (e: RejectedExecutionException) {
            waiting.remove(key)?.forEach { it(null) }
        }
    }

    private fun finish(key: String, gen: Int, v: V?) {
        val cbs = waiting.remove(key) ?: if (gen != generation) mutableListOf() else return
        if (gen != generation) { cbs.forEach { it(null) }; return } // cleared meanwhile: discard
        if (v != null) cache.put(key, v) else markFailed(key)
        cbs.forEach { it(v) }
    }

    private fun markFailed(key: String) {
        failedAt[key] = clock()
        while (failedAt.size > MAX_FAILED) failedAt.remove(failedAt.keys.first())
    }

    fun clear() {
        generation++
        cache.clear()
        failedAt.clear()
        val pending = waiting.values.flatten()
        waiting.clear()
        pending.forEach { cb -> post(Runnable { cb(null) }) }
    }

    fun failedCount(): Int = failedAt.size

    companion object { const val MAX_FAILED = 200 }
}
