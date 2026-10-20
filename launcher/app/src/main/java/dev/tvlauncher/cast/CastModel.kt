package dev.tvlauncher.cast

import dev.tvlauncher.appsettings.PageSpec

/**
 * Pure logic of Settings > Connections > Cast & AirPlay and of the AirPlay row in the Inputs
 * panel. Mintaka does not receive casts itself: it shows which receivers the TV already has
 * (Google Cast built in, a maker's AirPlay input) and how to reach them.
 */
object CastModel {
    /** The Google Cast receiver that ships with Google TV / Android TV ("Chromecast built-in"). */
    const val CAST_RECEIVER_PACKAGE = "com.google.android.apps.mediashell"

    /** Kodi (already in the Discover catalog): receives videos and music from phone apps over DLNA/UPnP. */
    const val RECEIVER_APP_PACKAGE = "org.xbmc.kodi"
    const val RECEIVER_APP_NAME = "Kodi"

    const val READY = "Ready"
    const val NOT_ON_TV = "Not on this TV"
    const val CONNECTED = "Connected"
    const val NOT_CONNECTED = "Not connected"
    const val UNKNOWN_NAME = "Not set"

    const val AIRPLAY_LABEL = "AirPlay"
    const val AIRPLAY_SUBTITLE = "Ready for iPhone, iPad and Mac"

    const val IPHONE_HOWTO = "Open Control Centre, choose Screen Mirroring, then pick this TV. " +
        "In a video app, the AirPlay button sends just the video."
    const val ANDROID_HOWTO = "In a video or music app, press the Cast button and pick this TV. " +
        "To show the whole screen, use Cast or Screen cast in the phone's quick settings."
    const val COMPUTER_HOWTO = "In Chrome on a computer, open the menu, choose Cast, then pick this TV."
    const val NETWORK_HOWTO = "The phone and the TV must be on the same Wi-Fi network."

    /** True for a TV input that is a maker's AirPlay receiver (e.g. TCL's "AirPlay" passthrough input). */
    fun isAirPlayInput(id: String, label: String?): Boolean {
        val norm = Regex("""[\s_\-]""")
        val l = label.orEmpty().replace(norm, "").lowercase()
        val i = id.replace(norm, "").lowercase()
        return "airplay" in l || "airplay" in i
    }

    /** What the TV offers, read once per page build (plain values; JVM-testable). */
    data class State(
        val tvName: String?,
        val castReceiver: Boolean,
        /** The AirPlay input id when the TV exposes one. */
        val airPlayInput: String?,
        /** The TV is on a local network (Wi-Fi or Ethernet). The network's name is never shown. */
        val onNetwork: Boolean,
        val receiverAppInstalled: Boolean = false
    ) {
        val airPlay: Boolean get() = airPlayInput != null
    }

    /** The first usable name: the TV's device name, then its Bluetooth name. */
    fun tvName(deviceName: String?, bluetoothName: String?): String? =
        listOf(deviceName, bluetoothName).map { it?.trim().orEmpty() }.firstOrNull { it.isNotEmpty() }

    fun status(present: Boolean) = if (present) READY else NOT_ON_TV

    fun networkStatus(onNetwork: Boolean) = if (onNetwork) CONNECTED else NOT_CONNECTED

    const val NAME = "cast.name"
    const val NETWORK = "cast.network"
    const val CHROMECAST = "cast.chromecast"
    const val AIRPLAY = "cast.airplay"
    const val OPEN_AIRPLAY = "cast.open"
    const val GET_RECEIVER = "cast.getapp"
    const val HOWTO = "cast.howto"

    val SPEC = PageSpec(CastSection.ID, listOf(NAME, NETWORK, CHROMECAST, AIRPLAY, OPEN_AIRPLAY, GET_RECEIVER, HOWTO))

    enum class Kind { INFO, ACTION }

    /** One row of the page, before it becomes a view. [positive] draws the value as a green pill. */
    data class Row(val key: String, val title: String, val value: String, val subtitle: String, val kind: Kind, val positive: Boolean = false)

    fun rows(s: State): List<Row> {
        val out = ArrayList<Row>()
        out += Row(NAME, "TV name", s.tvName ?: UNKNOWN_NAME, "Phones and computers show this name", Kind.INFO)
        out += Row(NETWORK, "Same network", networkStatus(s.onNetwork), NETWORK_HOWTO, Kind.INFO, positive = s.onNetwork)
        out += Row(CHROMECAST, "Chromecast", status(s.castReceiver),
            if (s.castReceiver) "Android phones, Chrome and many apps" else "This TV has no Google Cast receiver",
            Kind.INFO, positive = s.castReceiver)
        out += Row(AIRPLAY, AIRPLAY_LABEL, status(s.airPlay),
            if (s.airPlay) "iPhone, iPad and Mac" else "This TV has no AirPlay receiver",
            Kind.INFO, positive = s.airPlay)
        if (s.airPlay) {
            out += Row(OPEN_AIRPLAY, "Open AirPlay", "", "Switches the TV to its AirPlay screen", Kind.ACTION)
        } else {
            out += Row(GET_RECEIVER, "Get a receiver app", if (s.receiverAppInstalled) "Open" else "",
                "$RECEIVER_APP_NAME plays videos and music sent from phone apps", Kind.ACTION)
        }
        out += Row(HOWTO, "How to cast", "", "Steps for iPhone, Android and computers", Kind.ACTION)
        return out
    }

    /** How-to cards for the sub-page: heading and plain-words steps, AirPlay only when the TV has it. */
    fun howTo(s: State): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        if (s.airPlay) out += "iPhone, iPad and Mac" to IPHONE_HOWTO
        if (s.castReceiver) {
            out += "Android phones and tablets" to ANDROID_HOWTO
            out += "Computers" to COMPUTER_HOWTO
        }
        if (out.isEmpty()) out += "This TV" to "This TV has no built-in receiver. A receiver app such as $RECEIVER_APP_NAME " +
            "can play videos and music sent from phone apps."
        if (!s.airPlay && s.castReceiver) out += "iPhone and iPad" to "This TV has no AirPlay receiver. Apps with a Cast button " +
            "(YouTube, Netflix and many more) can still send video to it."
        return out
    }
}
