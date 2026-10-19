package dev.tvlauncher.system

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelHeaderView
import dev.tvlauncher.panel.PanelList
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.panel.TrapFrame
import dev.tvlauncher.panel.Trailing
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.dp

/**
 * A small glass sheet for typing one value with the TV's own keyboard (the system IME, driven by
 * the remote): a Wi-Fi password, a hidden network's name, a parental PIN. The field takes focus
 * and the keyboard opens; DOWN leaves the keyboard for the rows below (Show password, the confirm
 * row, Cancel). BACK closes the keyboard first, then the sheet.
 *
 * [Request.onSubmit] returns null to close, or one friendly line to show under the field.
 */
class TextEntrySheet(private val context: Context, private val lite: Boolean) {
    enum class Kind { TEXT, PASSWORD, PIN }

    class Request(
        val title: String,
        val subtitle: String,
        val kind: Kind,
        val confirm: String,
        val initial: String = "",
        val hint: String = "",
        val icon: Int = R.drawable.ic_sys_lock,
        val onCancel: () -> Unit = {},
        val onSubmit: (String) -> String?
    )

    private var request: Request? = null
    private var reveal = false

    private val root: TrapFrame = object : TrapFrame(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) cancel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = PanelHeaderView(context)
    private val field = EditText(context)
    private val error = TextView(context).apply {
        panelText(PanelStyle.DETAIL_SP, FontWeight.MEDIUM, 0xFFFFB4A9.toInt(), singleLine = false)
        visibility = View.GONE
    }
    private val list = PanelList(context)

    val isShowing: Boolean get() = root.parent != null

    /** Runs after the sheet left the screen (submit, cancel or [dismiss]); the host redraws and refocuses. */
    var onClosed: (() -> Unit)? = null

    init {
        root.setBackgroundColor(PanelStyle.SCRIM)
        root.isClickable = true
        card.background = PanelStyle.surface(context, lite)
        val pad = context.dp(PanelStyle.G2)
        card.setPadding(pad, pad, pad, pad)
        card.isClickable = true
        card.addView(header, LinearLayout.LayoutParams(-1, -2))
        field.apply {
            panelText(PanelStyle.ROW_SP, FontWeight.MEDIUM)
            setTextColor(PanelStyle.TEXT)
            setHintTextColor(PanelStyle.TEXT_2)
            background = PanelStyle.rounded(context, PanelStyle.QUIET_FILL, PanelStyle.ROW_RADIUS)
            setPadding(pad, context.dp(12), pad, context.dp(12))
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO) { submit(); true } else false
            }
            setOnFocusChangeListener { _, has -> if (has) showKeyboard() }
            setOnClickListener { showKeyboard() }
        }
        card.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(PanelStyle.G2) })
        card.addView(error, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(PanelStyle.G1); marginStart = context.dp(4)
        })
        card.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(PanelStyle.G1) })
        list.onRowClick = { row ->
            when (row.key) {
                KEY_REVEAL -> { reveal = !reveal; applyInputType(); renderRows() }
                KEY_CONFIRM -> submit()
                KEY_CANCEL -> cancel()
            }
        }
        root.addView(card, FrameLayout.LayoutParams(context.dp(WIDTH_DP), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = context.dp(TOP_DP)
        })
    }

    fun show(host: ViewGroup, req: Request) {
        request = req
        reveal = false
        (root.parent as? ViewGroup)?.removeView(root)
        host.addView(root, ViewGroup.LayoutParams(-1, -1))
        header.bind(PanelHeader(req.icon, req.title, req.subtitle))
        field.hint = req.hint
        applyInputType()
        field.setText(req.initial)
        field.setSelection(field.text.length)
        error.visibility = View.GONE
        renderRows()
        field.requestFocus()
        field.postDelayed({ showKeyboard() }, 150)
    }

    /** Shows [message] under the field and returns focus to it. */
    fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
        field.requestFocus()
    }

    fun dismiss() {
        val was = isShowing
        hideKeyboard()
        (root.parent as? ViewGroup)?.removeView(root)
        request = null
        if (was) onClosed?.invoke()
    }

    private fun cancel() {
        val r = request
        dismiss()
        r?.onCancel?.invoke()
    }

    private fun submit() {
        val r = request ?: return
        val msg = r.onSubmit(field.text.toString())
        // onSubmit may have replaced this request with a follow-up one; only close our own.
        if (request !== r) return
        if (msg == null) dismiss() else showError(msg)
    }

    private fun renderRows() {
        val r = request ?: return
        val rows = ArrayList<PanelEntry>()
        if (r.kind == Kind.PASSWORD) rows += PanelEntry.Row(KEY_REVEAL, R.drawable.ic_set_eye, "Show password", trailing = Trailing.Switch(reveal))
        rows += PanelEntry.Row(KEY_CONFIRM, R.drawable.ic_set_check, r.confirm)
        rows += PanelEntry.Row(KEY_CANCEL, R.drawable.ic_net_close, "Cancel")
        list.render(rows)
    }

    private fun applyInputType() {
        val kind = request?.kind ?: Kind.TEXT
        field.inputType = when {
            kind == Kind.PIN -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            kind == Kind.PASSWORD && !reveal -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            kind == Kind.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        field.setSelection(field.text.length)
    }

    private fun imm() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private fun showKeyboard() {
        if (!isShowing) return
        try { imm()?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) } catch (e: RuntimeException) { }
    }

    private fun hideKeyboard() {
        try { imm()?.hideSoftInputFromWindow(field.windowToken, 0) } catch (e: RuntimeException) { }
    }

    private companion object {
        const val KEY_REVEAL = "entry:reveal"
        const val KEY_CONFIRM = "entry:confirm"
        const val KEY_CANCEL = "entry:cancel"
        const val WIDTH_DP = 520
        const val TOP_DP = 48
    }
}
