package dev.tvlauncher.devices

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.Trailing

/**
 * Pure content of the Devices panel: one row per connected device in [DeviceRegistry] order
 * (consoles, streaming, audio, ...), a chevron on TV inputs (OK switches to them) and the
 * launcher's own device settings as the last row.
 */
object DevicesPanelModel {
    const val KEY_SETTINGS = "act:settings"
    const val PREFIX = "dev:"

    fun header(devices: List<ConnectedDevice>) = PanelHeader(R.drawable.ic_set_devices, "Devices", when (devices.size) {
        0 -> "Nothing connected"
        1 -> "1 connected"
        else -> "${devices.size} connected"
    })

    fun build(devices: List<ConnectedDevice>, notice: String? = null): PanelContent {
        val e = ArrayList<PanelEntry>()
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        if (devices.isEmpty()) e += PanelEntry.Message("msg:empty", "Nothing connected right now.")
        devices.mapTo(e) { row(it) }
        e += PanelEntry.Row(KEY_SETTINGS, R.drawable.ic_settings, "Device settings", trailing = Trailing.Chevron)
        return PanelContent(header(devices), e)
    }

    fun row(d: ConnectedDevice): PanelEntry.Row {
        val label = dev.tvlauncher.system.DevicesPageModel.tidyName(d)
        val sub = DeviceCopy.subtitle(d, true).takeUnless { it == label }
        val input = DeviceActions.passthroughInputId(d) != null
        return PanelEntry.Row(PREFIX + d.id, DeviceIcons.res(d.kind), label, sub,
            if (input) Trailing.Chevron else Trailing.None)
    }
}

/** One passthrough TV input as read from TvInputManager (plain values; JVM-testable). */
data class RawInput(
    val id: String,
    val parentId: String?,
    val label: String,
    val type: Int,
    /** TvInputManager.INPUT_STATE_*: 0 connected, 1 connected standby, 2 disconnected. */
    val state: Int
)

/**
 * A physical input port with the name of the device behind it, when known. [cec] is true when an
 * HDMI-CEC device input was folded into this port (a live CEC child is real detection).
 */
data class TvInputItem(
    val id: String,
    val label: String,
    val type: Int,
    val state: Int,
    val device: String?,
    val cec: Boolean = false
) {
    val signal: InputSignal get() = InputsModel.signal(type, state, cec)
}

/**
 * What the launcher may honestly say about a port. Only HDMI has hotplug (and CEC) detection;
 * analog ports (AV/composite, S-Video, SCART, component, VGA) are reported "connected" by the
 * TV whether or not a cable is in, so they are only ever [AVAILABLE].
 */
enum class InputSignal(val word: String, val live: Boolean) {
    CONNECTED("Connected", true),
    STANDBY("Standby", true),
    NO_SIGNAL("No signal", false),
    AVAILABLE("Available", false)
}

/**
 * Pure content of the Inputs panel: every physical input port in natural order (HDMI 1, HDMI 2,
 * ... HDMI 10), each with the device behind it or its signal state; OK switches to it. CEC child
 * inputs fold into their port, contributing the device name. Ports without detection say
 * "Available" ([InputSignal]), never "Connected".
 */
object InputsModel {
    const val KEY_SETTINGS = "act:settings"
    const val PREFIX = "in:"

    const val STATE_CONNECTED = 0
    const val STATE_STANDBY = 1
    const val STATE_DISCONNECTED = 2

    // android.media.tv.TvInputInfo.TYPE_* (plain ints so this stays JVM-only).
    const val TYPE_TUNER = 0
    const val TYPE_COMPOSITE = 1001
    const val TYPE_SVIDEO = 1002
    const val TYPE_SCART = 1003
    const val TYPE_COMPONENT = 1004
    const val TYPE_VGA = 1005
    const val TYPE_DVI = 1006
    const val TYPE_HDMI = 1007
    const val TYPE_DISPLAY_PORT = 1008

    /**
     * Folds CEC children into their parent port (the first named child gives [TvInputItem.device];
     * a live child makes the port live). [names] maps an input id to a known device name (e.g.
     * from the device monitor); generic names ("HDMI 2", "HDMI device") are ignored.
     */
    fun merge(raw: List<RawInput>, names: Map<String, String> = emptyMap()): List<TvInputItem> {
        val ids = raw.map { it.id }.toSet()
        val children = raw.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId!! }
        return raw.filter { it.parentId == null || it.parentId !in ids }.map { p ->
            val kids = children[p.id].orEmpty()
            val device = (kids.map { it.label } + listOfNotNull(names[p.id]))
                .map { it.trim() }.firstOrNull { it.isNotEmpty() && !isGeneric(it, p.label) }
            val state = (kids.map { it.state } + p.state).minOrNull() ?: p.state
            val cec = kids.any { it.state != STATE_DISCONNECTED }
            TvInputItem(p.id, p.label.trim().ifEmpty { fallbackLabel(p.type) }, p.type, state, device, cec)
        }.sortedWith(compareBy(NATURAL) { it.label })
    }

    fun isGeneric(name: String, portLabel: String): Boolean {
        val n = name.trim()
        if (n.equals(portLabel.trim(), true)) return true
        if (n.equals(HdmiPortTracker.GENERIC_NAME, true) || n.equals("Device", true)) return true
        return Regex("""hdmi\s*-?\s*\d*""", RegexOption.IGNORE_CASE).matches(n)
    }

    /** True for port types whose connection state the TV really detects (HDMI hotplug / CEC). */
    fun detects(type: Int) = type == TYPE_HDMI

    /**
     * TvInputManager state mapped to what may be shown: HDMI ports (or any port with a live CEC
     * device) report their real state; every other port is [InputSignal.AVAILABLE].
     */
    fun signal(type: Int, state: Int, cec: Boolean = false): InputSignal = when {
        !detects(type) && !cec -> InputSignal.AVAILABLE
        state == STATE_CONNECTED -> InputSignal.CONNECTED
        state == STATE_STANDBY -> InputSignal.STANDBY
        else -> InputSignal.NO_SIGNAL
    }

    fun header(items: List<TvInputItem>): PanelHeader {
        // Same words as the rows: a port in standby is counted as "in standby", not "connected".
        val on = items.count { it.signal == InputSignal.CONNECTED }
        val standby = items.count { it.signal == InputSignal.STANDBY }
        return PanelHeader(R.drawable.ic_input, "Inputs", when {
            items.isEmpty() -> "None found"
            on == 0 && standby == 0 -> "Nothing connected"
            standby == 0 -> "$on connected"
            on == 0 -> "$standby in standby"
            else -> "$on connected, $standby in standby"
        })
    }

    fun build(items: List<TvInputItem>, notice: String? = null): PanelContent {
        val e = ArrayList<PanelEntry>()
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        if (items.isEmpty()) e += PanelEntry.Message("msg:empty", "This TV has no external inputs.")
        items.mapTo(e) { row(it) }
        return PanelContent(header(items), e)
    }

    fun row(i: TvInputItem): PanelEntry.Row {
        val sig = i.signal
        return PanelEntry.Row(PREFIX + i.id, iconFor(i.type), i.label, if (i.device != null && sig.live) i.device else sig.word)
    }

    fun iconFor(type: Int) = when (type) {
        TYPE_HDMI, TYPE_DVI, TYPE_DISPLAY_PORT -> R.drawable.ic_dev_hdmi
        TYPE_TUNER -> R.drawable.ic_dev_antenna
        TYPE_COMPOSITE, TYPE_SVIDEO, TYPE_SCART -> R.drawable.ic_dev_av
        TYPE_COMPONENT, TYPE_VGA -> R.drawable.ic_dev_component
        else -> R.drawable.ic_input
    }

    fun fallbackLabel(type: Int) = when (type) {
        TYPE_HDMI -> "HDMI"
        TYPE_TUNER -> "TV"
        TYPE_COMPOSITE -> "AV"
        TYPE_COMPONENT -> "Component"
        TYPE_VGA -> "VGA"
        else -> "Input"
    }

    /** "HDMI 2" before "HDMI 10": digit runs compare by value, the rest case-insensitively. */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        val ra = Regex("""\d+|\D+""").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("""\d+|\D+""").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                (x.toBigInteger().compareTo(y.toBigInteger())).takeIf { it != 0 } ?: x.length.compareTo(y.length)
            } else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ra.size.compareTo(rb.size)
    }
}
