package dev.tvlauncher.store.core

/**
 * Classifies requested permissions for the confirmation screen. DANGEROUS = platform runtime permissions that
 * touch personal data or sensors; SPECIAL = high-impact special-access permissions (overlay, accessibility,
 * installing apps, device admin, ...). Everything else, including app-defined permissions, is NORMAL.
 */
object PermissionRisk {

    enum class Level { SPECIAL, DANGEROUS, NORMAL }

    private const val P = "android.permission."

    private val dangerous: Map<String, String> = mapOf(
        "CAMERA" to "Camera",
        "RECORD_AUDIO" to "Microphone",
        "ACCESS_FINE_LOCATION" to "Precise location",
        "ACCESS_COARSE_LOCATION" to "Approximate location",
        "ACCESS_BACKGROUND_LOCATION" to "Location in the background",
        "ACCESS_MEDIA_LOCATION" to "Location in your media",
        "READ_CONTACTS" to "Read contacts",
        "WRITE_CONTACTS" to "Change contacts",
        "GET_ACCOUNTS" to "Find accounts on this device",
        "READ_CALENDAR" to "Read calendar",
        "WRITE_CALENDAR" to "Change calendar",
        "READ_SMS" to "Read text messages",
        "SEND_SMS" to "Send text messages",
        "RECEIVE_SMS" to "Receive text messages",
        "RECEIVE_MMS" to "Receive MMS",
        "RECEIVE_WAP_PUSH" to "Receive WAP push",
        "READ_CALL_LOG" to "Read call log",
        "WRITE_CALL_LOG" to "Change call log",
        "PROCESS_OUTGOING_CALLS" to "Reroute outgoing calls",
        "CALL_PHONE" to "Make phone calls",
        "READ_PHONE_STATE" to "Phone status and identity",
        "READ_PHONE_NUMBERS" to "Read phone numbers",
        "ANSWER_PHONE_CALLS" to "Answer phone calls",
        "ADD_VOICEMAIL" to "Add voicemail",
        "USE_SIP" to "Internet calls (SIP)",
        "ACCEPT_HANDOVER" to "Continue calls from another app",
        "BODY_SENSORS" to "Body sensors",
        "BODY_SENSORS_BACKGROUND" to "Body sensors in the background",
        "ACTIVITY_RECOGNITION" to "Physical activity",
        "READ_EXTERNAL_STORAGE" to "Read your files",
        "WRITE_EXTERNAL_STORAGE" to "Change your files",
        "READ_MEDIA_IMAGES" to "Photos",
        "READ_MEDIA_VIDEO" to "Videos",
        "READ_MEDIA_AUDIO" to "Music and audio",
        "READ_MEDIA_VISUAL_USER_SELECTED" to "Selected photos and videos",
        "NEARBY_WIFI_DEVICES" to "Nearby Wi-Fi devices",
        "BLUETOOTH_SCAN" to "Find Bluetooth devices",
        "BLUETOOTH_CONNECT" to "Connect to Bluetooth devices",
        "BLUETOOTH_ADVERTISE" to "Advertise to Bluetooth devices",
        "UWB_RANGING" to "Ultra-wideband ranging",
        "POST_NOTIFICATIONS" to "Show notifications",
    )

    private val special: Map<String, String> = mapOf(
        "SYSTEM_ALERT_WINDOW" to "Draw over other apps",
        "REQUEST_INSTALL_PACKAGES" to "Install other apps",
        "INSTALL_PACKAGES" to "Install apps silently (system only)",
        "DELETE_PACKAGES" to "Remove apps silently (system only)",
        "REQUEST_DELETE_PACKAGES" to "Ask to remove apps",
        "BIND_ACCESSIBILITY_SERVICE" to "Accessibility service (can read the screen)",
        "BIND_DEVICE_ADMIN" to "Device administrator",
        "BIND_NOTIFICATION_LISTENER_SERVICE" to "Read all notifications",
        "BIND_VPN_SERVICE" to "Run a VPN",
        "MANAGE_EXTERNAL_STORAGE" to "Access all files",
        "PACKAGE_USAGE_STATS" to "See which apps you use",
        "WRITE_SETTINGS" to "Change system settings",
        "WRITE_SECURE_SETTINGS" to "Change secure settings (system only)",
        "QUERY_ALL_PACKAGES" to "See all installed apps",
        "READ_LOGS" to "Read system logs",
        "CHANGE_CONFIGURATION" to "Change system configuration",
        "MANAGE_ACCOUNTS" to "Manage accounts",
        "USE_FULL_SCREEN_INTENT" to "Full-screen alerts",
    )

    fun classify(name: String?): Level {
        if (name == null || !name.startsWith(P)) return Level.NORMAL
        val short = name.substring(P.length)
        return when {
            short in special -> Level.SPECIAL
            short in dangerous -> Level.DANGEROUS
            else -> Level.NORMAL
        }
    }

    fun isSensitive(name: String?): Boolean = classify(name) != Level.NORMAL

    /** Deduplicated, riskiest first (SPECIAL, DANGEROUS, NORMAL), stable within a level. */
    fun sort(names: List<String>): List<String> =
        names.distinct().sortedBy { classify(it).ordinal }

    /** A short human label; falls back to the last segment of the permission name. */
    fun label(name: String?): String {
        if (name.isNullOrBlank()) return "Unnamed permission"
        if (name.startsWith(P)) {
            val short = name.substring(P.length)
            special[short]?.let { return it }
            dangerous[short]?.let { return it }
        }
        return name.substringAfterLast('.').ifEmpty { name }
    }
}
