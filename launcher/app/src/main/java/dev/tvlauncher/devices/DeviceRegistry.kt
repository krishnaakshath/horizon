package dev.tvlauncher.devices

/** Pure state holder plus the diff and debounce helpers used by the monitor. */
class DeviceRegistry {
    private val devices = LinkedHashMap<String, ConnectedDevice>()

    fun applyConnected(device: ConnectedDevice) { devices[device.id] = device }
    fun applyDisconnected(id: String) { devices.remove(id) }
    fun snapshot(): List<ConnectedDevice> = sorted(devices.values)

    companion object {
        private fun priority(kind: DeviceKind): Int = when (kind.group()) {
            DeviceGroup.CONSOLES -> 0
            DeviceGroup.STREAMING -> 1
            DeviceGroup.AUDIO -> 2
            DeviceGroup.STORAGE -> 3
            DeviceGroup.CONTROLLERS -> 4
            DeviceGroup.NETWORK -> 6
            DeviceGroup.OTHER -> 5
        }

        fun sorted(list: Collection<ConnectedDevice>): List<ConnectedDevice> =
            list.sortedWith(compareBy<ConnectedDevice>({ priority(it.kind) }, { it.kind.ordinal }, { it.name.lowercase() }, { it.id }))

        /** Events turning [previous] into [current], keyed by id (a rename is not an event). */
        fun diff(previous: List<ConnectedDevice>, current: List<ConnectedDevice>): List<DeviceEvent> {
            val prev = previous.associateBy { it.id }
            val cur = current.associateBy { it.id }
            val out = ArrayList<DeviceEvent>()
            for (d in current) if (d.id !in prev) out.add(DeviceEvent.Connected(d))
            for (d in previous) if (d.id !in cur) out.add(DeviceEvent.Disconnected(d))
            return out
        }
    }
}

/** Suppresses repeats of the same event type for the same id within [minGapMs]. */
class Debouncer(private val minGapMs: Long, private val clock: () -> Long) {
    private val last = HashMap<String, Long>()

    fun accept(event: DeviceEvent): Boolean {
        val key = (if (event is DeviceEvent.Connected) "c:" else "d:") + event.device.id
        val now = clock()
        val prev = last[key]
        if (prev != null && now - prev < minGapMs) return false
        last[key] = now
        return true
    }
}

/**
 * Drops a Connected/Disconnected event when an equivalent one (same direction, same broad kind)
 * from a different source was accepted within [gapMs] - e.g. a USB keyboard reported by both
 * UsbManager and InputManager, or a BT gamepad reported by ACL and by InputManager.
 */
class CrossSourceDeduper(private val gapMs: Long, private val clock: () -> Long) {
    private class Seen(val source: String, val at: Long)
    private val seen = HashMap<String, Seen>()

    fun accept(source: String, event: DeviceEvent): Boolean {
        val key = (if (event is DeviceEvent.Connected) "c:" else "d:") + family(event.device.kind)
        val now = clock()
        val prev = seen[key]
        if (prev != null && prev.source != source && now - prev.at < gapMs) return false
        seen[key] = Seen(source, now)
        return true
    }

    companion object {
        fun family(kind: DeviceKind): String = when (kind) {
            DeviceKind.GAMEPAD, DeviceKind.BT_GAMEPAD -> "pad"
            DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.BT_KEYBOARD_MOUSE -> "kbm"
            else -> kind.name
        }
    }
}
