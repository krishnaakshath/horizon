package dev.tvlauncher.devices

/** One passthrough TV input as read from TvInputManager. [state]: 0 connected, 1 standby, 2 disconnected. */
data class InputSnapshot(
    val id: String,
    val parentId: String?,
    val label: String,
    val type: Int,
    val state: Int
) {
    val live: Boolean get() = state != STATE_DISCONNECTED

    companion object {
        const val STATE_DISCONNECTED = 2
    }
}

/** Tolerant parsing of vendor input ids; never throws, returns null when nothing is recognised. */
object InputIdParser {
    private val cecChild = Regex("""[/#](?:HDMI|CEC)([0-9A-Fa-f]{4,8})$""", RegexOption.IGNORE_CASE)

    /** HDMI port number (1-based) encoded in a CEC child id's physical address, or null. */
    fun parentPortOf(id: String?): Int? {
        val m = cecChild.find(id ?: return null) ?: return null
        val n = m.groupValues[1][0].digitToIntOrNull(16) ?: return null
        return if (n in 1..9) n else null
    }
}

/** True for names that carry no identity: blank, "HDMI device", "HDMI 2", "Device" or the port label itself. */
internal fun isGenericDevice(d: ConnectedDevice): Boolean {
    val n = d.name.trim()
    if (n.isEmpty() || n.equals("Device", true) || n.equals(HdmiPortTracker.GENERIC_NAME, true)) return true
    if (Regex("""hdmi\s*-?\s*\d*""", RegexOption.IGNORE_CASE).matches(n)) return true
    return d.port != null && n.equals(d.port.trim(), true)
}

/**
 * Holds back Disconnected events for [quietMs]. A device that returns inside the window is swallowed
 * entirely, EXCEPT a genuine swap: Updated is emitted only when both the held and the returning
 * identities are named (non-generic) and differ, or a generic port comes back as a named device.
 * A generic returning device (port back before its CEC child) is swallowed and the last named
 * identity is remembered for [memoryMs]; a child that later reappears with the same name emits
 * nothing, a different name emits one Updated. A disconnect that outlasts the window is released
 * once by [tick] and clears the memory (the port is then a fresh device).
 */
class FlapGuard(
    private val quietMs: Long = DEFAULT_QUIET_MS,
    private val clock: () -> Long
) {
    private val memoryMs = MEMORY_MS
    private class Held(val event: DeviceEvent, val due: Long)
    private class Mem(val device: ConnectedDevice, val until: Long)
    private val held = LinkedHashMap<String, Held>()
    private val memory = HashMap<String, Mem>()

    private fun remembered(id: String): ConnectedDevice? {
        val m = memory[id] ?: return null
        if (clock() >= m.until) { memory.remove(id); return null }
        return m.device
    }

    fun submit(e: DeviceEvent): List<DeviceEvent> {
        val id = e.device.id
        return when (e) {
            is DeviceEvent.Disconnected -> { held[id] = Held(e, clock() + quietMs); emptyList() }
            is DeviceEvent.Connected -> {
                val h = held.remove(id) ?: return listOf(e)
                val prevDev = h.event.device
                val cur = e.device
                when {
                    isGenericDevice(cur) -> {
                        if (!isGenericDevice(prevDev)) memory[id] = Mem(prevDev, clock() + memoryMs)
                        emptyList()
                    }
                    !isGenericDevice(prevDev) && prevDev.name == cur.name && prevDev.kind == cur.kind -> emptyList()
                    remembered(id)?.name == cur.name -> emptyList()
                    else -> { memory[id] = Mem(cur, clock() + memoryMs); listOf(DeviceEvent.Updated(cur, prevDev)) }
                }
            }
            is DeviceEvent.Updated -> {
                val h = held.remove(id)
                val prevDev = h?.event?.device
                val mem = remembered(id)
                when {
                    prevDev != null && !isGenericDevice(prevDev) && prevDev.name == e.device.name -> emptyList()
                    mem != null && mem.name == e.device.name -> emptyList()
                    else -> { memory[id] = Mem(e.device, clock() + memoryMs); listOf(e) }
                }
            }
        }
    }

    fun tick(): List<DeviceEvent> {
        val now = clock()
        val due = held.values.filter { it.due <= now }
        for (h in due) { held.remove(h.event.device.id); memory.remove(h.event.device.id) }
        return due.map { it.event }
    }

    fun nextDueDelayMs(): Long? = held.values.minOfOrNull { it.due }?.let { (it - clock()).coerceAtLeast(0L) }
    fun clear() { held.clear(); memory.clear() }

    companion object {
        const val DEFAULT_QUIET_MS = 8000L
        const val MEMORY_MS = 60_000L
    }
}

/**
 * Models an HDMI port and its CEC child as ONE device keyed by the port id. The name is the
 * child's CEC name while a child is live, otherwise a generic "HDMI device". A child appearing
 * emits a single [DeviceEvent.Updated]; a child vanishing while the port stays up is silent;
 * a port that really disconnects is announced once after the [FlapGuard] quiet window.
 */
class HdmiPortTracker(clock: () -> Long, quietMs: Long = FlapGuard.DEFAULT_QUIET_MS) {
    private val guard = FlapGuard(quietMs, clock)
    private var prev: List<ConnectedDevice> = emptyList()

    fun devices(): List<ConnectedDevice> = prev

    /** [emit] false = seed silently (initial state). */
    fun update(inputs: List<InputSnapshot>, emit: Boolean = true): List<DeviceEvent> {
        val cur = buildPorts(inputs)
        val events = if (emit) diff(prev, cur) else emptyList()
        prev = cur
        return events.flatMap { guard.submit(it) }
    }

    fun tick(): List<DeviceEvent> = guard.tick()
    fun nextDueDelayMs(): Long? = guard.nextDueDelayMs()
    fun reset() { prev = emptyList(); guard.clear() }

    private fun isGeneric(d: ConnectedDevice) = isGenericDevice(d)

    private fun diff(old: List<ConnectedDevice>, new: List<ConnectedDevice>): List<DeviceEvent> {
        val out = ArrayList<DeviceEvent>()
        val oldMap = old.associateBy { it.id }
        val newMap = new.associateBy { it.id }
        for (c in new) {
            val p = oldMap[c.id]
            if (p == null) out.add(DeviceEvent.Connected(c))
            else if ((p.name != c.name || p.kind != c.kind) && !isGeneric(c)) out.add(DeviceEvent.Updated(c, p))
        }
        for (p in old) if (p.id !in newMap) out.add(DeviceEvent.Disconnected(p))
        return out
    }

    companion object {
        const val GENERIC_NAME = "HDMI device"
        private const val TYPE_HDMI = 1007
        private const val TYPE_DISPLAY_PORT = 1008

        fun buildPorts(inputs: List<InputSnapshot>): List<ConnectedDevice> {
            val byId = inputs.associateBy { it.id }
            fun parentOf(i: InputSnapshot): String? {
                i.parentId?.takeIf { it in byId && it != i.id }?.let { return it }
                val n = InputIdParser.parentPortOf(i.id) ?: return null
                val rx = Regex("""hdmi\s*-?\s*$n\b""", RegexOption.IGNORE_CASE)
                return inputs.firstOrNull { it.id != i.id && it.parentId == null && rx.containsMatchIn(it.label) }?.id
            }
            val parent = inputs.associate { it.id to parentOf(it) }
            val out = ArrayList<ConnectedDevice>()
            for (port in inputs) {
                if (parent[port.id] != null) continue
                val child = inputs.filter { parent[it.id] == port.id && it.live }.minByOrNull { it.id }
                if (!port.live && child == null) continue
                val isHdmi = port.type == TYPE_HDMI || port.type == TYPE_DISPLAY_PORT
                val portLabel = port.label.trim().takeIf { it.isNotEmpty() }
                val childName = child?.label?.trim()?.takeIf { it.isNotEmpty() }
                val kind = if (child != null) DeviceClassifier.fromTvInputType(child.type, childName)
                else DeviceClassifier.fromTvInputType(port.type, null)
                if (kind == DeviceKind.UNKNOWN) continue
                val name = childName ?: if (isHdmi) GENERIC_NAME else (portLabel ?: "Input")
                out.add(ConnectedDevice("tv:" + port.id, name, kind, portLabel, null))
            }
            return out
        }
    }
}
