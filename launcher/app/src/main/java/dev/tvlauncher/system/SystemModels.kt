package dev.tvlauncher.system

import dev.tvlauncher.appsettings.PageSpec
import java.security.MessageDigest
import java.util.Locale

/** Ids and declared rows of every launcher-owned system page (RowGuard-checked, at most 7 primary rows). */
object SystemSpecs {
    const val NETWORK = "network"
    const val BLUETOOTH = "bluetooth"
    const val SYSTEM = "system"
    const val APPS = "apps"
    const val ACCESSIBILITY = "accessibility"
    const val PARENTAL = "parental"

    val WIFI = PageSpec(NETWORK,
        listOf("wifi.toggle", "wifi.current", "wifi.choose", "wifi.hidden", "net.ethernet", "net.check"),
        listOf("wifi.location", "wifi.managed"))
    val BT = PageSpec(BLUETOOTH, listOf("bt.toggle", "bt.open", "bt.paired"))
    val SYSTEM_HUB = PageSpec(SYSTEM,
        listOf("sys.about", "sys.datetime", "sys.language", "sys.volume", "sys.storage", "sys.power", "sys.tv"),
        listOf("sys.cec", "sys.reset", "sys.managed"))
    val ABOUT = PageSpec("sys.about",
        listOf("about.model", "about.android", "about.storage", "about.ram", "about.display", "about.launcher"))
    val DATETIME = PageSpec("sys.datetime", listOf("dt.now", "dt.zone", "dt.auto", "dt.format"))
    val LANGUAGE = PageSpec("sys.language", listOf("lang.current", "lang.list", "lang.keyboard", "lang.note"))
    val VOLUME = PageSpec("sys.volume", listOf("vol.level", "vol.mute", "vol.output"))
    val STORAGE = PageSpec("sys.storage", listOf("st.internal", "st.free", "st.usb"))
    val POWER = PageSpec("sys.power", listOf("pw.off", "pw.timer", "pw.on", "pw.screensaver", "pw.dream"))
    val TV = PageSpec("sys.tv", listOf("tv.live", "tv.guide", "tv.channels", "tv.inputs"))
    val CEC = PageSpec("sys.cec", listOf("cec.control", "cec.devices"))
    val DIAGNOSTICS = PageSpec("net.check",
        listOf("diag.link", "diag.internet", "diag.ip", "diag.gateway", "diag.dns", "diag.speed"))
    val APP_INFO = PageSpec("apps.info",
        listOf("app.open", "app.version", "app.size", "app.stop", "app.uninstall", "app.home"))
    val ACCESS = PageSpec(ACCESSIBILITY,
        listOf("a11y.text", "a11y.contrast", "a11y.motion", "a11y.captions", "a11y.reader", "a11y.helper"),
        listOf("a11y.bold", "a11y.sounds", "a11y.daltonizer", "a11y.inversion"))
    val PIN = PageSpec(PARENTAL, listOf("pin.state", "pin.set", "pin.remove"))
    val MANAGED = PageSpec("sys.managed",
        listOf("tv.picture", "tv.display", "tv.access", "tv.update", "tv.reset", "tv.all"))

    val ALL = listOf(WIFI, BT, SYSTEM_HUB, ABOUT, DATETIME, LANGUAGE, VOLUME, STORAGE, POWER, TV, CEC,
        DIAGNOSTICS, APP_INFO, ACCESS, PIN, MANAGED)
}

/**
 * "Managed by the TV" rows open the TV's own screen only when the user insists: the first OK arms
 * the row (it then says "Press OK again"), a second OK on the same row within [windowMs] opens it.
 */
class InsistGate(private val windowMs: Long = 4000L) {
    private var armed: String? = null
    private var at = 0L

    fun isArmed(key: String, now: Long) = armed == key && now - at <= windowMs

    /** True when this press should open the TV screen. */
    fun press(key: String, now: Long): Boolean {
        if (isArmed(key, now)) { armed = null; return true }
        armed = key; at = now
        return false
    }

    fun reset() { armed = null }
}

/** Parental PIN rules: exactly four digits, stored only as a salted SHA-256. */
object PinRules {
    const val LENGTH = 4

    fun valid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun matches(pin: String, salt: String, stored: String?): Boolean = stored != null && valid(pin) && hash(pin, salt) == stored

    /** What the PIN dialog says after a wrong entry; locks out for 30 s after five misses. */
    fun lockoutSeconds(misses: Int): Int = if (misses >= 5) 30 else 0
}

/** Accessibility helpers that need no Android types. */
object A11yModel {
    const val HELPER_COMPONENT = "dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"
    val TALKBACK_PACKAGES = listOf("com.google.android.marvin.talkback", "com.android.talkback")

    /** Launcher text size: label and font scale. */
    enum class TextSize(val label: String, val scale: Float) { S("Small", 0.9f), M("Default", 1f), L("Large", 1.15f), XL("Largest", 1.3f) }

    fun textSizeOf(name: String?): TextSize = TextSize.values().firstOrNull { it.name == name } ?: TextSize.M

    /** Settings.Secure ENABLED_ACCESSIBILITY_SERVICES with [component] added once, others kept in order. */
    fun enable(current: String?, component: String): String {
        val list = split(current)
        return if (list.any { it.equals(component, true) || short(it).equals(short(component), true) }) list.joinToString(":")
        else (list + component).joinToString(":")
    }

    fun disable(current: String?, component: String): String =
        split(current).filterNot { it.equals(component, true) || short(it).equals(short(component), true) }.joinToString(":")

    fun isEnabled(current: String?, component: String): Boolean =
        split(current).any { it.equals(component, true) || short(it).equals(short(component), true) }

    private fun split(s: String?) = s.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

    /** "pkg/.Cls" and "pkg/pkg.Cls" are the same component. */
    fun short(component: String): String {
        val slash = component.indexOf('/')
        if (slash < 0) return component
        val pkg = component.substring(0, slash)
        val cls = component.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else component
    }
}

/** Sleep timer choices (minutes; 0 = off). */
object SleepTimer {
    val CHOICES = listOf(0, 15, 30, 60, 90, 120)

    fun label(min: Int) = when {
        min <= 0 -> "Off"
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun next(current: Int, step: Int = 1): Int {
        val i = CHOICES.indexOf(current).coerceAtLeast(0)
        return CHOICES[(i + step + CHOICES.size) % CHOICES.size]
    }

    /** Minutes left (rounded up) until [endsAt]; 0 when off or passed. */
    fun minutesLeft(endsAt: Long, now: Long): Int = if (endsAt <= now) 0 else (((endsAt - now) + 59_999) / 60_000).toInt()
}

/** Sizes and short words for the system pages. */
object SystemText {
    fun bytes(b: Long): String {
        if (b < 0) return "Unknown"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble(); var u = 0
        while (v >= 1000 && u < units.size - 1) { v /= 1000; u++ }
        return if (u == 0) "$b B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[u])
    }

    fun usedOf(used: Long, total: Long): String =
        if (total <= 0) "Unknown" else "${bytes(used)} of ${bytes(total)} used"

    fun percent(used: Long, total: Long): Int = if (total <= 0) 0 else ((used * 100) / total).toInt().coerceIn(0, 100)

    fun linkSpeed(mbps: Int) = if (mbps > 0) "$mbps Mbps" else "Unknown"

    /** Volume as "7 of 15" style words for a stepper. */
    fun volume(level: Int, max: Int) = if (max <= 0) "$level" else "${(level * 100 + max / 2) / max}%"

    /** Internet check wording from NetworkCapabilities flags. */
    fun internet(hasNetwork: Boolean, validated: Boolean, captive: Boolean) = when {
        !hasNetwork -> "No network"
        captive -> "Sign-in needed"
        validated -> "Working"
        else -> "Connected, no internet"
    }
}
