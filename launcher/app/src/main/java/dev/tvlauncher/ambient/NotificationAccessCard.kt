package dev.tvlauncher.ambient

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelRowView
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.panel.Trailing
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.dp

/**
 * Ready-to-host block for granting notification access (`container.addView(NotificationAccessCard(this))`):
 * a short explanation and one panel row, "Notification access", whose value shows the current
 * state; OK opens the TV's access screen, or shows the ADB line when the TV has none. Call
 * [refresh] from the host's onResume to update the state after the user returns.
 */
class NotificationAccessCard(context: Context) : LinearLayout(context) {
    private val row = PanelRowView(context, rowFor(false))
    private val fallback = TextView(context)

    init {
        orientation = VERTICAL
        val pad = context.dp(PanelStyle.G2)
        setPadding(0, context.dp(PanelStyle.G1), 0, context.dp(PanelStyle.G1))
        addView(TextView(context).apply {
            panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
            text = "Shows up to 3 recent notifications on the ambient screen. Kept in memory only, never saved."
            setPadding(pad, 0, pad, 0)
        }, params(0))
        row.setOnClickListener { open() }
        addView(row, params(PanelStyle.G1))
        fallback.panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
        fallback.setPadding(pad, 0, pad, 0)
        fallback.text = NotificationAccess.instructions(settingsOpened = false)
        fallback.visibility = GONE
        addView(fallback, params(PanelStyle.G1))
        refresh()
    }

    private fun params(topDp: Int) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(topDp)
    }

    private fun rowFor(on: Boolean) = PanelEntry.Row("notificationAccess", R.drawable.ic_set_helper, "Notification access",
        if (on) "Allowed" else "Not allowed", if (on) Trailing.Check else Trailing.Chevron)

    /** Re-reads whether access is granted. Call from the host's onResume (the user returns from Settings). */
    fun refresh() {
        val on = NotificationAccess.isEnabled(context)
        row.bind(rowFor(on))
        if (on) fallback.visibility = GONE
    }

    private fun open() {
        val opened = NotificationAccess.openSettings(context)
        fallback.text = NotificationAccess.instructions(opened)
        fallback.visibility = VISIBLE
    }
}
