package dev.tvlauncher.browser.net

/**
 * Detects http -> https -> http redirect loops: the same http URL needing an upgrade twice is a
 * loop, unless the upgraded page has meanwhile finished and stayed for >= [stableMs], or the user
 * started a new navigation.
 */
class UpgradeLoopGuard(private val stableMs: Long = 2000) {
    private var last: String? = null
    private var finishedKey: String? = null
    private var finishedAt = 0L

    fun isLoop(httpUrl: String, now: Long): Boolean {
        val key = key(httpUrl)
        if (finishedKey != null && finishedKey == key && now - finishedAt >= stableMs) reset()
        if (key == last) return true
        last = key
        return false
    }

    /** A page finished loading; if it is the upgraded target, remember when. */
    fun onFinished(url: String, now: Long) {
        val k = key(url)
        if (k == last) { finishedKey = k; finishedAt = now }
    }

    fun onUserNavigation() = reset()

    private fun reset() { last = null; finishedKey = null }

    /** Scheme-insensitive key, so http://x and https://x compare equal. */
    private fun key(url: String): String {
        val n = SslErrorPolicy.normalize(url) ?: url.lowercase()
        return n.substringAfter("://")
    }
}
