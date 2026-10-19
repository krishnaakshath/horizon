package dev.tvlauncher.devices

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Outline
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * One premium glass card: circular icon medallion with a kind-specific accent, a one-shot pulse
 * ring animation, title and subtitle. Each family of devices has its own entrance:
 * consoles pop with an accent glow sweep, USB slides in with a plug-click bounce, Bluetooth audio
 * gets a longer ripple, controllers wiggle. LITE uses shorter timings, one ring and no layers.
 */
class DeviceToastView(context: Context, private val mode: EffectsMode) : FrameLayout(context) {
    private val lite = mode == EffectsMode.LITE
    private val animationsOn = animatorScale(context) > 0f
    private val medallionSize = context.dp(56)
    private val ringCount = if (!animationsOn) 0 else if (lite) 1 else 2

    private val medallion = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val disc = View(context)
    private val icon = ImageView(context)
    private val rings = ArrayList<View>()
    private val title = TextView(context)
    private val subtitle = TextView(context)
    private val sweep = SweepView(context)
    private val card = FrameLayout(context)
    private var running: Animator? = null
    private var pulse: Animator? = null
    private var kind = DeviceKind.UNKNOWN
    private var accent = DeviceAccent.SLATE

    val entranceMs: Long get() = if (lite) 160L else 220L
    val exitMs: Long get() = if (lite) 140L else 180L

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        val radius = context.dp(20).toFloat()
        card.background = GradientDrawable().apply {
            setColor(0xCC151922.toInt())
            cornerRadius = radius
            setStroke(context.dp(1), 0x33FFFFFF)
        }
        card.clipChildren = false
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }

        repeat(ringCount + 1) { // one spare for the longer Bluetooth ripple
            rings.add(View(context).apply { visibility = INVISIBLE })
        }
        for (r in rings) medallion.addView(r, LayoutParams(medallionSize, medallionSize))
        medallion.addView(disc, LayoutParams(medallionSize, medallionSize))
        medallion.addView(icon, LayoutParams(context.dp(28), context.dp(28), Gravity.CENTER))

        title.style(20f, Theme.medium, Theme.TEXT)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        subtitle.style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        subtitle.maxLines = 1
        subtitle.ellipsize = android.text.TextUtils.TruncateAt.END
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(2) })
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(context.dp(16), context.dp(14), context.dp(24), context.dp(14))
            addView(medallion, LinearLayout.LayoutParams(medallionSize, medallionSize))
            addView(texts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(16) })
        }
        card.addView(row, LayoutParams(-2, -2))
        card.addView(sweep, LayoutParams(context.dp(120), LayoutParams.MATCH_PARENT))
        sweep.visibility = GONE
        addView(card, LayoutParams(-2, -2))
        minimumWidth = context.dp(300)
        // The medallion rings extend beyond the card inside the host; give them room.
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    }

    fun bind(event: DeviceEvent) {
        val connected = event is DeviceEvent.Connected
        kind = event.device.kind
        accent = DeviceAccent.of(kind)
        title.text = DeviceCopy.title(event.device, connected)
        subtitle.text = DeviceCopy.subtitle(event.device, connected)
        contentDescription = "${title.text}. ${subtitle.text}"
        icon.setImageResource(DeviceIcons.res(kind))
        icon.setColorFilter(Color.WHITE)
        icon.alpha = if (connected) 1f else 0.7f
        disc.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((accent and 0x00FFFFFF) or (if (connected) 0x66000000 else 0x33000000))
            setStroke(context.dp(2), (accent and 0x00FFFFFF) or (if (connected) 0xFF000000.toInt() else 0x80000000.toInt()))
        }
        for (r in rings) r.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(context.dp(2), accent)
        }
        sweep.accent = accent
    }

    /** Plays the kind-specific entrance. [onShown] runs when the card has landed. */
    fun animateIn(connected: Boolean, onShown: () -> Unit) {
        cancel()
        val d = if (animationsOn) entranceMs else 0L
        if (!lite && animationsOn) card.setLayerType(LAYER_TYPE_HARDWARE, null)
        val slide = context.dp(40).toFloat()
        alpha = 0f
        card.translationX = slide
        medallion.scaleX = 1f; medallion.scaleY = 1f; icon.rotation = 0f

        val set = AnimatorSet()
        val parts = ArrayList<Animator>()
        parts.add(ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).setDuration(d))
        when {
            kind == DeviceKind.USB_STORAGE || kind == DeviceKind.USB_HID_OTHER -> {
                // Slide in from the edge and settle with a short "plug click" bounce.
                val kf = PropertyValuesHolder.ofKeyframe(
                    TRANSLATION_X,
                    Keyframe.ofFloat(0f, context.dp(120).toFloat()),
                    Keyframe.ofFloat(0.7f, -context.dp(8).toFloat()),
                    Keyframe.ofFloat(1f, 0f)
                )
                parts.add(ObjectAnimator.ofPropertyValuesHolder(card, kf).setDuration(d).apply { interpolator = DecelerateInterpolator() })
            }
            else -> parts.add(ObjectAnimator.ofFloat(card, TRANSLATION_X, slide, 0f).setDuration(d).apply { interpolator = DecelerateInterpolator() })
        }
        if (connected && animationsOn) {
            when {
                DeviceAccent.isConsole(kind) -> {
                    medallion.scaleX = 0.4f; medallion.scaleY = 0.4f
                    val pop = ObjectAnimator.ofPropertyValuesHolder(
                        medallion,
                        PropertyValuesHolder.ofFloat(SCALE_X, 0.4f, 1f),
                        PropertyValuesHolder.ofFloat(SCALE_Y, 0.4f, 1f)
                    ).setDuration(if (lite) 220L else 320L)
                    pop.interpolator = OvershootInterpolator(2.2f)
                    pop.startDelay = if (lite) 20L else 40L
                    parts.add(pop)
                    if (!lite) parts.add(sweepAnimator())
                }
                kind == DeviceKind.GAMEPAD || kind == DeviceKind.BT_GAMEPAD -> {
                    val kf = PropertyValuesHolder.ofKeyframe(
                        ROTATION,
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.25f, -16f), Keyframe.ofFloat(0.55f, 12f),
                        Keyframe.ofFloat(0.8f, -6f), Keyframe.ofFloat(1f, 0f)
                    )
                    parts.add(ObjectAnimator.ofPropertyValuesHolder(icon, kf).setDuration(if (lite) 320L else 480L).apply { startDelay = d / 2 })
                }
                else -> {
                    medallion.scaleX = 0.8f; medallion.scaleY = 0.8f
                    parts.add(ObjectAnimator.ofPropertyValuesHolder(
                        medallion,
                        PropertyValuesHolder.ofFloat(SCALE_X, 0.8f, 1f),
                        PropertyValuesHolder.ofFloat(SCALE_Y, 0.8f, 1f)
                    ).setDuration(d + 60))
                }
            }
        }
        set.playTogether(parts)
        set.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) {
                running = null
                if (!lite) card.setLayerType(LAYER_TYPE_NONE, null)
                if (!cancelled) onShown()
            }
        })
        running = set
        set.start()
        if (connected) startPulse(d)
    }

    fun animateOut(onGone: () -> Unit) {
        cancel()
        val d = if (animationsOn) exitMs else 0L
        if (!lite && animationsOn) card.setLayerType(LAYER_TYPE_HARDWARE, null)
        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(this, ALPHA, alpha, 0f).setDuration(d),
            ObjectAnimator.ofFloat(card, TRANSLATION_X, card.translationX, context.dp(24).toFloat()).setDuration(d)
        )
        set.interpolator = AccelerateInterpolator()
        set.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) {
                running = null
                if (!lite) card.setLayerType(LAYER_TYPE_NONE, null)
                if (!cancelled) onGone()
            }
        })
        running = set
        set.start()
    }

    /** Cancels every running animator and resets transient state. */
    fun cancel() {
        running?.cancel(); running = null
        pulse?.cancel(); pulse = null
        for (r in rings) { r.visibility = INVISIBLE; r.animate().cancel() }
        sweep.visibility = GONE
        sweep.animate().cancel()
        if (!lite) card.setLayerType(LAYER_TYPE_NONE, null)
    }

    private fun sweepAnimator(): Animator {
        sweep.visibility = VISIBLE
        val w = context.dp(120).toFloat()
        val a = ObjectAnimator.ofFloat(sweep, TRANSLATION_X, -w, context.dp(380).toFloat()).setDuration(620)
        a.startDelay = 80
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { sweep.visibility = GONE }
            override fun onAnimationCancel(animation: Animator) { sweep.visibility = GONE }
        })
        return a
    }

    /** One-shot expanding, fading rings around the medallion (3 waves for Bluetooth audio in FULL). */
    private fun startPulse(delay: Long) {
        if (ringCount == 0) return
        val waves = if (!lite && (kind == DeviceKind.BT_HEADPHONES || kind == DeviceKind.BT_SPEAKER)) 3 else ringCount
        val animators = ArrayList<Animator>()
        for (i in 0 until waves.coerceAtMost(rings.size)) {
            val ring = rings[i]
            ring.visibility = VISIBLE
            ring.alpha = 0f
            val a = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.55f),
                PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.55f),
                PropertyValuesHolder.ofFloat(ALPHA, 0.55f, 0f)
            ).setDuration(if (lite) 600L else 800L)
            a.startDelay = delay + i * 220L
            a.interpolator = DecelerateInterpolator()
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { ring.visibility = INVISIBLE }
                override fun onAnimationCancel(animation: Animator) { ring.visibility = INVISIBLE }
            })
            animators.add(a)
        }
        val set = AnimatorSet()
        set.playTogether(animators)
        pulse = set
        set.start()
    }

    override fun onDetachedFromWindow() {
        cancel()
        super.onDetachedFromWindow()
    }

    /** Soft horizontal accent band swept once across a console card. */
    private class SweepView(context: Context) : View(context) {
        private val paint = Paint()
        var accent: Int = DeviceAccent.SLATE
            set(v) { field = v; paint.shader = null; invalidate() }

        override fun onDraw(canvas: Canvas) {
            if (paint.shader == null) {
                val mid = (accent and 0x00FFFFFF) or 0x55000000
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    intArrayOf(Color.TRANSPARENT, mid, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    companion object {
        const val HOLD_MS = 2600L

        /** Settings.Global.ANIMATOR_DURATION_SCALE; 1 when unreadable. */
        fun animatorScale(context: Context): Float = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: RuntimeException) { 1f }
    }
}
