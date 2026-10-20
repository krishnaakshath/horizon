package dev.tvlauncher.devices

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.panel.PanelMotion
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.dp

/**
 * One device card in the shared panel style: the near-opaque panel surface, a quiet round icon,
 * a title and a one-line subtitle. Calm motion: a 220 ms (LITE 160 ms) fade-and-slide in, scaled by
 * the system animator scale, and for a connection one soft accent ring around the icon. Nothing
 * animates when motion is off.
 */
class DeviceToastView(context: Context, private val mode: EffectsMode) : FrameLayout(context) {
    private val lite = mode == EffectsMode.LITE
    private val scale = PanelStyle.animatorScale(context)
    private val iconSize = context.dp(PanelStyle.G4 + PanelStyle.G1)

    private val medallion = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val disc = View(context)
    private val ring = View(context).apply { visibility = INVISIBLE }
    private val icon = ImageView(context)
    private val title = TextView(context).apply { panelText(18f, FontWeight.SEMIBOLD) }
    private val subtitle = TextView(context).apply { panelText(15f, FontWeight.REGULAR, PanelStyle.TEXT_2) }
    private val card = LinearLayout(context)
    private var running: Animator? = null
    private var pulse: Animator? = null

    val entranceMs: Long get() = PanelMotion.duration(lite, scale, Motion.enabled)
    val exitMs: Long get() = PanelMotion.closeDuration(lite, scale, Motion.enabled)

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        disc.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PanelStyle.QUIET_FILL) }
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(context.dp(2), PanelStyle.ACCENT)
        }
        medallion.addView(ring, LayoutParams(iconSize, iconSize))
        medallion.addView(disc, LayoutParams(iconSize, iconSize))
        medallion.addView(icon, LayoutParams(context.dp(PanelStyle.ICON_DP), context.dp(PanelStyle.ICON_DP), Gravity.CENTER))

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(4) })
        }
        card.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            dev.tvlauncher.ui.Glass.frost(this, full = !lite, radiusDp = 20, overContentScrim = 0f)
            setPadding(context.dp(PanelStyle.G2), context.dp(PanelStyle.G2), context.dp(PanelStyle.G3), context.dp(PanelStyle.G2))
            addView(medallion, LinearLayout.LayoutParams(iconSize, iconSize))
            addView(texts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(PanelStyle.G2) })
            minimumWidth = context.dp(280)
        }
        addView(card, LayoutParams(-2, -2))
        // Room for the ring to breathe past the card edge without being clipped.
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    }

    fun bind(event: DeviceEvent) {
        val connected = event !is DeviceEvent.Disconnected
        title.text = DeviceCopy.title(event.device, connected)
        subtitle.text = DeviceCopy.subtitle(event.device, connected)
        contentDescription = "${title.text}. ${subtitle.text}"
        icon.setImageResource(DeviceIcons.res(event.device.kind))
        icon.imageTintList = ColorStateList.valueOf(PanelStyle.TEXT)
        icon.alpha = if (connected) 1f else 0.6f
    }

    /** Fades and slides in; [onShown] runs when the card has landed. */
    fun animateIn(connected: Boolean, onShown: () -> Unit) {
        cancel()
        val d = entranceMs
        if (d <= 0) {
            alpha = 1f; card.translationX = 0f
            onShown()
            return
        }
        alpha = 0f
        card.translationX = context.dp(PanelStyle.G3).toFloat()
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).setDuration(d),
            ObjectAnimator.ofFloat(card, TRANSLATION_X, card.translationX, 0f).setDuration(d)
        )
        set.interpolator = DecelerateInterpolator(2f)
        run(set, onShown)
        if (connected) pulse = ringPulse(d).also { it.start() }
    }

    fun animateOut(onGone: () -> Unit) {
        cancel()
        val d = exitMs
        if (d <= 0) { alpha = 0f; onGone(); return }
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(this, ALPHA, alpha, 0f).setDuration(d),
            ObjectAnimator.ofFloat(card, TRANSLATION_X, card.translationX, context.dp(PanelStyle.G2).toFloat()).setDuration(d)
        )
        set.interpolator = AccelerateInterpolator()
        run(set, onGone)
    }

    private fun run(set: AnimatorSet, then: () -> Unit) {
        set.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) {
                if (running === set) running = null
                if (!cancelled) then()
            }
        })
        running = set
        set.start()
    }

    /** One soft accent ring that grows and fades around the icon. */
    private fun ringPulse(delay: Long): Animator {
        ring.visibility = VISIBLE
        ring.alpha = 0f
        return ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.6f),
            PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.6f),
            PropertyValuesHolder.ofFloat(ALPHA, 0.6f, 0f)
        ).setDuration(((if (lite) 480L else 640L) * scale.coerceAtMost(10f)).toLong()).apply {
            startDelay = delay
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { ring.visibility = INVISIBLE }
                override fun onAnimationCancel(animation: Animator) { ring.visibility = INVISIBLE }
            })
        }
    }

    /** Cancels every running animator and resets transient state. */
    fun cancel() {
        running?.cancel(); running = null
        pulse?.cancel(); pulse = null
        ring.visibility = INVISIBLE
    }

    override fun onDetachedFromWindow() {
        cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val HOLD_MS = 2600L
    }
}
