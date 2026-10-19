package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style

/**
 * Glass address pill. Unfocused: lock (https) or warning (http) + page title and host.
 * Focused: the full URL, selected, with the system keyboard; typing reports [onQuery].
 */
class UrlBar(
    context: Context,
    private val onSubmit: (String) -> Unit,
    private val onQuery: (String) -> Unit,
    private val onFocus: (Boolean) -> Unit
) : LinearLayout(context) {
    private val bg = GradientDrawable().apply { cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat() }
    private val icon = ImageView(context)
    private var fullUrl: String? = null
    private var title: String = ""
    private var hovering = false
    private var rendering = false

    val edit: EditText = EditText(context).apply {
        background = null
        style(15f, Theme.regular)
        setHintTextColor(Theme.TEXT_3)
        hint = "Search or type a web address"
        isSingleLine = true
        setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isFocusableInTouchMode = true
        setOnEditorActionListener { _, action, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE || enter) { submit(); true } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!rendering && isFocused) onQuery(s?.toString() ?: "") }
        })
        setOnFocusChangeListener { _, has ->
            if (has) {
                rendering = true
                setText(fullUrl ?: "")
                rendering = false
                post { selectAll() }
                postDelayed({ if (isFocused) selectAll() }, 250)
            } else {
                imm().hideSoftInputFromWindow(windowToken, 0)
                render()
            }
            refresh()
            onFocus(has)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(context.dp(16), 0, context.dp(18), 0)
        isClickable = true
        setOnClickListener { focusAndType() }
        addView(icon, LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(12) })
        addView(edit, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        render()
        refresh()
    }

    fun focusAndType() {
        edit.requestFocus()
        edit.post { imm().showSoftInput(edit, 0) }
    }

    private fun imm() = context.getSystemService(InputMethodManager::class.java)

    private fun submit() {
        val text = edit.text.toString()
        edit.clearFocus()
        onSubmit(text)
    }

    /** url == null means the start page. */
    fun show(url: String?, pageTitle: String?) {
        fullUrl = url
        title = pageTitle?.trim().orEmpty()
        if (!edit.isFocused) render()
    }

    private fun render() {
        rendering = true
        val u = fullUrl
        val secure = u != null && u.startsWith("https://")
        val insecure = u != null && u.startsWith("http://")
        icon.setImageResource(if (secure) R.drawable.ic_lock else if (insecure) R.drawable.ic_warn else R.drawable.ic_search)
        icon.imageTintList = ColorStateList.valueOf(if (secure) 0xFF7FD69B.toInt() else if (insecure) 0xFFFBBF24.toInt() else Theme.TEXT_2)
        if (u == null) edit.setText("") else {
            val host = UrlPolicy.hostOf(u).ifEmpty { u }
            val sb = SpannableStringBuilder()
            if (insecure) sb.append("Not secure  ", ForegroundColorSpan(0xFFFBBF24.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (title.isNotEmpty() && title != host && !title.startsWith("http")) {
                sb.append(title.take(80))
                sb.append("   " + host, ForegroundColorSpan(Theme.TEXT_3), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else sb.append(host)
            edit.setText(sb)
        }
        edit.setSelection(0)
        rendering = false
    }

    private fun refresh() {
        val focused = edit.isFocused
        bg.setColor(if (focused || hovering) 0x26FFFFFF else 0x14FFFFFF)
        bg.setStroke(context.dp(if (focused) 2 else 1), if (focused) Theme.TEXT else if (hovering) 0x66FFFFFF else 0x24FFFFFF)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val r = super.dispatchHoverEvent(event)
        val inside = event.action != MotionEvent.ACTION_HOVER_EXIT
        if (inside != hovering) { hovering = inside; refresh() }
        return r
    }
}
