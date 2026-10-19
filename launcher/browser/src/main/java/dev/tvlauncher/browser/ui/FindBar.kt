package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.ui.Theme.style

/** Find-in-page pill: query field, "3 of 12", previous / next / close. */
class FindBar(
    context: Context,
    private val onQuery: (String) -> Unit,
    private val onNext: (Boolean) -> Unit,
    private val onDone: () -> Unit
) : LinearLayout(context) {
    private val count = TextView(context).apply { style(13f, Theme.medium, Theme.TEXT_2) }
    val field = EditText(context).apply {
        background = null
        style(15f, Theme.regular)
        hint = "Find in page"; setHintTextColor(Theme.TEXT_3)
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setOnEditorActionListener { _, _, _ -> onNext(true); true }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onQuery(s?.toString() ?: "") }
        })
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Theme.rounded(context, 0xFF1A1F2B.toInt(), Theme.RADIUS_PILL, Theme.STROKE, 1)
        elevation = context.dpf(14f)
        setPadding(context.dp(18), context.dp(6), context.dp(8), context.dp(6))
        visibility = GONE
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_find); imageTintList = ColorStateList.valueOf(Theme.TEXT_2) },
            LayoutParams(context.dp(18), context.dp(18)))
        addView(field, LayoutParams(context.dp(260), -2).apply { marginStart = context.dp(12) })
        addView(count, LayoutParams(context.dp(76), -2))
        listOf(Triple(R.drawable.ic_up, "Previous") { onNext(false) }, Triple(R.drawable.ic_down, "Next") { onNext(true) },
            Triple(R.drawable.ic_close, "Close") { onDone() }).forEach { (icon, label, fn) ->
            addView(RoundButton(context, icon, label, fn).apply { val p = context.dp(9); setPadding(p, p, p, p) },
                LayoutParams(context.dp(38), context.dp(38)).apply { marginStart = context.dp(6) })
        }
    }

    val isOpen get() = visibility == VISIBLE

    fun open() {
        visibility = VISIBLE
        field.setText("")
        count.text = ""
        field.requestFocus()
        field.post { context.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0) }
    }

    fun setCount(active: Int, total: Int) {
        count.text = if (field.text.isEmpty()) "" else if (total == 0) "No matches" else "${active + 1} of $total"
    }

    fun close() {
        visibility = GONE
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
    }
}
