package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.ui.Theme.style

/**
 * Pill-shaped address field. Unfocused it shows a tidy "host/path" with a lock for https; focusing
 * it (click with the cursor, or OK in focus mode) shows the full URL selected and opens the
 * system on-screen keyboard. "Go" on the keyboard submits.
 */
class UrlBar(context: Context, private val onSubmit: (String) -> Unit) : LinearLayout(context) {
    private val bg = GradientDrawable().apply { cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat() }
    private val icon = ImageView(context)
    private var fullUrl: String? = null
    private var hovering = false

    val edit = EditText(context).apply {
        background = null
        style(15f, Theme.regular)
        setHintTextColor(Theme.TEXT_3)
        hint = "Search or type a web address"
        isSingleLine = true
        maxLines = 1
        setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isFocusableInTouchMode = true
        setSelectAllOnFocus(true)
        setOnEditorActionListener { _, action, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE || enter) {
                submit(); true
            } else false
        }
        setOnFocusChangeListener { _, has ->
            if (has) {
                setText(fullUrl ?: "")
                selectAll()
                post { imm().showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
            } else {
                imm().hideSoftInputFromWindow(windowToken, 0)
                render()
            }
            refresh()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(context.dp(16), 0, context.dp(18), 0)
        isClickable = true
        setOnClickListener { edit.requestFocus() }
        addView(icon, LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(12) })
        addView(edit, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        render()
        refresh()
    }

    private fun imm() = context.getSystemService(InputMethodManager::class.java)

    private fun submit() {
        val text = edit.text.toString()
        edit.clearFocus()
        onSubmit(text)
    }

    /** null = start page. */
    fun showUrl(url: String?) {
        fullUrl = url
        if (!edit.isFocused) render()
    }

    private fun render() {
        val u = fullUrl
        val secure = u != null && u.startsWith("https://")
        icon.setImageResource(if (secure) R.drawable.ic_lock else R.drawable.ic_search)
        icon.imageTintList = ColorStateList.valueOf(if (secure) 0xFF7FD69B.toInt() else Theme.TEXT_2)
        edit.setText(u?.let { pretty(it) } ?: "")
    }

    private fun pretty(u: String): String =
        u.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

    private fun refresh() {
        val focused = edit.isFocused
        bg.setColor(if (focused || hovering) Theme.SURFACE_2 else Theme.SURFACE)
        bg.setStroke(context.dp(if (focused) 2 else 1), if (focused) Theme.TEXT else if (hovering) 0x55FFFFFF else Theme.STROKE)
    }

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        hovering = hovered
        refresh()
    }

    override fun dispatchHoverEvent(event: android.view.MotionEvent): Boolean {
        val r = super.dispatchHoverEvent(event)
        val inside = event.action != android.view.MotionEvent.ACTION_HOVER_EXIT
        if (inside != hovering) { hovering = inside; refresh() }
        return r
    }
}
