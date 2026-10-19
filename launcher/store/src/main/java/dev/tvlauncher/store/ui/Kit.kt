package dev.tvlauncher.store.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Visual system for Get Apps: premium dark glass without blur (LITE-friendly), D-pad focus states. */
object Kit {
    const val BASE = 0xFF0B0D12.toInt()
    const val SURFACE = 0xFF151922.toInt()
    const val SURFACE_2 = 0xFF1C2230.toInt()
    const val SURFACE_FOCUS = 0xFF232B3D.toInt()
    const val TEXT = 0xFFF5F7FA.toInt()
    const val TEXT_2 = 0xFFA7B0C0.toInt()
    const val TEXT_3 = 0xFF6F7A8E.toInt()
    const val ACCENT = 0xFF7CF2C8.toInt()
    const val ACCENT_2 = 0xFF5B8CFF.toInt()
    const val DANGER = 0xFFFF5C6C.toInt()
    const val DANGER_BG = 0x33FF5C6C
    const val WARN = 0xFFFFB547.toInt()
    const val WARN_BG = 0x26FFB547
    const val OK_BG = 0x267CF2C8
    const val STROKE = 0x1FFFFFFF
    const val ON_ACCENT = 0xFF0B0D12.toInt()

    const val MARGIN_H = 48
    const val MARGIN_V = 27
    const val RADIUS = 18
    const val RADIUS_SMALL = 12

    const val FOOTER = "Install apps only from sources you trust. The Play Store is the best place for Netflix, " +
        "YouTube and other official apps."

    val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    val regular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    val light: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    val mono: Typeface by lazy { Typeface.MONOSPACE }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpf(radiusDp.toFloat())
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

fun Context.text(s: CharSequence, sp: Float, color: Int = Kit.TEXT, face: Typeface = Kit.regular): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
        setLineSpacing(dpf(3f), 1f)
    }

fun vertical(ctx: Context, gapDp: Int = 0): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.VERTICAL
    if (gapDp > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(1, ctx.dp(gapDp)); setColor(0) }
    }
}

fun horizontal(ctx: Context, gapDp: Int = 0): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    if (gapDp > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(ctx.dp(gapDp), 1); setColor(0) }
    }
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

/** Scales a focusable view up slightly while it has focus (the TV focus cue alongside the colour change). */
fun View.focusLift(scale: Float = 1.04f) {
    setOnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(140).start()
        (v as? FocusAware)?.onFocus(has)
    }
}

interface FocusAware { fun onFocus(focused: Boolean) }

private fun Context.stateBg(normal: Drawable, focused: Drawable): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), focused)
    addState(intArrayOf(android.R.attr.state_pressed), focused)
    addState(intArrayOf(), normal)
}

/** Pill button. Primary = mint fill; secondary = glass; danger = red outline. */
fun Context.button(label: String, kind: ButtonKind = ButtonKind.SECONDARY, onClick: () -> Unit): TextView {
    val tv = text(label, 16f, Kit.TEXT, Kit.medium)
    tv.gravity = Gravity.CENTER
    tv.setPadding(dp(26), dp(13), dp(26), dp(13))
    tv.isFocusable = true
    tv.isClickable = true
    val (normal, focused) = when (kind) {
        ButtonKind.PRIMARY -> rounded(0x337CF2C8, 999, Kit.ACCENT, 1) to rounded(Kit.ACCENT, 999)
        ButtonKind.SECONDARY -> rounded(Kit.SURFACE_2, 999, Kit.STROKE, 1) to rounded(Kit.TEXT, 999)
        ButtonKind.DANGER -> rounded(Kit.DANGER_BG, 999, Kit.DANGER, 1) to rounded(Kit.DANGER, 999)
    }
    tv.background = stateBg(normal, focused)
    val focusedText = if (kind == ButtonKind.DANGER) Kit.TEXT else Kit.ON_ACCENT
    tv.setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(focusedText, Kit.TEXT_3, Kit.TEXT),
        ),
    )
    tv.setOnClickListener { onClick() }
    tv.focusLift(1.06f)
    return tv
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

/** Generic focusable glass container for list rows / cards. */
fun Context.glass(child: View, padH: Int = 20, padV: Int = 16, onClick: (() -> Unit)? = null): FrameLayout {
    val f = FrameLayout(this)
    f.setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
    f.addView(child)
    if (onClick != null) {
        f.isFocusable = true
        f.isClickable = true
        f.background = stateBg(
            rounded(Kit.SURFACE, Kit.RADIUS, Kit.STROKE, 1),
            rounded(Kit.SURFACE_FOCUS, Kit.RADIUS, Kit.ACCENT, 2),
        )
        f.setOnClickListener { onClick() }
        f.focusLift(1.02f)
    } else {
        f.background = rounded(Kit.SURFACE, Kit.RADIUS, Kit.STROKE, 1)
    }
    return f
}

/** Rounded square with a glyph (used as icon placeholder and card icons). */
fun Context.iconTile(drawable: Drawable?, sizeDp: Int, letter: String? = null): View {
    val f = FrameLayout(this)
    f.background = rounded(Kit.SURFACE_2, (sizeDp * 0.28f).toInt(), Kit.STROKE, 1)
    if (drawable != null) {
        val iv = ImageView(this)
        iv.setImageDrawable(drawable)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = dp((sizeDp * 0.2f).toInt())
        f.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(pad, pad, pad, pad)
        })
    } else {
        val t = text((letter ?: "?").take(1).uppercase(), sizeDp * 0.42f, Kit.ACCENT, Kit.medium)
        t.gravity = Gravity.CENTER
        f.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    f.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return f
}

/** Small rounded label. */
fun Context.chip(label: String, fg: Int, bg: Int): TextView = text(label, 12f, fg, Kit.medium).apply {
    setPadding(dp(10), dp(5), dp(10), dp(5))
    background = rounded(bg, 999)
}

/** Callout box for warnings / blockers. */
fun Context.callout(message: String, fg: Int, bg: Int, sp: Float = 15f, bold: Boolean = false): TextView =
    text(message, sp, fg, if (bold) Kit.medium else Kit.regular).apply {
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(bg, Kit.RADIUS_SMALL)
    }

fun Context.input(hint: String, initial: String = "", uri: Boolean = false, onGo: () -> Unit): EditText = EditText(this).apply {
    setText(initial)
    setSelection(initial.length)
    this.hint = hint
    setHintTextColor(Kit.TEXT_3)
    setTextColor(Kit.TEXT)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
    typeface = Kit.regular
    isSingleLine = true
    inputType = if (uri) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    setPadding(dp(20), dp(14), dp(20), dp(14))
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(Kit.SURFACE_FOCUS, Kit.RADIUS_SMALL, Kit.ACCENT, 2))
        addState(intArrayOf(), rounded(Kit.SURFACE, Kit.RADIUS_SMALL, Kit.STROKE, 1))
    }
    setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { onGo(); true } else false
    }
}
