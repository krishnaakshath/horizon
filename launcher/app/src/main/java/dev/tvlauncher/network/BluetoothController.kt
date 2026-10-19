package dev.tvlauncher.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

data class BtSnapshot(
    val present: Boolean,
    val enabled: Boolean,
    val scanning: Boolean,
    val bonded: List<BtRaw>,
    val found: List<BtRaw>,
    val connected: Set<String>,
    val pairing: Map<String, PairState>,
    val note: BluetoothController.Note
) {
    val sections: BtSections get() = BtListBuilder.build(bonded, found, connected)
}

/**
 * Bluetooth adapter state, discovery, pairing and connection state. All listener calls are on the
 * main thread. [start] in onStart, [stop] in onStop (cancels discovery, unregisters the receiver,
 * closes every profile proxy). Never throws: operations return a result instead.
 *
 * Platform limits: connect/disconnect of a profile and bond removal are not public API (Forget
 * uses reflection with a settings fallback); enable()/disable() only work on API <= 32 and with
 * BLUETOOTH_ADMIN; battery level is not exposed, so none is shown.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BluetoothController(context: Context) {
    fun interface Listener { fun onChanged(snapshot: BtSnapshot) }
    enum class Note { NONE, NEEDS_LOCATION, SCAN_FAILED }
    enum class Result { OK, NOT_PERMITTED, FAILED }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = try { BluetoothAdapter.getDefaultAdapter() } catch (e: RuntimeException) { null }
    private val listeners = LinkedHashSet<Listener>()
    private val found = LinkedHashMap<String, BtRaw>()
    private val acl = HashSet<String>()
    private val profileConnected = HashMap<Int, Set<String>>()
    private val proxies = HashMap<Int, BluetoothProfile>()
    private val pairing = HashMap<String, PairState>()
    private var receiver: BroadcastReceiver? = null
    private var started = false
    private var scanning = false
    private var note = Note.NONE

    var snapshot: BtSnapshot = build()
        private set
    val isStarted: Boolean get() = started
    val isPresent: Boolean get() = adapter != null

    private val stopScanRunnable = Runnable { stopDiscovery() }

    fun addListener(l: Listener) { listeners.add(l); l.onChanged(snapshot) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun start() {
        if (started) return
        started = true
        registerReceiver()
        openProxies()
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        stopDiscovery(publish = false)
        receiver?.let { try { app.unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        receiver = null
        closeProxies()
        acl.clear()
        found.clear()
    }

    fun hasLocationPermission() =
        app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun startDiscovery(): Result {
        val a = adapter ?: return Result.FAILED
        note = Note.NONE
        if (!isEnabled()) return Result.FAILED
        if (!hasLocationPermission()) { note = Note.NEEDS_LOCATION; publish(); return Result.NOT_PERMITTED }
        return try {
            found.clear()
            if (a.isDiscovering) a.cancelDiscovery()
            if (a.startDiscovery()) {
                scanning = true
                main.removeCallbacks(stopScanRunnable)
                main.postDelayed(stopScanRunnable, SCAN_MS)
                publish(); Result.OK
            } else {
                note = Note.SCAN_FAILED; scanning = false; publish(); Result.FAILED
            }
        } catch (e: SecurityException) {
            note = Note.SCAN_FAILED; scanning = false; publish(); Result.NOT_PERMITTED
        }
    }

    fun stopDiscovery(publish: Boolean = true) {
        main.removeCallbacks(stopScanRunnable)
        try { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() } catch (e: RuntimeException) { }
        scanning = false
        if (publish) publish()
    }

    /** enable()/disable() are deprecated and blocked on newer Android; callers fall back to Settings. */
    fun setEnabled(on: Boolean): Result {
        val a = adapter ?: return Result.FAILED
        if (Build.VERSION.SDK_INT >= 33) return Result.NOT_PERMITTED
        return try {
            val ok = if (on) a.enable() else a.disable()
            if (ok) Result.OK else Result.NOT_PERMITTED
        } catch (e: SecurityException) {
            Result.NOT_PERMITTED
        }
    }

    fun pair(address: String): Result {
        val a = adapter ?: return Result.FAILED
        return try {
            stopDiscovery()
            val d = a.getRemoteDevice(address)
            pairing[address] = PairState.PAIRING
            if (d.createBond()) { publish(); Result.OK } else { pairing[address] = PairState.FAILED; publish(); Result.FAILED }
        } catch (e: SecurityException) {
            pairing[address] = PairState.FAILED; publish(); Result.NOT_PERMITTED
        } catch (e: IllegalArgumentException) {
            pairing[address] = PairState.FAILED; publish(); Result.FAILED
        }
    }

    /** removeBond is a hidden API: reflection, and OK means the call returned true. */
    fun forget(address: String): Result {
        val a = adapter ?: return Result.FAILED
        return try {
            val d = a.getRemoteDevice(address)
            val m = BluetoothDevice::class.java.getMethod("removeBond")
            val ok = m.invoke(d) as? Boolean ?: false
            pairing.remove(address)
            publish()
            if (ok) Result.OK else Result.FAILED
        } catch (e: Exception) {
            Result.NOT_PERMITTED
        }
    }

    // ---- internals ----

    private fun isEnabled() = try { adapter?.isEnabled == true } catch (e: RuntimeException) { false }

    private fun publish() {
        snapshot = build()
        for (l in listeners.toList()) l.onChanged(snapshot)
    }

    private fun build(): BtSnapshot {
        val bonded = try {
            adapter?.bondedDevices.orEmpty().map { raw(it) }
        } catch (e: RuntimeException) { emptyList() }
        val connected = HashSet<String>(acl)
        for (s in profileConnected.values) connected.addAll(s)
        return BtSnapshot(
            present = adapter != null, enabled = isEnabled(), scanning = scanning,
            bonded = bonded, found = found.values.toList(), connected = connected,
            pairing = HashMap(pairing), note = note
        )
    }

    private fun raw(d: BluetoothDevice, extraName: String? = null): BtRaw {
        val name = extraName ?: try { d.name } catch (e: RuntimeException) { null }
        val cls = try { d.bluetoothClass } catch (e: RuntimeException) { null }
        return BtRaw(d.address, name, cls?.majorDeviceClass ?: -1, cls?.deviceClass ?: -1)
    }

    private fun registerReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (st == BluetoothAdapter.STATE_OFF || st == BluetoothAdapter.STATE_TURNING_OFF) {
                            main.removeCallbacks(stopScanRunnable)
                            scanning = false; found.clear(); acl.clear(); profileConnected.clear(); closeProxies()
                        } else if (st == BluetoothAdapter.STATE_ON) {
                            openProxies()
                        }
                    }
                    BluetoothDevice.ACTION_FOUND -> dev?.let {
                        val extra = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        val r2 = raw(it, extra)
                        val old = found[it.address]
                        if (old == null || old.name.isNullOrBlank() || !r2.name.isNullOrBlank()) found[it.address] = r2
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { scanning = false; main.removeCallbacks(stopScanRunnable) }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> dev?.let {
                        val st = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        val prev = pairing[it.address] ?: PairState.IDLE
                        pairing[it.address] = NetworkPanelState.pair(prev, st)
                        if (st == BluetoothDevice.BOND_BONDED) found.remove(it.address)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> dev?.let { acl.add(it.address) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> dev?.let { acl.remove(it.address) }
                }
                publish()
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try { app.registerReceiver(r, f, null, main); receiver = r } catch (e: RuntimeException) { receiver = null }
    }

    private fun openProxies() {
        val a = adapter ?: return
        if (!isEnabled()) return
        for (p in PROFILES) {
            if (proxies.containsKey(p)) continue
            try {
                a.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        main.post {
                            if (!started) { closeQuietly(profile, proxy); return@post }
                            proxies[profile] = proxy
                            profileConnected[profile] = try {
                                proxy.connectedDevices.map { it.address }.toSet()
                            } catch (e: RuntimeException) { emptySet() }
                            publish()
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        main.post { proxies.remove(profile); profileConnected.remove(profile); publish() }
                    }
                }, p)
            } catch (e: RuntimeException) { }
        }
    }

    private fun closeProxies() {
        for ((p, proxy) in proxies.toMap()) closeQuietly(p, proxy)
        proxies.clear()
        profileConnected.clear()
    }

    private fun closeQuietly(profile: Int, proxy: BluetoothProfile) {
        try { adapter?.closeProfileProxy(profile, proxy) } catch (e: RuntimeException) { }
    }

    companion object {
        const val SCAN_MS = 30_000L
        private const val PROFILE_HID_HOST = 4 // hidden constant, same int on all releases
        private val PROFILES = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, PROFILE_HID_HOST)
    }
}
