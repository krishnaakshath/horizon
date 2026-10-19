package dev.tvlauncher.system

import dev.tvlauncher.devices.ConnectedDevice
import dev.tvlauncher.devices.DeviceActions
import dev.tvlauncher.devices.DeviceCopy
import dev.tvlauncher.devices.DeviceGroup
import dev.tvlauncher.devices.DeviceIcons
import dev.tvlauncher.devices.DeviceKind
import dev.tvlauncher.devices.InputsModel
import dev.tvlauncher.devices.TvInputItem
import dev.tvlauncher.devices.group

/**
 * Settings > Devices as plain rows in groups: Inputs (every port, with the device behind it or an
 * honest signal word; analog ports say "Available"), Controllers & remotes (the TV's own remote
 * named "TV remote", duplicates folded), Audio, Storage, Other, and one Network row. Pure.
 */
object DevicesPageModel {
    enum class Action { SWITCH_INPUT, NETWORK, NONE }

    data class Row(val key: String, val title: String, val value: String?, val icon: Int, val action: Action, val target: String? = null)
    data class Section(val title: String, val rows: List<Row>)

    const val REMOTE_NAME = "TV remote"
    private val remoteWord = Regex("""(^|[\s_\-])(rc\d{2,4}\w*|remote|remote control)($|[\s_\-])""", RegexOption.IGNORE_CASE)
    private val hidSuffix = Regex("""\s+(keyboard|mouse|consumer control|system control|touchpad)$""", RegexOption.IGNORE_CASE)

    /** True for the TV's own remote (e.g. "TCL_RC902V_M4 Keyboard"). */
    fun isRemote(name: String): Boolean = remoteWord.containsMatchIn(name.trim())

    /** A readable name: the TV remote by role, vendor HID suffixes dropped, underscores as spaces. */
    fun tidyName(d: ConnectedDevice): String {
        val raw = d.name.trim()
        if (raw.isEmpty()) return DeviceCopy.label(d.kind)
        if (isRemote(raw)) return REMOTE_NAME
        val cleaned = hidSuffix.replace(raw, "").replace('_', ' ').trim()
        return cleaned.ifEmpty { DeviceCopy.label(d.kind) }
    }

    fun sections(devices: List<ConnectedDevice>, inputs: List<TvInputItem>): List<Section> {
        val out = ArrayList<Section>()
        if (inputs.isNotEmpty()) out += Section("Inputs", inputs.map { i ->
            val sig = i.signal
            Row(InputsModel.PREFIX + i.id, i.label, if (i.device != null && sig.live) i.device else sig.word,
                InputsModel.iconFor(i.type), Action.SWITCH_INPUT, i.id)
        })
        val others = devices.filter { DeviceActions.passthroughInputId(it) == null }
        fun group(title: String, pick: (ConnectedDevice) -> Boolean) {
            val seen = HashSet<String>()
            val rows = others.filter(pick).mapNotNull { d ->
                val t = tidyName(d)
                if (!seen.add(t.lowercase())) return@mapNotNull null
                val kind = DeviceCopy.label(d.kind)
                Row("dev:" + d.id, t, kind.takeUnless { it.equals(t, true) || t == REMOTE_NAME }, DeviceIcons.res(d.kind), Action.NONE)
            }
            if (rows.isNotEmpty()) out += Section(title, rows)
        }
        group("Controllers & remotes") { it.kind.group() == DeviceGroup.CONTROLLERS || (it.kind == DeviceKind.BT_OTHER && isRemote(it.name)) }
        group("Audio") { it.kind.group() == DeviceGroup.AUDIO }
        group("Storage") { it.kind.group() == DeviceGroup.STORAGE }
        group("Other") {
            val g = it.kind.group()
            g != DeviceGroup.CONTROLLERS && g != DeviceGroup.AUDIO && g != DeviceGroup.STORAGE && g != DeviceGroup.NETWORK &&
                !(it.kind == DeviceKind.BT_OTHER && isRemote(it.name))
        }
        val net = others.firstOrNull { it.kind == DeviceKind.NETWORK_ETHERNET } ?: others.firstOrNull { it.kind == DeviceKind.NETWORK_WIFI }
        out += Section("Network", listOf(when (net?.kind) {
            DeviceKind.NETWORK_ETHERNET -> Row("net", "Ethernet", "Connected", DeviceIcons.res(DeviceKind.NETWORK_ETHERNET), Action.NETWORK)
            DeviceKind.NETWORK_WIFI -> Row("net", "Wi-Fi", net.detail?.takeIf { it.isNotBlank() } ?: "Connected",
                dev.tvlauncher.R.drawable.ic_sc_wifi, Action.NETWORK)
            else -> Row("net", "Network", "Not connected", dev.tvlauncher.R.drawable.ic_sc_wifi, Action.NETWORK)
        }))
        return out
    }
}
