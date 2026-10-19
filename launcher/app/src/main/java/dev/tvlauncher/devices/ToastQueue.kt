package dev.tvlauncher.devices

/** Pure queue behind the toast host: at most [max] pending, coalesced per device id. */
class ToastQueue(private val max: Int = 3) {
    enum class Offer { QUEUED, IGNORED, PREEMPT }

    var current: DeviceEvent? = null
        private set
    private val pending = ArrayList<DeviceEvent>()
    val pendingSize: Int get() = pending.size

    fun offer(e: DeviceEvent): Offer {
        val cur = current
        if (cur != null && cur.device.id == e.device.id) {
            if (cur::class == e::class) return Offer.IGNORED
            pending.removeAll { it.device.id == e.device.id }
            pending.add(0, e)
            return Offer.PREEMPT
        }
        pending.removeAll { it.device.id == e.device.id }
        pending.add(e)
        while (pending.size > max) pending.removeAt(0)
        return Offer.QUEUED
    }

    /** Moves to the next pending event (null when empty) and makes it current. */
    fun advance(): DeviceEvent? {
        current = if (pending.isEmpty()) null else pending.removeAt(0)
        return current
    }
}
