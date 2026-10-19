package dev.tvlauncher.devices

/**
 * Pure home-screen rules on top of DeviceMonitor's own filtering: which events get a toast card
 * and which sound plays with it.
 * - Wi-Fi network events are not "devices": never a card. Ethernet only for plug/unplug.
 * - Nothing while the splash runs or within [QUIET_AFTER_START_MS] of DeviceMonitor.start
 *   (seed storms on wake), and never for the TV's built-in devices ([BuiltInDevices]).
 * - Connect (and Updated) plays CONNECT, disconnect plays DISCONNECT, a coalesced card plays nothing.
 */
object DeviceAnnouncePolicy {
    const val QUIET_AFTER_START_MS = 5_000L

    enum class Sound { CONNECT, DISCONNECT, NONE }

    fun showCard(e: DeviceEvent, msSinceStart: Long, splashRunning: Boolean): Boolean {
        if (splashRunning || msSinceStart < QUIET_AFTER_START_MS) return false
        if (BuiltInDevices.isTvInternal(e.device.name)) return false
        return when (e.device.kind) {
            DeviceKind.NETWORK_WIFI -> false
            DeviceKind.NETWORK_ETHERNET -> e !is DeviceEvent.Updated
            else -> true
        }
    }

    private fun connects(e: DeviceEvent) = e !is DeviceEvent.Disconnected

    /**
     * [prior] is the card already shown or queued for the same device (ToastQueue.eventFor, read
     * before offering). A new card is one that is neither ignored nor folded into a same-direction
     * card for that device.
     */
    fun isNewCard(e: DeviceEvent, prior: DeviceEvent?, offer: ToastQueue.Offer): Boolean = when {
        offer == ToastQueue.Offer.IGNORED -> false
        prior == null -> true
        else -> connects(prior) != connects(e)
    }

    fun sound(e: DeviceEvent, newCard: Boolean): Sound = when {
        !newCard -> Sound.NONE
        e is DeviceEvent.Disconnected -> Sound.DISCONNECT
        else -> Sound.CONNECT
    }
}
