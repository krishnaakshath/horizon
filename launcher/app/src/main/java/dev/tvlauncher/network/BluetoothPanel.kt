package dev.tvlauncher.network

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.BouncingDots
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Bluetooth glass panel: on/off row, Connected / Paired / Nearby sections, pairing, and a device
 * action set. The host calls [show], forwards BACK to [onBack] and restores focus to the pill
 * afterwards. Pairing is real (createBond). Connect/disconnect is not public API: after a bond the
 * system connects HID/A2DP itself, and the panel only reports the state it can observe.
 */
class BluetoothPanel(
    private val context: Context,
    private val controller: BluetoothController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private val main = Handler(Looper.getMainLooper())
    private var startedHere = false
    private var latest: BtSnapshot? = null
    private var selected: String? = null
    private var message: String? = null
    private var firstRender = true
    private var lastRenderAt = 0L
    private val listener = BluetoothController.Listener { latest = it; schedule() }
    private val renderRunnable = Runnable { latest?.let { render(it) } }

    /** True from [show] until the close animation has finished. */
    val isShowing: Boolean get() = shell.isVisible

    /** True while open and accepting input (false once the close animation has started). */
    val isOpen: Boolean get() = shell.isOpen

    fun show(host: ViewGroup) {
        if (shell.isOpen) return
        if (!controller.isStarted) { controller.start(); startedHere = true }
        selected = null; message = null; firstRender = true; lastRenderAt = 0L
        shell.show(host)
        controller.addListener(listener)
        if (controller.snapshot.enabled && !controller.snapshot.scanning) controller.startDiscovery()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        detach()
        shell.dismiss()
    }

    /**
     * Immediate teardown, no animation: removes the view, cancels pending renders and discovery,
     * drops the listener and stops the controller if this panel started it. Idempotent. Call from
     * the host's onStop/onDestroy.
     */
    fun release() {
        detach()
        shell.release()
    }

    private fun detach() {
        main.removeCallbacks(renderRunnable)
        controller.removeListener(listener)
        controller.stopDiscovery(publish = false)
        if (startedHere) { controller.stop(); startedHere = false }
    }

    /** BACK leaves the device actions first, then closes the panel; consumed while animating out. */
    fun onBack(): Boolean {
        if (!shell.isOpen) return shell.isVisible
        if (selected != null) {
            selected = null
            latest?.let { render(it) }
        } else {
            dismiss()
        }
        return true
    }

    /** Coalesces bursts (ACTION_FOUND) to at most one rebuild per 250 ms. */
    private fun schedule() {
        main.removeCallbacks(renderRunnable)
        val wait = (lastRenderAt + 250 - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0)
        main.postDelayed(renderRunnable, wait)
    }

    private fun render(s: BtSnapshot) {
        if (!shell.isOpen) return
        lastRenderAt = android.os.SystemClock.uptimeMillis()
        val sections = s.sections
        val sel = selected?.let { a -> sections.all.firstOrNull { it.address.equals(a, true) } }
        if (selected != null && sel == null) selected = null
        if (sel != null) {
            renderDeviceHeader(sel)
            shell.rebuildBody("act:forget") { buildDeviceBody(sel) }
        } else {
            renderHeader(s, sections)
            val content = NetworkPanelState.bluetooth(s.present, s.enabled, s.locationEnabled, s.scanning, sections.all.size)
            shell.rebuildBody(if (firstRender) "act:toggle" else null) { buildBody(s, sections, content) }
        }
        firstRender = false
    }

    private fun headerRow(iconRes: Int, title: String, sub: String): LinearLayout {
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(context).apply {
            setImageResource(iconRes); imageTintList = ColorStateList.valueOf(Theme.TEXT)
        }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(Theme.S3) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            style(22f, Theme.medium); text = title; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = sub },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(2) })
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return top
    }

    private fun renderHeader(s: BtSnapshot, sec: BtSections) {
        shell.header.removeAllViews()
        val state = when {
            !s.enabled -> NetIcons.BtState.OFF
            sec.connected.isNotEmpty() -> NetIcons.BtState.CONNECTED
            else -> NetIcons.BtState.ON
        }
        val sub = when {
            !s.present -> "No Bluetooth on this device"
            !s.enabled -> "Off"
            sec.connected.isNotEmpty() -> "On · ${sec.connected.size} connected"
            else -> "On · nothing connected"
        }
        shell.header.addView(headerRow(NetIcons.forBluetooth(state), "Bluetooth", sub))
    }

    private fun renderDeviceHeader(d: BtDeviceModel) {
        shell.header.removeAllViews()
        val sub = if (d.connected) "Connected" else "Paired · not connected right now"
        shell.header.addView(headerRow(kindIcon(d.kind), d.name, sub))
    }

    private fun buildBody(s: BtSnapshot, sec: BtSections, content: BtContent) {
        val body = shell.body
        if (!s.present) {
            body.addView(PanelViews.note(context, "This device has no Bluetooth adapter."))
            return
        }
        val toggle = PanelRow(context, NetIcons.forBluetooth(if (s.enabled) NetIcons.BtState.ON else NetIcons.BtState.OFF),
            "Bluetooth", if (s.enabled) "On" else "Off", key = "act:toggle")
        toggle.setTrailing(SwitchView(context).apply { checked = s.enabled })
        toggle.setOnClickListener { onToggle(s) }
        body.addView(toggle, rowParams())

        if (s.enabled) {
            val scan = PanelRow(context, R.drawable.ic_net_refresh,
                if (s.scanning) "Stop scanning" else "Scan for devices",
                if (s.scanning) "Looking for devices… stops by itself after 30 s" else "Put the device in pairing mode first",
                key = "act:scan")
            if (s.scanning) scan.setTrailing(BouncingDots(context, Theme.TEXT_2))
            scan.setOnClickListener { if (s.scanning) controller.stopDiscovery() else controller.startDiscovery() }
            body.addView(scan, rowParams())
        }
        val settings = PanelRow(context, R.drawable.ic_settings, "Open Bluetooth settings", null, key = "act:settings")
        settings.setOnClickListener { NetworkSettings.openBluetooth(context) }
        body.addView(settings, rowParams())
        message?.let { body.addView(PanelViews.note(context, it)) }

        if (s.note == BluetoothController.Note.NEEDS_LOCATION) {
            val card = PanelViews.card(context)
            card.addView(TextView(context).apply {
                style(Theme.SP_SUB, Theme.medium); text = "Location permission needed to find devices"
            })
            card.addView(PanelViews.note(context, "Android requires it for Bluetooth discovery. Run this once from a computer, then scan again:"))
            card.addView(PanelViews.note(context, NetworkSettings.ADB_LOCATION_GRANT, mono = true))
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S3) })
        } else if (s.enabled && !s.locationEnabled) {
            val card = PanelViews.card(context)
            card.addView(TextView(context).apply {
                style(Theme.SP_SUB, Theme.medium); text = "Location is switched off"
            })
            card.addView(PanelViews.note(context, "Turn on Location in the TV settings \u2014 Android needs it for nearby device scans, even though this launcher never reads your position."))
            card.addView(PanelViews.note(context, "From a computer instead:"))
            card.addView(PanelViews.note(context, NetworkSettings.ADB_LOCATION_ON, mono = true))
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S3) })
            val open = PanelRow(context, R.drawable.ic_settings, "Open Location settings", null, key = "act:location")
            open.setOnClickListener { NetworkSettings.openLocation(context) }
            body.addView(open, rowParams())
        } else if (s.note == BluetoothController.Note.SCAN_FAILED) {
            body.addView(PanelViews.note(context, "Scanning could not start on this TV. You can still pair from the TV's Bluetooth settings."))
        }

        when (content) {
            BtContent.LOCATION_OFF -> Unit
            BtContent.OFF -> body.addView(PanelViews.note(context, "Bluetooth is off. Switch it on above."))
            BtContent.SCANNING -> body.addView(PanelViews.centered(context,
                BouncingDots(context, Theme.TEXT_2),
                TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = "Looking for devices…" }))
            BtContent.EMPTY -> body.addView(PanelViews.note(context, "No devices yet. Put one in pairing mode, then Scan for devices."))
            else -> {
                section("Connected", sec.connected, s)
                section("Paired", sec.paired, s)
                section("Nearby", sec.nearby, s)
            }
        }
    }

    private fun section(label: String, list: List<BtDeviceModel>, s: BtSnapshot) {
        if (list.isEmpty()) return
        shell.body.addView(PanelViews.sectionLabel(context, label))
        for (d in list) shell.body.addView(deviceRow(d, s), rowParams())
    }

    private fun deviceRow(d: BtDeviceModel, s: BtSnapshot): PanelRow {
        val pair = s.pairing[d.address] ?: PairState.IDLE
        val sub = when {
            d.connected -> "Connected"
            d.bonded -> "Paired"
            pair == PairState.PAIRING -> "Pairing…"
            pair == PairState.PAIRED -> "Paired"
            pair == PairState.FAILED -> "Pairing failed. Press OK to try again"
            else -> "Press OK to pair"
        }
        val row = PanelRow(context, kindIcon(d.kind), d.name, sub, key = "bt:" + d.address)
        when {
            !d.bonded && pair == PairState.PAIRING -> row.setTrailing(BouncingDots(context, Theme.TEXT_2))
            d.connected || pair == PairState.PAIRED -> row.trailingIcon(R.drawable.ic_net_check)
        }
        row.setOnClickListener {
            if (d.bonded) {
                selected = d.address; message = null
                latest?.let { render(it) }
            } else if (pair != PairState.PAIRING) {
                message = null
                val r = controller.pair(d.address)
                if (r == BluetoothController.Result.NOT_PERMITTED) {
                    message = "This TV did not allow pairing from here. Use the TV's Bluetooth settings."
                    latest?.let { render(it) }
                }
            }
        }
        return row
    }

    private fun buildDeviceBody(d: BtDeviceModel) {
        val body = shell.body
        val forget = PanelRow(context, R.drawable.ic_net_close, "Forget ${d.name}", "Remove the pairing", key = "act:forget")
        forget.setOnClickListener {
            when (controller.forget(d.address)) {
                BluetoothController.Result.OK -> { message = "Forgetting ${d.name}\u2026"; selected = null }
                else -> {
                    message = "Forgetting is not allowed here, so the TV's Bluetooth settings were opened."
                    NetworkSettings.openBluetooth(context)
                    selected = null
                }
            }
            latest?.let { render(it) }
        }
        body.addView(forget, rowParams())
        val settings = PanelRow(context, R.drawable.ic_settings, "Open Bluetooth settings", null, key = "act:settings")
        settings.setOnClickListener { NetworkSettings.openBluetooth(context) }
        body.addView(settings, rowParams())
        val back = PanelRow(context, null, "Back to devices", null, key = "act:back")
        back.setOnClickListener { selected = null; latest?.let { render(it) } }
        body.addView(back, rowParams())
        body.addView(PanelViews.note(context,
            "Connecting and disconnecting is handled by the TV: a paired device reconnects on its own when it is switched on. To disconnect it, use the TV's Bluetooth settings."))
    }

    private fun onToggle(s: BtSnapshot) {
        message = null
        // Discovery starts from the controller's STATE_ON broadcast, not a fixed delay.
        val r = controller.setEnabled(!s.enabled, scanWhenReady = true)
        if (r != BluetoothController.Result.OK) {
            message = "This TV does not let apps switch Bluetooth. Use the TV's Bluetooth settings."
            latest?.let { render(it) }
        }
    }

    private fun kindIcon(k: BtKind) = when (k) {
        BtKind.HEADPHONES -> R.drawable.ic_net_kind_headphones
        BtKind.SPEAKER -> R.drawable.ic_net_kind_speaker
        BtKind.GAMEPAD -> R.drawable.ic_net_kind_gamepad
        BtKind.KEYBOARD -> R.drawable.ic_net_kind_keyboard
        BtKind.MOUSE -> R.drawable.ic_net_kind_mouse
        BtKind.PHONE -> R.drawable.ic_net_kind_phone
        BtKind.COMPUTER -> R.drawable.ic_net_kind_computer
        BtKind.WATCH -> R.drawable.ic_net_kind_watch
        BtKind.OTHER -> R.drawable.ic_net_kind_other
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(2) }

    /** Small on/off switch drawn directly (no animation, no layers). */
    private class SwitchView(context: Context) : View(context) {
        var checked = false
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(46), context.dp(26))

        override fun onDraw(canvas: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            paint.color = if (checked) NetColors.SWITCH_ON else NetColors.SWITCH_OFF
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
            paint.color = if (checked) NetColors.SWITCH_THUMB_ON else NetColors.SWITCH_THUMB_OFF
            val r = height / 2f - context.dp(3)
            val cx = if (checked) width - height / 2f else height / 2f
            canvas.drawCircle(cx, height / 2f, r, paint)
        }
    }
}
