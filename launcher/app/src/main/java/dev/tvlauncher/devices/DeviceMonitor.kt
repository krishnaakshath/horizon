package dev.tvlauncher.devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice

/**
 * Watches everything that can be attached to the TV (HDMI/AV inputs, USB, input devices,
 * Bluetooth, network) and reports changes as [DeviceEvent]s on the main thread.
 *
 * [start] (HomeActivity.onStart) seeds the current state silently - devices already present or
 * bonded do not animate - then registers listeners; [stop] (onStop) unregisters everything and
 * forgets all state. Every source is wrapped so a missing permission, service or API level never
 * crashes the launcher.
 */
class DeviceMonitor(context: Context, private val onEvent: (DeviceEvent) -> Unit) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
    private val registry = DeviceRegistry()
    private val bySource = HashMap<String, List<ConnectedDevice>>()
    private var debouncer = Debouncer(DEBOUNCE_MS, clock)
    private var dedupe = CrossSourceDedupe(6000L, clock)
    private val remoteFilter = RemoteFilter(clock)
    private val vidPidById = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > RemoteFilter.MAX_TRACKED
    }
    private val tracker = HdmiPortTracker(clock)
    private var netPolicy = NetworkEventPolicy(3000L, clock)
    private val anonymousBt = HashSet<String>()
    private var started = false
    private var seeding = false

    /** Invoked (main thread) after any change to [devices], including silent seeding. */
    var onChanged: (() -> Unit)? = null

    /** Current devices, ordered for display. */
    fun devices(): List<ConnectedDevice> = registry.snapshot()

    private var tvCallback: TvInputManager.TvInputCallback? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var inputListener: InputManager.InputDeviceListener? = null
    private var btReceiver: BroadcastReceiver? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val btDevices = LinkedHashMap<String, ConnectedDevice>()
    private val btProxies = ArrayList<Pair<Int, BluetoothProfile>>()

    private val tick = Runnable {
        if (started) {
            emit("tv", tracker.tick())
            emit("net", netPolicy.tick())
            scheduleTick()
        }
    }

    fun start() {
        if (started) return
        started = true
        seeding = true
        refreshTvInputs(); refreshUsb(); refreshInput(); seedBluetooth(); refreshNetwork()
        for (src in listOf("usb", "input", "bt")) bySource[src].orEmpty().forEach { remoteFilter.seed(it.id, vidPidById[it.id], it.kind) }
        seeding = false
        registerTv(); registerUsb(); registerInput(); registerBluetooth(); registerNetwork()
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacksAndMessages(null)
        guard {
            tvCallback?.let { (ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.unregisterCallback(it) }
        }
        tvCallback = null
        unregister(usbReceiver); usbReceiver = null
        unregister(btReceiver); btReceiver = null
        guard { inputListener?.let { (ctx.getSystemService(Context.INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(it) } }
        inputListener = null
        guard { netCallback?.let { (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it) } }
        netCallback = null
        closeBtProxies()
        // Forget everything so nothing stale survives a stop()/start() cycle.
        btDevices.clear(); bySource.clear(); registry.clear(); anonymousBt.clear(); vidPidById.clear()
        remoteFilter.reset(); tracker.reset(); netPolicy = NetworkEventPolicy(3000L, clock)
        debouncer = Debouncer(DEBOUNCE_MS, clock); dedupe = CrossSourceDedupe(6000L, clock)
        onChanged?.invoke()
    }

    // ---- core

    /** Updates the registry for [source] and returns the id-level diff (no emission). */
    private fun apply(source: String, now: List<ConnectedDevice>): List<DeviceEvent> {
        val before = bySource[source].orEmpty()
        val events = DeviceRegistry.diff(before, now)
        bySource[source] = now
        for (e in events) {
            if (e is DeviceEvent.Disconnected) registry.applyDisconnected(e.device.id)
        }
        for (d in now) registry.applyConnected(d)
        if (events.isNotEmpty() || now.size != before.size || now != before) onChanged?.invoke()
        return events
    }

    /** Applies debounce, remote filtering and cross-source dedupe, then delivers. */
    private fun emit(source: String, events: List<DeviceEvent>) {
        if (seeding || !started) return
        for (e in events) {
            if (!debouncer.accept(e)) continue
            if (source == "usb" || source == "input" || source == "bt") {
                val d = e.device
                val cand = Candidate(DeviceSource.of(d.id), d.id, d.name, d.kind, d.id in anonymousBt, vidPidById[d.id])
                if (!remoteFilter.shouldAnnounce(cand, e !is DeviceEvent.Disconnected)) continue
                if (!dedupe.accept(source, e, vidPidById[d.id])) continue
            }
            onEvent(e)
        }
    }

    private fun scheduleTick() {
        main.removeCallbacks(tick)
        val due = listOfNotNull(tracker.nextDueDelayMs(), netPolicy.nextDueDelayMs()).minOrNull() ?: return
        main.postDelayed(tick, due + 20L)
    }

    private inline fun guard(block: () -> Unit) {
        try { block() } catch (t: Throwable) { if (t is OutOfMemoryError) throw t }
    }

    private fun unregister(r: BroadcastReceiver?) {
        if (r != null) try { ctx.unregisterReceiver(r) } catch (e: IllegalArgumentException) { }
    }

    private fun postMain(block: () -> Unit) { main.post { if (started) block() } }

    // ---- TV inputs (HDMI ports + CEC devices, composite, component, ...)

    private fun registerTv() = guard {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return
        val cb = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) = refreshTvInputs()
            override fun onInputAdded(inputId: String) = refreshTvInputs()
            override fun onInputRemoved(inputId: String) = refreshTvInputs()
            override fun onInputUpdated(inputId: String) = refreshTvInputs()
        }
        tim.registerCallback(cb, main)
        tvCallback = cb
    }

    private fun refreshTvInputs() {
        val snaps = ArrayList<InputSnapshot>()
        guard {
            val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return@guard
            for (info in tim.tvInputList) {
                if (!info.isPassthroughInput) continue
                val state = try { tim.getInputState(info.id) } catch (e: RuntimeException) { InputSnapshot.STATE_DISCONNECTED }
                snaps.add(InputSnapshot(info.id, parentIdOf(info), labelOf(info), info.type, state))
            }
        }
        val events = tracker.update(snaps, emit = started && !seeding)
        apply("tv", tracker.devices())
        emit("tv", events)
        if (started) scheduleTick()
    }

    /** getParentId is API 26; below that the tracker derives the parent from the id string. */
    private fun parentIdOf(info: TvInputInfo): String? =
        if (Build.VERSION.SDK_INT >= 26) try { info.parentId } catch (t: Throwable) { null } else null

    private fun labelOf(info: TvInputInfo): String = try {
        val custom = if (Build.VERSION.SDK_INT >= 24) info.loadCustomLabel(ctx) else null
        (custom ?: info.loadLabel(ctx))?.toString().orEmpty()
    } catch (t: Throwable) { "" }

    // ---- USB

    private fun registerUsb() = guard {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = refreshUsb()
        }
        val f = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ctx.registerReceiver(r, f, null, main)
        usbReceiver = r
    }

    private fun refreshUsb() {
        val out = ArrayList<ConnectedDevice>()
        guard {
            val um = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@guard
            for ((key, dev) in um.deviceList) {
                if (dev.deviceClass == 9) continue // hubs
                val product = dev.productName?.trim().orEmpty()
                val maker = dev.manufacturerName?.trim().orEmpty()
                val name = listOf(maker, product).filter { it.isNotEmpty() }.joinToString(" ")
                var kind = DeviceKind.UNKNOWN
                if (dev.deviceClass != 0) kind = DeviceClassifier.fromUsb(dev.deviceClass, dev.deviceSubclass, name)
                var i = 0
                while (kind == DeviceKind.UNKNOWN && i < dev.interfaceCount) {
                    val itf = dev.getInterface(i++)
                    kind = DeviceClassifier.fromUsb(itf.interfaceClass, itf.interfaceSubclass, name)
                }
                // Anything else stays UNKNOWN (non-HID): it is announced and never hit by the remote filter.
                RemoteFilter.vidPidOf(dev.vendorId, dev.productId)?.let { vidPidById["usb:$key"] = it }
                out.add(ConnectedDevice("usb:$key", name.ifEmpty { "USB device" }, kind, "USB", null))
            }
        }
        emit("usb", apply("usb", out))
    }

    // ---- Input devices (gamepads, keyboards, mice; remotes are filtered)

    private fun registerInput() = guard {
        val im = ctx.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refreshInput()
            override fun onInputDeviceRemoved(deviceId: Int) = refreshInput()
            override fun onInputDeviceChanged(deviceId: Int) = refreshInput()
        }
        im.registerInputDeviceListener(l, main)
        inputListener = l
    }

    private fun refreshInput() {
        val out = ArrayList<ConnectedDevice>()
        guard {
            for (id in InputDevice.getDeviceIds()) {
                if (id < 0) continue
                val d = InputDevice.getDevice(id) ?: continue
                // InputDevice.isExternal() is hidden; an alphabetic keyboard is the practical equivalent.
                val external = d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                val kind = DeviceClassifier.fromInputDevice(d.sources, d.name, external, d.isVirtual)
                if (kind == DeviceKind.UNKNOWN) continue
                RemoteFilter.vidPidOf(d.vendorId, d.productId)?.let { vidPidById["input:" + d.descriptor] = it }
                out.add(ConnectedDevice("input:" + d.descriptor, d.name.orEmpty().ifBlank { DeviceCopy.label(kind) }, kind, null, null))
            }
        }
        emit("input", apply("input", out))
    }

    // ---- Bluetooth (battery level is not reliably readable on TV builds, so it is never shown)

    private fun registerBluetooth() = guard {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                if (i.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    // Adapter switched on after start: re-seed bonded/connected devices silently.
                    if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) seedBluetooth()
                    return
                }
                val dev = bluetoothDevice(i) ?: return
                when (i.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> btDevices[dev.address] = toDevice(dev)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> btDevices.remove(dev.address)
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                        if (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_NONE) {
                            btDevices.remove(dev.address)
                        } else return
                    else -> return
                }
                emit("bt", apply("bt", btDevices.values.toList()))
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ctx.registerReceiver(r, f, null, main)
        btReceiver = r
    }

    @Suppress("DEPRECATION")
    private fun bluetoothDevice(i: Intent): BluetoothDevice? = try {
        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    } catch (e: RuntimeException) { null }

    @Suppress("MissingPermission")
    private fun toDevice(dev: BluetoothDevice): ConnectedDevice {
        var name = ""
        var kind = DeviceKind.BT_OTHER
        var classNull = true
        try {
            name = dev.name.orEmpty()
            val cls = dev.bluetoothClass
            classNull = cls == null
            kind = DeviceClassifier.fromBluetooth(cls?.majorDeviceClass ?: 0, cls?.deviceClass ?: 0, name)
        } catch (e: SecurityException) { }
        val id = "bt:" + dev.address
        if (classNull && name.isBlank()) anonymousBt.add(id) else anonymousBt.remove(id)
        return ConnectedDevice(id, name.ifBlank { DeviceCopy.label(kind) }, kind, "Bluetooth", null)
    }

    /**
     * Seeds every bonded device as "known" (so reconnects of remotes/keyboards stay silent) and the
     * currently connected A2DP / headset / HID-host devices into the panel without any card.
     * PROFILE 4 is BluetoothProfile.INPUT_DEVICE (HID host).
     */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun seedBluetooth() = guard {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        // Bonded devices are readable on most stacks even while the adapter is off.
        guard { adapter.bondedDevices?.forEach { remoteFilter.seed("bt:" + it.address) } }
        if (!adapter.isEnabled) return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (!started) { guard { adapter.closeProfileProxy(profile, proxy) }; return }
                btProxies.add(profile to proxy)
                guard {
                    var changed = false
                    for (d in proxy.connectedDevices) {
                        remoteFilter.seed("bt:" + d.address)
                        if (!btDevices.containsKey(d.address)) { btDevices[d.address] = toDevice(d); changed = true }
                    }
                    if (changed) apply("bt", btDevices.values.toList()) // silent: connected before start()
                }
            }
            override fun onServiceDisconnected(profile: Int) { btProxies.removeAll { it.first == profile } }
        }
        adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(ctx, listener, BluetoothProfile.HEADSET)
        adapter.getProfileProxy(ctx, listener, 4)
    }

    @Suppress("DEPRECATION")
    private fun closeBtProxies() {
        val adapter = try { BluetoothAdapter.getDefaultAdapter() } catch (t: Throwable) { null }
        for ((profile, proxy) in btProxies) guard { adapter?.closeProfileProxy(profile, proxy) }
        btProxies.clear()
    }

    // ---- Network

    private fun registerNetwork() = guard {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { postMain { refreshNetwork() } }
            override fun onLost(network: Network) { postMain { refreshNetwork() } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { postMain { refreshNetwork() } }
        }
        cm.registerDefaultNetworkCallback(cb) // API 24
        netCallback = cb
    }

    private fun refreshNetwork() {
        val out = ArrayList<ConnectedDevice>()
        guard {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@guard
            val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return@guard) ?: return@guard
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    out.add(ConnectedDevice("net:eth", "Ethernet", DeviceKind.NETWORK_ETHERNET, null, null))
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    out.add(ConnectedDevice("net:wifi", "Wi-Fi", DeviceKind.NETWORK_WIFI, null, ssidOrNull()))
            }
        }
        val events = apply("net", out)
        if (seeding || !started) return
        emit("net", netPolicy.submitAll(events))
        scheduleTick()
    }

    /** SSID only when readable without location permission; otherwise null. */
    @Suppress("DEPRECATION")
    private fun ssidOrNull(): String? = try {
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = wm?.connectionInfo?.ssid?.trim('"')
        if (raw.isNullOrBlank() || raw == WifiManager.UNKNOWN_SSID) null else raw
    } catch (t: Throwable) { null }

    companion object {
        const val DEBOUNCE_MS = 1500L
    }
}
