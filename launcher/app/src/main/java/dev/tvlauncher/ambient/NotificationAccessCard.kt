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
 * Ready-to-host block for granting notification access (`container.addView(NotificationAccessCard(this))`).
 * Calm, friendly states only, never developer commands (those live in docs/adb-setup.md):
 * - access granted: the block hides itself (GONE);
 * - not granted and the TV has an access screen: a short explanation and one "Allow" panel row;
 * - no such screen: the explanation and one line ([NotificationAccessText.SETUP_LINE]).
 * Call [refresh] from the host's onResume to update the state after the user returns.
 */
class NotificationAccessCard @JvmOverloads constructor(
    context: Context,
    title: String = DEFAULT_TITLE,
    body: String = DEFAULT_BODY
) : LinearLayout(context) {
    private val row = PanelRowView(context, ALLOW_ROW)
    private val setup = TextView(context)

    init {
        orientation = VERTICAL
        val pad = context.dp(PanelStyle.G2)
        setPadding(0, context.dp(PanelStyle.G1), 0, context.dp(PanelStyle.G1))
        addView(TextView(context).apply {
            panelText(PanelStyle.MESSAGE_SP, FontWeight.MEDIUM, PanelStyle.TEXT, singleLine = false)
            text = title
            setPadding(pad, 0, pad, 0)
        }, params(0))
        addView(TextView(context).apply {
            panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
            text = body
            maxLines = 3
            setPadding(pad, 0, pad, 0)
        }, params(4))
        row.setOnClickListener { open() }
        addView(row, params(PanelStyle.G1))
        setup.panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
        setup.setPadding(pad, 0, pad, 0)
        setup.text = NotificationAccessText.SETUP_LINE
        addView(setup, params(PanelStyle.G1))
        refresh()
    }

    private fun params(topDp: Int) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = context.dp(topDp)
    }

    /** Re-reads whether access is granted. Call from the host's onResume (the user returns from Settings). */
    fun refresh() {
        val on = NotificationAccess.isEnabled(context)
        visibility = if (on) GONE else VISIBLE
        if (on) return
        val screen = NotificationAccess.hasSettingsScreen(context)
        row.visibility = if (screen) VISIBLE else GONE
        setup.visibility = if (screen) GONE else VISIBLE
    }

    /** Back from the system's access screen: the window regains focus, so the state is re-read here too. */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refresh()
    }

    private fun open() {
        if (!NotificationAccess.openSettings(context)) {
            row.visibility = GONE
            setup.visibility = VISIBLE
        }
    }

    companion object {
        private val ALLOW_ROW = PanelEntry.Row("notificationAccess", R.drawable.ic_set_helper, "Allow", null, Trailing.Chevron)
        const val DEFAULT_TITLE = "Notifications on the ambient screen"
        const val DEFAULT_BODY = "Shows up to 3 recent notifications. Kept in memory only, never saved."
        const val NOW_PLAYING_TITLE = "Allow Now Playing"
        const val NOW_PLAYING_BODY = "Shows what's playing, with pause and skip. Nothing is saved."
    }
}
