package dev.tvlauncher.network

enum class BtKind { HEADPHONES, SPEAKER, GAMEPAD, KEYBOARD, MOUSE, PHONE, COMPUTER, WATCH, OTHER;

    companion object {
        private const val MAJOR_MASK = 0x1F00
        private val GAMEPAD_HINTS = listOf("dualsense", "dualshock", "xbox", "controller", "gamepad", "joy-con", "pro controller")
        private val HEADPHONE_HINTS = listOf("airpods", "buds", "headphone", "headset", "earbud", "wh-1000", "wf-1000")
        private val SPEAKER_HINTS = listOf("speaker", "soundbar", "boombox", "homepod")

        /**
         * [majorClass]/[deviceClass] are BluetoothClass.getMajorDeviceClass()/getDeviceClass() (or -1).
         * Names that clearly say "controller" win over the class (several pads report a keyboard class).
         */
        fun classify(name: String?, majorClass: Int, deviceClass: Int): BtKind {
            val n = name.orEmpty().lowercase()
            if (GAMEPAD_HINTS.any { it in n }) return GAMEPAD
            val major = if (majorClass >= 0) majorClass and MAJOR_MASK else if (deviceClass >= 0) deviceClass and MAJOR_MASK else -1
            val byClass = when (major) {
                0x0100 -> COMPUTER
                0x0200 -> PHONE
                0x0700 -> WATCH
                0x0400 -> when (deviceClass and 0xFF) {
                    0x14, 0x20, 0x24, 0x28 -> SPEAKER
                    0x04, 0x08, 0x18 -> HEADPHONES
                    else -> SPEAKER
                }
                0x0500 -> when (deviceClass and 0xC0) {
                    0x40, 0xC0 -> KEYBOARD
                    0x80 -> MOUSE
                    else -> when (deviceClass and 0x3C) {
                        0x04, 0x08 -> GAMEPAD
                        else -> OTHER
                    }
                }
                else -> OTHER
            }
            if (byClass != OTHER) return byClass
            return when {
                HEADPHONE_HINTS.any { it in n } -> HEADPHONES
                SPEAKER_HINTS.any { it in n } -> SPEAKER
                "keyboard" in n -> KEYBOARD
                "mouse" in n -> MOUSE
                else -> OTHER
            }
        }
    }
}

data class BtRaw(val address: String, val name: String?, val majorClass: Int = -1, val deviceClass: Int = -1)

data class BtDeviceModel(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val connected: Boolean,
    val kind: BtKind
)

data class BtSections(
    val connected: List<BtDeviceModel>,
    val paired: List<BtDeviceModel>,
    val nearby: List<BtDeviceModel>
) {
    val all: List<BtDeviceModel> get() = connected + paired + nearby
}

object BtListBuilder {
    fun build(bonded: List<BtRaw>, found: List<BtRaw>, connectedAddresses: Set<String>): BtSections {
        val conn = connectedAddresses.map { it.uppercase() }.toSet()
        val bondedModels = merge(bonded).map { model(it, true, it.address.uppercase() in conn) }
        val bondedKeys = bondedModels.map { it.address.uppercase() }.toSet()
        val nearby = merge(found).filter { it.address.uppercase() !in bondedKeys }.map { model(it, false, false) }
        val byName = compareBy<BtDeviceModel>({ it.name.lowercase() }, { it.address })
        return BtSections(
            connected = bondedModels.filter { it.connected }.sortedWith(byName),
            paired = bondedModels.filter { !it.connected }.sortedWith(byName),
            nearby = nearby.sortedWith(byName)
        )
    }

    private fun model(r: BtRaw, bonded: Boolean, connected: Boolean): BtDeviceModel {
        val name = r.name?.trim().orEmpty().ifEmpty { r.address }
        return BtDeviceModel(r.address, name, bonded, connected, BtKind.classify(r.name, r.majorClass, r.deviceClass))
    }

    /** Dedupe by address (case-insensitive); a later entry only fills in a missing name/class. */
    private fun merge(list: List<BtRaw>): List<BtRaw> {
        val map = LinkedHashMap<String, BtRaw>()
        for (r in list) {
            val k = r.address.uppercase()
            val cur = map[k]
            map[k] = when {
                cur == null -> r
                cur.name.isNullOrBlank() && !r.name.isNullOrBlank() -> r
                else -> cur
            }
        }
        return map.values.toList()
    }
}
