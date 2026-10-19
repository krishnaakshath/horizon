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
 * spam the user. The TV's built-in devices ([BuiltInDevices.isTvInternal]) are silent whatever
 * their kind. Other rules are scoped by kind:
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
 *
 * Intended behaviours that can look like bugs:
 * - Already-paired headphones/speakers or pads switched on within 30 minutes of the launcher
 *   starting are SILENT (status dot only); after 30 minutes unseen they announce again.
 * - A second unit of a seeded keyboard/mouse MODEL (same vendor:product) is silent too.
 * - Two different devices with tokenless generic names (e.g. "Wireless Controller") and no
 *   vendor:product id are not merged by the dedupe, so they produce two cards.
 * - A zero vendor or product id never matches anything.
 * The memory maps are capped at [MAX_TRACKED] entries (oldest stamp evicted).
 */
class RemoteFilter(private val clock: () -> Long = { 0L }) {
    private val seeded = HashSet<String>()
    private val seededVidPid = HashSet<String>()
    private val lastEvent = HashMap<String, Long>()

    /** Number of stamped keys (tests / diagnostics). */
    internal val trackedCount: Int get() = lastEvent.size

    private fun stamp(key: String, at: Long) {
        lastEvent[key] = at
        while (lastEvent.size > MAX_TRACKED) {
            val oldest = lastEvent.entries.minByOrNull { it.value }?.key ?: break
            lastEvent.remove(oldest)
        }
    }

    fun seed(key: String, vidPid: String? = null, kind: DeviceKind? = null) {
        seeded.add(key)
        if (kind == null || isFlappy(kind)) stamp(key, clock())
        val vp = validVidPid(vidPid)
        if (vp != null && kind != null && isHid(kind) && !isPad(kind) && seededVidPid.size < MAX_TRACKED) seededVidPid.add(vp)
    }

    fun isSeeded(key: String) = key in seeded
    fun reset() { seeded.clear(); seededVidPid.clear(); lastEvent.clear() }

    fun shouldAnnounce(c: Candidate, connected: Boolean = true): Boolean {
        if (BuiltInDevices.isTvInternal(c.name)) return false // the TV's own receivers, any kind
        val now = clock()
        val hid = isHid(c.kind)
        val pad = isPad(c.kind)
        val flappy = isFlappy(c.kind)
        val prev = if (flappy) lastEvent[c.key] else null
        if (flappy) stamp(c.key, now)
        if (!hid && !flappy) return true
        if (hid) {
            if (isExplicitRemoteName(c.name)) return false
            if (!pad && isSoftRemoteName(c.name)) return false
            if (c.anonymous) return false
            if (!pad) {
                if (c.key in seeded) return false
                val vp = validVidPid(c.vidPid)
                if (vp != null && vp in seededVidPid) return false
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
        const val MAX_TRACKED = 256

        /** "vvvv:pppp" hex, or null when absent, malformed or either half is zero. */
        fun validVidPid(s: String?): String? {
            val parts = (s ?: return null).split(':')
            if (parts.size != 2) return null
            val v = parts[0].toIntOrNull(16) ?: return null
            val p = parts[1].toIntOrNull(16) ?: return null
            return if (v == 0 || p == 0) null else s
        }

        fun vidPidOf(vendorId: Int, productId: Int): String? =
            if (vendorId == 0 || productId == 0) null else "%04x:%04x".format(vendorId, productId)

        private fun isFlappy(k: DeviceKind) = isPad(k) || k == DeviceKind.BT_HEADPHONES || k == DeviceKind.BT_SPEAKER
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
