package dev.tvlauncher.setup

/**
 * A colon-separated component list as stored in secure settings
 * (enabled_notification_listeners, enabled_accessibility_services). Pure.
 */
object SecureList {
    /** "pkg/.Cls" and "pkg/pkg.Cls" are the same component; returns the long form. */
    fun normalize(entry: String): String {
        val t = entry.trim()
        val slash = t.indexOf('/')
        if (slash <= 0 || slash == t.length - 1) return t
        val pkg = t.substring(0, slash)
        val cls = t.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else t
    }

    fun entries(flat: String?): List<String> =
        flat.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }

    fun contains(flat: String?, component: String): Boolean {
        val c = normalize(component)
        return entries(flat).any { normalize(it) == c }
    }

    /** [flat] with [component] appended once; every existing entry is kept as it was. */
    fun add(flat: String?, component: String): String {
        val list = entries(flat)
        return if (contains(flat, component)) list.joinToString(":") else (list + component).joinToString(":")
    }
}

/** Everything the automatic part of setup needs to decide (read by [SetupProber]). */
data class AutoInputs(
    val canWriteSecure: Boolean,
    val listenerGranted: Boolean,
    val listenerFlat: String?,
    val helperInstalled: Boolean,
    val a11yFlat: String?,
    val a11yEnabled: String?,
    /** Raw secure tv_user_setup_complete; null when this TV does not have the setting. */
    val tvUserSetupComplete: String?,
    /** Now Playing was already switched on once by setup (later the user's choice wins). */
    val nowPlayingAutoDone: Boolean,
    /** Room lighting has saved settings (the user or an earlier setup chose already). */
    val roomConfigured: Boolean,
    val zoneId: String
)

sealed class AutoOp {
    data class PutSecure(val key: String, val value: String) : AutoOp()
    /** Switch the Now Playing card on (once). */
    object EnableNowPlaying : AutoOp()
    /** Room lighting AUTO, with a place guessed from the time zone (null = time of day only). */
    data class ConfigureRoom(val latitude: Double?, val longitude: Double?) : AutoOp()
}

/**
 * The silent part of "everything gets configured": what setup changes by itself, on every
 * start, without asking. Secure settings are written only when the setup helper granted
 * WRITE_SECURE_SETTINGS; nothing already set is ever removed. Idempotent: once applied, the
 * plan is empty.
 */
object SetupAuto {
    const val LISTENERS = "enabled_notification_listeners"
    const val A11Y_SERVICES = "enabled_accessibility_services"
    const val A11Y_ENABLED = "accessibility_enabled"
    const val TV_SETUP_COMPLETE = "tv_user_setup_complete"

    const val LISTENER_COMPONENT = "dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService"
    const val HELPER_COMPONENT = "dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"

    fun plan(i: AutoInputs): List<AutoOp> {
        val ops = ArrayList<AutoOp>()
        val writeListener = i.canWriteSecure && !i.listenerGranted && !SecureList.contains(i.listenerFlat, LISTENER_COMPONENT)
        if (writeListener) ops += AutoOp.PutSecure(LISTENERS, SecureList.add(i.listenerFlat, LISTENER_COMPONENT))
        if (i.canWriteSecure && i.helperInstalled) {
            if (!SecureList.contains(i.a11yFlat, HELPER_COMPONENT)) {
                ops += AutoOp.PutSecure(A11Y_SERVICES, SecureList.add(i.a11yFlat, HELPER_COMPONENT))
            }
            if (i.a11yEnabled?.trim() != "1") ops += AutoOp.PutSecure(A11Y_ENABLED, "1")
        }
        // Some TVs leave this at 0, and then the Home key does nothing; only a present 0 is fixed.
        if (i.canWriteSecure && i.tvUserSetupComplete?.trim() == "0") ops += AutoOp.PutSecure(TV_SETUP_COMPLETE, "1")
        if (!i.nowPlayingAutoDone && (i.listenerGranted || writeListener)) ops += AutoOp.EnableNowPlaying
        if (!i.roomConfigured) {
            val g = TimeZoneGuess.place(i.zoneId)
            ops += AutoOp.ConfigureRoom(g?.first, g?.second)
        }
        return ops
    }
}

/** Gate for the automatic first-run start of the setup screen. */
object SetupGate {
    /** Shown at most this many times without being finished (a crash there never loops). */
    const val MAX_AUTO_STARTS = 2

    fun shouldAutoStart(completed: Boolean, autoStarts: Int, spotlightRequested: Boolean): Boolean =
        !completed && autoStarts < MAX_AUTO_STARTS && !spotlightRequested
}
