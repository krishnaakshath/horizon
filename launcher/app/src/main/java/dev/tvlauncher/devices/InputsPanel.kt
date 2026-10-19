package dev.tvlauncher.devices

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelShell

/**
 * Inputs panel (HDMI and other passthrough inputs) in the shared [PanelShell] anatomy; content
 * comes from [InputsModel]. Reads TvInputManager on open and follows its callbacks while open.
 * OK switches to the input; a failure stays in the panel as a one-line notice. [names] supplies
 * known device names per input id (the home screen's device monitor).
 */
class InputsPanel(
    private val context: Context,
    lite: Boolean,
    private val names: () -> Map<String, String> = { emptyMap() }
) {
    private val shell = PanelShell(context, lite)
    private val main = Handler(Looper.getMainLooper())
    private val tim: TvInputManager? = try {
        context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    } catch (e: RuntimeException) { null }
    private var callback: TvInputManager.TvInputCallback? = null
    private var notice: String? = null
    private var items: List<TvInputItem> = emptyList()

    val isShowing: Boolean get() = shell.isVisible
    val isOpen: Boolean get() = shell.isOpen

    /** True when the TV has at least one passthrough input (otherwise the host may fall back). */
    val hasInputs: Boolean get() = read().isNotEmpty()

    /** Called when an input was opened (the host may close everything). */
    var onSwitched: (() -> Unit)? = null

    init {
        shell.onRowClick = ::onRow
    }

    fun show(host: ViewGroup) {
        if (shell.isOpen) return
        notice = null
        shell.show(host)
        register()
        refresh()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        unregister()
        shell.dismiss()
    }

    fun release() {
        unregister()
        shell.release()
    }

    fun onBack(): Boolean {
        if (shell.isOpen) { dismiss(); return true }
        return shell.isVisible
    }

    private fun refresh() {
        if (!shell.isOpen) return
        items = InputsModel.merge(read(), names())
        shell.render(InputsModel.build(items, notice))
    }

    private fun read(): List<RawInput> = try {
        tim?.tvInputList.orEmpty().filter { it.isPassthroughInput }.map { info ->
            val state = try { tim?.getInputState(info.id) ?: InputsModel.STATE_DISCONNECTED } catch (e: RuntimeException) { InputsModel.STATE_DISCONNECTED }
            RawInput(info.id, parentOf(info), labelOf(info), info.type, state)
        }
    } catch (e: RuntimeException) { emptyList() }

    private fun parentOf(info: TvInputInfo): String? =
        if (Build.VERSION.SDK_INT >= 26) try { info.parentId } catch (t: Throwable) { null } else null

    private fun labelOf(info: TvInputInfo): String = try {
        (info.loadCustomLabel(context) ?: info.loadLabel(context))?.toString().orEmpty()
    } catch (t: Throwable) { "" }

    private fun register() {
        val m = tim ?: return
        if (callback != null) return
        val cb = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) = refresh()
            override fun onInputAdded(inputId: String) = refresh()
            override fun onInputRemoved(inputId: String) = refresh()
            override fun onInputUpdated(inputId: String) = refresh()
        }
        try { m.registerCallback(cb, main); callback = cb } catch (e: RuntimeException) { }
    }

    private fun unregister() {
        val cb = callback ?: return
        callback = null
        try { tim?.unregisterCallback(cb) } catch (e: RuntimeException) { }
    }

    private fun onRow(row: PanelEntry.Row) {
        val id = row.key.removePrefix(InputsModel.PREFIX)
        val ok = DevicesPanel.switchTo(context, id)
        if (ok) {
            onSwitched?.invoke()
        } else {
            notice = "Couldn't switch to ${row.label}."
            refresh()
        }
    }
}
