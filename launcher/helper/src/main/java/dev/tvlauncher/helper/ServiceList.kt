package dev.tvlauncher.helper

/**
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES as a colon-separated list. Pure: adding the
 * Helper keeps every other service exactly as it was and never duplicates an entry.
 */
object ServiceList {
    const val HELPER = "dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"

    private fun norm(e: String): String {
        val t = e.trim()
        val slash = t.indexOf('/')
        if (slash <= 0) return t
        val pkg = t.substring(0, slash)
        val cls = t.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else t
    }

    fun entries(flat: String?): List<String> =
        flat.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }

    fun contains(flat: String?, component: String = HELPER): Boolean = entries(flat).any { norm(it) == norm(component) }

    fun add(flat: String?, component: String = HELPER): String =
        (if (contains(flat, component)) entries(flat) else entries(flat) + component).joinToString(":")
}

/** What the Helper screen offers to switch the service on. Pure. */
enum class EnableRoute { ALREADY_ON, OPEN_SCREEN, SELF_ENABLE, FINISH_SETUP, NONE }

object EnableRoutes {
    fun pick(enabled: Boolean, screenResolves: Boolean, canWriteSecure: Boolean, setupResolves: Boolean): EnableRoute = when {
        enabled -> EnableRoute.ALREADY_ON
        canWriteSecure -> EnableRoute.SELF_ENABLE
        screenResolves -> EnableRoute.OPEN_SCREEN
        setupResolves -> EnableRoute.FINISH_SETUP
        else -> EnableRoute.NONE
    }
}
