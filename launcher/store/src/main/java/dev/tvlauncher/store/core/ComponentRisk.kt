package dev.tvlauncher.store.core

/**
 * Capabilities that never show up in requestedPermissions: an app becomes an accessibility service, device
 * admin, notification listener, VPN, keyboard, ... by DECLARING a service/receiver protected by the matching
 * BIND_* permission. PackageManager gives us each component's `permission` attribute; this maps them to plain
 * language and a severity so the review screen can list them above ordinary permissions.
 */
object ComponentRisk {

    enum class Severity { HIGH, MEDIUM, INFO }
    enum class Kind { SERVICE, RECEIVER, ACTIVITY, PROVIDER }

    data class RiskEntry(val key: String, val severity: Severity, val text: String)

    private const val P = "android.permission."

    private val table: Map<String, Pair<Severity, String>> = mapOf(
        "BIND_ACCESSIBILITY_SERVICE" to (Severity.HIGH to "Can read the screen and act for you (accessibility service)"),
        "BIND_DEVICE_ADMIN" to (Severity.HIGH to "Can lock/wipe the device (device administrator)"),
        "BIND_NOTIFICATION_LISTENER_SERVICE" to (Severity.HIGH to "Can read your notifications"),
        "BIND_VPN_SERVICE" to (Severity.HIGH to "Can route all network traffic (VPN)"),
        "BIND_INPUT_METHOD" to (Severity.HIGH to "Can read everything you type (keyboard)"),
        "BIND_AUTOFILL_SERVICE" to (Severity.MEDIUM to "Can see and fill in forms and passwords (autofill service)"),
        "BIND_VOICE_INTERACTION" to (Severity.MEDIUM to "Can act as the voice assistant"),
        "BIND_SCREENING_SERVICE" to (Severity.MEDIUM to "Can screen phone calls"),
        "BIND_CALL_REDIRECTION_SERVICE" to (Severity.MEDIUM to "Can redirect phone calls"),
        "BIND_CARRIER_SERVICES" to (Severity.INFO to "Provides mobile-carrier services"),
        "BIND_WALLPAPER" to (Severity.INFO to "Provides a live wallpaper"),
        "BIND_QUICK_SETTINGS_TILE" to (Severity.INFO to "Adds a quick-settings tile"),
        "BIND_DREAM_SERVICE" to (Severity.INFO to "Provides a screensaver"),
    )

    /** null when the component's permission isn't one of the capability-granting BIND_* permissions. */
    fun classify(kind: Kind, permission: String?): RiskEntry? {
        if (permission == null || !permission.startsWith(P)) return null
        if (kind == Kind.PROVIDER) return null
        val short = permission.substring(P.length)
        val (sev, text) = table[short] ?: return null
        return RiskEntry(short, sev, text)
    }

    /** Deduplicated (one entry per capability), most severe first. */
    fun collect(components: List<Pair<Kind, String?>>): List<RiskEntry> =
        components.mapNotNull { (k, p) -> classify(k, p) }
            .groupBy { it.key }
            .map { (_, v) -> v.minBy { it.severity.ordinal } }
            .sortedWith(compareBy<RiskEntry> { it.severity.ordinal }.thenBy { it.key })
}
