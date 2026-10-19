package dev.tvlauncher.system

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.appsettings.Chip
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsStyle
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelLimits
import dev.tvlauncher.panel.Trailing

/**
 * A Settings page that shows a panel screen ([WifiScreen], [BluetoothScreen]) as native Settings
 * rows: same row style as every other page, one title (the pane's), no card inside the pane.
 * The panel header becomes a one-line status note; messages become notes. [onBackKey] lets the
 * screen leave a sub-view before Settings goes up a level.
 */
class PanelRowsPage(context: Context, style: SettingsStyle) : PanelSink {
    val page = SettingsPage(context, style)
    var onRowClick: ((PanelEntry.Row) -> Unit)? = null
    var onBackKey: (() -> Boolean)? = null
    private var signature: List<String> = emptyList()
    private val rows = LinkedHashMap<String, SettingsRow>()
    private var swallowUp = false

    /** Runs when the page is shown / leaves the screen (start and stop the screen's controller). */
    var onShown: (() -> Unit)? = null
    var onHidden: (() -> Unit)? = null

    /** The view to return from buildPage. */
    val view: FrameLayout = object : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)
        override fun onAttachedToWindow() { super.onAttachedToWindow(); onShown?.invoke() }
        override fun onDetachedFromWindow() { onHidden?.invoke(); super.onDetachedFromWindow() }
    }.apply { addView(page, FrameLayout.LayoutParams(-1, -1)) }

    override val active: Boolean get() = view.isAttachedToWindow
    override val anchor: View get() = view

    private fun handleKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            swallowUp = onBackKey?.invoke() == true
            return swallowUp
        }
        if (event.action == KeyEvent.ACTION_UP && swallowUp) { swallowUp = false; return true }
        return false
    }

    override fun render(content: PanelContent, preferKey: String?) {
        val sig = listOf("h:" + content.header.title) + content.entries.map { sigOf(it) }
        if (sig == signature) {
            for (r in content.rows) rows[r.key]?.let { v -> v.setValue(valueOf(r)); v.setTitle(r.label) }
            return
        }
        signature = sig
        val focusedKey = (page.findFocus()?.let { f -> generateSequence(f) { it.parent as? View }.firstOrNull { it is SettingsRow } } as? SettingsRow)?.tag as? String
        val hadFocus = page.hasFocus()
        page.column.removeAllViews()
        rows.clear()
        val sub = content.header.subtitle
        page.body.note(if (sub.isBlank()) content.header.title else "${content.header.title} · $sub", maxLines = 1)
        for (e in content.entries) when (e) {
            is PanelEntry.Message -> page.body.note(e.text, maxLines = 2)
            is PanelEntry.Row -> rows[e.key] = page.body.row(e.key, e.label, kindOf(e), subtitle = subtitleOf(e), iconRes = e.icon ?: 0)
        }
        val keys = rows.keys.toList()
        val i = PanelLimits.focusIndex(keys, focusedKey, preferKey)
        if (hadFocus && i >= 0) rows[keys[i]]?.requestFocus()
    }

    private fun sigOf(e: PanelEntry): String = when (e) {
        is PanelEntry.Message -> "m:" + e.text
        is PanelEntry.Row -> "r:" + e.key + ":" + e.enabled + ":" + when (val t = e.trailing) {
            is Trailing.Switch -> "s" + t.on
            else -> t.toString()
        }
    }

    private fun valueOf(r: PanelEntry.Row): String? = when (r.trailing) {
        Trailing.Busy -> r.value ?: "…"
        else -> r.value
    }

    private fun subtitleOf(r: PanelEntry.Row): String? = if (r.trailing == Trailing.Lock) "Secured" else null

    private fun kindOf(r: PanelEntry.Row): RowKind {
        val click = { onRowClick?.invoke(r); Unit }
        if (!r.enabled || r.key.startsWith("info:")) return RowKind.Info(r.value.orEmpty())
        return when (val t = r.trailing) {
            is Trailing.Switch -> RowKind.Toggle(t.on) { click() }
            Trailing.Chevron -> RowKind.Action(r.value, chevron = true) { click() }
            Trailing.Check -> RowKind.Action(r.value ?: "", chevron = false, chip = Chip.POSITIVE) { click() }
            else -> RowKind.Action(valueOf(r), chevron = false) { click() }
        }
    }
}
