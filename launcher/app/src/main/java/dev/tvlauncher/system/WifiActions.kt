package dev.tvlauncher.system

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The legacy WifiManager calls the launcher uses to run Wi-Fi itself: switch on/off, join (open,
 * WEP, WPA/WPA2, WPA3 where the TV supports it), rejoin a saved network, forget, reconnect.
 *
 * These calls only work because :app targets API 28: from targetSdk 29 they always fail for
 * ordinary apps (see app/lint.xml). Platform limits that remain on Android 10/11:
 *  - forget (removeNetwork) succeeds only for networks the launcher saved itself; networks saved
 *    by the TV's own settings report [Result.NOT_OWNER];
 *  - joining a network the TV saved works (enableNetwork is allowed for any saved network), but a
 *    new password for it cannot be stored from here.
 * Every call is guarded; nothing here throws.
 */
@Suppress("DEPRECATION")
class WifiActions(context: Context) {
    enum class Result { OK, NOT_OWNER, REFUSED }

    data class Saved(val networkId: Int, val ssid: String)

    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    val available: Boolean get() = wm != null

    fun isEnabled(): Boolean = safe(false) { wm?.isWifiEnabled == true }

    fun setEnabled(on: Boolean): Boolean = !MonkeyGuard.blocks() && safe(false) { wm?.setWifiEnabled(on) == true }

    /** Networks saved on this TV (needs location access; empty otherwise). */
    @SuppressLint("MissingPermission")
    fun saved(): List<Saved> = safe(emptyList()) {
        wm?.configuredNetworks.orEmpty().mapNotNull { c ->
            val ssid = c.SSID?.trim('"')?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Saved(c.networkId, ssid)
        }
    }

    fun savedId(ssid: String): Int? = saved().firstOrNull { it.ssid == ssid }?.networkId

    /** Adds [c] (or rejoins the TV's saved copy when the system will not let us replace it) and connects. */
    fun join(c: WifiCredential): Result {
        if (MonkeyGuard.blocks()) return Result.REFUSED
        val m = wm ?: return Result.REFUSED
        val config = toConfig(c)
        var id = safe(-1) { m.addNetwork(config) }
        if (id == -1) id = savedId(c.ssid) ?: return Result.REFUSED
        return connect(id)
    }

    /** Connects to a saved network by id (also works for networks the TV saved). */
    fun connect(networkId: Int): Result {
        if (MonkeyGuard.blocks()) return Result.REFUSED
        val m = wm ?: return Result.REFUSED
        val ok = safe(false) { m.enableNetwork(networkId, true) }
        if (!ok) return Result.REFUSED
        safe(false) { m.reconnect() }
        return Result.OK
    }

    fun forget(networkId: Int): Result {
        if (MonkeyGuard.blocks()) return Result.REFUSED
        val m = wm ?: return Result.REFUSED
        return if (safe(false) { m.removeNetwork(networkId) }) Result.OK else Result.NOT_OWNER
    }

    fun reconnect(): Boolean = safe(false) { wm?.reconnect() == true }

    private fun toConfig(c: WifiCredential) = WifiConfiguration().apply {
        SSID = c.quotedSsid
        hiddenSSID = c.hidden
        when (c.keyMgmt) {
            KeyMgmt.NONE -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            KeyMgmt.WEP -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                wepKeys[0] = c.wepKey
                wepTxKeyIndex = 0
            }
            KeyMgmt.WPA_PSK -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                preSharedKey = c.psk
            }
            KeyMgmt.SAE, KeyMgmt.OWE -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    setSecurityParams(if (c.keyMgmt == KeyMgmt.SAE) WifiConfiguration.SECURITY_TYPE_SAE else WifiConfiguration.SECURITY_TYPE_OWE)
                } else {
                    allowedKeyManagement.set(c.keyMgmt.bit)
                }
                if (c.keyMgmt == KeyMgmt.SAE) preSharedKey = c.psk
            }
        }
    }
}

/**
 * Watches one join attempt ([JoinTracker]) through the supplicant broadcasts and a 1 s tick.
 * [currentSsid] reads the connected SSID; [inRange] whether the latest scan lists the network.
 * [onOutcome] runs once on the main thread. [cancel] stops everything; idempotent.
 */
@Suppress("DEPRECATION")
class WifiJoinSession(
    context: Context,
    private val currentSsid: () -> String?,
    private val inRange: (String) -> Boolean,
    private val onOutcome: (String, JoinOutcome) -> Unit
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val tracker = JoinTracker()
    private var receiver: BroadcastReceiver? = null

    val active: Boolean get() = tracker.active
    val target: String? get() = tracker.target.takeIf { tracker.active }

    private val tick = object : Runnable {
        override fun run() {
            if (!tracker.active) return
            val t = tracker.target ?: return
            val o = tracker.onConnection(currentSsid()) ?: tracker.onTick(SystemClock.elapsedRealtime(), inRange(t))
            if (o != null) finish(o) else main.postDelayed(this, 1000)
        }
    }

    fun start(ssid: String) {
        cancel()
        tracker.start(ssid, SystemClock.elapsedRealtime())
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != WifiManager.SUPPLICANT_STATE_CHANGED_ACTION) return
                val err = i.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1)
                val state = try { i.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)?.name } catch (e: RuntimeException) { null }
                val o = (if (err >= 0) tracker.onSupplicantError(err) else null) ?: state?.let { tracker.onSupplicantState(it) }
                if (o != null) finish(o)
            }
        }
        receiver = try {
            app.registerReceiver(r, IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION), null, main); r
        } catch (e: RuntimeException) { null }
        main.postDelayed(tick, 1000)
    }

    /** The system refused the request outright. */
    fun refused() {
        val t = tracker.target ?: return
        val o = tracker.refused()
        stop(); onOutcome(t, o)
    }

    fun cancel() {
        tracker.cancel()
        stop()
    }

    private fun finish(o: JoinOutcome) {
        val t = tracker.target ?: return
        stop()
        onOutcome(t, o)
    }

    private fun stop() {
        main.removeCallbacks(tick)
        receiver?.let { try { app.unregisterReceiver(it) } catch (e: RuntimeException) { } }
        receiver = null
    }
}

internal inline fun <T> safe(fallback: T, block: () -> T): T = try {
    block()
} catch (t: Throwable) {
    if (t is OutOfMemoryError) throw t
    fallback
}
