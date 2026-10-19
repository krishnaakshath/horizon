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

/**
 * [anonymous]: a Bluetooth device with no class and no name (typical sleepy BLE remote).
 * [vidPid]: "vendor:product" in hex when the platform exposes it (input devices, USB), else null.
 */
data class Candidate(
    val source: DeviceSource,
    val key: String,
    val name: String?,
    val kind: DeviceKind,
    val anonymous: Boolean = false,
    val vidPid: String? = null
)

/**
 * Decides whether a device deserves a card, so the TV's own remote and flapping companions do not
 * spam the user. Rules are scoped by kind:
 *
 * - NON-HID kinds (USB storage, unknown USB, consoles, streamers, soundbars, phones, network,
 *   HDMI/AV inputs) are ALWAYS announced, connect and disconnect, whatever their name says.
 *   A flash drive called "TCL 32GB" or "Voice Recorder" gets its card.
 * - HID-like kinds (keyboard, mouse, gamepad, BT keyboard/mouse/gamepad, BT_OTHER,
 *   USB_HID_OTHER) get the name rules: whole-word "remote", "rcu", "voice", "assistant", "rc",
 *   "rc802v"-style, "ir", "mi bt" are silent ("Voicemod" is not); a name containing "keyboard"
 *   is exempt from these words, so "Remote Keyboard" is announced unless it was seeded. Soft
 *   names (tv, tcl, hisense, xiaomi, mi box) are silent for non-pad HID kinds. A classless,
 *   nameless Bluetooth device is silent. Non-pad HID devices present or bonded at start are
 *   silent on connect and disconnect, and so is any non-pad HID device whose vendor:product id
 *   matches a seeded HID device (the TV's own remote re-enumerating under a new name).
 * - FLAPPY kinds (pads and Bluetooth headphones/speakers) sleep and wake constantly: their
 *   disconnect is always silent (status dot only), and a connect is announced only when the key
 *   was not seen in the last [QUIET_MS] (30 min). Seeded devices count as seen at start.
 */
class RemoteFilter(private val clock: () -> Long = { 0L }) {
    private val seeded = HashSet<String>()
    private val seededVidPid = HashSet<String>()
    private val lastEvent = HashMap<String, Long>()

    fun seed(key: String, vidPid: String? = null, kind: DeviceKind? = null) {
        seeded.add(key)
        lastEvent[key] = clock()
        if (vidPid != null && kind != null && isHid(kind) && !isPad(kind)) seededVidPid.add(vidPid)
    }

    fun isSeeded(key: String) = key in seeded
    fun reset() { seeded.clear(); seededVidPid.clear(); lastEvent.clear() }

    fun shouldAnnounce(c: Candidate, connected: Boolean = true): Boolean {
        val now = clock()
        val prev = lastEvent[c.key]
        lastEvent[c.key] = now
        val hid = isHid(c.kind)
        val pad = isPad(c.kind)
        val flappy = pad || c.kind == DeviceKind.BT_HEADPHONES || c.kind == DeviceKind.BT_SPEAKER
        if (!hid && !flappy) return true
        if (hid) {
            if (isExplicitRemoteName(c.name)) return false
            if (!pad && isSoftRemoteName(c.name)) return false
            if (c.anonymous) return false
            if (!pad) {
                if (c.key in seeded) return false
                if (c.vidPid != null && c.vidPid in seededVidPid) return false
            }
        }
        if (flappy) {
            if (!connected) return false
            return prev == null || now - prev >= QUIET_MS
        }
        return true
    }

    companion object {
        const val QUIET_MS = 30L * 60_000L
        private val HID = setOf(DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.GAMEPAD, DeviceKind.BT_KEYBOARD_MOUSE,
            DeviceKind.BT_GAMEPAD, DeviceKind.BT_OTHER, DeviceKind.USB_HID_OTHER)
        private val explicitWord = Regex("""\b(remote|rcu|voice|assistant|rc(\d\w*)?|ir|mi ?bt)\b""")
        private val softWord = Regex("""\b(tv|tcl|hisense|xiaomi|mi box)\b""")
        private val controllerWord = Regex("""(controller|gamepad|joystick|dualsense|dualshock)""")

        fun isHid(kind: DeviceKind) = kind in HID
        private fun isPad(kind: DeviceKind) = kind == DeviceKind.GAMEPAD || kind == DeviceKind.BT_GAMEPAD

        private fun norm(name: String?) = (name ?: "").trim().lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')

        fun isExplicitRemoteName(name: String?): Boolean {
            val n = norm(name)
            if (n.isEmpty() || n.contains("keyboard")) return false
            return explicitWord.containsMatchIn(n)
        }

        fun isSoftRemoteName(name: String?): Boolean {
            val n = norm(name)
            if (n.isEmpty() || n.contains("keyboard") || controllerWord.containsMatchIn(n)) return false
            return softWord.containsMatchIn(n)
        }
    }
}
