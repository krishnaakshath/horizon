package dev.tvlauncher.panel

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.network.PanelLifecycle
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * The single panel anatomy used by every overlay: a full-screen scrim (audit:scrim) that traps
 * D-pad focus, and one near-opaque card with a title row ([PanelHeaderView]) and a list of
 * [PanelEntry] rows that scrolls with focus after [PanelLimits.MAX_VISIBLE_ROWS] rows.
 *
 * [Anchor.SIDE] sits under the top bar on the right (Wi-Fi, Bluetooth, Inputs, Devices);
 * [Anchor.CENTER] is a centred menu (tile options). Opening fades and lifts the card by 8 dp in
 * [PanelMotion] time (220 ms, 160 ms on LITE, scaled by the system animator scale, instant when
 * motion is off). The host restores focus to the opener after [dismiss]; [PanelLifecycle] makes
 * sure a stale close animation never removes a re-opened panel.
 *
 * BACK: hosts that route BACK themselves call their own onBack; menus without such a route set
 * [onBackKey], which the scrim then handles while focus is inside the panel.
 */
class PanelShell(
    private val context: Context,
    private val lite: Boolean,
    private val anchor: Anchor = Anchor.SIDE,
    widthDp: Int = if (anchor == Anchor.SIDE) PanelStyle.SIDE_WIDTH else PanelStyle.MENU_WIDTH
) {
    enum class Anchor { SIDE, CENTER }

    val root: TrapFrame = object : TrapFrame(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val back = onBackKey
            if (back != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) back()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val header = PanelHeaderView(context)
    private val list = PanelList(context)
    val scroll = MaxHeightScrollView(context)
    val lifecycle = PanelLifecycle()

    /** OK on an enabled row. */
    var onRowClick: ((PanelEntry.Row) -> Unit)?
        get() = list.onRowClick
        set(v) { list.onRowClick = v }
    /** D-pad LEFT/RIGHT on a row (steppers); return true when consumed. */
    var onRowKey: ((PanelEntry.Row, Int) -> Boolean)?
        get() = list.onRowKey
        set(v) { list.onRowKey = v }
    /** Set to make the panel handle BACK itself (see class doc). */
    var onBackKey: (() -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = lifecycle.isOpen
    val isVisible: Boolean get() = lifecycle.isVisible

    init {
        root.setBackgroundColor(PanelStyle.SCRIM)
        root.auditTag(AuditTags.SCRIM)
        root.isClickable = true
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        card.background = PanelStyle.surface(context, lite)
        val pad = context.dp(PanelStyle.G1)
        card.setPadding(pad, context.dp(PanelStyle.G2), pad, pad)
        card.isClickable = true // touches on the card never reach the scrim
        card.addView(header, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        scroll.maxHeightPx = context.dp(PanelLimits.bodyMaxDp())
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = context.dp(PanelStyle.G1) })
        val lp = FrameLayout.LayoutParams(context.dp(widthDp), -2,
            if (anchor == Anchor.SIDE) Gravity.TOP or Gravity.END else Gravity.CENTER)
        if (anchor == Anchor.SIDE) {
            lp.topMargin = context.dp(Theme.MARGIN_V + TOP_BAR_CLEARANCE)
            lp.marginEnd = context.dp(Theme.MARGIN_H)
        } else {
            lp.topMargin = context.dp(Theme.MARGIN_V)
        }
        lp.bottomMargin = context.dp(Theme.MARGIN_V)
        root.addView(card, lp)
    }

    fun show(host: ViewGroup) {
        if (lifecycle.show() == null) return
        root.animate().cancel(); card.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
        host.addView(root, ViewGroup.LayoutParams(-1, -1))
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val ms = PanelStyle.openMs(context, lite)
        if (ms > 0) {
            root.alpha = 0f
            card.translationY = context.dp(PanelStyle.G1).toFloat()
            card.animate().translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator(2f)).start()
            root.animate().alpha(1f).setDuration(ms).setInterpolator(DecelerateInterpolator(2f)).start()
        } else {
            root.alpha = 1f
            card.translationY = 0f
        }
        focusFirstOrHold()
    }

    fun dismiss() {
        val gen = lifecycle.dismiss() ?: return
        // While it fades out nothing inside may keep or take focus (the host restores it elsewhere).
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.isFocusable = false
        val ms = PanelStyle.closeMs(context, lite)
        val end = Runnable {
            if (lifecycle.finishClose(gen)) {
                (root.parent as? ViewGroup)?.removeView(root)
                onClosed?.invoke()
            }
        }
        if (ms <= 0) { end.run(); return }
        card.animate().translationY(context.dp(PanelStyle.G1).toFloat()).setDuration(ms).setInterpolator(AccelerateInterpolator()).start()
        root.animate().alpha(0f).setDuration(ms).setInterpolator(AccelerateInterpolator()).withEndAction(end).start()
    }

    /** Immediate teardown without animation. Idempotent. */
    fun release() {
        lifecycle.release()
        root.animate().cancel(); card.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
    }

    /**
     * Shows [content]. Rows are updated in place when the key sequence is unchanged (no flicker,
     * focus kept); otherwise the list is rebuilt and focus goes to the previously focused key, then
     * [preferKey], then the first row ([PanelLimits.focusIndex]).
     */
    fun render(content: PanelContent, preferKey: String? = null) {
        header.bind(content.header)
        val focusedKey = (root.findFocus() as? PanelRowView)?.key
        val hadFocus = root.hasFocus()
        val sy = scroll.scrollY
        if (!list.render(content.entries)) {
            if (!root.hasFocus() && isOpen) focusFirstOrHold(preferKey)
            return
        }
        val rows = list.rowViews()
        val i = PanelLimits.focusIndex(rows.map { it.key }, focusedKey, preferKey)
        if (!isOpen) return
        if (i >= 0 && (hadFocus || !root.hasFocus())) {
            root.isFocusable = false
            rows[i].requestFocus()
            if (rows[i].key == focusedKey) scroll.post { scroll.scrollTo(0, sy) }
        } else if (i < 0) {
            holdFocus()
        }
    }

    private fun focusFirstOrHold(preferKey: String? = null) {
        val rows = list.rowViews()
        val i = PanelLimits.focusIndex(rows.map { it.key }, null, preferKey)
        if (i >= 0) { root.isFocusable = false; rows[i].requestFocus() } else holdFocus()
    }

    /** No focusable row: the scrim itself holds focus so BACK and the trap keep working. */
    private fun holdFocus() {
        if (root.hasFocus() && root.findFocus() !== root) return
        root.isFocusable = true
        root.requestFocus()
    }

    companion object {
        /** Space kept free for the top bar above a SIDE panel (dp). */
        const val TOP_BAR_CLEARANCE = 56
    }
}
