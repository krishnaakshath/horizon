package dev.tvlauncher.devices

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeaderView
import dev.tvlauncher.panel.PanelList
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.dp

/**
 * The Devices list as an embeddable page (Settings > Devices): the same header and rows as the
 * home screen's Devices panel ([DevicesPanelModel]), on the shared panel surface. Call
 * [setDevices] with DeviceMonitor.devices() whenever it changes. TV-input rows switch to that
 * input on OK; a failure shows inline and is reported to [onSwitchResult].
 */
class DevicesPanel(context: Context) : ScrollView(context) {
    private val header = PanelHeaderView(context)
    private val list = PanelList(context)
    private var devices: List<ConnectedDevice> = emptyList()
    private var notice: String? = null

    /** Called after a switch attempt; false means no handler could open the input. */
    var onSwitchResult: ((Boolean) -> Unit)? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        background = PanelStyle.surface(context, lite = false)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(PanelStyle.G1), context.dp(PanelStyle.G2), context.dp(PanelStyle.G1), context.dp(PanelStyle.G1))
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(PanelStyle.G1) })
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        list.onRowClick = ::onRow
        setDevices(emptyList())
    }

    fun setDevices(devices: List<ConnectedDevice>) {
        this.devices = devices
        render()
    }

    private fun render() {
        val c: PanelContent = DevicesPanelModel.build(devices, notice)
        header.bind(c.header)
        val focusedKey = (findFocus() as? dev.tvlauncher.panel.PanelRowView)?.key
        if (list.render(c.entries) && focusedKey != null) {
            val rows = list.rowViews()
            (rows.firstOrNull { it.key == focusedKey } ?: rows.firstOrNull())?.requestFocus()
        }
    }

    private fun onRow(row: PanelEntry.Row) {
        if (row.key == DevicesPanelModel.KEY_SETTINGS) { SystemActions.launcherSettings(context, "devices"); return }
        val d = devices.firstOrNull { DevicesPanelModel.PREFIX + it.id == row.key } ?: return
        val inputId = DeviceActions.passthroughInputId(d) ?: return
        val ok = switchTo(context, inputId)
        notice = if (ok) null else "Couldn't switch to ${row.label}."
        render()
        onSwitchResult?.invoke(ok)
    }

    companion object {
        fun switchTo(context: Context, inputId: String): Boolean = try {
            SystemActions.start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)))
        } catch (e: RuntimeException) { false }
    }
}
