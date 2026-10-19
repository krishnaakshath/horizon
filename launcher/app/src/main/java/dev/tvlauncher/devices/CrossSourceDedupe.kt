package dev.tvlauncher.devices

import java.util.Locale

/**
 * Collapses one physical device reported by two sources. Only pairs involving the input source
 * can be duplicates (usb<->input, bt<->input); usb and bt reports never merge. Two events match
 * when they come from different sources, have the same direction, compatible kinds, fall inside
 * [windowMs], and share a vendor:product id or a non-empty common name token (subset or Jaccard
 * >= 0.5) or an identical full name. A match CONSUMES the earlier record, so two identical devices
 * (one USB pad, one BT pad) still yield two cards.
 */
class CrossSourceDedupe(private val windowMs: Long = 6000L, private val clock: () -> Long) {
    private class Seen(val source: String, val connected: Boolean, val kind: DeviceKind, val tokens: Set<String>,
                       val full: String, val vidPid: String?, val at: Long) { var consumed = false }
    private val seen = ArrayList<Seen>()

    fun accept(source: String, event: DeviceEvent, vidPid: String? = null): Boolean {
        val now = clock()
        seen.removeAll { now - it.at >= windowMs }
        val connected = event !is DeviceEvent.Disconnected
        val kind = event.device.kind
        val tokens = tokens(event.device.name)
        val full = full(event.device.name)
        val match = seen.firstOrNull {
            !it.consumed && it.source != source && (it.source == "input" || source == "input") &&
                it.connected == connected && compatible(it.kind, kind) && similar(it, tokens, full, vidPid)
        }
        if (match != null) { match.consumed = true; return false }
        seen.add(Seen(source, connected, kind, tokens, full, vidPid, now))
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

    private fun similar(r: Seen, tokens: Set<String>, full: String, vidPid: String?): Boolean {
        if (vidPid != null && vidPid == r.vidPid) return true
        if (full.isNotEmpty() && full == r.full) return true
        val inter = r.tokens.intersect(tokens).size
        if (inter == 0) return false
        if (inter == r.tokens.size || inter == tokens.size) return true
        return inter.toFloat() / r.tokens.union(tokens).size >= 0.5f
    }

    companion object {
        private val stop = setOf("usb", "bluetooth", "wireless", "device", "hid", "the", "controller", "gamepad",
            "keyboard", "mouse", "receiver", "and", "bt")

        fun full(name: String?): String = (name ?: "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        fun tokens(name: String?): Set<String> =
            (name ?: "").lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in stop }.toSet()
    }
}
