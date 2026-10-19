package dev.tvlauncher.browser.net

/** Disk-cache eviction: expired entries first, then least-recently-used until under budget. */
object CachePolicy {
    data class Entry(val name: String, val bytes: Long, val lastUsed: Long)

    fun evict(entries: List<Entry>, maxBytes: Long, ttlMs: Long, now: Long): List<String> {
        val out = ArrayList<String>()
        val live = ArrayList<Entry>()
        for (e in entries) if (now - e.lastUsed > ttlMs) out.add(e.name) else live.add(e)
        var total = live.sumOf { it.bytes }
        for (e in live.sortedBy { it.lastUsed }) {
            if (total <= maxBytes) break
            out.add(e.name)
            total -= e.bytes
        }
        return out
    }
}
