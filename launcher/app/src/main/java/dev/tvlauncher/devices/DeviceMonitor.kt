package dev.tvlauncher.devices

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
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
 * [start] (HomeActivity.onStart) seeds the current state silently - devices already present do
 * not animate - then registers listeners; [stop] (onStop) unregisters everything. Every source
 * is wrapped so a missing permission or service never crashes the launcher.
 */
class DeviceMonitor(context: Context, private val onEvent: (DeviceEvent) -> Unit) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val registry = DeviceRegistry()
    private val bySource = HashMap<String, List<ConnectedDevice>>()
    private val debouncer = Debouncer(DEBOUNCE_MS) { SystemClock.elapsedRealtime() }
    private val crossSource = CrossSourceDeduper(DEBOUNCE_MS) { SystemClock.elapsedRealtime() }
    private var started = false
    private var seeding = false

    /** Invoked (main thread) after any change to [devices], including silent seeding. */
    var onChanged: (() -> Unit)? = null

    /** Current devices, ordered for display. */
    fun devices(): List<ConnectedDevice> = registry.snapshot()

    // ---- sources' registrations
    private var tvCallback: TvInputManager.TvInputCallback? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var inputListener: InputManager.InputDeviceListener? = null
    private var btReceiver: BroadcastReceiver? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val btDevices = LinkedHashMap<String, ConnectedDevice>()
    private val btProxies = ArrayList<Pair<Int, BluetoothProfile>>()

    fun start() {
        if (started) return
        started = true
        seeding = true
        refreshTvInputs(); refreshUsb(); refreshInput(); seedBluetooth(); refreshNetwork()
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
    }

    // ---- core

    private fun publish(source: String, now: List<ConnectedDevice>) {
        val before = bySource[source].orEmpty()
        val events = DeviceRegistry.diff(before, now)
        bySource[source] = now
        for (e in events) when (e) {
            is DeviceEvent.Connected -> registry.applyConnected(e.device)
            is DeviceEvent.Disconnected -> registry.applyDisconnected(e.device.id)
        }
        // Renames / kind changes: keep the registry current without emitting.
        for (d in now) registry.applyConnected(d)
        if (events.isNotEmpty() || now.size != before.size) onChanged?.invoke()
        if (seeding || !started) return
        for (e in events) {
            if (debouncer.accept(e) && crossSource.accept(source, e)) onEvent(e)
        }
    }

    private inline fun guard(block: () -> Unit) {
        try { block() } catch (e: RuntimeException) { /* permission / service missing: ignore */ }
    }

    private fun unregister(r: BroadcastReceiver?) {
        if (r != null) try { ctx.unregisterReceiver(r) } catch (e: IllegalArgumentException) { }
    }

    private fun postMain(block: () -> Unit) { main.post { if (started) block() } }

    // ---- TV inputs (HDMI CEC devices, composite, component, ...)

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
        val out = ArrayList<ConnectedDevice>()
        guard {
            val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return@guard
            val inputs = tim.tvInputList.filter { it.isPassthroughInput }
            val states = inputs.associate { it.id to tim.getInputState(it.id) }
            fun live(i: TvInputInfo) = (states[i.id] ?: TvInputManager.INPUT_STATE_DISCONNECTED) != TvInputManager.INPUT_STATE_DISCONNECTED
            val labelOf = inputs.associate { it.id to safeLabel(it) }
            // A physical HDMI port whose CEC child device is connected is reported via the child only.
            val parentsWithChild = inputs.filter { live(it) }.mapNotNull { it.parentId }.toSet()
            for (info in inputs) {
                if (!live(info) || info.id in parentsWithChild) continue
                val label = labelOf[info.id].orEmpty()
                val parentLabel = info.parentId?.let { labelOf[it] }
                val port = (parentLabel ?: label).takeIf { it.isNotBlank() }
                val kind = DeviceClassifier.fromTvInputType(info.type, label)
                if (kind == DeviceKind.UNKNOWN) continue
                out.add(ConnectedDevice("tv:" + info.id, label.ifBlank { port ?: "HDMI device" }, kind, port, null))
            }
        }
        publish("tv", out)
    }

    private fun safeLabel(info: TvInputInfo): String = try {
        (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx))?.toString().orEmpty()
    } catch (e: RuntimeException) { "" }

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
                if (kind == DeviceKind.UNKNOWN) kind = DeviceKind.USB_HID_OTHER
                out.add(ConnectedDevice("usb:$key", name.ifEmpty { "USB device" }, kind, "USB", null))
            }
        }
        publish("usb", out)
    }

    // ---- Input devices (gamepads, keyboards, mice; the TV remote is ignored by the classifier)

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
                out.add(ConnectedDevice("input:" + d.descriptor, d.name.orEmpty().ifBlank { DeviceCopy.label(kind) }, kind, null, null))
            }
        }
        publish("input", out)
    }

    // ---- Bluetooth (battery level is not reliably readable on TV builds, so it is never shown)

    private fun registerBluetooth() = guard {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val dev = bluetoothDevice(i) ?: return
                when (i.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> btDevices[dev.address] = toDevice(dev)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> btDevices.remove(dev.address)
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                        if (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) == BluetoothDevice.BOND_NONE) btDevices.remove(dev.address)
                        else return
                    else -> return
                }
                publish("bt", btDevices.values.toList())
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
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
        try {
            name = dev.name.orEmpty()
            val cls = dev.bluetoothClass
            kind = DeviceClassifier.fromBluetooth(cls?.majorDeviceClass ?: 0, cls?.deviceClass ?: 0, name)
        } catch (e: SecurityException) { }
        return ConnectedDevice("bt:" + dev.address, name.ifBlank { DeviceCopy.label(kind) }, kind, "Bluetooth", null)
    }

    /** Devices already connected over A2DP/headset when we start (HID pads come via InputManager). */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun seedBluetooth() = guard {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                try {
                    if (!started) return
                    var changed = false
                    for (d in proxy.connectedDevices) {
                        if (!btDevices.containsKey(d.address)) { btDevices[d.address] = toDevice(d); changed = true }
                    }
                    if (changed) {
                        // Silent: these were already connected before start().
                        val wasSeeding = seeding
                        seeding = true
                        publish("bt", btDevices.values.toList())
                        seeding = wasSeeding
                    }
                } catch (e: RuntimeException) { }
                btProxies.add(profile to proxy)
            }
            override fun onServiceDisconnected(profile: Int) { btProxies.removeAll { it.first == profile } }
        }
        adapter.getProfileProxy(ctx, listener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(ctx, listener, BluetoothProfile.HEADSET)
    }

    @Suppress("DEPRECATION")
    private fun closeBtProxies() {
        val adapter = try { BluetoothAdapter.getDefaultAdapter() } catch (e: RuntimeException) { null }
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
        publish("net", out)
    }

    /** SSID only when readable without location permission; otherwise null. */
    @Suppress("DEPRECATION")
    private fun ssidOrNull(): String? = try {
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = wm?.connectionInfo?.ssid?.trim('"')
        if (raw.isNullOrBlank() || raw == WifiManager.UNKNOWN_SSID) null else raw
    } catch (e: RuntimeException) { null }

    companion object {
        const val DEBOUNCE_MS = 1500L
    }
}
