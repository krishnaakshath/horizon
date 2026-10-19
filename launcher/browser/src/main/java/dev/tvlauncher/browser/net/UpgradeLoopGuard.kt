package dev.tvlauncher.browser.net

/**
 * Detects http -> https -> http redirect loops: the same http URL being upgraded twice without a
 * successful load in between means the site insists on cleartext.
 */
class UpgradeLoopGuard {
    private var last: String? = null

    fun isLoop(httpUrl: String): Boolean {
        val key = SslErrorPolicy.normalize(httpUrl) ?: httpUrl.lowercase()
        if (key == last) return true
        last = key
        return false
    }

    fun reset() { last = null }
}
