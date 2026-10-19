package dev.tvlauncher.profiles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.dp

/** The avatar: the profile's initial on its colour disc. Purely drawn (no bitmap). */
class AvatarView(context: Context) : View(context) {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Theme.semibold
    }
    private var initial = "?"

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(p: Profile) {
        val c = ProfileModel.colorOf(p)
        disc.color = c
        glyph.color = if (ProfileModel.glyphIsDark(c)) Theme.TEXT_DARK else Theme.TEXT
        initial = p.initial
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, r, disc)
        glyph.textSize = r * 1.05f
        val fm = glyph.fontMetrics
        canvas.drawText(initial, cx, cy - (fm.ascent + fm.descent) / 2f, glyph)
    }
}

/**
 * The top bar's avatar chip: avatar + name, styled like the other pills (white when focused).
 * Shown only while two or more profiles exist ([ProfileState.showChip]); otherwise GONE, so the
 * top bar stays as minimal as before.
 */
class ProfileChip(context: Context, onClick: (View) -> Unit) : LinearLayout(context) {
    private val avatar = AvatarView(context)
    private val label = TextView(context).apply { style(Theme.SP_BUTTON, Theme.medium); maxLines = 1 }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        visibility = GONE
        setPadding(context.dp(6), context.dp(6), context.dp(16), context.dp(6))
        addView(avatar, LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(8) })
        addView(label)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setColor(Theme.TEXT); cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
            })
            addState(intArrayOf(), Glass.clear(context, false, Theme.RADIUS_PILL))
        }
        Glass.track(this)
        setOnClickListener { onClick(this) }
    }

    /** Shows [state]'s active profile, or hides the chip when there is only one profile. */
    fun bind(state: ProfileState) {
        val show = state.showChip
        if (!show && hasFocus()) (parent as? View)?.let { focusSearch(FOCUS_RIGHT)?.requestFocus() }
        visibility = if (show) VISIBLE else GONE
        val p = state.active
        avatar.bind(p)
        label.text = p.name
        contentDescription = "Profile: ${p.name}. Switch profile"
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        label.setTextColor(if (gainFocus) Theme.TEXT_DARK else Theme.TEXT)
        animate().scaleX(if (gainFocus) 1.06f else 1f).scaleY(if (gainFocus) 1.06f else 1f)
            .setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
    }
}
