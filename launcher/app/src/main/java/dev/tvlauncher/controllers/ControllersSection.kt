package dev.tvlauncher.controllers

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.input.GamepadSettings
import dev.tvlauncher.input.GamepadSettingsSource
import dev.tvlauncher.input.PadVibration
import dev.tvlauncher.ui.dp

/**
 * Settings > System > Controllers: which game controllers are connected, a live test, and the
 * few choices that matter (stick sensitivity, A/B swap, the Guide button, vibration). Pairing
 * stays in the home screen's Bluetooth panel.
 */
object ControllersSection : SettingsSection, SpecifiedPage {
    const val ID = "controllers"
    override val id = ID
    override val title = "Controllers"
    override val iconRes = R.drawable.ic_dev_controller
    override val group = SettingsGroup.SYSTEM
    override val description = "Game controllers work everywhere the remote does."

    const val CONNECTED = "controllers.connected"
    const val TEST = "controllers.test"
    const val SENSITIVITY = "controllers.sensitivity"
    const val SWAP = "controllers.swap"
    const val GUIDE = "controllers.guide"
    const val VIBRATE = "controllers.vibrate"
    const val BUTTONS = "controllers.buttons"

    override val spec = PageSpec(ID, listOf(CONNECTED, TEST, SENSITIVITY, SWAP, GUIDE, VIBRATE, BUTTONS))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val src = GamepadSettingsSource.get(context)
        val s = src.current()
        val pads = ControllerList.scan()
        val page = SettingsPage(context, nav.style, spec)
        page.row(CONNECTED, "Connected", RowKind.Info(ControllerList.value(pads)),
            subtitle = ControllerList.subtitle(pads), iconRes = R.drawable.ic_dev_controller)
        page.row(TEST, "Test controller", RowKind.Action {
            nav.push("Test controller") { c, n -> testPage(c, n) }
        }, subtitle = "See every button and stick respond", iconRes = R.drawable.ic_set_check)
        page.row(SENSITIVITY, "Stick sensitivity", RowKind.Choice(GamepadSettings.SENSITIVITY_LABELS, s.sensitivity) { i ->
            src.update { it.copy(sensitivity = i) }
        }, subtitle = "How far to tilt the stick to move", iconRes = R.drawable.ic_set_tune)
        lateinit var legend: dev.tvlauncher.appsettings.SettingsRow
        page.row(SWAP, "Swap A and B", RowKind.Toggle(s.swapAB) { on ->
            src.update { it.copy(swapAB = on) }
            legend.setSubtitle(ControllerList.legend(on))
        }, subtitle = "Nintendo layout: B selects, A goes back", iconRes = R.drawable.ic_set_reset)
        page.row(GUIDE, "Guide button opens Home", RowKind.Toggle(s.guideHome) { on ->
            src.update { it.copy(guideHome = on) }
        }, subtitle = "Xbox, PS or Home button; in other apps too while the Helper is on", iconRes = R.drawable.ic_set_home)
        page.row(VIBRATE, "Vibrate on focus move", RowKind.Toggle(s.vibrate) { on ->
            src.update { it.copy(vibrate = on) }
            if (on) pads.filter { it.vibrates }.forEach { PadVibration.pulse(it.id) }
        }, subtitle = ControllerList.vibrationSubtitle(pads), iconRes = R.drawable.ic_set_motion)
        legend = page.row(BUTTONS, "Buttons", RowKind.Info(), subtitle = ControllerList.legend(s.swapAB), iconRes = R.drawable.ic_set_about)
        watchDevices(page, nav)
        return page
    }

    /** Re-reads the list when a controller connects or disconnects while the page is open. */
    private fun watchDevices(page: SettingsPage, nav: SettingsNav) {
        val im = page.context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return
        val main = Handler(Looper.getMainLooper())
        val refresh = Runnable { nav.refresh() }
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = later()
            override fun onInputDeviceRemoved(deviceId: Int) = later()
            override fun onInputDeviceChanged(deviceId: Int) {}
            private fun later() { main.removeCallbacks(refresh); main.postDelayed(refresh, 400) }
        }
        try {
            im.registerInputDeviceListener(l, main)
        } catch (e: RuntimeException) {
            return
        }
        page.doOnDetach {
            main.removeCallbacks(refresh)
            try { im.unregisterInputDeviceListener(l) } catch (e: RuntimeException) { }
        }
    }

    /** The live controller drawing; holding B (or the remote's BACK) returns to the page. */
    private fun testPage(context: Context, nav: SettingsNav): View {
        val box = FrameLayout(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(24)
            setPadding(pad, context.dp(8), pad, pad)
        }
        val view = ControllerTestView(context, nav.style.accent) { nav.pop() }
        col.addView(view, LinearLayout.LayoutParams(-1, -2))
        box.addView(col, FrameLayout.LayoutParams(-1, -2))
        view.post { view.requestFocus() }
        return box
    }
}
