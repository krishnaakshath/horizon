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
