package dev.tvlauncher.ui

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.SplashReveal.Part

/** The views the cold-start reveal animates. [tiles] returns visible tiles' content, left to right. */
class RevealTargets(
    val content: View,
    val topBar: View,
    val hero: View,
    val shelf: View,
    val dock: View,
    val tiles: () -> List<View>
)

/** Cold-start reveal: a logo mark over the backdrop, then the UI animates in (see [SplashReveal]). */
class SplashOverlay(context: Context, private val full: Boolean) : FrameLayout(context) {
    private val decel = DecelerateInterpolator(2f)
    /** Mark, wordmark and loading dots; animated as one group. */
    private val mark = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(MarkView(context), LinearLayout.LayoutParams(context.dp(MARK_DP), context.dp(MARK_DP)))
        addView(TextView(context).apply {
            text = WORDMARK
            style(20f, Theme.light)
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S4) })
        addView(BouncingDots(context), LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S5) })
    }

    override fun hasOverlappingRendering() = false

    init {
        auditTag(AuditTags.FULLBLEED, AuditTags.IGNORE) // transient cold-start layer
        // FULL: a soft cool halo behind the mark, so the first frame reads as a lit stage, not a void.
        if (full) background = android.graphics.drawable.GradientDrawable().apply {
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(SplashLook.HALO_CENTER, 0x00000000)
            gradientRadius = context.dp(SplashLook.HALO_RADIUS_DP).toFloat()
        }
        isClickable = false
        isFocusable = false
        addView(mark, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        mark.alpha = 0f
        mark.scaleX = 0.88f
        mark.scaleY = 0.88f
    }

    /** Hide everything the reveal will bring in. Call right after the layout is built. */
    fun prepare(t: RevealTargets) {
        if (full) {
            t.topBar.alpha = 0f; t.topBar.translationY = -dp(16)
            t.hero.alpha = 0f; t.hero.translationY = dp(24)
            t.shelf.alpha = 0f
            t.dock.alpha = 0f; t.dock.translationY = dp(32)
        } else {
            t.content.alpha = 0f; t.content.translationY = dp(20)
        }
    }

    private val timeline = if (full) null else SplashReveal.lite()
    private var startedAt = 0L

    /** Phase 1, at onCreate: the mark animates in while apps load. */
    fun start() {
        startedAt = SystemClock.uptimeMillis()
        val inStep = (timeline ?: SplashReveal.full(0)).first(Part.MARK_IN) ?: return
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.2f)).at(inStep, 0L)
    }

    /**
     * Phase 2, once the first apps are laid out: everything after MARK_IN runs on the
     * [SplashReveal] offsets, shifted so it never starts before the mark has finished coming in.
     * Removes itself from its parent when done.
     */
    fun reveal(t: RevealTargets) {
        val tiles = if (full) t.tiles().take(SplashReveal.MAX_STAGGERED_TILES) else emptyList()
        val tl = timeline ?: SplashReveal.full(tiles.size)
        val outStep = tl.first(Part.MARK_OUT) ?: return
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val wait = maxOf(0L, outStep.startMs - elapsed)
        if (full) {
            // Start values must be set before animate() captures them. Tiles past the stagger
            // cap are off-screen and simply appear.
            for (v in tiles) { v.alpha = 0f; v.translationX = dp(40) }
            t.shelf.alpha = 1f
        }
        for (s in tl.steps) {
            val shift = wait - outStep.startMs
            when (s.part) {
                Part.MARK_IN -> Unit
                Part.MARK_OUT -> {
                    mark.animate().scaleX(1.06f).scaleY(1.06f).setInterpolator(decel).at(s, shift)
                    animate().alpha(0f).setInterpolator(decel).at(s, shift)
                        .withEndAction { (parent as? ViewGroup)?.removeView(this) }
                }
                Part.TOP_BAR -> t.topBar.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.HERO -> t.hero.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.TILE -> tiles.getOrNull(s.index)?.animate()?.alpha(1f)?.translationX(0f)
                    ?.setInterpolator(decel)?.at(s, shift)
                Part.DOCK -> t.dock.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.CONTENT -> t.content.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
            }
        }
    }

    private fun android.view.ViewPropertyAnimator.at(s: SplashReveal.Step, shift: Long) =
        setStartDelay(s.startMs + shift).setDuration(s.durationMs).also { it.start() }
    private fun dp(v: Int) = context.dp(v).toFloat()

    /** A rounded-square mark with a soft silver gradient and a play glyph cut in the base color. */
    private class MarkView(context: Context) : View(context) {
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.BASE
            pathEffect = CornerPathEffect(context.dp(4).toFloat())
        }
        private val rect = RectF()
        private val play = Path()

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            rect.set(0f, 0f, w.toFloat(), h.toFloat())
            body.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFFFFFFF.toInt(), 0xFFAEB8CC.toInt(), Shader.TileMode.CLAMP)
            val cx = w * 0.53f
            val cy = h / 2f
            val r = w * 0.2f
            play.reset()
            play.moveTo(cx - r * 0.8f, cy - r)
            play.lineTo(cx + r, cy)
            play.lineTo(cx - r * 0.8f, cy + r)
            play.close()
        }

        override fun onDraw(canvas: Canvas) {
            val radius = width * 0.26f
            canvas.drawRoundRect(rect, radius, radius, body)
            canvas.drawPath(play, glyph)
        }
    }

    companion object {
        private const val MARK_DP = 88
        private const val WORDMARK = "TV Launcher"

        /** False when the user turned animations off (animator duration scale 0). */
        fun enabled(context: Context): Boolean = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (e: RuntimeException) {
            true
        }
    }
}

/** Cold-start look constants (JVM-testable). */
object SplashLook {
    /** Accent blue (AppearanceSettings.DEFAULT_ACCENT) at ~16 % in the halo's centre. */
    const val HALO_CENTER = 0x294C8DFF
    const val HALO_RADIUS_DP = 360
}
