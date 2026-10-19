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

/**
 * Holds back Disconnected events for [quietMs]. Connected -> Disconnected -> Connected inside the
 * window is swallowed entirely; a disconnect that outlasts the window is released once by [tick].
 */
class FlapGuard(private val quietMs: Long = DEFAULT_QUIET_MS, private val clock: () -> Long) {
    private class Held(val event: DeviceEvent, val due: Long)
    private val held = LinkedHashMap<String, Held>()

    fun submit(e: DeviceEvent): List<DeviceEvent> {
        val id = e.device.id
        return when (e) {
            is DeviceEvent.Disconnected -> { held[id] = Held(e, clock() + quietMs); emptyList() }
            is DeviceEvent.Connected -> {
                val h = held.remove(id)
                when {
                    h == null -> listOf(e)
                    h.event.device.name == e.device.name && h.event.device.kind == e.device.kind -> emptyList()
                    else -> listOf(DeviceEvent.Updated(e.device, h.event.device)) // different device swapped in
                }
            }
            is DeviceEvent.Updated -> {
                val h = held.remove(id)
                if (h != null && h.event.device.name == e.device.name) emptyList() else listOf(e)
            }
        }
    }

    fun tick(): List<DeviceEvent> {
        val now = clock()
        val due = held.values.filter { it.due <= now }
        for (h in due) held.remove(h.event.device.id)
        return due.map { it.event }
    }

    fun nextDueDelayMs(): Long? = held.values.minOfOrNull { it.due }?.let { (it - clock()).coerceAtLeast(0L) }
    fun clear() = held.clear()

    companion object { const val DEFAULT_QUIET_MS = 8000L }
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

    private fun isGeneric(d: ConnectedDevice) = d.kind == DeviceKind.HDMI_OTHER && d.name == GENERIC_NAME

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
