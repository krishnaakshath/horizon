package dev.tvlauncher.appsettings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/** What a [SettingsRow] does on OK / LEFT / RIGHT. */
sealed class RowKind {
    /** OK flips. */
    class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : RowKind()

    /** OK cycles (wrapping); LEFT/RIGHT step and stop at the ends so focus can still leave. */
    class Choice(val options: List<String>, val index: Int, val onChange: (Int) -> Unit) : RowKind()

    /** LEFT/RIGHT step within [min]..[max]; OK steps up and wraps. */
    class Stepper(
        val value: Int,
        val min: Int,
        val max: Int,
        val label: (Int) -> String,
        val onChange: (Int) -> Unit
    ) : RowKind()

    /** OK runs [onClick]; [value] is shown on the right, followed by a chevron when [chevron]. */
    class Action(val value: String? = null, val chevron: Boolean = true, val onClick: () -> Unit) : RowKind()

    /** Read-only line; still focusable so long pages scroll with the D-pad. */
    class Info(val value: String = "") : RowKind()
}

/**
 * The one reusable settings line: optional icon chip, title, optional subtitle and a trailing
 * control. Focus: accent ring on a tinted glass fill (cross-faded), slight scale and brighter text.
 * The row's tag is [key] so the screen can restore focus after a rebuild.
 */
class SettingsRow(
    context: Context,
    private val style: SettingsStyle,
    val key: String,
    title: String,
    subtitle: String? = null,
    iconRes: Int = 0,
    val kind: RowKind
) : LinearLayout(context) {
    private val titleView = TextView(context).apply {
        text = title
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(SP_TITLE, Theme.medium)
    }
    private val subtitleView = TextView(context).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        style(SP_SUB, Theme.regular, Theme.TEXT_2)
        setLineSpacing(0f, 1.1f)
    }
    private val valueView = TextView(context).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        style(SP_VALUE, Theme.regular, Theme.TEXT_2)
    }
    private val iconView = ImageView(context)
    private val arrowLeft = chevron(R.drawable.ic_set_chevron_left)
    private val arrowRight = chevron(R.drawable.ic_set_chevron)
    private var toggle: ToggleSwitch? = null

    private var checked = (kind as? RowKind.Toggle)?.checked ?: false
    private var index = (kind as? RowKind.Choice)?.index ?: 0
    private var step = (kind as? RowKind.Stepper)?.value ?: 0

    init {
        tag = key
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(MIN_HEIGHT_DP)
        setPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
        background = StateListDrawable().apply {
            setEnterFadeDuration(style.focusMs.toInt())
            setExitFadeDuration(style.focusMs.toInt())
            addState(
                intArrayOf(android.R.attr.state_focused),
                Glass.rect(context, style.tint(0.26f), Glass.RADIUS_ROW, style.accentOnDark, 2)
            )
            addState(intArrayOf(), Glass.rect(context, Glass.ROW, Glass.RADIUS_ROW))
        }

        if (iconRes != 0) {
            val chip = FrameLayout(context).apply { background = Glass.rect(context, Glass.CHIP, Glass.RADIUS_CHIP) }
            iconView.setImageResource(iconRes)
            iconView.imageTintList = ColorStateList.valueOf(Theme.TEXT)
            chip.addView(iconView, FrameLayout.LayoutParams(context.dp(22), context.dp(22), Gravity.CENTER))
            addView(chip, LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(16) })
        }

        val column = LinearLayout(context).apply { orientation = VERTICAL }
        column.addView(titleView)
        column.addView(subtitleView, LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        setSubtitle(subtitle)
        addView(column, LayoutParams(0, -2, 1f))

        when (kind) {
            is RowKind.Toggle -> {
                val t = ToggleSwitch(context, style.accent, style.focusMs)
                t.setChecked(checked, animate = false)
                toggle = t
                addView(t, LayoutParams(-2, -2).apply { marginStart = context.dp(16) })
            }
            is RowKind.Choice, is RowKind.Stepper -> {
                addView(arrowLeft, arrowParams())
                addView(valueView, LayoutParams(-2, -2).apply { marginStart = context.dp(6); marginEnd = context.dp(6) })
                addView(arrowRight, arrowParams())
                arrowLeft.alpha = 0f; arrowRight.alpha = 0f
                renderValue()
            }
            is RowKind.Action -> {
                addView(valueView, valueParams())
                valueView.text = kind.value.orEmpty()
                if (kind.chevron) addView(arrowRight, arrowParams().apply { marginStart = context.dp(8) })
                arrowRight.alpha = 0.55f
            }
            is RowKind.Info -> {
                addView(valueView, valueParams())
                valueView.text = kind.value
            }
        }
        setOnClickListener { onOk() }
    }

    private fun chevron(res: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(Theme.TEXT)
    }

    private fun arrowParams() = LayoutParams(context.dp(18), context.dp(18))

    private fun valueParams() = LayoutParams(-2, -2).apply { marginStart = context.dp(16) }

    fun setValue(text: String?) {
        valueView.text = text.orEmpty()
    }

    fun setSubtitle(text: String?) {
        subtitleView.text = text.orEmpty()
        subtitleView.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setTitle(text: String) {
        titleView.text = text
    }

    private fun renderValue() {
        when (kind) {
            is RowKind.Choice -> valueView.text = kind.options.getOrElse(index) { "" }
            is RowKind.Stepper -> valueView.text = kind.label(step)
            else -> Unit
        }
        updateArrows()
    }

    private fun updateArrows() {
        val f = isFocused
        when (kind) {
            is RowKind.Choice -> {
                arrowLeft.alpha = if (f && index > 0) ARROW_ON else if (f) ARROW_DIM else 0f
                arrowRight.alpha = if (f && index < kind.options.size - 1) ARROW_ON else if (f) ARROW_DIM else 0f
            }
            is RowKind.Stepper -> {
                arrowLeft.alpha = if (f && step > kind.min) ARROW_ON else if (f) ARROW_DIM else 0f
                arrowRight.alpha = if (f && step < kind.max) ARROW_ON else if (f) ARROW_DIM else 0f
            }
            else -> Unit
        }
    }

    private fun onOk() {
        when (kind) {
            is RowKind.Toggle -> {
                checked = !checked
                toggle?.setChecked(checked, animate = true)
                kind.onChange(checked)
            }
            is RowKind.Choice -> if (kind.options.isNotEmpty()) setIndex((index + 1) % kind.options.size)
            is RowKind.Stepper -> setStep(if (step >= kind.max) kind.min else step + 1)
            is RowKind.Action -> kind.onClick()
            is RowKind.Info -> Unit
        }
    }

    private fun setIndex(i: Int) {
        val k = kind as RowKind.Choice
        if (i == index) return
        index = i
        renderValue()
        k.onChange(i)
    }

    private fun setStep(v: Int) {
        val k = kind as RowKind.Stepper
        if (v == step) return
        step = v
        renderValue()
        k.onChange(v)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> 0
        }
        if (delta != 0) {
            when (kind) {
                is RowKind.Choice -> {
                    val next = index + delta
                    if (next in kind.options.indices) { setIndex(next); playSoundEffect(android.view.SoundEffectConstants.CLICK); return true }
                }
                is RowKind.Stepper -> {
                    val next = step + delta
                    if (next in kind.min..kind.max) { setStep(next); playSoundEffect(android.view.SoundEffectConstants.CLICK); return true }
                }
                else -> Unit
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        valueView.setTextColor(if (gainFocus) Theme.TEXT else Theme.TEXT_2)
        subtitleView.setTextColor(if (gainFocus) SUB_FOCUSED else Theme.TEXT_2)
        if (kind is RowKind.Action) arrowRight.alpha = if (gainFocus) 1f else 0.55f
        updateArrows()
        val s = if (gainFocus) FOCUS_SCALE else 1f
        animate().cancel()
        if (style.focusMs > 0) {
            animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
        } else {
            scaleX = s; scaleY = s
        }
    }

    override fun hasOverlappingRendering() = false

    private companion object {
        const val MIN_HEIGHT_DP = 68
        const val SP_TITLE = 19f
        const val SP_SUB = 14f
        const val SP_VALUE = 17f
        const val FOCUS_SCALE = 1.015f
        const val ARROW_ON = 0.95f
        const val ARROW_DIM = 0.25f
        const val SUB_FOCUSED = Theme.TEXT_SUB_FOCUSED
    }
}
