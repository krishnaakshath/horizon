package dev.tvlauncher.devices

import java.util.Locale

enum class DeviceSource {
    TV_INPUT, USB, INPUT, BLUETOOTH, NETWORK;

    companion object {
        fun of(deviceId: String): DeviceSource = when {
            deviceId.startsWith("tv:") -> TV_INPUT
            deviceId.startsWith("usb:") -> USB
            deviceId.startsWith("input:") -> INPUT
            deviceId.startsWith("bt:") -> BLUETOOTH
            else -> NETWORK
        }
    }
}

/** [anonymous]: a Bluetooth device with no class and no name (typical sleepy HID remote). */
data class Candidate(
    val source: DeviceSource,
    val key: String,
    val name: String?,
    val kind: DeviceKind,
    val anonymous: Boolean = false
)

/**
 * Decides whether a USB / input / Bluetooth device deserves a card, so the TV's own remote and
 * already-known companions do not spam the user. Heuristics, in order:
 *
 * 1. Audio devices (headphones, speakers) are never treated as remotes.
 * 2. Explicit remote names (substring "remote", "rcu", "voice", "assistant"; whole words "rc",
 *    "rc802v"-style, "ir", "mi bt") are silent, unless the name also says "keyboard".
 * 3. Soft remote names ("tv", "tcl", "hisense", "xiaomi", "mi box") are silent for HID-ish kinds
 *    unless the name looks like a game controller.
 * 4. A nameless, classless Bluetooth device is silent (BLE remote waking up).
 * 5. Devices present or bonded when the monitor started ("seeded") are silent on reconnect for
 *    input/USB sources and for Bluetooth keyboards/mice/unknown HID. Seeded Bluetooth audio
 *    devices and pads still announce on reconnect (the user switched them on).
 * Everything newly bonded or first seen after start is announced.
 */
class RemoteFilter {
    private val seeded = HashSet<String>()

    fun seed(key: String) { seeded.add(key) }
    fun isSeeded(key: String) = key in seeded
    fun reset() = seeded.clear()

    fun shouldAnnounce(c: Candidate): Boolean {
        val audio = c.kind == DeviceKind.BT_HEADPHONES || c.kind == DeviceKind.BT_SPEAKER || c.kind == DeviceKind.SOUNDBAR_AUDIO
        val pad = c.kind == DeviceKind.GAMEPAD || c.kind == DeviceKind.BT_GAMEPAD
        if (!audio) {
            if (isExplicitRemoteName(c.name)) return false
            if (!pad && isSoftRemoteName(c.name)) return false
            if (c.anonymous) return false
        }
        if (c.key in seeded) {
            return when (c.source) {
                DeviceSource.BLUETOOTH -> audio || pad
                DeviceSource.INPUT, DeviceSource.USB -> false
                else -> true
            }
        }
        return true
    }

    companion object {
        private val explicitWord = Regex("""\b(rc(\d\w*)?|ir|mi ?bt)\b""", RegexOption.IGNORE_CASE)
        private val softWord = Regex("""\b(tv|tcl|hisense|xiaomi|mi box)\b""", RegexOption.IGNORE_CASE)
        private val controllerWord = Regex("""(controller|gamepad|joystick|dualsense|dualshock)""", RegexOption.IGNORE_CASE)

        fun isExplicitRemoteName(name: String?): Boolean {
            val n = (name ?: "").trim().lowercase(Locale.ROOT)
            if (n.isEmpty() || n.contains("keyboard")) return false
            return n.contains("remote") || n.contains("rcu") || n.contains("voice") ||
                n.contains("assistant") || explicitWord.containsMatchIn(n)
        }

        fun isSoftRemoteName(name: String?): Boolean {
            val n = (name ?: "").trim().lowercase(Locale.ROOT)
            if (n.isEmpty() || n.contains("keyboard") || controllerWord.containsMatchIn(n)) return false
            return softWord.containsMatchIn(n)
        }
    }
}
