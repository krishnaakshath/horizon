package dev.tvlauncher.devices

/**
 * Network source filter. A Disconnected is held for [holdMs]; any Connected arriving inside the
 * window (same link = blip, other link = Wi-Fi/Ethernet handover) swallows both events. A loss that
 * lasts longer is released once by [tick].
 */
class NetworkEventPolicy(private val holdMs: Long = 3000L, private val clock: () -> Long) {
    private class Held(val event: DeviceEvent, val due: Long)
    private val held = LinkedHashMap<String, Held>()

    fun submit(e: DeviceEvent): List<DeviceEvent> = when (e) {
        is DeviceEvent.Disconnected -> { held[e.device.id] = Held(e, clock() + holdMs); emptyList() }
        else -> if (held.isNotEmpty()) { held.clear(); emptyList() } else listOf(e)
    }

    /** One refresh's events together: connect + disconnect in the same batch is a handover. */
    fun submitAll(events: List<DeviceEvent>): List<DeviceEvent> {
        val hasDown = events.any { it is DeviceEvent.Disconnected }
        val hasUp = events.any { it !is DeviceEvent.Disconnected }
        if (hasDown && hasUp) return emptyList()
        return events.flatMap { submit(it) }
    }

    fun tick(): List<DeviceEvent> {
        val now = clock()
        val due = held.values.filter { it.due <= now }
        for (h in due) held.remove(h.event.device.id)
        return due.map { it.event }
    }

    fun nextDueDelayMs(): Long? = held.values.minOfOrNull { it.due }?.let { (it - clock()).coerceAtLeast(0L) }
    fun clear() = held.clear()
}
