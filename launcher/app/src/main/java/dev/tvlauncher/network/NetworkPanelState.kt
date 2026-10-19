package dev.tvlauncher.network

enum class WifiContent { PERMISSION_MISSING, WIFI_OFF, LIST, SCANNING, EMPTY }
enum class BtContent { NO_ADAPTER, OFF, LIST, SCANNING, EMPTY }
enum class PairState { IDLE, PAIRING, PAIRED, FAILED }

/** Pure reducers that decide what a panel body shows. */
object NetworkPanelState {
    fun wifi(hasLocationPermission: Boolean, wifiEnabled: Boolean, scanning: Boolean, networkCount: Int) = when {
        !hasLocationPermission -> WifiContent.PERMISSION_MISSING
        !wifiEnabled -> WifiContent.WIFI_OFF
        networkCount > 0 -> WifiContent.LIST
        scanning -> WifiContent.SCANNING
        else -> WifiContent.EMPTY
    }

    fun bluetooth(present: Boolean, enabled: Boolean, scanning: Boolean, deviceCount: Int) = when {
        !present -> BtContent.NO_ADAPTER
        !enabled -> BtContent.OFF
        deviceCount > 0 -> BtContent.LIST
        scanning -> BtContent.SCANNING
        else -> BtContent.EMPTY
    }

    const val BOND_NONE = 10
    const val BOND_BONDING = 11
    const val BOND_BONDED = 12

    /** Folds a BluetoothDevice bond-state (10/11/12) into the pairing UI state. */
    fun pair(prev: PairState, bondState: Int): PairState = when (bondState) {
        BOND_BONDING -> PairState.PAIRING
        BOND_BONDED -> PairState.PAIRED
        else -> if (prev == PairState.PAIRING) PairState.FAILED else PairState.IDLE
    }
}

/** Android allows 4 foreground Wi-Fi scans per 2 minutes; this mirrors it so the UI can say so. */
class ScanThrottle(private val max: Int = 4, private val windowMs: Long = 120_000L) {
    private val stamps = ArrayDeque<Long>()

    private fun prune(now: Long) { while (stamps.isNotEmpty() && now - stamps.first() >= windowMs) stamps.removeFirst() }

    fun tryAcquire(now: Long): Boolean {
        prune(now)
        if (stamps.size >= max) return false
        stamps.addLast(now)
        return true
    }

    fun retryAfterSeconds(now: Long): Int {
        prune(now)
        if (stamps.size < max) return 0
        return (((stamps.first() + windowMs - now) + 999) / 1000).toInt()
    }
}
