package dev.tvlauncher.devices

import java.util.Locale

/**
 * Collapses one physical device reported by two sources (ACL + InputManager, UsbManager +
 * InputManager). Two events are the same device when they come from different sources, have the
 * same direction, compatible kinds, fall inside [windowMs] and have similar names (token subset or
 * Jaccard >= 0.5; a generic/empty name matches anything compatible).
 */
class CrossSourceDedupe(private val windowMs: Long = 6000L, private val clock: () -> Long) {
    private class Seen(val source: String, val connected: Boolean, val kind: DeviceKind, val tokens: Set<String>, val at: Long)
    private val seen = ArrayList<Seen>()

    fun accept(source: String, event: DeviceEvent): Boolean {
        val now = clock()
        seen.removeAll { now - it.at >= windowMs }
        val connected = event !is DeviceEvent.Disconnected
        val kind = event.device.kind
        val tokens = tokens(event.device.name)
        val dup = seen.any { it.source != source && it.connected == connected && compatible(it.kind, kind) && similar(it.tokens, tokens) }
        if (dup) return false
        seen.add(Seen(source, connected, kind, tokens, now))
        return true
    }

    fun clear() = seen.clear()

    private fun family(k: DeviceKind): String = when (k) {
        DeviceKind.GAMEPAD, DeviceKind.BT_GAMEPAD -> "pad"
        DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.BT_KEYBOARD_MOUSE -> "kbm"
        else -> k.name
    }

    private fun compatible(a: DeviceKind, b: DeviceKind): Boolean {
        val fa = family(a); val fb = family(b)
        if (fa == fb) return true
        val hid = DeviceKind.USB_HID_OTHER.name
        return (fa == hid && (fb == "pad" || fb == "kbm")) || (fb == hid && (fa == "pad" || fa == "kbm"))
    }

    private fun similar(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return true
        val inter = a.intersect(b).size
        if (inter == a.size || inter == b.size) return true
        return inter.toFloat() / a.union(b).size >= 0.5f
    }

    companion object {
        private val stop = setOf("usb", "bluetooth", "wireless", "device", "hid", "the", "controller", "gamepad",
            "keyboard", "mouse", "receiver", "and", "bt")

        fun tokens(name: String?): Set<String> =
            (name ?: "").lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in stop }.toSet()
    }
}
